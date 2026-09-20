package com.nanocore.adminhub.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Plugin(
    id          = "nanocore-adminhub",
    name        = "NanoCore AdminHub",
    version     = "0.1.0",
    description = "Admin hub — /admServerHub для управления серверами",
    authors     = {"NanoCore"}
)
public class AdminHubVelocityPlugin {

    public  static final String PERMISSION   = "nanocore.adminhub";
    private static final String SERVER_NAME  = "nanocore-adminhub";
    private static final String NANOCORE_API = "http://localhost:8080/admin/api";
    private static final int    ADMIN_PORT   = 25599;

    private final ProxyServer server;
    private final Logger      logger;
    private final AtomicInteger adminCount = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private volatile boolean hubRunning = false;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    @Inject
    public AdminHubVelocityPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        CommandManager cm  = server.getCommandManager();
        CommandMeta    meta = cm.metaBuilder("admserverhub")
            .aliases("admhub", "ahub")
            .plugin(this).build();
        cm.register(meta, new AdminHubCommand(this));
        logger.info("NanoCore AdminHub загружен. Команда: /admServerHub");
    }

    // ------------------------------------------------------------------
    // Запуск хаба
    // ------------------------------------------------------------------

    public void openHubFor(com.velocitypowered.api.proxy.Player player) {
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(Component.text("❌ Нет доступа.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("⏳ Запускаю Admin Hub...", NamedTextColor.YELLOW));

        scheduler.submit(() -> {
            try {
                // Стартуем admin server через NanoCore API
                if (!hubRunning) {
                    postToNanoCore(NANOCORE_API + "/adminhub", "{\"action\":\"start\"}");
                    hubRunning = true;
                    // Ждём пока сервер поднимется
                    Thread.sleep(8000);
                }

                // Регистрируем сервер в Velocity если ещё нет
                Optional<RegisteredServer> existing = server.getServer(SERVER_NAME);
                if (existing.isEmpty()) {
                    server.registerServer(new ServerInfo(
                        SERVER_NAME,
                        new InetSocketAddress("127.0.0.1", ADMIN_PORT)
                    ));
                }

                // Подключаем игрока
                server.getServer(SERVER_NAME).ifPresent(rs ->
                    player.createConnectionRequest(rs).fireAndForget()
                );
                adminCount.incrementAndGet();

            } catch (Exception ex) {
                player.sendMessage(Component.text("❌ Ошибка: " + ex.getMessage(), NamedTextColor.RED));
                logger.error("AdminHub error", ex);
            }
        });
    }

    // ------------------------------------------------------------------
    // Мониторинг отключений
    // ------------------------------------------------------------------

    @Subscribe
    public void onLeave(KickedFromServerEvent e) {
        if (!SERVER_NAME.equals(e.getServer().getServerInfo().getName())) return;
        if (!e.getPlayer().hasPermission(PERMISSION)) return;
        checkAndStop();
    }

    @Subscribe
    public void onConnect(ServerConnectedEvent e) {
        // Игрок перешёл С admin hub на другой сервер
        if (e.getPreviousServer().isPresent() &&
            SERVER_NAME.equals(e.getPreviousServer().get().getServerInfo().getName())) {
            checkAndStop();
        }
    }

    private void checkAndStop() {
        int remaining = (int) server.getAllPlayers().stream()
            .filter(p -> server.getServer(SERVER_NAME)
                .map(rs -> rs.equals(p.getCurrentServer().map(cs -> cs.getServer()).orElse(null)))
                .orElse(false))
            .count();

        if (remaining == 0 && hubRunning) {
            scheduler.schedule(() -> {
                // Ещё раз проверяем после паузы
                long actualRemaining = server.getAllPlayers().stream()
                    .filter(p -> p.getCurrentServer()
                        .map(cs -> SERVER_NAME.equals(cs.getServerInfo().getName()))
                        .orElse(false))
                    .count();

                if (actualRemaining == 0) {
                    logger.info("Все вышли из AdminHub — останавливаю сервер");
                    try { postToNanoCore(NANOCORE_API + "/adminhub", "{\"action\":\"stop\"}"); }
                    catch (Exception ex) { logger.error("Stop hub error", ex); }
                    hubRunning = false;
                    server.getServer(SERVER_NAME).ifPresent(rs ->
                        server.unregisterServer(rs.getServerInfo()));
                }
            }, 10, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------
    // HTTP helper
    // ------------------------------------------------------------------

    void postToNanoCore(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HTTP.send(req, HttpResponse.BodyHandlers.discarding());
    }

    public boolean isHubRunning() { return hubRunning; }
}

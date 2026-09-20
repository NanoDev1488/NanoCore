package com.nanocore.watchdog;

import com.nanocore.core.ServerInstance;
import com.nanocore.core.ServerManager;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watchdog — следит за процессами серверов и перезапускает при краше.
 *
 * Настройки через nanocore.properties каждого сервера:
 *   watchdog=true           включить watchdog (по умолчанию: true)
 *   watchdog_max_restarts=5 максимум рестартов за 10 минут (0 = без лимита)
 *   watchdog_cooldown=10    секунды ожидания перед рестартом
 */
public class Watchdog {

    private static final int CHECK_INTERVAL_SEC = 5;
    private static final int WINDOW_MINUTES     = 10;

    private final ServerManager manager;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-watchdog");
            t.setDaemon(true);
            return t;
        });

    // crashCount[id] -> кол-во крашей за последние WINDOW_MINUTES минут
    private final Map<String, AtomicInteger> crashCount = new ConcurrentHashMap<>();
    private final Map<String, Long>          lastCrash  = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public Watchdog(ServerManager manager) {
        this.manager = manager;
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::check,
            CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
        System.out.println("[watchdog] Запущен — проверка каждые " + CHECK_INTERVAL_SEC + "с");
    }

    public void stop() {
        running = false;
        scheduler.shutdownNow();
    }

    private void check() {
        for (String id : manager.ids()) {
            ServerInstance inst = manager.getInstance(id);
            if (inst == null || !inst.getConfig().isWatchdogEnabled()) continue;
            if (inst.isRunning()) continue;
            if (!inst.wasStarted()) continue; // сервер ещё не запускался — не трогаем

            // Сервер упал — проверяем лимит рестартов
            int maxRestarts = inst.getConfig().getWatchdogMaxRestarts();
            long now = System.currentTimeMillis();

            // Сброс счётчика если прошло больше WINDOW_MINUTES
            Long last = lastCrash.get(id);
            if (last != null && (now - last) > WINDOW_MINUTES * 60_000L) {
                crashCount.computeIfAbsent(id, k -> new AtomicInteger()).set(0);
            }

            int count = crashCount.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            lastCrash.put(id, now);

            if (maxRestarts > 0 && count > maxRestarts) {
                System.out.printf("[watchdog] ❌ %s превысил лимит рестартов (%d/%d за %d мин) — не перезапускаю%n",
                    id, count, maxRestarts, WINDOW_MINUTES);
                manager.notifyChannel("[watchdog] ❌ " + id + " превысил лимит рестартов — требуется ручное вмешательство");
                continue;
            }

            int cooldown = inst.getConfig().getWatchdogCooldown();
            System.out.printf("[watchdog] ⚠  %s упал (краш #%d) — перезапуск через %ds...%n",
                id, count, cooldown);
            manager.notifyChannel("[watchdog] ⚠ " + id + " упал, перезапускаю... (краш #" + count + ")");

            scheduler.schedule(() -> {
                try {
                    System.out.println("[watchdog] 🔄 Перезапускаю " + id);
                    inst.start(manager.getJavaExec());
                    manager.notifyChannel("[watchdog] ✅ " + id + " перезапущен");
                } catch (Exception e) {
                    System.out.println("[watchdog] ❌ Ошибка рестарта " + id + ": " + e.getMessage());
                }
            }, cooldown, TimeUnit.SECONDS);
        }
    }
}

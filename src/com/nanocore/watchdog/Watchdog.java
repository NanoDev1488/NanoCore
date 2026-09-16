package com.nanocore.watchdog;

import com.nanocore.core.ServerInstance;
import com.nanocore.core.ServerManager;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watchdog — следит за процессами серверов и перезапускает при краше.
 *
 * Настройки в nanocore.properties каждого сервера:
 *   watchdog=true              включить (по умолчанию true)
 *   watchdog_max_restarts=5    макс. рестартов за 10 мин (0 = без лимита)
 *   watchdog_cooldown=10       секунды ожидания перед рестартом
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

    private final Map<String, AtomicInteger> crashCount = new ConcurrentHashMap<>();
    private final Map<String, Long>          lastCrash  = new ConcurrentHashMap<>();

    public Watchdog(ServerManager manager) {
        this.manager = manager;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::check,
            CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
        System.out.println("[watchdog] Started — checking every " + CHECK_INTERVAL_SEC + "s");
    }

    public void stop() { scheduler.shutdownNow(); }

    private void check() {
        for (String id : manager.ids()) {
            ServerInstance inst = manager.getInstance(id);
            if (inst == null || !inst.getConfig().isWatchdogEnabled()) continue;
            if (inst.isRunning() || !inst.wasStarted()) continue;

            int maxRestarts = inst.getConfig().getWatchdogMaxRestarts();
            long now = System.currentTimeMillis();

            Long last = lastCrash.get(id);
            if (last != null && (now - last) > WINDOW_MINUTES * 60_000L)
                crashCount.computeIfAbsent(id, k -> new AtomicInteger()).set(0);

            int count = crashCount.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
            lastCrash.put(id, now);

            if (maxRestarts > 0 && count > maxRestarts) {
                System.out.printf("[watchdog] ❌ %s exceeded restart limit (%d/%d in %d min)%n",
                    id, count, maxRestarts, WINDOW_MINUTES);
                manager.notifyChannel("[watchdog] ❌ " + id + " exceeded restart limit — manual intervention required");
                continue;
            }

            int cooldown = inst.getConfig().getWatchdogCooldown();
            System.out.printf("[watchdog] ⚠  %s crashed (#%d) — restarting in %ds...%n", id, count, cooldown);
            manager.notifyChannel("[watchdog] ⚠ " + id + " crashed, restarting... (#" + count + ")");

            scheduler.schedule(() -> {
                try {
                    inst.start(manager.getJavaExec());
                    manager.notifyChannel("[watchdog] ✅ " + id + " restarted");
                } catch (Exception e) {
                    System.out.println("[watchdog] ❌ Restart failed " + id + ": " + e.getMessage());
                }
            }, cooldown, TimeUnit.SECONDS);
        }
    }
}

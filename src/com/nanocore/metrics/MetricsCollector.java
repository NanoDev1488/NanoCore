package com.nanocore.metrics;

import com.nanocore.core.ServerInstance;
import com.nanocore.core.ServerManager;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class MetricsCollector {

    private static final int INTERVAL_SEC = 30;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServerManager manager;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nanocore-metrics");
            t.setDaemon(true);
            return t;
        });

    private final Map<String, ServerMetrics> latest = new ConcurrentHashMap<>();

    public MetricsCollector(ServerManager manager) {
        this.manager = manager;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::collect, 5, INTERVAL_SEC, TimeUnit.SECONDS);
        System.out.println("[metrics] Started — collecting every " + INTERVAL_SEC + "s");
    }

    public void stop() { scheduler.shutdownNow(); }

    public Map<String, ServerMetrics> getLatest() { return Collections.unmodifiableMap(latest); }
    public ServerMetrics get(String id) { return latest.getOrDefault(id, new ServerMetrics()); }

    private void collect() {
        for (String id : manager.ids()) {
            ServerInstance inst = manager.getInstance(id);
            if (inst == null) continue;
            ServerMetrics m = new ServerMetrics();
            m.timestamp = LocalDateTime.now().format(TS);
            m.running   = inst.isRunning();
            m.pid       = inst.pid();
            m.uptimeSec = inst.uptimeSeconds();
            if (m.running && m.pid > 0) {
                m.ramMB = readRamMB(m.pid);
                parseLog(inst.tail(30), m);
            }
            latest.put(id, m);
        }
    }

    private long readRamMB(long pid) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/" + pid + "/status")))
                if (line.startsWith("VmRSS:"))
                    return Long.parseLong(line.split("\\s+")[1]) / 1024;
        } catch (Exception ignored) {}
        return -1;
    }

    private void parseLog(List<String> tail, ServerMetrics m) {
        for (int i = tail.size() - 1; i >= 0; i--) {
            String line = tail.get(i).toLowerCase();
            if (m.tps < 0 && line.contains("tps from last")) {
                try {
                    String[] parts = line.split(":");
                    String[] vals  = parts[parts.length-1].trim().split(",");
                    m.tps = Double.parseDouble(vals[0].trim().replaceAll("[^0-9.]",""));
                } catch (Exception ignored) {}
            }
            if (m.onlinePlayers < 0 && line.contains("there are") && line.contains("players online")) {
                try {
                    String[] w = line.split("\\s+");
                    for (int j = 0; j < w.length - 1; j++)
                        if (w[j].equals("are")) { m.onlinePlayers = Integer.parseInt(w[j+1]); break; }
                } catch (Exception ignored) {}
            }
            if (m.tps >= 0 && m.onlinePlayers >= 0) break;
        }
    }

    public void printMetrics() {
        System.out.println("\n=== NanoCore Metrics ===");
        System.out.printf("%-20s %-8s %-8s %-8s %-8s %-10s%n",
            "Server","Status","RAM","TPS","Online","Uptime");
        System.out.println("-".repeat(65));
        for (String id : manager.ids()) {
            ServerMetrics m = get(id);
            System.out.printf("%-20s %-8s %-8s %-8s %-8s %-10s%n",
                id,
                m.running ? "\u001B[32mON\u001B[0m" : "\u001B[90mOFF\u001B[0m",
                m.ramMB > 0 ? m.ramMB + "MB" : "—",
                m.tps >= 0  ? String.format("%.1f",m.tps) : "—",
                m.onlinePlayers >= 0 ? String.valueOf(m.onlinePlayers) : "—",
                formatUptime(m.uptimeSec));
        }
    }

    private String formatUptime(long sec) {
        if (sec < 0) return "—";
        long h=sec/3600, m=(sec%3600)/60, s=sec%60;
        if (h>0) return h+"h"+m+"m"; if (m>0) return m+"m"+s+"s"; return s+"s";
    }

    public static class ServerMetrics {
        public String  timestamp="—"; public boolean running=false;
        public long    pid=-1; public long ramMB=-1;
        public double  tps=-1; public int onlinePlayers=-1; public long uptimeSec=-1;
        public String toJson() {
            return String.format(
                "{\"running\":%b,\"pid\":%d,\"ram_mb\":%d,\"tps\":%.1f,\"online\":%d,\"uptime_sec\":%d,\"ts\":\"%s\"}",
                running,pid,ramMB,tps,onlinePlayers,uptimeSec,timestamp);
        }
    }
}

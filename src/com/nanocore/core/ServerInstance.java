package com.nanocore.core;

import com.nanocore.config.ServerConfig;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ServerInstance {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int TAIL_BUFFER = 200;

    private final ServerConfig config;
    private final Path logFile;
    private Process process;
    private Thread logThread;
    private final List<String> tailBuffer = new ArrayList<>(TAIL_BUFFER);

    public ServerInstance(ServerConfig config) {
        this.config = config;
        this.logFile = config.workdir.resolve("nanocore.log");
    }

    public ServerConfig getConfig() { return config; }

    public synchronized void start(String javaExecutable) throws IOException {
        if (isRunning()) { System.out.println("[" + config.id + "] Already running PID " + process.pid()); return; }
        if (config.type == ServerType.PAPER) config.ensurePaperEnv();

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable);
        cmd.addAll(config.buildJvmArgs());
        System.out.println("[" + config.id + "] Starting: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(config.workdir.toFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        startLogThread();
        System.out.println("✅ [" + config.id + "] started PID " + process.pid());
    }

    public synchronized void stop(int timeoutSeconds) {
        if (!isRunning()) { System.out.println("[" + config.id + "] Not running."); return; }
        System.out.println("[" + config.id + "] Sending '" + config.type.stopCommand() + "'...");
        sendCommand(config.type.stopCommand());
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                System.out.println("[" + config.id + "] Timeout — SIGTERM");
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    System.out.println("[" + config.id + "] SIGKILL");
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        System.out.println("🛑 [" + config.id + "] stopped.");
    }

    public void restart(String javaExecutable) throws IOException {
        stop(30);
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        start(javaExecutable);
    }

    public synchronized boolean sendCommand(String command) {
        if (!isRunning()) return false;
        try {
            OutputStream stdin = process.getOutputStream();
            stdin.write((command + "\n").getBytes());
            stdin.flush();
            return true;
        } catch (IOException e) { System.out.println("[" + config.id + "] Send failed: " + e.getMessage()); return false; }
    }

    private void startLogThread() {
        logThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedWriter writer = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String stamped = "[" + LocalDateTime.now().format(TS) + "] " + line;
                    writer.write(stamped); writer.newLine(); writer.flush();
                    synchronized (tailBuffer) {
                        tailBuffer.add(stamped);
                        if (tailBuffer.size() > TAIL_BUFFER) tailBuffer.remove(0);
                    }
                }
            } catch (IOException ignored) {}
        }, "log-" + config.id);
        logThread.setDaemon(true);
        logThread.start();
    }

    public List<String> tail(int lines) {
        synchronized (tailBuffer) {
            int from = Math.max(0, tailBuffer.size() - lines);
            return new ArrayList<>(tailBuffer.subList(from, tailBuffer.size()));
        }
    }

    public synchronized boolean isRunning() { return process != null && process.isAlive(); }
    public synchronized long pid() { return isRunning() ? process.pid() : -1; }

    public String statusLine() {
        String status = isRunning() ? "\u001B[32mRUNNING\u001B[0m PID=" + pid() : "\u001B[90mSTOPPED\u001B[0m";
        return String.format("  %-20s %-10s port=%-6d heap=%-6s %s",
            config.id, config.type, config.port, config.heap, status);
    }
}

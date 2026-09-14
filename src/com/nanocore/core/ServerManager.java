package com.nanocore.core;

import com.nanocore.config.ServerConfig;
import com.nanocore.download.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ServerManager {

    public static final String[] PAPER_VERSIONS = {"1.21.4", "1.21.11"};

    private final Path         baseDir;
    private final String       javaExec;
    private final PortRegistry ports;
    private final Map<String, ServerInstance> instances = new LinkedHashMap<>();

    public ServerManager(Path baseDir, String javaExec) {
        this.baseDir = baseDir; this.javaExec = javaExec;
        this.ports = new PortRegistry(baseDir);
        discover();
    }

    private void discover() {
        instances.clear();
        Path proxyDir = baseDir.resolve("proxy");
        if (Files.isDirectory(proxyDir)) {
            findJar(proxyDir).ifPresent(jar -> {
                ServerConfig cfg = ServerConfig.load("velocity", ServerType.VELOCITY,
                    proxyDir, jar.getFileName().toString(), ports);
                instances.put("velocity", new ServerInstance(cfg));
            });
        }
        Path serversDir = baseDir.resolve("servers");
        if (Files.isDirectory(serversDir)) {
            listSorted(serversDir).forEach(sub -> {
                if (!Files.isDirectory(sub)) return;
                findJar(sub).ifPresent(jar -> {
                    String id = sub.getFileName().toString();
                    ServerType t = id.toLowerCase().startsWith("bungee") ? ServerType.BUNGEE : ServerType.PAPER;
                    ServerConfig cfg = ServerConfig.load(id, t, sub, jar.getFileName().toString(), ports);
                    instances.put(id, new ServerInstance(cfg));
                });
            });
        }
        if (instances.isEmpty())
            System.out.println("No servers found. Run: java -jar NanoCore.jar download");
        else {
            System.out.println("Servers found: " + instances.size());
            instances.values().forEach(i -> System.out.println("  " + i.getConfig()));
        }
    }

    private Optional<Path> findJar(Path dir) {
        try (Stream<Path> s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".jar")).findFirst(); }
        catch (IOException e) { return Optional.empty(); }
    }

    private List<Path> listSorted(Path dir) {
        try (Stream<Path> s = Files.list(dir)) { return s.sorted().toList(); }
        catch (IOException e) { return List.of(); }
    }

    public void downloadAll() {
        System.out.println("\n[download] Downloading all server jars...");
        try { FillApiClient.downloadVelocity(baseDir.resolve("proxy")); }
        catch (Exception e) { System.out.println("Velocity: " + e.getMessage()); }
        try { FillApiClient.downloadBungeeCord(baseDir.resolve("servers/bungee-legacy")); }
        catch (Exception e) { System.out.println("BungeeCord: " + e.getMessage()); }
        for (String ver : PAPER_VERSIONS) {
            try { FillApiClient.downloadPaper(ver, baseDir.resolve("servers/paper-" + ver)); }
            catch (Exception e) { System.out.println("Paper " + ver + ": " + e.getMessage()); }
        }
        System.out.println("\n[download] Done. Re-discovering servers...");
        discover();
    }

    public void start(String id)   { withInstance(id, inst -> { try { inst.start(javaExec); } catch (IOException e) { System.out.println("Start error: " + e.getMessage()); } }); }
    public void stop(String id)    { withInstance(id, inst -> inst.stop(30)); }
    public void restart(String id) { withInstance(id, inst -> { try { inst.restart(javaExec); } catch (IOException e) { System.out.println("Restart error: " + e.getMessage()); } }); }
    public void startAll() { instances.keySet().forEach(this::start); }
    public void stopAll()  { new ArrayList<>(instances.keySet()).forEach(this::stop); }

    public boolean sendCommand(String id, String cmd) {
        ServerInstance inst = instances.get(id);
        if (inst == null) { System.out.println("Server '" + id + "' not found."); return false; }
        return inst.sendCommand(cmd);
    }

    public List<String> tail(String id, int lines) {
        ServerInstance inst = instances.get(id);
        if (inst == null) return List.of("Server '" + id + "' not found.");
        return inst.tail(lines);
    }

    public void printStatus() {
        System.out.println("\n=== NanoCore Server Status ===");
        if (instances.isEmpty()) System.out.println("No servers. Run: java -jar NanoCore.jar download");
        else instances.values().forEach(i -> System.out.println(i.statusLine()));
        System.out.println("==============================");
    }

    public void printPorts() { ports.printAll(); }
    public Set<String> ids() { return Collections.unmodifiableSet(instances.keySet()); }

    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[NanoCore] Shutting down all servers...");
            stopAll();
        }, "nanocore-shutdown"));
    }

    private void withInstance(String id, Consumer<ServerInstance> action) {
        if ("all".equalsIgnoreCase(id)) { instances.values().forEach(action); return; }
        ServerInstance inst = instances.get(id);
        if (inst == null) System.out.println("Server '" + id + "' not found. Available: " + instances.keySet());
        else action.accept(inst);
    }
}

package com.nanocore.core;

import com.nanocore.config.ServerConfig;
import com.nanocore.download.*;
import java.util.regex.*;
import com.nanocore.notify.NotifyService;

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
    private NotifyService      notify;
    private final Map<String, ServerInstance> instances = new LinkedHashMap<>();

    public ServerManager(Path baseDir, String javaExec) {
        this.baseDir  = baseDir;
        this.javaExec = javaExec;
        this.ports    = new PortRegistry(baseDir);
        discover();
    }

    public void setNotify(NotifyService n) { this.notify = n; }

    private void discover() {
        instances.clear();
        Path proxyDir = baseDir.resolve("proxy");
        if (Files.isDirectory(proxyDir))
            findJar(proxyDir).ifPresent(jar -> {
                ServerConfig cfg = ServerConfig.load("velocity", ServerType.VELOCITY,
                    proxyDir, jar.getFileName().toString(), ports);
                instances.put("velocity", new ServerInstance(cfg));
            });

        Path serversDir = baseDir.resolve("servers");
        if (Files.isDirectory(serversDir))
            listSorted(serversDir).forEach(sub -> {
                if (!Files.isDirectory(sub)) return;
                findJar(sub).ifPresent(jar -> {
                    String id = sub.getFileName().toString();
                    ServerType t = id.toLowerCase().startsWith("bungee") ? ServerType.BUNGEE : ServerType.PAPER;
                    instances.put(id, new ServerInstance(
                        ServerConfig.load(id, t, sub, jar.getFileName().toString(), ports)));
                });
            });

        if (instances.isEmpty()) System.out.println("No servers found. Run: java -jar NanoCore.jar download");
        else { System.out.println("Servers found: " + instances.size()); instances.values().forEach(i -> System.out.println("  " + i.getConfig())); }
    }

    private Optional<Path> findJar(Path dir) {
        try (Stream<Path> s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".jar")).findFirst(); }
        catch (IOException e) { return Optional.empty(); }
    }
    private List<Path> listSorted(Path dir) {
        try (Stream<Path> s = Files.list(dir)) { return s.sorted().toList(); }
        catch (IOException e) { return List.of(); }
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    public void downloadAll() {
        System.out.println("\n[download] Downloading all server jars...");
        try { FillApiClient.downloadVelocity(baseDir.resolve("proxy")); }
        catch (Exception e) { System.out.println("❌ Velocity: " + e.getMessage()); }
        try { FillApiClient.downloadBungeeCord(baseDir.resolve("servers/bungee-legacy")); }
        catch (Exception e) { System.out.println("❌ BungeeCord: " + e.getMessage()); }
        for (String v : PAPER_VERSIONS) {
            try { FillApiClient.downloadPaper(v, baseDir.resolve("servers/paper-" + v)); }
            catch (Exception e) { System.out.println("❌ Paper " + v + ": " + e.getMessage()); }
        }
        System.out.println("\n[download] Done. Re-discovering servers...");
        discover();
    }

    // ------------------------------------------------------------------
    // Velocity forwarding secret sync
    // ------------------------------------------------------------------

    public void syncVelocitySecret() {
        Path secretFile = baseDir.resolve("proxy").resolve("forwarding.secret");
        String secret;
        try {
            if (Files.exists(secretFile)) {
                secret = Files.readString(secretFile).trim();
            } else {
                secret = generateSecret();
                Files.writeString(secretFile, secret);
                System.out.println("[secret] Сгенерирован новый forwarding.secret");
            }

            // Синхронизируем с Paper-серверами через paper-global.yml
            for (String id : instances.keySet()) {
                var inst = instances.get(id);
                if (inst.getConfig().type != ServerType.PAPER) continue;
                Path cfg = inst.getConfig().workdir.resolve("config/paper-global.yml");
                if (!Files.exists(cfg)) continue;
                String content = Files.readString(cfg);
                content = content.replaceAll("secret: '.*?'", "secret: '" + secret + "'");
                Files.writeString(cfg, content);
                System.out.println("[secret] Синхронизирован: " + id);
            }
            System.out.println("[secret] ✅ Velocity forwarding secret актуален: " + secret.substring(0,6) + "...");
        } catch (IOException e) {
            System.out.println("[secret] ❌ Ошибка: " + e.getMessage());
        }
    }

    private String generateSecret() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        var sb = new StringBuilder(32);
        var rng = new java.util.Random();
        for (int i = 0; i < 32; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void start(String id) {
        withInstance(id, inst -> {
            try {
                inst.start(javaExec);
                if (notify != null) notify.serverStarted(id);
            } catch (IOException e) { System.out.println("❌ Start error: " + e.getMessage()); }
        });
    }

    public void stop(String id) {
        withInstance(id, inst -> {
            inst.stop(30);
            if (notify != null) notify.serverStopped(id);
        });
    }

    public void restart(String id) {
        withInstance(id, inst -> {
            try { inst.restart(javaExec); }
            catch (IOException e) { System.out.println("❌ Restart error: " + e.getMessage()); }
        });
    }

    public void startAll() { instances.keySet().forEach(this::start); }
    public void stopAll()  { new ArrayList<>(instances.keySet()).forEach(this::stop); }

    public boolean sendCommand(String id, String cmd) {
        ServerInstance inst = instances.get(id);
        if (inst == null) { System.out.println("Server '" + id + "' not found."); return false; }
        return inst.sendCommand(cmd);
    }

    public List<String> tail(String id, int lines) {
        ServerInstance inst = instances.get(id);
        return inst != null ? inst.tail(lines) : List.of("Server '" + id + "' not found.");
    }

    public void notifyChannel(String msg) { if (notify != null) notify.custom(msg); }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    public void printStatus() {
        System.out.println("\n=== NanoCore Server Status ===");
        if (instances.isEmpty()) System.out.println("  No servers. Run: java -jar NanoCore.jar download");
        else instances.values().forEach(i -> System.out.println(i.statusLine()));
        System.out.println("==============================");
    }

    public void printPorts() { ports.printAll(); }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Set<String>      ids()                       { return Collections.unmodifiableSet(instances.keySet()); }
    public boolean          isEmpty()                   { return instances.isEmpty(); }
    public boolean          has(String id)              { return instances.containsKey(id); }
    public Path             getBaseDir()                { return baseDir; }
    public String           getJavaExec()               { return javaExec; }
    public ServerInstance   getInstance(String id)      { return instances.get(id); }


    // ------------------------------------------------------------------
    // AdminHub — опциональная загрузка плагинов
    // ------------------------------------------------------------------

    public void downloadAdminHubIfNeeded(java.nio.file.Path baseDir) {
        String RELEASES_API = "https://api.github.com/repos/NanoDev1488/NanoCore/releases/latest";
        String[] assets = {"NanoCore-AdminHub-Velocity.jar", "NanoCore-AdminHub-Paper.jar"};
        String[] dests  = {
            "proxy/plugins/NanoCore-AdminHub-Velocity.jar",
            "servers/paper-1.21.4/plugins/NanoCore-AdminHub-Paper.jar"
        };

        System.out.println("[adminhub] Checking AdminHub plugins...");
        boolean allExist = true;
        for (String dest : dests)
            if (!java.nio.file.Files.exists(baseDir.resolve(dest))) { allExist = false; break; }

        if (allExist) { System.out.println("[adminhub] Already installed."); return; }

        try {
            String json = Downloader.fetch(RELEASES_API);
            for (int i = 0; i < assets.length; i++) {
                java.nio.file.Path dest = baseDir.resolve(dests[i]);
                if (java.nio.file.Files.exists(dest)) continue;
                dest.getParent().toFile().mkdirs();

                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"browser_download_url\":\\s*\"([^\"]*" +
                    java.util.regex.Pattern.quote(assets[i]) + ")\"");
                java.util.regex.Matcher m = p.matcher(json);
                if (m.find()) {
                    System.out.println("[adminhub] Downloading " + assets[i] + "...");
                    Downloader.download(m.group(1), dest);
                    System.out.println("[adminhub] ✅ " + assets[i]);
                } else {
                    System.out.println("[adminhub] ⚠ " + assets[i] + " not found in latest release");
                }
            }
        } catch (Exception e) {
            System.out.println("[adminhub] Could not download AdminHub plugins: " + e.getMessage());
        }
    }

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

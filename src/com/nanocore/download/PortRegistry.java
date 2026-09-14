package com.nanocore.download;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PortRegistry {

    private static final int PROXY_PORT = 25565;
    private static final int BASE_PORT  = 25566;

    private final Path propsFile;
    private final Properties props = new Properties();

    public PortRegistry(Path baseDir) {
        this.propsFile = baseDir.resolve("ports.properties");
        load();
    }

    private void load() {
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) { props.load(in); }
            catch (IOException ignored) {}
        }
    }

    private void save() {
        try (OutputStream out = Files.newOutputStream(propsFile,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(out, "NanoCore port assignments — do not edit manually");
        } catch (IOException e) { System.out.println("Warning: could not save ports.properties: " + e.getMessage()); }
    }

    public int getOrAssign(String serverId) {
        if ("velocity".equalsIgnoreCase(serverId)) {
            props.setProperty("velocity", String.valueOf(PROXY_PORT)); save(); return PROXY_PORT;
        }
        String stored = props.getProperty(serverId);
        if (stored != null) { try { return Integer.parseInt(stored.trim()); } catch (NumberFormatException ignored) {} }
        Set<Integer> used = usedPorts();
        int port = BASE_PORT;
        while (used.contains(port)) port++;
        props.setProperty(serverId, String.valueOf(port)); save();
        System.out.println("  [ports] " + serverId + " -> " + port);
        return port;
    }

    private Set<Integer> usedPorts() {
        Set<Integer> used = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            try { used.add(Integer.parseInt(props.getProperty(key).trim())); } catch (NumberFormatException ignored) {}
        }
        return used;
    }

    public void printAll() {
        System.out.println("Assigned ports:");
        props.stringPropertyNames().stream().sorted()
            .forEach(k -> System.out.printf("  %-22s -> %s%n", k, props.getProperty(k)));
    }
}

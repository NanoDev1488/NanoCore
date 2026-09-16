package com.nanocore.plugins;

import com.nanocore.core.ServerManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PluginDeployer {

    private final ServerManager manager;

    public PluginDeployer(ServerManager manager) { this.manager = manager; }

    public void deploy(Path pluginJar, List<String> serverIds, boolean reload) {
        if (!Files.exists(pluginJar)) { System.out.println("❌ File not found: " + pluginJar); return; }
        String name = pluginJar.getFileName().toString();
        List<String> targets = resolveTargets(serverIds);
        System.out.println("[deploy] " + name + " → " + targets);
        for (String id : targets) {
            var inst = manager.getInstance(id);
            if (inst == null) { System.out.println("  ⚠ " + id + " not found"); continue; }
            Path dest = inst.getConfig().workdir.resolve("plugins").resolve(name);
            try {
                dest.getParent().toFile().mkdirs();
                Files.copy(pluginJar, dest, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  ✅ " + id + " → " + dest);
                if (reload && inst.isRunning()) {
                    String cmd = inst.getConfig().type.name().equals("VELOCITY") ? "velocity reload" : "reload confirm";
                    inst.sendCommand(cmd);
                    System.out.println("  🔄 " + id + " → reload sent");
                }
            } catch (IOException e) { System.out.println("  ❌ " + id + ": " + e.getMessage()); }
        }
    }

    public void list(String serverId) {
        var inst = manager.getInstance(serverId);
        if (inst == null) { System.out.println("Server not found: " + serverId); return; }
        Path pluginsDir = inst.getConfig().workdir.resolve("plugins");
        System.out.println("[plugins] " + serverId + " (" + pluginsDir + "):");
        if (!Files.isDirectory(pluginsDir)) { System.out.println("  (plugins dir missing)"); return; }
        try (var s = Files.list(pluginsDir)) {
            s.filter(p -> p.toString().endsWith(".jar")).sorted()
             .forEach(p -> System.out.printf("  %-40s %6s KB%n", p.getFileName(), p.toFile().length()/1024));
        } catch (IOException e) { System.out.println("  ❌ " + e.getMessage()); }
    }

    public void remove(String pluginName, List<String> serverIds) {
        for (String id : resolveTargets(serverIds)) {
            var inst = manager.getInstance(id);
            if (inst == null) continue;
            Path target = inst.getConfig().workdir.resolve("plugins").resolve(pluginName);
            try {
                if (Files.deleteIfExists(target)) System.out.println("  ✅ Removed: " + id + "/" + pluginName);
                else System.out.println("  ⚠  Not found: " + id + "/" + pluginName);
            } catch (IOException e) { System.out.println("  ❌ " + id + ": " + e.getMessage()); }
        }
    }

    private List<String> resolveTargets(List<String> ids) {
        if (ids.contains("all")) return new ArrayList<>(manager.ids());
        return ids;
    }
}

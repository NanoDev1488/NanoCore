package com.nanocore.plugins;

import com.nanocore.core.ServerManager;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * PluginDeployer — деплой плагинов на серверы.
 *
 * Использование:
 *   java -jar NanoCore.jar plugin deploy MyPlugin.jar paper-1.21.4 paper-1.21.11
 *   java -jar NanoCore.jar plugin deploy MyPlugin.jar all
 *   java -jar NanoCore.jar plugin list paper-1.21.4
 *   java -jar NanoCore.jar plugin remove MyPlugin.jar paper-1.21.4
 */
public class PluginDeployer {

    private final ServerManager manager;

    public PluginDeployer(ServerManager manager) {
        this.manager = manager;
    }

    /**
     * Деплой jar на выбранные серверы.
     * @param pluginJar   путь к плагину (локальный файл)
     * @param serverIds   список ID серверов или ["all"]
     * @param reload      true → отправить reload после копирования
     */
    public void deploy(Path pluginJar, List<String> serverIds, boolean reload) {
        if (!Files.exists(pluginJar)) {
            System.out.println("❌ Файл не найден: " + pluginJar);
            return;
        }
        String name = pluginJar.getFileName().toString();

        List<String> targets = resolveTargets(serverIds);
        System.out.println("[deploy] " + name + " → " + targets);

        for (String id : targets) {
            var inst = manager.getInstance(id);
            if (inst == null) { System.out.println("  ⚠ " + id + " не найден, пропускаю"); continue; }

            // Velocity использует /plugins/, Paper тоже
            Path pluginsDir = inst.getConfig().workdir.resolve("plugins");
            try {
                pluginsDir.toFile().mkdirs();
                Path dest = pluginsDir.resolve(name);
                Files.copy(pluginJar, dest, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  ✅ " + id + " → " + dest);

                if (reload && inst.isRunning()) {
                    String reloadCmd = inst.getConfig().type.name().equals("VELOCITY")
                        ? "velocity reload" : "reload confirm";
                    inst.sendCommand(reloadCmd);
                    System.out.println("  🔄 " + id + " → reload отправлен");
                }
            } catch (IOException e) {
                System.out.println("  ❌ " + id + ": " + e.getMessage());
            }
        }
    }

    /** Список плагинов на сервере */
    public void list(String serverId) {
        var inst = manager.getInstance(serverId);
        if (inst == null) { System.out.println("Сервер не найден: " + serverId); return; }

        Path pluginsDir = inst.getConfig().workdir.resolve("plugins");
        System.out.println("[plugins] " + serverId + " (" + pluginsDir + "):");

        if (!Files.isDirectory(pluginsDir)) {
            System.out.println("  (папка plugins не существует)");
            return;
        }
        try (var stream = Files.list(pluginsDir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                  .sorted()
                  .forEach(p -> System.out.printf("  %-40s %6s KB%n",
                      p.getFileName(), p.toFile().length() / 1024));
        } catch (IOException e) {
            System.out.println("  ❌ " + e.getMessage());
        }
    }

    /** Удалить плагин с выбранных серверов */
    public void remove(String pluginName, List<String> serverIds) {
        for (String id : resolveTargets(serverIds)) {
            var inst = manager.getInstance(id);
            if (inst == null) continue;
            Path target = inst.getConfig().workdir.resolve("plugins").resolve(pluginName);
            try {
                if (Files.deleteIfExists(target))
                    System.out.println("  ✅ Удалён: " + id + "/" + pluginName);
                else
                    System.out.println("  ⚠  Не найден: " + id + "/" + pluginName);
            } catch (IOException e) {
                System.out.println("  ❌ " + id + ": " + e.getMessage());
            }
        }
    }

    private List<String> resolveTargets(List<String> ids) {
        if (ids.contains("all")) return new ArrayList<>(manager.ids());
        return ids;
    }
}

package com.nanocore.patch;

import java.util.*;

/**
 * Описывает что удаляется из каждого jar при патче.
 *
 * Пути проверены для актуальных версий:
 *  - Velocity 4.1.x  (shaded fat-jar ~25k entries)
 *  - BungeeCord 1.21 (fat-jar ~14k entries)
 *
 * Paper качается как paperclip-лаунчер (~600 entries),
 * реальные классы извлекаются при первом запуске —
 * поэтому Paper "патчится" через конфиги (FillApiClient).
 */
public class PatchManifest {

    public final String serverType;
    public final String description;
    public final List<String> removePackages;
    public final List<String> removeClasses;

    private PatchManifest(Builder b) {
        this.serverType = b.serverType; this.description = b.description;
        this.removePackages = Collections.unmodifiableList(b.removePackages);
        this.removeClasses  = Collections.unmodifiableList(b.removeClasses);
    }

    public boolean shouldRemove(String entryName) {
        for (String pkg : removePackages) if (entryName.startsWith(pkg)) return true;
        for (String cls : removeClasses) {
            String base = cls.replace('.', '/');
            if (entryName.equals(base + ".class") || entryName.startsWith(base + "$")) return true;
        }
        return false;
    }

    public void printSummary() {
        System.out.println("  Patch for " + serverType + ": " + description);
        System.out.println("  Packages to remove: " + removePackages.size());
        System.out.println("  Classes  to remove: " + removeClasses.size());
    }

    // ------------------------------------------------------------------
    // Velocity 4.x — shaded fat-jar, все зависимости внутри
    // ------------------------------------------------------------------
    public static PatchManifest forVelocity() {
        return new Builder("velocity", "bStats + Metrics + legacy MC<1.7 + GS4 query + HealthCheck HTTP")
            // bStats телеметрия (Velocity 3.x путь)
            .removePackage("com/velocitypowered/proxy/util/bstats/")
            // bStats телеметрия (Velocity 4.x — переехал в другой пакет)
            .removePackage("com/velocitypowered/proxy/metrics/")
            // bStats общая либа (шейдированная внутрь)
            .removePackage("org/bstats/")
            // Legacy ping протокол (MC < 1.7, 2013 год)
            .removePackage("com/velocitypowered/proxy/protocol/packet/legacy/")
            // Старый путь legacy (на случай если версия использует network.protocol)
            .removePackage("com/velocitypowered/proxy/network/protocol/packet/legacy/")
            // GS4 Query (устаревший game-query протокол, UDP)
            .removePackage("com/velocitypowered/proxy/query/")
            // Встроенный HTTP HealthCheck сервер (лишний открытый порт)
            .removePackage("com/velocitypowered/proxy/util/InternalServer")
            // Debug /velocity dump команда
            .removeClass("com.velocitypowered.proxy.command.builtin.VelocityCommand$Dump")
            // Netty HAProxyMessageDecoder (редко используется)
            .removePackage("io/netty/handler/codec/haproxy/")
            .build();
    }

    // ------------------------------------------------------------------
    // BungeeCord — fat-jar с шейдированными зависимостями
    // ------------------------------------------------------------------
    public static PatchManifest forBungeeCord() {
        return new Builder("bungeecord", "bStats + legacy ping + Metrics")
            // bStats (несколько возможных расположений)
            .removePackage("net/md_5/bungee/module/cmd/bstats/")
            .removePackage("org/bstats/")
            // Legacy ping MC < 1.7
            .removePackage("net/md_5/bungee/connection/legacy/")
            // MySQL reconnect handler (если не используется MySQL)
            .removePackage("net/md_5/bungee/util/ReconnectHandler")
            .build();
    }

    // ------------------------------------------------------------------
    // Paper — paperclip launcher (не патчим бинарно, только через конфиги)
    // ------------------------------------------------------------------
    public static PatchManifest forPaper(String mcVersion) {
        // Paperclip содержит только ~600 файлов лаунчера, реальный сервер
        // загружается при первом запуске. Патч через конфиги в FillApiClient.
        return new Builder("paper-" + mcVersion, "config-only (paperclip launcher)")
            .build(); // ничего не удаляем из самого jar
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------
    public static class Builder {
        final String serverType, description;
        final List<String> removePackages = new ArrayList<>();
        final List<String> removeClasses  = new ArrayList<>();

        public Builder(String serverType, String description) {
            this.serverType = serverType; this.description = description;
        }
        public Builder removePackage(String path) { removePackages.add(path); return this; }
        public Builder removeClass(String fqn)    { removeClasses.add(fqn);   return this; }
        public PatchManifest build() { return new PatchManifest(this); }
    }
}

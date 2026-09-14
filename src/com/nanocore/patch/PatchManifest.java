package com.nanocore.patch;

import java.util.*;

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
        System.out.println("  Removing packages: " + removePackages.size());
        System.out.println("  Removing classes : " + removeClasses.size());
    }

    public static PatchManifest forVelocity() {
        return new Builder("velocity", "bStats + legacy protocols + GS4 query")
            .removePackage("com/velocitypowered/proxy/util/bstats/")
            .removePackage("com/velocitypowered/proxy/network/protocol/packet/legacy/")
            .removePackage("com/velocitypowered/proxy/query/")
            .removeClass("com.velocitypowered.proxy.command.builtin.VelocityCommand$Dump")
            .removeClass("com.velocitypowered.proxy.util.InternalServer")
            .build();
    }

    public static PatchManifest forPaper(String mcVersion) {
        return new Builder("paper-" + mcVersion, "bStats + legacy Bukkit + RCON + deprecated API")
            .removePackage("org/bstats/")
            .removePackage("org/bukkit/craftbukkit/legacy/")
            .removePackage("org/bukkit/craftbukkit/util/permissions/CraftDefaultPermissions")
            .removeClass("net.minecraft.server.rcon.thread.RconThread")
            .removeClass("net.minecraft.server.rcon.thread.RconClient")
            .removeClass("net.minecraft.server.rcon.PktUtils")
            .build();
    }

    public static PatchManifest forBungeeCord() {
        return new Builder("bungeecord", "bStats + legacy ping")
            .removePackage("net/md_5/bungee/module/cmd/bstats/")
            .removePackage("org/bstats/")
            .removePackage("net/md_5/bungee/connection/legacy/")
            .build();
    }

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

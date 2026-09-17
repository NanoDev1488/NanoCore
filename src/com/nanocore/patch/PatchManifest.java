package com.nanocore.patch;

import java.util.*;

/**
 * PatchManifest — агрессивное удаление ненужного из jar'ов.
 *
 * Velocity (~25k записей в fat-jar) — удаляем:
 *   bStats + legacy MC<1.7 + GS4 Query + 15+ неиспользуемых Netty-кодеков
 *   (HTTP/2, Redis, Memcache, SMTP, STOMP, DNS, SOCKS, SCTP, RTSP, XML,
 *    Protobuf, Marshalling, HAProxy, SPDY, kQueue/macOS, OIO/legacy, UDT/RXTX)
 *
 * BungeeCord — аналогично.
 *
 * Paper — paperclip launcher (~600 записей), реальные классы загружаются
 *         при первом запуске. Патч через конфиги в FillApiClient.
 */
public class PatchManifest {

    public final String serverType;
    public final String description;
    public final List<String> removePackages;
    public final List<String> removeClasses;

    private PatchManifest(Builder b) {
        this.serverType     = b.serverType;
        this.description    = b.description;
        this.removePackages = Collections.unmodifiableList(b.removePackages);
        this.removeClasses  = Collections.unmodifiableList(b.removeClasses);
    }

    public boolean shouldRemove(String entry) {
        for (String pkg : removePackages) if (entry.startsWith(pkg)) return true;
        for (String cls : removeClasses) {
            String base = cls.replace('.', '/');
            if (entry.equals(base + ".class") || entry.startsWith(base + "$")) return true;
        }
        return false;
    }

    public void printSummary() {
        System.out.println("  Patch: " + serverType + " — " + description);
        System.out.println("  Packages: " + removePackages.size() + " | Classes: " + removeClasses.size());
    }

    // ------------------------------------------------------------------
    // Velocity 4.x
    // ------------------------------------------------------------------
    public static PatchManifest forVelocity() {
        return new Builder("velocity", "bStats + legacy MC<1.7 + GS4 + 15 unused Netty codecs")
            // bStats
            .removePackage("com/velocitypowered/proxy/util/bstats/")
            .removePackage("com/velocitypowered/proxy/metrics/")
            .removePackage("org/bstats/")
            // Legacy protocol (MC < 1.7)
            .removePackage("com/velocitypowered/proxy/protocol/packet/legacy/")
            .removePackage("com/velocitypowered/proxy/network/protocol/packet/legacy/")
            // GS4 Query UDP
            .removePackage("com/velocitypowered/proxy/query/")
            // Debug dump
            .removeClass("com.velocitypowered.proxy.command.builtin.VelocityCommand$Dump")
            // Unused Netty codecs
            .removePackage("io/netty/handler/codec/http2/")
            .removePackage("io/netty/handler/codec/redis/")
            .removePackage("io/netty/handler/codec/memcache/")
            .removePackage("io/netty/handler/codec/smtp/")
            .removePackage("io/netty/handler/codec/stomp/")
            .removePackage("io/netty/handler/codec/rtsp/")
            .removePackage("io/netty/handler/codec/dns/")
            .removePackage("io/netty/resolver/dns/")
            .removePackage("io/netty/handler/codec/socksx/")
            .removePackage("io/netty/channel/sctp/")
            .removePackage("io/netty/handler/codec/sctp/")
            .removePackage("io/netty/handler/codec/xml/")
            .removePackage("io/netty/handler/codec/protobuf/")
            .removePackage("io/netty/handler/codec/marshalling/")
            .removePackage("io/netty/handler/codec/haproxy/")
            .removePackage("io/netty/handler/codec/spdy/")
            // Unused Netty transports
            .removePackage("io/netty/channel/kqueue/")   // macOS only
            .removePackage("io/netty/channel/oio/")      // deprecated blocking I/O
            .removePackage("io/netty/channel/rxtx/")     // serial port
            .removePackage("io/netty/channel/udt/")      // deprecated UDT
            .build();
    }

    // ------------------------------------------------------------------
    // BungeeCord
    // ------------------------------------------------------------------
    public static PatchManifest forBungeeCord() {
        return new Builder("bungeecord", "bStats + legacy ping + unused Netty codecs")
            .removePackage("net/md_5/bungee/module/cmd/bstats/")
            .removePackage("org/bstats/")
            .removePackage("net/md_5/bungee/connection/legacy/")
            .removePackage("io/netty/handler/codec/http2/")
            .removePackage("io/netty/handler/codec/redis/")
            .removePackage("io/netty/handler/codec/memcache/")
            .removePackage("io/netty/handler/codec/smtp/")
            .removePackage("io/netty/handler/codec/stomp/")
            .removePackage("io/netty/handler/codec/rtsp/")
            .removePackage("io/netty/handler/codec/dns/")
            .removePackage("io/netty/resolver/dns/")
            .removePackage("io/netty/handler/codec/socksx/")
            .removePackage("io/netty/channel/sctp/")
            .removePackage("io/netty/handler/codec/xml/")
            .removePackage("io/netty/handler/codec/protobuf/")
            .removePackage("io/netty/handler/codec/haproxy/")
            .removePackage("io/netty/channel/kqueue/")
            .removePackage("io/netty/channel/oio/")
            .removePackage("io/netty/channel/rxtx/")
            .removePackage("io/netty/channel/udt/")
            .build();
    }

    // ------------------------------------------------------------------
    // Paper — config-only patching
    // ------------------------------------------------------------------
    public static PatchManifest forPaper(String mcVersion) {
        return new Builder("paper-" + mcVersion, "config-only (paperclip launcher, no binary patch)")
            .build();
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------
    public static class Builder {
        final String serverType, description;
        final List<String> removePackages = new ArrayList<>();
        final List<String> removeClasses  = new ArrayList<>();

        public Builder(String t, String d) { this.serverType = t; this.description = d; }
        public Builder removePackage(String p) { removePackages.add(p); return this; }
        public Builder removeClass(String c)   { removeClasses.add(c);  return this; }
        public PatchManifest build() { return new PatchManifest(this); }
    }
}

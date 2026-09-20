package com.nanocore.patch;

import java.util.*;

/**
 * PatchManifest — описывает что удаляется из jar при патче.
 *
 * АГРЕССИВНЫЙ РЕЖИМ для Velocity/BungeeCord:
 * Velocity — fat-jar ~25k записей. Удаляем:
 *   - bStats телеметрию
 *   - Legacy протоколы MC < 1.7
 *   - GS4 Query UDP-сервер
 *   - Неиспользуемые Netty-кодеки (HTTP/2, Redis, Memcache, SMTP, STOMP, DNS, SOCKS, SCTP, RTSP...)
 *   - Netty kQueue (macOS, не нужен на Linux)
 *   - Netty OIO (устаревший blocking I/O)
 *   - HAProxy decoder
 *
 * BungeeCord — аналогично.
 *
 * Paper — paperclip launcher, патчим через конфиги (см. FillApiClient).
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

    public boolean shouldRemove(String entryName) {
        for (String pkg : removePackages) if (entryName.startsWith(pkg)) return true;
        for (String cls : removeClasses) {
            String base = cls.replace('.', '/');
            if (entryName.equals(base + ".class") || entryName.startsWith(base + "$")) return true;
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
        return new Builder("velocity", "bStats + legacy MC<1.7 + GS4 + unused Netty codecs")

            // === bStats телеметрия ===
            .removePackage("com/velocitypowered/proxy/util/bstats/")
            .removePackage("com/velocitypowered/proxy/metrics/")
            .removePackage("org/bstats/")

            // === Legacy протоколы (MC < 1.7, 2013 год) ===
            .removePackage("com/velocitypowered/proxy/protocol/packet/legacy/")
            .removePackage("com/velocitypowered/proxy/network/protocol/packet/legacy/")

            // === GS4 Query (UDP game-query, устаревший) ===
            .removePackage("com/velocitypowered/proxy/query/")

            // === Debug команды ===
            .removeClass("com.velocitypowered.proxy.command.builtin.VelocityCommand$Dump")

            // === Неиспользуемые Netty кодеки ===
            // HTTP/2 — тяжёлый, Velocity не работает по HTTP/2
            .removePackage("io/netty/handler/codec/http2/")
            // Redis — Netty поставляется с ним, но Velocity его не использует
            .removePackage("io/netty/handler/codec/redis/")
            // Memcache — аналогично
            .removePackage("io/netty/handler/codec/memcache/")
            // SMTP — зачем почтовый протокол в прокси-сервере?
            .removePackage("io/netty/handler/codec/smtp/")
            // STOMP — message broker protocol, не нужен
            .removePackage("io/netty/handler/codec/stomp/")
            // RTSP — real-time streaming protocol, не нужен
            .removePackage("io/netty/handler/codec/rtsp/")
            // DNS — Velocity использует стандартный DNS JVM, не Netty DNS
            .removePackage("io/netty/resolver/dns/")
            .removePackage("io/netty/handler/codec/dns/")
            // SOCKS — SOCKS proxy codec, не нужен
            .removePackage("io/netty/handler/codec/socksx/")
            // SCTP — stream control transmission, не нужен
            .removePackage("io/netty/channel/sctp/")
            .removePackage("io/netty/handler/codec/sctp/")
            // XML codec
            .removePackage("io/netty/handler/codec/xml/")
            // Protobuf codec
            .removePackage("io/netty/handler/codec/protobuf/")
            // Marshalling (JBoss)
            .removePackage("io/netty/handler/codec/marshalling/")
            // HAProxy message decoder (редко используется)
            .removePackage("io/netty/handler/codec/haproxy/")
            // SPDY (устаревшее расширение HTTP, заменено HTTP/2)
            .removePackage("io/netty/handler/codec/spdy/")

            // === Netty устаревшие transport ===
            // kQueue — macOS only, Linux-серверу не нужен
            .removePackage("io/netty/channel/kqueue/")
            // OIO — старый blocking I/O, deprecated в Netty
            .removePackage("io/netty/channel/oio/")
            // RxTX — serial port channel
            .removePackage("io/netty/channel/rxtx/")
            // UDT — User Datagram Transport (deprecated)
            .removePackage("io/netty/channel/udt/")

            .build();
    }

    // ------------------------------------------------------------------
    // BungeeCord
    // ------------------------------------------------------------------
    public static PatchManifest forBungeeCord() {
        return new Builder("bungeecord", "bStats + legacy ping + unused Netty codecs")
            // bStats
            .removePackage("net/md_5/bungee/module/cmd/bstats/")
            .removePackage("org/bstats/")
            // Legacy ping MC < 1.7
            .removePackage("net/md_5/bungee/connection/legacy/")
            // Неиспользуемые Netty кодеки (те же что и для Velocity)
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
    // Paper — paperclip launcher, только конфиги
    // ------------------------------------------------------------------
    public static PatchManifest forPaper(String mcVersion) {
        return new Builder("paper-" + mcVersion,
            "config-only (paperclip launcher, binary patch не применяется)")
            .build();
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
        public Builder removePackage(String p) { removePackages.add(p); return this; }
        public Builder removeClass(String c)   { removeClasses.add(c);  return this; }
        public PatchManifest build() { return new PatchManifest(this); }
    }
}

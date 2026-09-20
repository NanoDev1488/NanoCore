package com.nanocore.config;

import com.nanocore.core.ServerType;
import com.nanocore.download.PortRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public class ServerConfig {

    public final String     id;
    public final ServerType type;
    public final Path       workdir;
    public final String     jar;
    public final String     heap;
    public final String     extraFlags;
    public final int        port;
    public final boolean    watchdogEnabled;
    public final int        watchdogMaxRestarts;
    public final int        watchdogCooldown;

    public ServerConfig(String id, ServerType type, Path workdir, String jar,
                        String heap, String extraFlags, int port,
                        boolean watchdogEnabled, int watchdogMaxRestarts, int watchdogCooldown) {
        this.id = id; this.type = type; this.workdir = workdir;
        this.jar = jar; this.heap = heap; this.extraFlags = extraFlags; this.port = port;
        this.watchdogEnabled     = watchdogEnabled;
        this.watchdogMaxRestarts = watchdogMaxRestarts;
        this.watchdogCooldown    = watchdogCooldown;
    }

    public static ServerConfig load(String id, ServerType type, Path workdir,
                                    String jar, PortRegistry ports) {
        Properties p = loadProps(workdir);
        String  heap        = p.getProperty("heap", type == ServerType.VELOCITY ? "512M" : "1G");
        String  extraFlags  = p.getProperty("extra_flags", "");
        int     port        = ports.getOrAssign(id);
        boolean watchdog    = !"false".equalsIgnoreCase(p.getProperty("watchdog", "true"));
        int     maxRestarts = Integer.parseInt(p.getProperty("watchdog_max_restarts", "5"));
        int     cooldown    = Integer.parseInt(p.getProperty("watchdog_cooldown", "10"));
        return new ServerConfig(id, type, workdir, jar, heap, extraFlags, port,
                                watchdog, maxRestarts, cooldown);
    }

    private static Properties loadProps(Path workdir) {
        Properties p = new Properties();
        Path f = workdir.resolve("nanocore.properties");
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            catch (IOException ignored) {}
        }
        return p;
    }

    public List<String> buildJvmArgs() {
        List<String> args = new ArrayList<>();
        for (String f : type.defaultFlags().split("\\s+"))
            if (!f.isBlank()) args.add(f);
        args.add("-Xmx" + heap);
        if (!extraFlags.isBlank())
            for (String f : extraFlags.strip().split("\\s+"))
                if (!f.isBlank()) args.add(f);
        args.add("-jar"); args.add(jar); args.add("nogui");
        return args;
    }

    public void ensurePaperEnv() throws IOException {
        Path eula = workdir.resolve("eula.txt");
        if (!Files.exists(eula)) Files.writeString(eula, "eula=true\n");
        Path sp = workdir.resolve("server.properties");
        if (!Files.exists(sp))
            Files.writeString(sp,
                "server-port=" + port + "\n" +
                "online-mode=false\n" +
                "enforce-secure-profile=false\n" +
                "enable-rcon=false\n" +
                "enable-query=false\n");
    }

    public boolean isWatchdogEnabled()    { return watchdogEnabled; }
    public int     getWatchdogMaxRestarts(){ return watchdogMaxRestarts; }
    public int     getWatchdogCooldown()  { return watchdogCooldown; }

    @Override public String toString() {
        return String.format("[%s | %s | port=%d | heap=%s | watchdog=%s]",
            id, type, port, heap, watchdogEnabled ? "on" : "off");
    }
}

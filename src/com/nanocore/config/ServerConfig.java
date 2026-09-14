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

    public ServerConfig(String id, ServerType type, Path workdir,
                        String jar, String heap, String extraFlags, int port) {
        this.id = id; this.type = type; this.workdir = workdir;
        this.jar = jar; this.heap = heap; this.extraFlags = extraFlags; this.port = port;
    }

    public static ServerConfig load(String id, ServerType type, Path workdir,
                                    String jar, PortRegistry ports) {
        Properties props = new Properties();
        Path propsFile = workdir.resolve("nanocore.properties");
        if (Files.exists(propsFile)) {
            try (InputStream in = Files.newInputStream(propsFile)) { props.load(in); }
            catch (IOException ignored) {}
        }
        String heap       = props.getProperty("heap", type == ServerType.VELOCITY ? "512M" : "1G");
        String extraFlags = props.getProperty("extra_flags", "");
        int    port       = ports.getOrAssign(id);
        return new ServerConfig(id, type, workdir, jar, heap, extraFlags, port);
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
            Files.writeString(sp, "server-port=" + port + "\nonline-mode=false\nenforce-secure-profile=false\n");
    }

    @Override public String toString() {
        return String.format("[%s | %s | port=%d | heap=%s]", id, type, port, heap);
    }
}

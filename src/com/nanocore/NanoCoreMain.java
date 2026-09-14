package com.nanocore;

import com.nanocore.cli.ConsoleCLI;
import com.nanocore.core.ServerManager;

import java.nio.file.*;

/**
 * NanoCore — Multi-Core Minecraft Server Manager
 *
 * Usage:
 *   java -jar NanoCore.jar [--basedir <path>] [--java <path>] [command]
 *
 * Commands:
 *   download | list | start [id|all] | stop [id|all] |
 *   restart <id> | send <id> <cmd> | logs <id> [N] | ports
 *
 * Without a command — interactive console.
 */
public class NanoCoreMain {

    static final String VERSION = "1.1.0";

    public static void main(String[] args) {
        printBanner();

        Path   basedir  = detectBaseDir();
        String javaExec = detectJava();

        int skip = 0;
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals("--basedir")) { basedir  = Path.of(args[i+1]); skip = i+2; }
            if (args[i].equals("--java"))    { javaExec = args[i+1];          skip = i+2; }
        }
        String[] cmd = new String[Math.max(0, args.length - skip)];
        System.arraycopy(args, skip, cmd, 0, cmd.length);

        try { Files.createDirectories(basedir.resolve("proxy")); }   catch (Exception ignored) {}
        try { Files.createDirectories(basedir.resolve("servers")); } catch (Exception ignored) {}

        System.out.println("Basedir : " + basedir.toAbsolutePath());
        System.out.println("Java    : " + javaExec);
        System.out.println();

        ServerManager mgr = new ServerManager(basedir, javaExec);
        mgr.registerShutdownHook();

        if (cmd.length > 0) {
            switch (cmd[0].toLowerCase()) {
                case "download","dl"      -> mgr.downloadAll();
                case "list","ls","status" -> mgr.printStatus();
                case "ports"              -> mgr.printPorts();
                case "start"              -> { if (cmd.length>1) mgr.start(cmd[1]); else mgr.startAll(); }
                case "stop"               -> { if (cmd.length>1) mgr.stop(cmd[1]);  else mgr.stopAll();  }
                case "restart"            -> { if (cmd.length>1) mgr.restart(cmd[1]); }
                case "send","console"     -> { if (cmd.length>2) mgr.sendCommand(cmd[1], cmd[2]); }
                case "logs"               -> {
                    int n = 40;
                    try { if (cmd.length>2) n = Integer.parseInt(cmd[2]); } catch (NumberFormatException e) {}
                    if (cmd.length>1) mgr.tail(cmd[1], n).forEach(System.out::println);
                }
                default -> { System.out.println("Unknown command: " + cmd[0]); System.exit(1); }
            }
            return;
        }

        new ConsoleCLI(mgr).run();
    }

    private static Path detectBaseDir() {
        Path cur = Path.of("NanoCore");
        if (Files.isDirectory(cur)) return cur;
        Path home = Path.of(System.getProperty("user.home"), "NanoCore");
        if (Files.isDirectory(home)) return home;
        return cur;
    }

    private static String detectJava() {
        String jh = System.getProperty("java.home");
        if (jh != null) {
            Path p = Path.of(jh, "bin", "java");
            if (Files.exists(p)) return p.toAbsolutePath().toString();
        }
        return "java";
    }

    private static void printBanner() {
        System.out.println(" NanoCore v" + VERSION + " | Multi-Core Minecraft Manager");
        System.out.println(" Velocity + Paper (1.21.4, 1.21.11) + BungeeCord");
        System.out.println(" https://github.com/NanoDev1488/NanoCore");
        System.out.println();
    }
}

package com.nanocore;

import com.nanocore.cli.ConsoleCLI;
import com.nanocore.core.ServerManager;
import com.nanocore.download.BundleManager;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.*;

/**
 * NanoCore — Multi-Core Minecraft Server Manager v1.2.0
 *
 * java -jar NanoCore.jar [--basedir <path>] [--java <path>] [command]
 *
 * Commands:
 *   download          — скачать и запатчить все ядра с GitHub Releases / PaperMC
 *   start  [id|all]  — запустить сервер(а)
 *   stop   [id|all]  — остановить
 *   restart <id>     — перезапустить
 *   send <id> <cmd>  — команда в консоль сервера
 *   logs  <id> [N]   — последние N строк лога
 *   ports            — назначенные порты
 *   bundle           — создать portable NanoCore-bundle.jar (всё в одном ~200MB)
 *   list / status    — статус всех серверов
 *
 * Без команды — интерактивная консоль.
 * При первом запуске без серверов — автоматически скачивает ядра.
 * При запуске bundle-jar — автоматически распаковывает серверы.
 */
public class NanoCoreMain {

    public static final String VERSION = "0.2.0";

    public static void main(String[] args) throws Exception {
        printBanner();

        Path   basedir  = detectBaseDir();
        String javaExec = detectJava();

        // Флаги --basedir / --java
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

        // Команда bundle — не требует ServerManager
        if (cmd.length > 0 && cmd[0].equalsIgnoreCase("bundle")) {
            Path jar = getRunningJar();
            if (jar == null) { System.out.println("❌ Не определить путь к jar."); System.exit(1); }
            BundleManager.createBundle(jar, basedir, jar.resolveSibling("NanoCore-bundle.jar"));
            return;
        }

        // Если это bundle-jar — распаковываем серверы при первом запуске
        Path runningJar = getRunningJar();
        if (runningJar != null && BundleManager.isBundleJar(runningJar)) {
            BundleManager.extractIfNeeded(runningJar, basedir);
        }

        ServerManager mgr = new ServerManager(basedir, javaExec);
        mgr.registerShutdownHook();

        // Авто-скачка если серверов нет
        if (mgr.isEmpty() && (cmd.length == 0
                || cmd[0].equalsIgnoreCase("start")
                || cmd[0].equalsIgnoreCase("all"))) {
            System.out.println("⚡ Серверов не найдено — авто-загрузка из GitHub Releases / PaperMC...");
            mgr.downloadAll();
        }

        // Однострочный режим
        if (cmd.length > 0) {
            switch (cmd[0].toLowerCase()) {
                case "download","dl"      -> mgr.downloadAll();
                case "list","ls","status" -> mgr.printStatus();
                case "ports"              -> mgr.printPorts();
                case "start"              -> { if (cmd.length>1) mgr.start(cmd[1]); else mgr.startAll(); }
                case "stop"               -> { if (cmd.length>1) mgr.stop(cmd[1]);  else mgr.stopAll();  }
                case "restart"            -> { if (cmd.length>1) mgr.restart(cmd[1]); }
                case "send","console"     -> { if (cmd.length>2) mgr.sendCommand(cmd[1],cmd[2]); }
                case "logs"               -> {
                    int n=40; try{if(cmd.length>2)n=Integer.parseInt(cmd[2]);}catch(NumberFormatException e){}
                    if(cmd.length>1) mgr.tail(cmd[1],n).forEach(System.out::println);
                }
                default -> { System.out.println("Unknown command: " + cmd[0]); System.exit(1); }
            }
            return;
        }

        new ConsoleCLI(mgr).run();
    }

    /** Путь к запущенному jar (null если из .class) */
    public static Path getRunningJar() {
        try {
            File f = new File(NanoCoreMain.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.getName().endsWith(".jar")) return f.toPath();
        } catch (URISyntaxException ignored) {}
        return null;
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

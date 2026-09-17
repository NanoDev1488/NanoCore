package com.nanocore;

import com.nanocore.backup.BackupManager;
import com.nanocore.cli.ConsoleCLI;
import com.nanocore.core.ServerManager;
import com.nanocore.download.BundleManager;
import com.nanocore.metrics.MetricsCollector;
import com.nanocore.notify.NotifyService;
import com.nanocore.plugins.PluginDeployer;
import com.nanocore.update.AutoUpdater;
import com.nanocore.watchdog.Watchdog;
import com.nanocore.web.WebPanel;

import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/**
 * NanoCore v0.1.0 — Multi-Core Minecraft Server Manager
 *
 * java -jar NanoCore.jar [--basedir <path>] [--java <path>] [--no-watchdog] [--no-web] [--web-port N] [command]
 *
 * Commands:
 *   download                    — скачать и запатчить все ядра
 *   start  [id|all]
 *   stop   [id|all]
 *   restart <id>
 *   send <id> <cmd>
 *   logs  <id> [N]
 *   ports
 *   metrics                     — RAM/TPS/online по каждому серверу
 *   secret                      — синхронизировать Velocity forwarding secret
 *   backup [id|all]             — бэкап миров прямо сейчас
 *   plugin deploy <jar> [id...] — задеплоить плагин
 *   plugin list <id>
 *   plugin remove <jar> [id...]
 *   update                      — проверить и скачать обновление NanoCore
 *   bundle                      — создать portable NanoCore-bundle.jar (~200MB)
 *   list / status
 *
 * Без команды — интерактивная консоль.
 * При первом запуске без серверов — авто-скачка.
 * При запуске bundle-jar — авто-распаковка.
 */
public class NanoCoreMain {

    public static final String VERSION = "0.1.0";

    public static void main(String[] args) throws Exception {
        printBanner();

        Path    basedir     = detectBaseDir();
        String  javaExec    = detectJava();
        boolean noWatchdog  = false;
        boolean noWeb       = false;
        int     webPort     = 8080;

        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--basedir"     -> basedir    = Path.of(args[++i]);
                case "--java"        -> javaExec   = args[++i];
                case "--no-watchdog" -> noWatchdog = true;
                case "--no-web"      -> noWeb      = true;
                case "--web-port"    -> webPort    = Integer.parseInt(args[++i]);
                default              -> rest.add(args[i]);
            }
        }
        String[] cmd = rest.toArray(new String[0]);

        try { Files.createDirectories(basedir.resolve("proxy")); }   catch (Exception ignored) {}
        try { Files.createDirectories(basedir.resolve("servers")); } catch (Exception ignored) {}

        System.out.println("Version : v" + VERSION);
        System.out.println("Basedir : " + basedir.toAbsolutePath());
        System.out.println("Java    : " + javaExec);
        System.out.println();

        // Bundle не требует ServerManager
        if (cmd.length > 0 && cmd[0].equalsIgnoreCase("bundle")) {
            Path jar = getRunningJar();
            if (jar == null) { System.out.println("❌ Cannot determine jar path."); System.exit(1); }
            BundleManager.createBundle(jar, basedir, jar.resolveSibling("NanoCore-bundle.jar"));
            return;
        }

        // Авто-распаковка bundle
        Path runningJar = getRunningJar();
        if (runningJar != null && BundleManager.isBundleJar(runningJar))
            BundleManager.extractIfNeeded(runningJar, basedir);

        // Инициализация модулей
        NotifyService    notify  = new NotifyService(basedir);
        ServerManager    mgr     = new ServerManager(basedir, javaExec);
        mgr.setNotify(notify);

        MetricsCollector metrics = new MetricsCollector(mgr);
        BackupManager    backup  = new BackupManager(mgr, notify, basedir);
        PluginDeployer   plugins = new PluginDeployer(mgr);
        AutoUpdater      updater = new AutoUpdater();

        mgr.registerShutdownHook();

        // Watchdog + Web в фоне (если не одноразовая команда)
        boolean headless = cmd.length > 0
            && !cmd[0].equalsIgnoreCase("start")
            && !cmd[0].equalsIgnoreCase("all");

        if (!headless) {
            if (!noWatchdog) new Watchdog(mgr).start();
            if (!noWeb) {
                try {
                    WebPanel.fromConfig(basedir, mgr, metrics).start();
                    metrics.start();
                    backup.startScheduled();
                    updater.startPeriodicCheck(12);
                } catch (Exception e) {
                    System.out.println("[web] Could not start panel: " + e.getMessage());
                }
            }
        }

        // Авто-скачка при первом запуске
        if (mgr.isEmpty() && (cmd.length == 0 || cmd[0].equalsIgnoreCase("start"))) {
            System.out.println("⚡ No servers found — auto-downloading from GitHub Releases / PaperMC...");
            mgr.downloadAll();
            mgr.syncVelocitySecret();
        }

        // CLI команды
        if (cmd.length > 0) {
            switch (cmd[0].toLowerCase()) {
                case "download","dl"      -> { mgr.downloadAll(); mgr.syncVelocitySecret(); }
                case "list","ls","status" -> mgr.printStatus();
                case "ports"             -> mgr.printPorts();
                case "metrics","m"       -> metrics.printMetrics();
                case "secret"            -> mgr.syncVelocitySecret();
                case "update"            -> updater.checkAndUpdate();
                case "start"  -> { if (cmd.length>1) mgr.start(cmd[1]); else mgr.startAll(); }
                case "stop"   -> { if (cmd.length>1) mgr.stop(cmd[1]);  else mgr.stopAll();  }
                case "restart"-> { if (cmd.length>1) mgr.restart(cmd[1]); }
                case "send","console" -> { if (cmd.length>2) mgr.sendCommand(cmd[1], join(cmd,2)); }
                case "logs"   -> {
                    int n=40; try{if(cmd.length>2)n=Integer.parseInt(cmd[2]);}catch(NumberFormatException ignored){}
                    if (cmd.length>1) mgr.tail(cmd[1],n).forEach(System.out::println);
                }
                case "backup" -> {
                    String t = cmd.length>1 ? cmd[1] : "all";
                    if ("all".equals(t)) backup.runAll(); else backup.runFor(t);
                }
                case "plugin" -> handlePlugin(cmd, plugins);
                default -> { System.out.println("Unknown command: " + cmd[0]); System.exit(1); }
            }
            return;
        }

        new ConsoleCLI(mgr, metrics, backup, plugins).run();
    }

    private static void handlePlugin(String[] cmd, PluginDeployer p) {
        if (cmd.length < 2) { System.out.println("Usage: plugin <deploy|list|remove>"); return; }
        switch (cmd[1].toLowerCase()) {
            case "deploy" -> {
                if (cmd.length<3) { System.out.println("Usage: plugin deploy <jar> [servers...]"); return; }
                List<String> t = cmd.length>3 ? Arrays.asList(Arrays.copyOfRange(cmd,3,cmd.length)) : List.of("all");
                p.deploy(Path.of(cmd[2]), t, true);
            }
            case "list"   -> { if (cmd.length>2) p.list(cmd[2]); }
            case "remove" -> {
                if (cmd.length<3) { System.out.println("Usage: plugin remove <jar> [servers...]"); return; }
                List<String> t = cmd.length>3 ? Arrays.asList(Arrays.copyOfRange(cmd,3,cmd.length)) : List.of("all");
                p.remove(cmd[2], t);
            }
        }
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    public static Path getRunningJar() {
        try {
            java.io.File f = new java.io.File(
                NanoCoreMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.getName().endsWith(".jar")) return f.toPath();
        } catch (URISyntaxException ignored) {}
        return null;
    }

    private static Path detectBaseDir() {
        Path c = Path.of("NanoCore"); if (Files.isDirectory(c)) return c;
        Path h = Path.of(System.getProperty("user.home"),"NanoCore"); if (Files.isDirectory(h)) return h;
        return c;
    }

    private static String detectJava() {
        String jh = System.getProperty("java.home");
        if (jh != null) { Path p = Path.of(jh,"bin","java"); if (Files.exists(p)) return p.toAbsolutePath().toString(); }
        return "java";
    }

    private static void printBanner() {
        System.out.println(" ┌─────────────────────────────────────────┐");
        System.out.println(" │  NanoCore v" + VERSION + " — Multi-Core Minecraft   │");
        System.out.println(" │  Velocity + Paper + BungeeCord           │");
        System.out.println(" │  github.com/NanoDev1488/NanoCore         │");
        System.out.println(" └─────────────────────────────────────────┘");
        System.out.println();
    }
}

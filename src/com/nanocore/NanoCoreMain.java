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

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/**
 * NanoCore v0.1.0 — Multi-Core Minecraft Server Manager
 *
 * Парсинг аргументов намеренно мягкий: любые неизвестные флаги (--port,
 * --ip, --memory и т.д.) молча игнорируются — это позволяет запускать
 * NanoCore через Pterodactyl без изменения шаблона команды запуска.
 *
 * Известные флаги:
 *   --basedir <path>   — базовая папка (по умолчанию ./NanoCore или ~/NanoCore)
 *   --java <path>      — путь к java для дочерних процессов
 *   --no-watchdog      — отключить авто-рестарт при краше
 *   --no-web           — отключить веб-панель
 *   --web-port <N>     — порт веб-панели (по умолчанию 8080)
 *
 * Команды (если нет ни одной — запускается интерактивная консоль):
 *   download | start [id|all] | stop [id|all] | restart <id>
 *   send <id> <cmd> | logs <id> [N] | ports | metrics
 *   secret | backup [id|all] | plugin deploy/list/remove | update | bundle
 */
public class NanoCoreMain {

    public static final String VERSION = "0.1.0";

    public static void main(String[] args) throws Exception {
        printBanner();

        // --- Разбор аргументов с мягким парсингом ---
        Path    basedir    = detectBaseDir();
        String  javaExec   = detectJava();
        boolean noWatchdog = false;
        boolean noWeb      = false;
        int     webPort    = 8080;

        List<String> commands = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (!arg.startsWith("-")) {
                // Не флаг — это команда или её аргумент
                commands.add(arg);
                continue;
            }

            // Известные флаги
            switch (arg) {
                case "--basedir" -> {
                    if (i + 1 < args.length) basedir = Path.of(args[++i]);
                }
                case "--java" -> {
                    if (i + 1 < args.length) javaExec = args[++i];
                }
                case "--no-watchdog" -> noWatchdog = true;
                case "--no-web"      -> noWeb      = true;
                case "--web-port" -> {
                    if (i + 1 < args.length) {
                        try { webPort = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException ignored) {}
                    }
                }
                default -> {
                    // ⚠ Неизвестный флаг (--port, --memory, --ip и т.д.)
                    // Молча пропускаем + следующий аргумент если он не флаг
                    // (Pterodactyl передаёт --port 25565, --ip 0.0.0.0 и т.п.)
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        i++; // пропускаем значение тоже
                    }
                }
            }
        }

        String[] cmd = commands.toArray(new String[0]);

        try { Files.createDirectories(basedir.resolve("proxy")); }   catch (Exception ignored) {}
        try { Files.createDirectories(basedir.resolve("servers")); } catch (Exception ignored) {}

        System.out.println("Version : v" + VERSION);
        System.out.println("Basedir : " + basedir.toAbsolutePath());
        System.out.println("Java    : " + javaExec);
        System.out.println();

        // Bundle — отдельный путь, не нужен ServerManager
        if (cmd.length > 0 && cmd[0].equalsIgnoreCase("bundle")) {
            Path jar = getRunningJar();
            if (jar == null) { System.out.println("❌ Cannot determine jar path."); System.exit(1); }
            BundleManager.createBundle(jar, basedir, jar.resolveSibling("NanoCore-bundle.jar"));
            return;
        }

        // Авто-распаковка bundle-jar при первом запуске
        Path runningJar = getRunningJar();
        if (runningJar != null && BundleManager.isBundleJar(runningJar))
            BundleManager.extractIfNeeded(runningJar, basedir);

        // Читаем конфиг NanoCore (nanocore.properties в basedir)
        NanoCoreConfig config = NanoCoreConfig.load(basedir);

        // Инициализация модулей
        NotifyService    notify  = new NotifyService(basedir);
        ServerManager    mgr     = new ServerManager(basedir, javaExec);
        mgr.setNotify(notify);

        MetricsCollector metrics = new MetricsCollector(mgr);
        BackupManager    backup  = new BackupManager(mgr, notify, basedir);
        PluginDeployer   plugins = new PluginDeployer(mgr);
        AutoUpdater      updater = new AutoUpdater();

        mgr.registerShutdownHook();

        // Фоновые сервисы (только если не одноразовая команда)
        boolean headless = cmd.length > 0
            && !cmd[0].equalsIgnoreCase("start")
            && !cmd[0].equalsIgnoreCase("all");

        if (!headless) {
            if (!noWatchdog) new Watchdog(mgr).start();
            if (!noWeb) {
                try {
                    new WebPanel(webPort, config.webUser, config.webPass, mgr, metrics).start();
                    metrics.start();
                    backup.startScheduled();
                    updater.startPeriodicCheck(12);
                } catch (Exception e) {
                    System.out.println("[web] Could not start panel: " + e.getMessage());
                }
            }
            // AdminHub — только если включён в конфиге
            if (config.adminHubEnabled) {
                mgr.downloadAdminHubIfNeeded(basedir);
            }
        }

        // Авто-скачка при первом запуске
        if (mgr.isEmpty() && (cmd.length == 0 || cmd[0].equalsIgnoreCase("start"))) {
            System.out.println("⚡ No servers found — auto-downloading...");
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
                case "start" -> { if (cmd.length>1) mgr.start(cmd[1]); else mgr.startAll(); }
                case "stop"  -> { if (cmd.length>1) mgr.stop(cmd[1]);  else mgr.stopAll();  }
                case "restart" -> { if (cmd.length>1) mgr.restart(cmd[1]); }
                case "send","console" -> { if (cmd.length>2) mgr.sendCommand(cmd[1], join(cmd,2)); }
                case "logs" -> {
                    int n=40; try{if(cmd.length>2)n=Integer.parseInt(cmd[2]);}catch(NumberFormatException e){}
                    if (cmd.length>1) mgr.tail(cmd[1],n).forEach(System.out::println);
                }
                case "backup" -> {
                    String t = cmd.length>1 ? cmd[1] : "all";
                    if ("all".equals(t)) backup.runAll(); else backup.runFor(t);
                }
                case "plugin" -> handlePlugin(cmd, plugins);
                default -> System.out.println("[NanoCore] Unknown command ignored: " + cmd[0]);
            }
            return;
        }

        new ConsoleCLI(mgr, metrics, backup, plugins).run();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void handlePlugin(String[] cmd, PluginDeployer p) {
        if (cmd.length < 2) { System.out.println("Usage: plugin <deploy|list|remove>"); return; }
        switch (cmd[1].toLowerCase()) {
            case "deploy" -> {
                if (cmd.length<3) { System.out.println("Usage: plugin deploy <jar> [servers...]"); return; }
                List<String> t = cmd.length>3
                    ? Arrays.asList(Arrays.copyOfRange(cmd,3,cmd.length)) : List.of("all");
                p.deploy(Path.of(cmd[2]), t, true);
            }
            case "list"   -> { if (cmd.length>2) p.list(cmd[2]); }
            case "remove" -> {
                if (cmd.length<3) { System.out.println("Usage: plugin remove <jar> [servers...]"); return; }
                List<String> t = cmd.length>3
                    ? Arrays.asList(Arrays.copyOfRange(cmd,3,cmd.length)) : List.of("all");
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
        Path h = Path.of(System.getProperty("user.home"),"NanoCore");
        if (Files.isDirectory(h)) return h;
        return c;
    }

    private static String detectJava() {
        String jh = System.getProperty("java.home");
        if (jh != null) {
            Path p = Path.of(jh,"bin","java");
            if (Files.exists(p)) return p.toAbsolutePath().toString();
        }
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

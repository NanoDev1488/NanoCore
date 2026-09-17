package com.nanocore.cli;

import com.nanocore.NanoCoreMain;
import com.nanocore.backup.BackupManager;
import com.nanocore.core.ServerManager;
import com.nanocore.download.BundleManager;
import com.nanocore.metrics.MetricsCollector;
import com.nanocore.plugins.PluginDeployer;

import java.nio.file.Path;
import java.util.*;

public class ConsoleCLI {

    private final ServerManager    mgr;
    private final MetricsCollector metrics;
    private final BackupManager    backup;
    private final PluginDeployer   plugins;

    public ConsoleCLI(ServerManager mgr, MetricsCollector metrics,
                      BackupManager backup, PluginDeployer plugins) {
        this.mgr = mgr; this.metrics = metrics; this.backup = backup; this.plugins = plugins;
    }

    public void run() {
        System.out.println("\n  NanoCore v" + NanoCoreMain.VERSION + " Console — 'help' for commands\n");
        mgr.printStatus();
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("\nnanocore> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().strip();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            switch (p[0].toLowerCase()) {
                case "list","ls","status" -> mgr.printStatus();
                case "metrics","m"        -> metrics.printMetrics();
                case "download","dl"      -> { mgr.downloadAll(); mgr.syncVelocitySecret(); }
                case "secret"             -> mgr.syncVelocitySecret();
                case "ports"              -> mgr.printPorts();
                case "start"              -> { if (p.length>1) mgr.start(p[1]); else mgr.startAll(); }
                case "stop"               -> { if (p.length>1) mgr.stop(p[1]);  else mgr.stopAll();  }
                case "restart"            -> { if (p.length>1) mgr.restart(p[1]); }
                case "send","console"     -> { if (p.length>2) mgr.sendCommand(p[1], join(p,2)); else System.out.println("Usage: send <id> <cmd>"); }
                case "logs"               -> {
                    if (p.length<2) { System.out.println("Usage: logs <id> [N]"); break; }
                    int n=40; try{if(p.length>2)n=Integer.parseInt(p[2]);}catch(NumberFormatException ignored){}
                    mgr.tail(p[1],n).forEach(System.out::println);
                }
                case "backup" -> {
                    String t = p.length>1 ? p[1] : "all";
                    if ("all".equals(t)) backup.runAll(); else backup.runFor(t);
                }
                case "plugin" -> {
                    if (p.length<2) { System.out.println("plugin <deploy|list|remove>"); break; }
                    switch (p[1].toLowerCase()) {
                        case "deploy" -> { if(p.length>2) { List<String> t = p.length>3 ? Arrays.asList(Arrays.copyOfRange(p,3,p.length)) : List.of("all"); plugins.deploy(Path.of(p[2]),t,true); } }
                        case "list"   -> { if(p.length>2) plugins.list(p[2]); }
                        case "remove" -> { if(p.length>2) { List<String> t = p.length>3 ? Arrays.asList(Arrays.copyOfRange(p,3,p.length)) : List.of("all"); plugins.remove(p[2],t); } }
                    }
                }
                case "bundle" -> {
                    try {
                        Path jar = NanoCoreMain.getRunningJar();
                        if (jar==null) { System.out.println("❌ Cannot determine jar path."); break; }
                        BundleManager.createBundle(jar, mgr.getBaseDir(), jar.resolveSibling("NanoCore-bundle.jar"));
                    } catch (Exception e) { System.out.println("❌ " + e.getMessage()); }
                }
                case "help","?" -> printHelp();
                case "exit","quit","q" -> { System.out.println("Bye!"); System.exit(0); }
                default -> System.out.println("Unknown: '" + p[0] + "'. Type 'help'.");
            }
        }
    }

    private String join(String[] a, int f) { return String.join(" ", Arrays.copyOfRange(a, f, a.length)); }

    private void printHelp() {
        System.out.println("""

  list / ls / m(etrics)     Статус и метрики серверов
  download                  Скачать/патч ядра + синхр. секрет
  start [id|all]            Запустить
  stop  [id|all]            Остановить
  restart <id>              Перезапустить
  send <id> <cmd>           Команда в консоль сервера
  logs <id> [N]             Последние N строк лога
  backup [id|all]           Бэкап миров
  plugin deploy <jar> [id]  Деплой плагина
  plugin list <id>          Список плагинов
  plugin remove <jar> [id]  Удалить плагин
  secret                    Синхр. Velocity forwarding secret
  ports                     Назначенные порты
  bundle                    Создать NanoCore-bundle.jar
  exit / quit               Выход
""");
    }
}

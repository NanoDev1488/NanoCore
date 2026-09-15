package com.nanocore.cli;

import com.nanocore.NanoCoreMain;
import com.nanocore.core.ServerManager;
import com.nanocore.download.BundleManager;

import java.nio.file.Path;
import java.util.Scanner;

public class ConsoleCLI {

    private final ServerManager manager;

    public ConsoleCLI(ServerManager manager) { this.manager = manager; }

    public void run() {
        System.out.println("\n  NanoCore Console — 'help' for commands.\n");
        manager.printStatus();

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("\nnanocore> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().strip();
            if (line.isEmpty()) continue;

            String[] p = line.split("\\s+", 3);
            switch (p[0].toLowerCase()) {
                case "list","ls","status" -> manager.printStatus();
                case "download","dl"      -> manager.downloadAll();
                case "ports"              -> manager.printPorts();
                case "start"              -> { if (p.length>1) manager.start(p[1]); else manager.startAll(); }
                case "stop"               -> { if (p.length>1) manager.stop(p[1]);  else manager.stopAll();  }
                case "restart"            -> { if (p.length>1) manager.restart(p[1]); }
                case "send","console"     -> {
                    if (p.length>2) manager.sendCommand(p[1],p[2]);
                    else System.out.println("Usage: send <id> <command>");
                }
                case "logs","log"         -> {
                    if (p.length<2) { System.out.println("Usage: logs <id> [N]"); break; }
                    int n=40; try{if(p.length>2)n=Integer.parseInt(p[2]);}catch(NumberFormatException ignored){}
                    manager.tail(p[1],n).forEach(System.out::println);
                }
                case "bundle"             -> {
                    try {
                        Path jar = NanoCoreMain.getRunningJar();
                        if (jar == null) { System.out.println("❌ Не определить путь к jar."); break; }
                        BundleManager.createBundle(jar, manager.getBaseDir(),
                            jar.resolveSibling("NanoCore-bundle.jar"));
                    } catch (Exception e) { System.out.println("❌ Bundle error: " + e.getMessage()); }
                }
                case "help"               -> printHelp();
                case "exit","quit","q"    -> { System.out.println("Bye!"); System.exit(0); }
                default -> System.out.println("Unknown: '" + p[0] + "'. Type 'help'.");
            }
        }
    }

    private void printHelp() {
        System.out.println("""

  list / ls             Статус всех серверов
  download              Скачать и запатчить все ядра
  start [id|all]        Запустить
  stop  [id|all]        Остановить
  restart <id>          Перезапустить
  send <id> <cmd>       Команда в консоль сервера
  logs <id> [N]         Последние N строк лога
  ports                 Назначенные порты
  bundle                Создать NanoCore-bundle.jar (всё в одном ~200MB)
  exit / quit           Выход (все серверы стопятся)
""");
    }
}

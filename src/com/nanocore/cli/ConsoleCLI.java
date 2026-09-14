package com.nanocore.cli;

import com.nanocore.core.ServerManager;
import java.util.Scanner;

public class ConsoleCLI {

    private final ServerManager manager;

    public ConsoleCLI(ServerManager manager) { this.manager = manager; }

    public void run() {
        System.out.println("\n  NanoCore Console. Type 'help' for commands.\n");
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
                case "send","console"     -> { if (p.length>2) manager.sendCommand(p[1],p[2]); else System.out.println("Usage: send <id> <command>"); }
                case "logs","log"         -> {
                    if (p.length<2) { System.out.println("Usage: logs <id> [N]"); break; }
                    int n = 40; try { if (p.length>2) n = Integer.parseInt(p[2]); } catch (NumberFormatException ignored) {}
                    manager.tail(p[1], n).forEach(System.out::println);
                }
                case "help"               -> printHelp();
                case "exit","quit","q"    -> { System.out.println("Bye!"); System.exit(0); }
                default -> System.out.println("Unknown command: '" + p[0] + "'. Type 'help'.");
            }
        }
    }

    private void printHelp() {
        System.out.println("""

  list / ls          Status of all servers
  download           Download & patch all server jars from PaperMC / GitHub Releases
  ports              Show assigned ports
  start [id|all]     Start server(s)
  stop  [id|all]     Graceful stop
  restart <id>       Restart server
  send <id> <cmd>    Send command to server console
  logs <id> [N]      Show last N log lines (default 40)
  exit / quit        Exit (all servers stop gracefully)
""");
    }
}

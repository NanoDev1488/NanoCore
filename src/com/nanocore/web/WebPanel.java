package com.nanocore.web;

import com.nanocore.core.ServerManager;
import com.nanocore.metrics.MetricsCollector;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WebPanel — встроенная HTTP-панель мониторинга.
 * Без внешних зависимостей — использует com.sun.net.httpserver (встроен в JDK).
 *
 * Маршруты:
 *   GET  /admin/monitor      — HTML dashboard с авто-refresh 5s
 *   GET  /admin/api/status   — JSON статус всех серверов
 *   GET  /admin/api/metrics  — JSON метрики (RAM/TPS/online)
 *   POST /admin/api/cmd      — {"server":"id","cmd":"say hi"} или {"action":"start"}
 *
 * Настройка: NanoCore/web.properties
 *   web_port=8080
 *   web_user=admin
 *   web_pass=changeme
 */
public class WebPanel {

    private final int port;
    private final String user, pass;
    private final ServerManager manager;
    private final MetricsCollector metrics;
    private HttpServer server;

    public WebPanel(int port, String user, String pass,
                    ServerManager manager, MetricsCollector metrics) {
        this.port = port; this.user = user; this.pass = pass;
        this.manager = manager; this.metrics = metrics;
    }

    public static WebPanel fromConfig(Path baseDir, ServerManager mgr, MetricsCollector metrics) throws IOException {
        Properties p = new Properties();
        Path f = baseDir.resolve("web.properties");
        if (Files.exists(f)) { try (InputStream in = Files.newInputStream(f)) { p.load(in); } }
        else Files.writeString(f, "web_port=8080\n# web_user=admin\n# web_pass=changeme\n");
        int port = Integer.parseInt(p.getProperty("web_port","8080"));
        return new WebPanel(port, p.getProperty("web_user",""), p.getProperty("web_pass",""), mgr, metrics);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/admin/", this::handleAdmin);
        server.createContext("/admin/api/", this::handleApi);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[web] Panel: http://localhost:" + port + "/admin/monitor");
    }

    public void stop() { if (server != null) server.stop(0); }

    private boolean auth(HttpExchange ex) throws IOException {
        if (user.isBlank()) return true;
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h != null && h.startsWith("Basic ")) {
            String d = new String(Base64.getDecoder().decode(h.substring(6)));
            if (d.equals(user + ":" + pass)) return true;
        }
        ex.getResponseHeaders().set("WWW-Authenticate","Basic realm=\"NanoCore\"");
        respond(ex, 401, "text/plain", "Unauthorized"); return false;
    }

    private void handleAdmin(HttpExchange ex) throws IOException {
        if (!auth(ex)) return;
        respond(ex, 200, "text/html; charset=utf-8", buildPage());
    }

    private String buildPage() {
        String rows = manager.ids().stream().map(id -> {
            var m = metrics.get(id);
            var inst = manager.getInstance(id);
            boolean on = m.running;
            return "<tr>" +
                "<td><b>" + id + "</b></td>" +
                "<td><span class='" + (on?"on":"off") + "'>" + (on?"● RUNNING":"● STOPPED") + "</span></td>" +
                "<td>" + (m.pid>0?m.pid:"—") + "</td>" +
                "<td>" + (m.ramMB>0?m.ramMB+" MB":"—") + "</td>" +
                "<td>" + (m.tps>=0?String.format("%.1f",m.tps):"—") + "</td>" +
                "<td>" + (m.onlinePlayers>=0?m.onlinePlayers:"—") + "</td>" +
                "<td>" + (m.uptimeSec>0?fmt(m.uptimeSec):"—") + "</td>" +
                "<td>" +
                "<button onclick=\"cmd('"+id+"','start')\">▶</button> " +
                "<button onclick=\"cmd('"+id+"','stop')\">⏹</button> " +
                "<button onclick=\"cmd('"+id+"','restart')\">🔄</button>" +
                "</td></tr>";
        }).collect(Collectors.joining());

        String opts = manager.ids().stream()
            .map(id -> "<option>" + id + "</option>").collect(Collectors.joining());

        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
            "<meta http-equiv='refresh' content='5'>" +
            "<title>NanoCore v" + com.nanocore.NanoCoreMain.VERSION + "</title>" +
            "<style>body{font-family:monospace;background:#0d1117;color:#c9d1d9;margin:20px}" +
            "h1{color:#58a6ff}table{width:100%;border-collapse:collapse}" +
            "th{background:#161b22;color:#58a6ff;padding:8px;text-align:left}" +
            "td{padding:8px;border-bottom:1px solid #21262d}" +
            ".on{color:#3fb950}.off{color:#6e7681}" +
            ".card{background:#161b22;border-radius:8px;padding:16px;margin:8px 0}" +
            "input,select,button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:6px;border-radius:4px}" +
            "button{cursor:pointer;color:#58a6ff}</style></head><body>" +
            "<h1>🖥 NanoCore v" + com.nanocore.NanoCoreMain.VERSION + "</h1>" +
            "<div class='card'><table><tr><th>Server</th><th>Status</th><th>PID</th>" +
            "<th>RAM</th><th>TPS</th><th>Online</th><th>Uptime</th><th>Actions</th></tr>" +
            rows + "</table></div>" +
            "<div class='card'><b>Console</b><br><br>" +
            "<select id='s'>" + opts + "</select> " +
            "<input id='c' style='width:380px' placeholder='command...'> " +
            "<button onclick='send()'>Send</button>" +
            "<div id='r' style='margin-top:8px;color:#58a6ff'></div></div>" +
            "<p style='color:#6e7681;font-size:11px'>Auto-refresh: 5s | " +
            "<a href='/admin/api/status' style='color:#58a6ff'>JSON API</a></p>" +
            "<script>" +
            "function cmd(id,a){fetch('/admin/api/cmd',{method:'POST'," +
            "headers:{'Content-Type':'application/json'},body:JSON.stringify({server:id,action:a})})}" +
            "function send(){var s=document.getElementById('s').value," +
            "c=document.getElementById('cmd'||'c').value||document.getElementById('c').value;" +
            "fetch('/admin/api/cmd',{method:'POST',headers:{'Content-Type':'application/json'}," +
            "body:JSON.stringify({server:s,cmd:c})}).then(()=>{" +
            "document.getElementById('r').innerText='Sent: '+c;" +
            "document.getElementById('c').value='';})}" +
            "</script></body></html>";
    }

    private void handleApi(HttpExchange ex) throws IOException {
        if (!auth(ex)) return;
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/status")) {
            String json = "{" + manager.ids().stream().map(id -> {
                var inst = manager.getInstance(id);
                return "\""+id+"\":{\"running\":"+(inst!=null&&inst.isRunning())+
                    ",\"port\":"+(inst!=null?inst.getConfig().port:0)+"}";
            }).collect(Collectors.joining(",")) + "}";
            respond(ex, 200, "application/json", json);
        } else if (path.endsWith("/metrics")) {
            String json = "{" + manager.ids().stream()
                .map(id -> "\""+id+"\":"+metrics.get(id).toJson())
                .collect(Collectors.joining(",")) + "}";
            respond(ex, 200, "application/json", json);
        } else if (path.endsWith("/cmd") && "POST".equals(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String srv = extract(body,"server"), cmd = extract(body,"cmd"), act = extract(body,"action");
            if (srv != null) {
                if (cmd != null && !cmd.isBlank()) manager.sendCommand(srv, cmd);
                else if (act != null) switch(act) {
                    case "start" -> manager.start(srv);
                    case "stop"  -> manager.stop(srv);
                    case "restart" -> manager.restart(srv);
                }
            }
            respond(ex, 200, "application/json", "{\"ok\":true}");
        } else respond(ex, 404, "text/plain", "Not found");
    }

    private void respond(HttpExchange ex, int code, String ct, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private String extract(String json, String key) {
        String needle = "\"" + key + "\":\""; int i = json.indexOf(needle);
        if (i < 0) return null; i += needle.length(); int j = json.indexOf("\"",i);
        return j < 0 ? null : json.substring(i, j);
    }

    private String fmt(long sec) {
        long h=sec/3600, m=(sec%3600)/60, s=sec%60;
        if(h>0) return h+"h"+m+"m"; if(m>0) return m+"m"+s+"s"; return s+"s";
    }
}

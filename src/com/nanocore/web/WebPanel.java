package com.nanocore.web;

import com.nanocore.core.ServerManager;
import com.nanocore.metrics.MetricsCollector;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * WebPanel — встроенная HTTP-панель мониторинга.
 * Использует com.sun.net.httpserver.HttpServer (встроен в JDK, без зависимостей).
 *
 * Маршруты:
 *   /admin/             → Dashboard (HTML)
 *   /admin/monitor      → Метрики в реальном времени (HTML + auto-refresh)
 *   /admin/api/status   → JSON статус всех серверов
 *   /admin/api/metrics  → JSON метрики
 *   /admin/api/cmd      → POST: {"server":"velocity","cmd":"say hello"}
 *
 * Порт: настраивается через NanoCore/web.properties (по умолчанию 8080)
 * Доступ: http://YOUR_IP:8080/admin/monitor
 *
 * Базовая HTTP-авторизация: задаётся в web.properties
 *   web_user=admin
 *   web_pass=changeme
 */
public class WebPanel {

    private final int            port;
    private final String         authUser;
    private final String         authPass;
    private final ServerManager  manager;
    private final MetricsCollector metrics;
    private HttpServer           server;

    public WebPanel(int port, String user, String pass,
                    ServerManager manager, MetricsCollector metrics) {
        this.port     = port;
        this.authUser = user;
        this.authPass = pass;
        this.manager  = manager;
        this.metrics  = metrics;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/admin/", this::handleAdmin);
        server.createContext("/admin/api/", this::handleApi);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("[web] Панель запущена: http://localhost:" + port + "/admin/monitor");
        System.out.println("[web] API: http://localhost:" + port + "/admin/api/status");
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------------
    // Auth check
    // ------------------------------------------------------------------

    private boolean checkAuth(HttpExchange ex) throws IOException {
        if (authUser.isBlank()) return true; // нет авторизации
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(auth.substring(6)));
            if (decoded.equals(authUser + ":" + authPass)) return true;
        }
        ex.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"NanoCore Admin\"");
        respond(ex, 401, "text/plain", "Unauthorized");
        return false;
    }

    // ------------------------------------------------------------------
    // HTML Dashboard
    // ------------------------------------------------------------------

    private void handleAdmin(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        String path = ex.getRequestURI().getPath();

        String body = path.contains("monitor") ? buildMonitorPage() : buildDashboardPage();
        respond(ex, 200, "text/html; charset=utf-8", body);
    }

    private String buildMonitorPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<meta http-equiv='refresh' content='5'>")
          .append("<title>NanoCore Monitor</title>")
          .append("<style>")
          .append("body{font-family:monospace;background:#0d1117;color:#c9d1d9;margin:20px;}")
          .append("h1{color:#58a6ff;}table{border-collapse:collapse;width:100%;}")
          .append("th{background:#161b22;color:#58a6ff;padding:8px;text-align:left;}")
          .append("td{padding:8px;border-bottom:1px solid #21262d;}")
          .append(".on{color:#3fb950;}.off{color:#6e7681;}")
          .append(".card{background:#161b22;border-radius:8px;padding:16px;margin:8px 0;}")
          .append("input,button{background:#21262d;color:#c9d1d9;border:1px solid #30363d;padding:6px;border-radius:4px;}")
          .append("button{cursor:pointer;color:#58a6ff;}")
          .append("</style></head><body>")
          .append("<h1>🖥 NanoCore Monitor</h1>")
          .append("<div class='card'><table><tr>")
          .append("<th>Server</th><th>Status</th><th>PID</th><th>RAM</th><th>TPS</th><th>Online</th><th>Uptime</th><th>Action</th></tr>");

        for (String id : manager.ids()) {
            MetricsCollector.ServerMetrics m = metrics.get(id);
            String status = m.running ? "<span class='on'>● RUNNING</span>" : "<span class='off'>● STOPPED</span>";
            String ram    = m.ramMB > 0 ? m.ramMB + " MB" : "—";
            String tps    = m.tps >= 0  ? String.format("%.1f", m.tps) : "—";
            String online = m.onlinePlayers >= 0 ? String.valueOf(m.onlinePlayers) : "—";
            String uptime = m.uptimeSec > 0 ? formatUptime(m.uptimeSec) : "—";

            sb.append("<tr><td><b>").append(id).append("</b></td>")
              .append("<td>").append(status).append("</td>")
              .append("<td>").append(m.pid > 0 ? m.pid : "—").append("</td>")
              .append("<td>").append(ram).append("</td>")
              .append("<td>").append(tps).append("</td>")
              .append("<td>").append(online).append("</td>")
              .append("<td>").append(uptime).append("</td>")
              .append("<td>")
              .append("<button onclick=\"sendAction('").append(id).append("','start')\">▶</button> ")
              .append("<button onclick=\"sendAction('").append(id).append("','stop')\">⏹</button> ")
              .append("<button onclick=\"sendAction('").append(id).append("','restart')\">🔄</button>")
              .append("</td></tr>");
        }

        sb.append("</table></div>")
          .append("<div class='card'><b>Консоль</b><br><br>")
          .append("<select id='srv'>")
          .append(manager.ids().stream()
              .map(id -> "<option>" + id + "</option>")
              .collect(java.util.stream.Collectors.joining()))
          .append("</select> ")
          .append("<input id='cmd' style='width:400px' placeholder='команда для сервера...'> ")
          .append("<button onclick='sendCmd()'>Отправить</button>")
          .append("<div id='res' style='margin-top:8px;color:#58a6ff'></div></div>")
          .append("<p style='color:#6e7681;font-size:12px'>Обновление: каждые 5 секунд | ")
          .append("<a href='/admin/api/status' style='color:#58a6ff'>JSON API</a></p>")
          .append("<script>")
          .append("function sendAction(id,action){")
          .append("fetch('/admin/api/cmd',{method:'POST',headers:{'Content-Type':'application/json'},")
          .append("body:JSON.stringify({server:id,action:action})});}")
          .append("function sendCmd(){var s=document.getElementById('srv').value,")
          .append("c=document.getElementById('cmd').value;")
          .append("fetch('/admin/api/cmd',{method:'POST',headers:{'Content-Type':'application/json'},")
          .append("body:JSON.stringify({server:s,cmd:c})}).then(()=>{")
          .append("document.getElementById('res').innerText='Отправлено: '+c;")
          .append("document.getElementById('cmd').value='';});}")
          .append("</script>")
          .append("</body></html>");

        return sb.toString();
    }

    private String buildDashboardPage() {
        return "<!DOCTYPE html><html><head><meta http-equiv='refresh' content='0;url=/admin/monitor'></head></html>";
    }

    // ------------------------------------------------------------------
    // JSON API
    // ------------------------------------------------------------------

    private void handleApi(HttpExchange ex) throws IOException {
        if (!checkAuth(ex)) return;
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.endsWith("/status")) {
            StringBuilder json = new StringBuilder("{\"servers\":{");
            boolean first = true;
            for (String id : manager.ids()) {
                var inst = manager.getInstance(id);
                if (!first) json.append(",");
                json.append("\"").append(id).append("\":{")
                    .append("\"running\":").append(inst != null && inst.isRunning()).append(",")
                    .append("\"port\":").append(inst != null ? inst.getConfig().port : 0)
                    .append("}");
                first = false;
            }
            json.append("}}");
            respond(ex, 200, "application/json", json.toString());

        } else if (path.endsWith("/metrics")) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (String id : manager.ids()) {
                if (!first) json.append(",");
                json.append("\"").append(id).append("\":").append(metrics.get(id).toJson());
                first = false;
            }
            json.append("}");
            respond(ex, 200, "application/json", json.toString());

        } else if (path.endsWith("/cmd") && "POST".equals(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String serverId = extract(body, "server");
            String cmd      = extract(body, "cmd");
            String action   = extract(body, "action");

            if (serverId != null) {
                if (cmd != null && !cmd.isBlank())
                    manager.sendCommand(serverId, cmd);
                else if (action != null) switch (action) {
                    case "start"   -> manager.start(serverId);
                    case "stop"    -> manager.stop(serverId);
                    case "restart" -> manager.restart(serverId);
                }
            }
            respond(ex, 200, "application/json", "{\"ok\":true}");

        } else {
            respond(ex, 404, "text/plain", "Not found");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void respond(HttpExchange ex, int code, String ct, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Простой JSON field extractor без внешних либ */
    private String extract(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        i += needle.length();
        int j = json.indexOf("\"", i);
        return j < 0 ? null : json.substring(i, j);
    }

    private String formatUptime(long sec) {
        long h = sec/3600, m = (sec%3600)/60, s = sec%60;
        if (h > 0) return h + "h" + m + "m";
        if (m > 0) return m + "m" + s + "s";
        return s + "s";
    }
}

package com.nanocore.notify;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;

/**
 * NotifyService — уведомления в Discord и Telegram.
 *
 * Настройка: NanoCore/notify.properties
 *   discord_webhook=https://discord.com/api/webhooks/...
 *   discord_username=NanoCore
 *   telegram_token=BOT_TOKEN
 *   telegram_chat_id=CHAT_ID
 *   notify_crash=true
 *   notify_start=true
 *   notify_stop=true
 *   notify_backup=true
 */
public class NotifyService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    private final Properties cfg;
    private final boolean enabled;

    public NotifyService(Path baseDir) {
        cfg = new Properties();
        Path f = baseDir.resolve("notify.properties");
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) { cfg.load(in); }
            catch (IOException ignored) {}
        } else {
            try {
                Files.writeString(f,
                    "# NanoCore Notify Config\n\n" +
                    "# Discord webhook URL\n# discord_webhook=https://discord.com/api/webhooks/YOUR_ID/YOUR_TOKEN\n" +
                    "# discord_username=NanoCore\n\n" +
                    "# Telegram Bot\n# telegram_token=YOUR_BOT_TOKEN\n# telegram_chat_id=YOUR_CHAT_ID\n\n" +
                    "notify_crash=true\nnotify_start=true\nnotify_stop=true\nnotify_backup=true\n");
                System.out.println("[notify] Template created: " + f);
            } catch (IOException ignored) {}
        }
        enabled = !cfg.getProperty("discord_webhook", "").isBlank()
               || !cfg.getProperty("telegram_token",  "").isBlank();
    }

    public void serverStarted(String id) { if (bool("notify_start")) send("✅ **" + id + "** started",  "green",  "✅ " + id + " started");  }
    public void serverStopped(String id) { if (bool("notify_stop"))  send("🛑 **" + id + "** stopped",  "red",    "🔴 " + id + " stopped");  }
    public void serverCrashed(String id, int n) {
        if (bool("notify_crash"))
            send("⚠️ **" + id + "** crashed (#" + n + ")", "orange", "⚠️ " + id + " crashed #" + n);
    }
    public void backupDone(String path, long mb) {
        if (bool("notify_backup"))
            send("💾 Backup: `" + path + "` (" + mb + " MB)", "blue", "💾 Backup: " + path + " (" + mb + " MB)");
    }
    public void custom(String msg) { send(msg, "grey", msg); }

    private void send(String discordMsg, String color, String tgMsg) {
        if (!enabled) return;
        String dw = cfg.getProperty("discord_webhook", "").trim();
        if (!dw.isBlank()) sendDiscord(dw, discordMsg, color);
        String tok = cfg.getProperty("telegram_token",  "").trim();
        String cid = cfg.getProperty("telegram_chat_id","").trim();
        if (!tok.isBlank() && !cid.isBlank()) sendTelegram(tok, cid, tgMsg);
    }

    private void sendDiscord(String url, String message, String color) {
        int c = switch (color) {
            case "green" -> 0x57F287; case "red" -> 0xED4245;
            case "orange"-> 0xFEE75C; case "blue"-> 0x5865F2; default -> 0x99AAB5;
        };
        String user = cfg.getProperty("discord_username", "NanoCore");
        String json = "{\"username\":\"" + esc(user) + "\",\"embeds\":[{\"description\":\"" + esc(message) + "\",\"color\":" + c + "}]}";
        post(url, json);
    }

    private void sendTelegram(String token, String chatId, String message) {
        String url  = "https://api.telegram.org/bot" + token + "/sendMessage";
        String json = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + esc(message) + "\",\"parse_mode\":\"Markdown\"}";
        post(url, json);
    }

    private void post(String url, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) { System.out.println("[notify] Error: " + e.getMessage()); }
    }

    private boolean bool(String k) { return !"false".equalsIgnoreCase(cfg.getProperty(k, "true")); }
    private String esc(String s) { return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"); }
}

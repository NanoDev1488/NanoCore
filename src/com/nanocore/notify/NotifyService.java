package com.nanocore.notify;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;

/**
 * NotifyService — отправляет уведомления в Discord и/или Telegram.
 *
 * Настройка через NanoCore/notify.properties:
 *
 *   # Discord
 *   discord_webhook=https://discord.com/api/webhooks/...
 *   discord_username=NanoCore
 *   discord_avatar=https://cdn...
 *
 *   # Telegram
 *   telegram_token=1234567890:ABC...
 *   telegram_chat_id=-1001234567890
 *
 *   # Какие события отправлять
 *   notify_crash=true
 *   notify_start=true
 *   notify_stop=true
 *   notify_backup=true
 */
public class NotifyService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final Properties cfg;
    private final boolean enabled;

    public NotifyService(Path baseDir) {
        cfg = new Properties();
        Path f = baseDir.resolve("notify.properties");
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) { cfg.load(in); }
            catch (IOException ignored) {}
        } else {
            // Создаём шаблон
            try {
                Files.writeString(f,
                    "# NanoCore Notify Config\n\n" +
                    "# Discord webhook URL\n" +
                    "# discord_webhook=https://discord.com/api/webhooks/YOUR_ID/YOUR_TOKEN\n" +
                    "# discord_username=NanoCore\n\n" +
                    "# Telegram Bot\n" +
                    "# telegram_token=YOUR_BOT_TOKEN\n" +
                    "# telegram_chat_id=YOUR_CHAT_ID\n\n" +
                    "notify_crash=true\n" +
                    "notify_start=true\n" +
                    "notify_stop=true\n" +
                    "notify_backup=true\n"
                );
                System.out.println("[notify] Создан шаблон: " + f);
            } catch (IOException ignored) {}
        }
        enabled = !cfg.getProperty("discord_webhook", "").isBlank()
               || !cfg.getProperty("telegram_token", "").isBlank();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void serverStarted(String serverId) {
        if (bool("notify_start"))
            send("✅ **" + serverId + "** запущен", "green", "🟢 " + serverId + " запущен");
    }

    public void serverStopped(String serverId) {
        if (bool("notify_stop"))
            send("🛑 **" + serverId + "** остановлен", "red", "🔴 " + serverId + " остановлен");
    }

    public void serverCrashed(String serverId, int crashCount) {
        if (bool("notify_crash"))
            send("⚠️ **" + serverId + "** упал (краш #" + crashCount + ")", "orange",
                "⚠️ " + serverId + " упал, краш #" + crashCount);
    }

    public void backupDone(String path, long sizeMB) {
        if (bool("notify_backup"))
            send("💾 Бэкап создан: `" + path + "` (" + sizeMB + " MB)", "blue",
                "💾 Бэкап: " + path + " (" + sizeMB + " MB)");
    }

    public void custom(String message) {
        send(message, "grey", message);
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void send(String discordMsg, String color, String telegramMsg) {
        if (!enabled) return;

        String discordUrl = cfg.getProperty("discord_webhook", "").trim();
        if (!discordUrl.isBlank()) sendDiscord(discordUrl, discordMsg, color);

        String tgToken  = cfg.getProperty("telegram_token",  "").trim();
        String tgChat   = cfg.getProperty("telegram_chat_id","").trim();
        if (!tgToken.isBlank() && !tgChat.isBlank()) sendTelegram(tgToken, tgChat, telegramMsg);
    }

    private void sendDiscord(String webhookUrl, String message, String color) {
        int colorInt = switch (color) {
            case "green"  -> 0x57F287;
            case "red"    -> 0xED4245;
            case "orange" -> 0xFEE75C;
            case "blue"   -> 0x5865F2;
            default       -> 0x99AAB5;
        };

        String username = cfg.getProperty("discord_username", "NanoCore");
        String avatar   = cfg.getProperty("discord_avatar", "");

        String json = String.format(
            "{\"username\":\"%s\"%s,\"embeds\":[{\"description\":\"%s\",\"color\":%d}]}",
            escape(username),
            avatar.isBlank() ? "" : ",\"avatar_url\":\"" + avatar + "\"",
            escape(message),
            colorInt
        );

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("[notify] Discord error: " + e.getMessage());
        }
    }

    private void sendTelegram(String token, String chatId, String message) {
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        String json = String.format(
            "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"Markdown\"}",
            chatId, escape(message)
        );

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("[notify] Telegram error: " + e.getMessage());
        }
    }

    private boolean bool(String key) {
        return "true".equalsIgnoreCase(cfg.getProperty(key, "true"));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

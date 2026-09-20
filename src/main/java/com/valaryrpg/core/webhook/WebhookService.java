package com.valaryrpg.core.webhook;

import com.valaryrpg.core.ValaryCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Invia log e SEVERE su Discord via HttpClient async. Un solo thread daemon.
 */
public final class WebhookService {

    private final ValaryCorePlugin plugin;
    private final ExecutorService executor;
    private final HttpClient http;
    private final Map<String, Long> errorCooldown = new ConcurrentHashMap<>();
    private final AtomicBoolean sending = new AtomicBoolean();
    private Handler consoleHandler;

    public WebhookService(ValaryCorePlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "valary-webhook");
            thread.setDaemon(true);
            return thread;
        });
        this.http = HttpClient.newBuilder()
                .executor(this.executor)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void enable() {
        attachConsole();
    }

    public void reload() {
        detachConsole();
        this.errorCooldown.clear();
        attachConsole();
    }

    public void disable() {
        detachConsole();
        this.executor.shutdownNow();
    }

    public void log(String category, String title, String body) {
        FileConfiguration yaml = yaml();
        if (!active(yaml) || !yaml.getBoolean("logs.enabled", true)) {
            return;
        }
        if (!yaml.getBoolean("logs.categories." + category, false)) {
            return;
        }
        String url = firstUrl(yaml.getString("logs.url"), yaml.getString("url"));
        if (url.isBlank()) {
            return;
        }
        post(url, title, body, yaml.getInt("logs.color", 5793266));
    }

    public void severe(String loggerName, String message, Throwable thrown) {
        FileConfiguration yaml = yaml();
        if (!active(yaml) || !yaml.getBoolean("errors.enabled", true)) {
            return;
        }
        if (this.sending.get() || httpClientNoise(loggerName)) {
            return;
        }
        String combined = (loggerName == null ? "" : loggerName) + " " + (message == null ? "" : message);
        if (thrown != null) {
            combined = combined + " " + thrown.getClass().getName() + " " + String.valueOf(thrown.getMessage());
        }
        if (yaml.getBoolean("errors.ignore-memory", true) && memoryNoise(combined, thrown)) {
            return;
        }
        long cooldown = Math.max(0L, yaml.getLong("errors.same-message-cooldown-millis", 15000L));
        String key = combined.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Long last = this.errorCooldown.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        this.errorCooldown.put(key, now);
        String url = firstUrl(yaml.getString("errors.url"), yaml.getString("url"));
        if (url.isBlank()) {
            return;
        }
        String body = (loggerName == null || loggerName.isBlank() ? "console" : loggerName) + "\n" + (message == null ? "" : message);
        if (thrown != null) {
            body = body + "\n" + thrown.getClass().getName() + ": " + String.valueOf(thrown.getMessage());
        }
        post(url, "Errore grave", body, yaml.getInt("errors.color", 15158332));
    }

    private void attachConsole() {
        FileConfiguration yaml = yaml();
        if (!active(yaml) || !yaml.getBoolean("errors.enabled", true)) {
            return;
        }
        this.consoleHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || record.getLevel().intValue() < Level.SEVERE.intValue()) {
                    return;
                }
                WebhookService.this.severe(record.getLoggerName(), record.getMessage(), record.getThrown());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Bukkit.getLogger().addHandler(this.consoleHandler);
    }

    private void detachConsole() {
        if (this.consoleHandler == null) {
            return;
        }
        Bukkit.getLogger().removeHandler(this.consoleHandler);
        this.consoleHandler = null;
    }

    private void post(String url, String title, String body, int color) {
        FileConfiguration yaml = yaml();
        if (yaml == null) {
            return;
        }
        int timeout = Math.max(500, yaml.getInt("timeout-millis", 4000));
        String username = yaml.getString("username", "ValaryRPG");
        String json = embed(username, title, body, color);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception ignored) {
            return;
        }
        this.sending.set(true);
        this.http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> this.sending.set(false));
    }

    private boolean active(FileConfiguration yaml) {
        return yaml != null
                && this.plugin.configs().root().getBoolean("modules.webhooks", true)
                && yaml.getBoolean("enabled", true);
    }

    private FileConfiguration yaml() {
        try {
            return this.plugin.configs().webhooks();
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean httpClientNoise(String loggerName) {
        if (loggerName == null) {
            return false;
        }
        String name = loggerName.toLowerCase(Locale.ROOT);
        return name.startsWith("jdk.internal.http")
                || name.startsWith("java.net.http")
                || name.contains("valary-webhook");
    }

    static boolean memoryNoise(String text, Throwable thrown) {
        if (thrown instanceof OutOfMemoryError) {
            return true;
        }
        Throwable cause = thrown;
        while (cause != null) {
            if (cause instanceof OutOfMemoryError) {
                return true;
            }
            cause = cause.getCause();
        }
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("outofmemory")
                || lower.contains("out of memory")
                || lower.contains("java heap")
                || lower.contains("heap space")
                || lower.contains("gc overhead")
                || lower.contains("metaspace")
                || lower.contains("unable to create native thread")
                || (lower.contains("memory") && (lower.contains("error") || lower.contains("fail")));
    }

    private static String firstUrl(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String embed(String username, String title, String body, int color) {
        String description = body == null ? "" : body;
        if (description.length() > 3500) {
            description = description.substring(0, 3500) + "…";
        }
        return "{\"username\":\"" + escape(username)
                + "\",\"embeds\":[{\"title\":\"" + escape(title)
                + "\",\"description\":\"" + escape(description)
                + "\",\"color\":" + color
                + ",\"timestamp\":\"" + Instant.now() + "\"}]}";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 32) {
                        builder.append(' ');
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}

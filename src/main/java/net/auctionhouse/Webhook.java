package net.auctionhouse;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sends auction events to a Discord webhook (asynchronously, never blocks the server). */
public class Webhook {

    private final AuctionHousePlugin plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private long lastWarn;

    public Webhook(AuctionHousePlugin plugin) { this.plugin = plugin; }

    private boolean on(String event) {
        var c = plugin.getConfig();
        return c.getBoolean("webhook.enabled", false)
                && !c.getString("webhook.url", "").isBlank()
                && c.getBoolean("webhook.events." + event, false);
    }

    private static String clean(String s) { return s == null ? "-" : ChatColor.stripColor(s); }

    private String label(String key, String def) { return plugin.getConfig().getString("webhook.labels." + key, def); }

    // ---------- events ----------
    public void listed(String seller, ItemStack item, double price) {
        if (!on("listed")) return;
        Map<String, String> f = new LinkedHashMap<>();
        f.put(label("item", "Item"), clean(plugin.itemName(item)));
        f.put(label("price", "Price"), clean(plugin.fmt(price)));
        f.put(label("seller", "Seller"), seller);
        send("listed", f);
    }

    public void sold(String buyer, Auction a) {
        if (!on("sold")) return;
        Map<String, String> f = new LinkedHashMap<>();
        f.put(label("item", "Item"), clean(plugin.itemName(a.item)));
        f.put(label("price", "Price"), clean(plugin.fmt(a.price)));
        f.put(label("seller", "Seller"), a.sellerName);
        f.put(label("buyer", "Buyer"), buyer);
        send("sold", f);
    }

    public void cancelled(Auction a) { simple("cancelled", a); }

    public void expired(Auction a) { simple("expired", a); }

    private void simple(String event, Auction a) {
        if (!on(event)) return;
        Map<String, String> f = new LinkedHashMap<>();
        f.put(label("item", "Item"), clean(plugin.itemName(a.item)));
        f.put(label("price", "Price"), clean(plugin.fmt(a.price)));
        f.put(label("seller", "Seller"), a.sellerName);
        send(event, f);
    }

    // ---------- sending ----------
    private void send(String event, Map<String, String> fields) {
        var c = plugin.getConfig();
        String url = c.getString("webhook.url", "").trim();
        StringBuilder fs = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (fs.length() > 0) fs.append(',');
            fs.append("{\"name\":\"").append(esc(e.getKey())).append("\",\"value\":\"").append(esc(e.getValue()))
                    .append("\",\"inline\":true}");
        }
        String avatar = c.getString("webhook.avatar-url", "");
        String mention = c.getString("webhook.mentions." + event, "").trim();
        List<String> parse = new ArrayList<>();
        if (mention.contains("@everyone") || mention.contains("@here")) parse.add("\"everyone\"");
        if (mention.contains("<@&")) parse.add("\"roles\"");
        if (mention.matches(".*<@!?\\d+>.*")) parse.add("\"users\"");
        String json = "{\"username\":\"" + esc(c.getString("webhook.username", "AuctionHouse")) + "\","
                + (avatar.isBlank() ? "" : "\"avatar_url\":\"" + esc(avatar) + "\",")
                + (mention.isEmpty() ? "" : "\"content\":\"" + esc(mention) + "\",")
                + "\"allowed_mentions\":{\"parse\":[" + String.join(",", parse) + "]},"
                + "\"embeds\":[{\"title\":\"" + esc(c.getString("webhook.titles." + event, event)) + "\","
                + "\"color\":" + c.getInt("webhook.colors." + event, 3447003) + ","
                + "\"fields\":[" + fs + "],"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"footer\":{\"text\":\"" + esc(c.getString("webhook.footer", "AuctionHouse - made by itzblace")) + "\"}}]}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
                if (r.statusCode() >= 300) warn("Discord answered " + r.statusCode() + ": " + r.body());
            }).exceptionally(t -> { warn("Could not reach Discord: " + t.getMessage()); return null; });
        } catch (IllegalArgumentException ex) {
            warn("Invalid webhook URL in config.yml");
        }
    }

    private void warn(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 30_000) return;   // don't spam the console
        lastWarn = now;
        plugin.getLogger().warning("[Webhook] " + msg);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch)); else sb.append(ch); }
            }
        }
        return sb.toString();
    }
}

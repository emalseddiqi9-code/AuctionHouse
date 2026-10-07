package net.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class AuctionManager {

    private final AuctionHousePlugin plugin;
    private final Map<Integer, Auction> map = new LinkedHashMap<>();
    private final File file;
    private int nextId = 1;
    private boolean pending;

    public AuctionManager(AuctionHousePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
    }

    public List<Auction> all() { return new ArrayList<>(map.values()); }

    // ---------- persistence ----------
    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getInt("next", 1);
        ConfigurationSection sec = y.getConfigurationSection("auctions");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            ItemStack item = s.getItemStack("item");
            if (item == null) continue;
            Auction a = new Auction();
            a.id = Integer.parseInt(key);
            String u = s.getString("seller");
            a.seller = u == null ? null : UUID.fromString(u);
            a.sellerName = s.getString("sellerName", "Server");
            a.item = item;
            a.price = s.getDouble("price");
            a.created = s.getLong("created");
            a.expires = s.getLong("expires");
            a.status = Auction.Status.valueOf(s.getString("status", "ACTIVE"));
            a.server = s.getBoolean("server");
            a.unlimited = s.getBoolean("unlimited");
            a.buyerName = s.getString("buyer");
            a.soldAt = s.getLong("soldAt");
            a.notified = s.getBoolean("notified");
            map.put(a.id, a);
        }
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextId);
        for (Auction a : map.values()) {
            String p = "auctions." + a.id + ".";
            y.set(p + "seller", a.seller == null ? null : a.seller.toString());
            y.set(p + "sellerName", a.sellerName);
            y.set(p + "item", a.item);
            y.set(p + "price", a.price);
            y.set(p + "created", a.created);
            y.set(p + "expires", a.expires);
            y.set(p + "status", a.status.name());
            y.set(p + "server", a.server);
            y.set(p + "unlimited", a.unlimited);
            y.set(p + "buyer", a.buyerName);
            y.set(p + "soldAt", a.soldAt);
            y.set(p + "notified", a.notified);
        }
        return y.saveToString();
    }

    private void write(String data) {
        try {
            plugin.getDataFolder().mkdirs();
            Files.write(file.toPath(), data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save auctions: " + e.getMessage());
        }
    }

    /** Debounced async save. */
    public void save() {
        if (pending) return;
        pending = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending = false;
            String data = serialize();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
        }, 40L);
    }

    public void saveNow() { write(serialize()); }

    // ---------- creating ----------
    public Auction create(Player seller, ItemStack item, double price) {
        long now = System.currentTimeMillis();
        Auction a = new Auction();
        a.id = nextId++;
        a.seller = seller.getUniqueId();
        a.sellerName = seller.getName();
        a.item = item.clone();
        a.price = price;
        a.created = now;
        a.expires = now + plugin.getConfig().getLong("settings.auction-duration-hours", 48) * 3_600_000L;
        map.put(a.id, a);
        save();
        return a;
    }

    public Auction createServer(ItemStack item, double price, boolean unlimited) {
        Auction a = new Auction();
        a.id = nextId++;
        a.sellerName = "Server";
        a.item = item.clone();
        a.price = price;
        a.created = System.currentTimeMillis();
        a.server = true;
        a.unlimited = unlimited;
        map.put(a.id, a);
        save();
        return a;
    }

    // ---------- queries ----------
    public int countActive(UUID id) {
        int c = 0;
        for (Auction a : map.values())
            if (a.status == Auction.Status.ACTIVE && id.equals(a.seller)) c++;
        return c;
    }

    public int maxAuctions(Player p) {
        int max = plugin.getConfig().getInt("settings.default-max-auctions", 3);
        String prefix = "auctionhouse.auctions.";
        for (PermissionAttachmentInfo pai : p.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String s = pai.getPermission().toLowerCase();
            if (s.startsWith(prefix)) {
                try { max = Math.max(max, Integer.parseInt(s.substring(prefix.length()))); }
                catch (NumberFormatException ignored) { }
            }
        }
        return max;
    }

    // ---------- actions ----------
    private boolean hasSpace(Player p, ItemStack item) {
        int free = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) free += item.getMaxStackSize();
            else if (s.isSimilar(item)) free += Math.max(0, s.getMaxStackSize() - s.getAmount());
        }
        return free >= item.getAmount();
    }

    /** @return null on success, otherwise a message key. */
    public String buy(Player p, Auction a) {
        if (a.status != Auction.Status.ACTIVE) return "not-available";
        if (!a.server && p.getUniqueId().equals(a.seller)) return "own-auction";
        if (plugin.economy().getBalance(p) < a.price) return "no-money";
        if (!hasSpace(p, a.item)) return "inventory-full";

        plugin.economy().withdrawPlayer(p, a.price);
        double payout = 0;
        if (!a.server) {
            double tax = plugin.getConfig().getDouble("settings.sell-tax-percent", 0);
            payout = a.price * (1 - tax / 100.0);
            plugin.economy().depositPlayer(Bukkit.getOfflinePlayer(a.seller), payout);
        }
        p.getInventory().addItem(a.item.clone());

        if (a.server) {
            if (!a.unlimited) map.remove(a.id);
        } else {
            a.status = Auction.Status.SOLD;
            a.buyerName = p.getName();
            a.soldAt = System.currentTimeMillis();
            Player sp = Bukkit.getPlayer(a.seller);
            if (sp != null) {
                a.notified = true;
                sp.sendMessage(plugin.msg("sold-notify", "%buyer%", p.getName(),
                        "%item%", plugin.itemName(a.item), "%price%", plugin.fmt(a.price),
                        "%payout%", plugin.fmt(payout)));
            }
        }
        save();
        return null;
    }

    public void cancel(Auction a) {
        if (a.server) map.remove(a.id);
        else if (a.status == Auction.Status.ACTIVE) a.status = Auction.Status.CANCELLED;
        save();
    }

    public int cancelAll(UUID owner) {
        int c = 0;
        for (Auction a : all())
            if (a.status == Auction.Status.ACTIVE && owner.equals(a.seller)) { a.status = Auction.Status.CANCELLED; c++; }
        if (c > 0) save();
        return c;
    }

    /** @return null on success, otherwise a message key. */
    public String returnOne(Player p, Auction a) {
        if (a.status != Auction.Status.EXPIRED && a.status != Auction.Status.CANCELLED) return "not-available";
        if (!p.getUniqueId().equals(a.seller)) return "not-available";
        if (!hasSpace(p, a.item)) return "inventory-full";
        p.getInventory().addItem(a.item.clone());
        map.remove(a.id);
        save();
        return null;
    }

    public int returnAll(Player p) {
        int c = 0;
        for (Auction a : all()) {
            if (!p.getUniqueId().equals(a.seller)) continue;
            if (a.status != Auction.Status.EXPIRED && a.status != Auction.Status.CANCELLED) continue;
            if (returnOne(p, a) != null) break;
            c++;
        }
        return c;
    }

    public int unnotifiedSales(UUID id) {
        int c = 0;
        for (Auction a : map.values())
            if (a.status == Auction.Status.SOLD && !a.notified && id.equals(a.seller)) { a.notified = true; c++; }
        if (c > 0) save();
        return c;
    }

    /** Runs every 30s: expires auctions and purges old history. */
    public void tick() {
        long now = System.currentTimeMillis();
        long keep = plugin.getConfig().getLong("settings.sold-history-days", 7) * 86_400_000L;
        boolean changed = false;
        Iterator<Auction> it = map.values().iterator();
        while (it.hasNext()) {
            Auction a = it.next();
            if (a.status == Auction.Status.ACTIVE && !a.server && a.expires > 0 && now >= a.expires) {
                a.status = Auction.Status.EXPIRED;
                changed = true;
                Player sp = Bukkit.getPlayer(a.seller);
                if (sp != null) sp.sendMessage(plugin.msg("expired-notify"));
            } else if (a.status == Auction.Status.SOLD && now - a.soldAt > keep) {
                it.remove();
                changed = true;
            }
        }
        if (changed) save();
    }
}

package net.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

public class AhCommand implements CommandExecutor, TabCompleter {

    private static final String[][] HELP = {
            {"", "auctionhouse.use", "open the auction house"},
            {"reload", "auctionhouse.reload", "reload the configuration"},
            {"show [player]", "auctionhouse.show", "open the auction house for a player"},
            {"menu [player]", "auctionhouse.menu", "open the auction house with a back button"},
            {"search <filter>", "auctionhouse.search", "view items matching the filter"},
            {"help", "auctionhouse.help", "show this help"},
            {"sell <price>", "auctionhouse.sell", "sell the item in your hand"},
            {"list <price> [count]", "auctionhouse.list", "list the item as server"},
            {"ulist <price> [count]", "auctionhouse.ulist", "list the item as server (unlimited buying)"},
            {"selling", "auctionhouse.selling", "view the items you are selling"},
            {"sold", "auctionhouse.sold", "view the items you sold recently"},
            {"expired", "auctionhouse.expired", "view your expired items"},
            {"cancel", "auctionhouse.cancel", "cancel all your auctions"},
            {"return", "auctionhouse.return", "return all cancelled/expired items"}
    };

    private final AuctionHousePlugin plugin;

    public AhCommand(AuctionHousePlugin plugin) { this.plugin = plugin; }

    private boolean perm(CommandSender s, String p) {
        if (s.hasPermission(p)) return true;
        s.sendMessage(plugin.msg("no-permission"));
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(plugin.msg("player-only"));
        return null;
    }

    private void open(Player p, AhMenu.Type t, String filter, boolean back) {
        new AhMenu(plugin, p, t, filter, back).open();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            Player p = player(s);
            if (p != null && perm(s, "auctionhouse.use")) open(p, AhMenu.Type.ALL, null, false);
            return true;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (!perm(s, "auctionhouse.reload")) return true;
                plugin.reload();
                s.sendMessage(plugin.msg("reload"));
            }
            case "show", "menu" -> {
                if (!perm(s, "auctionhouse." + sub)) return true;
                Player t;
                if (a.length > 1) {
                    t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { s.sendMessage(plugin.msg("player-not-found")); return true; }
                } else if (s instanceof Player pp) {
                    t = pp;
                } else {
                    s.sendMessage(plugin.msg("usage-show"));
                    return true;
                }
                open(t, AhMenu.Type.ALL, null, sub.equals("menu"));
            }
            case "search" -> {
                Player p = player(s);
                if (p == null || !perm(s, "auctionhouse.search")) return true;
                if (a.length < 2) { s.sendMessage(plugin.msg("usage-search")); return true; }
                open(p, AhMenu.Type.SEARCH, String.join(" ", Arrays.copyOfRange(a, 1, a.length)), false);
            }
            case "help" -> {
                if (!perm(s, "auctionhouse.help")) return true;
                s.sendMessage(AuctionHousePlugin.color("&6&lAuctionHouse &7- Commands &8(made by &bitzblace&8)"));
                for (String[] h : HELP)
                    if (s.hasPermission(h[1]))
                        s.sendMessage(AuctionHousePlugin.color("&e/ah " + h[0] + " &7- " + h[2]));
            }
            case "sell" -> {
                Player p = player(s);
                if (p != null && perm(s, "auctionhouse.sell")) sell(p, a);
            }
            case "list", "ulist" -> {
                Player p = player(s);
                if (p != null && perm(s, "auctionhouse." + sub)) serverList(p, a, sub.equals("ulist"));
            }
            case "selling" -> {
                Player p = player(s);
                if (p != null && perm(s, "auctionhouse.selling")) open(p, AhMenu.Type.SELLING, null, false);
            }
            case "sold" -> {
                Player p = player(s);
                if (p != null && perm(s, "auctionhouse.sold")) open(p, AhMenu.Type.SOLD, null, false);
            }
            case "expired" -> {
                Player p = player(s);
                if (p != null && perm(s, "auctionhouse.expired")) open(p, AhMenu.Type.EXPIRED, null, false);
            }
            case "cancel" -> {
                if (!perm(s, "auctionhouse.cancel")) return true;
                UUID target;
                if (a.length > 1) {
                    if (!perm(s, "auctionhouse.cancel.others")) return true;
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                    target = op.getUniqueId();
                } else {
                    Player p = player(s);
                    if (p == null) return true;
                    target = p.getUniqueId();
                }
                int n = plugin.manager().cancelAll(target);
                s.sendMessage(n == 0 ? plugin.msg("nothing") : plugin.msg("cancelled-all", "%count%", String.valueOf(n)));
            }
            case "return" -> {
                Player p = player(s);
                if (p == null || !perm(s, "auctionhouse.return")) return true;
                int n = plugin.manager().returnAll(p);
                p.sendMessage(n == 0 ? plugin.msg("none-to-return") : plugin.msg("returned", "%count%", String.valueOf(n)));
            }
            default -> s.sendMessage(plugin.msg("unknown"));
        }
        return true;
    }

    // ---------- helpers ----------
    private Double parsePrice(String in) {
        try {
            String s = in.toLowerCase().replace(',', '.');
            double mult = 1;
            if (s.endsWith("k")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) return null;
            return Math.round(v * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean checkPrice(Player p, Double price) {
        if (price == null) { p.sendMessage(plugin.msg("invalid-price")); return false; }
        double min = plugin.getConfig().getDouble("settings.min-price", 1);
        double max = plugin.getConfig().getDouble("settings.max-price", 1_000_000_000);
        if (price < min || price > max) {
            p.sendMessage(plugin.msg("price-range", "%min%", plugin.fmt(min), "%max%", plugin.fmt(max)));
            return false;
        }
        return true;
    }

    private ItemStack hand(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it == null || it.getType().isAir()) { p.sendMessage(plugin.msg("hold-item")); return null; }
        if (plugin.getConfig().getStringList("settings.blacklist").contains(it.getType().name())) {
            p.sendMessage(plugin.msg("blacklisted"));
            return null;
        }
        return it;
    }

    private void sell(Player p, String[] a) {
        if (a.length < 2) { p.sendMessage(plugin.msg("usage-sell")); return; }
        ItemStack it = hand(p);
        if (it == null) return;
        Double price = parsePrice(a[1]);
        if (!checkPrice(p, price)) return;
        AuctionManager m = plugin.manager();
        int max = m.maxAuctions(p);
        if (m.countActive(p.getUniqueId()) >= max) {
            p.sendMessage(plugin.msg("limit-reached", "%max%", String.valueOf(max)));
            return;
        }
        ItemStack copy = it.clone();
        p.getInventory().setItemInMainHand(null);
        m.create(p, copy, price);
        p.sendMessage(plugin.msg("listed", "%item%", plugin.itemName(copy), "%price%", plugin.fmt(price)));
        if (plugin.getConfig().getBoolean("settings.broadcast-on-sell", true))
            Bukkit.broadcastMessage(plugin.msg("broadcast", "%player%", p.getName(),
                    "%item%", plugin.itemName(copy), "%price%", plugin.fmt(price)));
    }

    private void serverList(Player p, String[] a, boolean unlimited) {
        if (a.length < 2) { p.sendMessage(plugin.msg(unlimited ? "usage-ulist" : "usage-list")); return; }
        ItemStack it = hand(p);
        if (it == null) return;
        Double price = parsePrice(a[1]);
        if (!checkPrice(p, price)) return;
        int count = 1;
        if (a.length > 2) {
            try { count = Math.max(1, Math.min(100, Integer.parseInt(a[2]))); }
            catch (NumberFormatException e) { p.sendMessage(plugin.msg("invalid-price")); return; }
        }
        for (int i = 0; i < count; i++) plugin.manager().createServer(it, price, unlimited);
        p.sendMessage(plugin.msg("listed-server", "%count%", String.valueOf(count),
                "%item%", plugin.itemName(it), "%price%", plugin.fmt(price)));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) {
            return Arrays.stream(HELP)
                    .filter(h -> !h[0].isEmpty() && s.hasPermission(h[1]))
                    .map(h -> h[0].split(" ")[0])
                    .filter(n -> n.startsWith(a[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("show") || a[0].equalsIgnoreCase("menu")
                || (a[0].equalsIgnoreCase("cancel") && s.hasPermission("auctionhouse.cancel.others")))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

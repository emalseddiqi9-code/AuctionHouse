package net.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class AuctionHousePlugin extends JavaPlugin {

    private Economy economy;
    private AuctionManager manager;
    private Webhook webhook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Vault economy found! Install an economy plugin (e.g. EssentialsX). Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        if (!getConfig().contains("webhook.mentions.sold", true)) { getConfig().options().copyDefaults(true); saveConfig(); }
        manager = new AuctionManager(this);
        manager.load();
        webhook = new Webhook(this);

        AhCommand cmd = new AhCommand(this);
        PluginCommand pc = getCommand("ah");
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L * 30, 20L * 30);
        getLogger().info("AuctionHouse enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.saveNow();
    }

    public void reload() {
        reloadConfig();
    }

    public Economy economy() { return economy; }
    public AuctionManager manager() { return manager; }
    public Webhook webhook() { return webhook; }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String msg(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace(kv[i], kv[i + 1]);
        return color(getConfig().getString("messages.prefix", "") + s);
    }

    public String fmt(double price) {
        return economy.format(price);
    }

    public String itemName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        String n;
        if (m != null && m.hasDisplayName()) {
            n = m.getDisplayName();
        } else {
            StringBuilder sb = new StringBuilder();
            for (String w : it.getType().name().toLowerCase().split("_"))
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            n = sb.toString().trim();
        }
        return it.getAmount() > 1 ? it.getAmount() + "x " + n : n;
    }

    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000, d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + (s % 60) + "s";
        return s + "s";
    }
}

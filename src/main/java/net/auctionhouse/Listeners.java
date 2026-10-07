package net.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

public class Listeners implements Listener {

    private final AuctionHousePlugin plugin;

    public Listeners(AuctionHousePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AhMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof AhMenu)
                menu.click(e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AhMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();

        // sign trigger
        BlockState st = b.getState();
        if (st instanceof Sign sign) {
            String l0 = org.bukkit.ChatColor.stripColor(sign.getLine(0)).trim();
            for (String s : plugin.getConfig().getStringList("triggers.sign-lines")) {
                if (s.equalsIgnoreCase(l0)) {
                    e.setCancelled(true);
                    if (p.hasPermission("auctionhouse.trigger.sign") && p.hasPermission("auctionhouse.use"))
                        new AhMenu(plugin, p, AhMenu.Type.ALL, null, false).open();
                    else p.sendMessage(plugin.msg("no-permission"));
                    return;
                }
            }
        }

        // block trigger
        String key = b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
        if (plugin.getConfig().getStringList("triggers.blocks").contains(key)) {
            e.setCancelled(true);
            if (p.hasPermission("auctionhouse.trigger.block") && p.hasPermission("auctionhouse.use"))
                new AhMenu(plugin, p, AhMenu.Type.ALL, null, false).open();
            else p.sendMessage(plugin.msg("no-permission"));
        }
    }

    @EventHandler
    public void onEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String tag = plugin.getConfig().getString("triggers.entity-tag", "auctionhouse");
        if (!e.getRightClicked().getScoreboardTags().contains(tag)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.hasPermission("auctionhouse.trigger.entity") && p.hasPermission("auctionhouse.use"))
            new AhMenu(plugin, p, AhMenu.Type.ALL, null, false).open();
        else p.sendMessage(plugin.msg("no-permission"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int c = plugin.manager().unnotifiedSales(p.getUniqueId());
            if (c > 0) p.sendMessage(plugin.msg("join-sold", "%count%", String.valueOf(c)));
        }, 40L);
    }
}

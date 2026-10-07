package net.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class AhMenu implements InventoryHolder {

    public enum Type { ALL, SEARCH, SELLING, SOLD, EXPIRED }

    private static final String ARROW = "\u25B6";
    private static final String[] SORTS = {"Newest Auctions", "Oldest Auctions", "Cheapest Auctions", "Most Paid Auctions"};

    private final AuctionHousePlugin plugin;
    private final Player viewer;
    private final Type type;
    private final String filter;
    private final boolean back;
    private final Inventory inv;
    private final Auction[] slots = new Auction[45];
    private int page = 0, sort = 0, maxPage = 0;

    public AhMenu(AuctionHousePlugin plugin, Player viewer, Type type, String filter, boolean back) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        this.filter = filter;
        this.back = back;
        String title = switch (type) {
            case ALL -> "&8Auctionhouse | Menu";
            case SEARCH -> "&8Auctionhouse | Search";
            case SELLING -> "&8Auctionhouse | Selling";
            case SOLD -> "&8Auctionhouse | Sold";
            case EXPIRED -> "&8Auctionhouse | Expired";
        };
        this.inv = Bukkit.createInventory(this, 54, AuctionHousePlugin.color(title));
    }

    @Override
    public Inventory getInventory() { return inv; }

    public void open() {
        render();
        viewer.openInventory(inv);
    }

    // ---------- rendering ----------
    private List<Auction> collect() {
        UUID id = viewer.getUniqueId();
        Comparator<Auction> cmp = switch (sort) {
            case 0 -> Comparator.comparingLong((Auction a) -> a.created).reversed();
            case 1 -> Comparator.comparingLong((Auction a) -> a.created);
            case 2 -> Comparator.comparingDouble((Auction a) -> a.price);
            default -> Comparator.comparingDouble((Auction a) -> a.price).reversed();
        };
        return plugin.manager().all().stream().filter(a -> switch (type) {
            case ALL -> a.status == Auction.Status.ACTIVE;
            case SEARCH -> a.status == Auction.Status.ACTIVE && matches(a);
            case SELLING -> a.status == Auction.Status.ACTIVE && id.equals(a.seller);
            case SOLD -> a.status == Auction.Status.SOLD && id.equals(a.seller);
            case EXPIRED -> (a.status == Auction.Status.EXPIRED || a.status == Auction.Status.CANCELLED) && id.equals(a.seller);
        }).sorted(cmp).collect(Collectors.toList());
    }

    private boolean matches(Auction a) {
        if (filter == null || filter.isBlank()) return true;
        StringBuilder h = new StringBuilder(a.item.getType().name().toLowerCase().replace('_', ' ')).append(' ');
        h.append(a.sellerName.toLowerCase()).append(' ');
        ItemMeta m = a.item.getItemMeta();
        if (m != null) {
            if (m.hasDisplayName()) h.append(ChatColor.stripColor(m.getDisplayName()).toLowerCase()).append(' ');
            if (m.hasLore()) for (String l : m.getLore()) h.append(ChatColor.stripColor(l).toLowerCase()).append(' ');
            for (Enchantment e : m.getEnchants().keySet()) h.append(e.getKey().getKey().replace('_', ' ')).append(' ');
            if (m instanceof EnchantmentStorageMeta esm)
                for (Enchantment e : esm.getStoredEnchants().keySet()) h.append(e.getKey().getKey().replace('_', ' ')).append(' ');
        }
        String hay = h.toString();
        for (String word : filter.toLowerCase().split("\\s+")) if (!hay.contains(word)) return false;
        return true;
    }

    private void render() {
        inv.clear();
        Arrays.fill(slots, null);
        List<Auction> list = collect();
        maxPage = Math.max(0, (list.size() - 1) / 45);
        page = Math.max(0, Math.min(page, maxPage));
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            slots[i] = list.get(idx);
            inv.setItem(i, display(list.get(idx)));
        }

        inv.setItem(45, button(Material.CHEST, "&a", "YOUR AUCTIONS",
                new String[]{"&fClick here to see ur &aItems", "&fthat you are &aselling"}, "View"));

        // sort
        List<String> sl = new ArrayList<>(Arrays.asList("&7Description", ""));
        for (int i = 0; i < SORTS.length; i++) sl.add((i == sort ? "&a" : "&f") + SORTS[i]);
        sl.add("");
        sl.add("&e" + ARROW + " &e&l&nCLICK &eto Sort");
        inv.setItem(46, named(Material.CAULDRON, "&a&lSORT", sl));

        if (type != Type.ALL)
            inv.setItem(47, button(Material.ARROW, "&e", "AUCTIONHOUSE",
                    new String[]{"&fClick here to go back", "&fto the Auctionhouse"}, "Navigate"));
        else if (back)
            inv.setItem(47, button(Material.BARRIER, "&c", "EXIT", new String[]{"&fClick here to close", "&fthe Auctionhouse"}, "Exit"));

        inv.setItem(48, button(Material.RED_DYE, "&c", "PREVIOUS PAGE",
                new String[]{"&fClick here to go", "&fto the previous page"}, "Navigate"));
        inv.setItem(49, button(Material.CRAFTING_TABLE, "&b", "AUCTIONHOUSE",
                new String[]{"&fClick here to go", "&frefresh the Auctionhouse", "&7Page &f" + (page + 1) + "&7/&f" + (maxPage + 1), "", "&7Made by &bitzblace"}, "Refresh"));
        inv.setItem(50, button(Material.LIME_DYE, "&a", "NEXT PAGE",
                new String[]{"&fClick here to go", "&fto the next page"}, "Navigate"));
        inv.setItem(53, named(Material.OAK_SIGN, "&a&lINFORMATION",
                Arrays.asList("&7Description", "", "&aSell Items: &f/ah sell (price)")));
    }

    private ItemStack display(Auction a) {
        ItemStack it = a.item.clone();
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
        lore.add("");
        String price = plugin.fmt(a.price);
        String left = a.expires == 0 ? "Never" : AuctionHousePlugin.duration(a.expires - System.currentTimeMillis());
        switch (type) {
            case SOLD -> {
                lore.add(AuctionHousePlugin.color("&7Sold to: &f" + a.buyerName));
                lore.add(AuctionHousePlugin.color("&7Price: &a" + price));
                lore.add(AuctionHousePlugin.color("&7Sold: &f" + AuctionHousePlugin.duration(System.currentTimeMillis() - a.soldAt) + " ago"));
            }
            case EXPIRED -> {
                lore.add(AuctionHousePlugin.color("&7Status: &c" + a.status.name().toLowerCase()));
                lore.add(AuctionHousePlugin.color("&e" + ARROW + " &e&l&nCLICK &eto return"));
            }
            case SELLING -> {
                lore.add(AuctionHousePlugin.color("&7Price: &a" + price));
                lore.add(AuctionHousePlugin.color("&7Expires in: &f" + left));
                lore.add(AuctionHousePlugin.color("&c" + ARROW + " &c&l&nCLICK &cto cancel"));
            }
            default -> {
                lore.add(AuctionHousePlugin.color("&7Price: &a" + price + (a.unlimited ? " &7(unlimited)" : "")));
                lore.add(AuctionHousePlugin.color("&7Seller: &f" + a.sellerName));
                lore.add(AuctionHousePlugin.color("&7Expires in: &f" + left));
                lore.add(AuctionHousePlugin.color("&e" + ARROW + " &e&l&nCLICK &eto buy"));
                if (viewer.hasPermission("auctionhouse.cancel.others"))
                    lore.add(AuctionHousePlugin.color("&7Shift+Right-click to cancel"));
            }
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack button(Material mat, String color, String name, String[] info, String action) {
        List<String> lore = new ArrayList<>(Arrays.asList("&7Description", "", color + "Information:"));
        lore.addAll(Arrays.asList(info));
        lore.add("");
        lore.add("&e" + ARROW + " &e&l&nCLICK &eto " + action);
        return named(mat, color + "&l" + name, lore);
    }

    private ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(AuctionHousePlugin.color(name));
        m.setLore(lore.stream().map(AuctionHousePlugin::color).collect(Collectors.toList()));
        it.setItemMeta(m);
        return it;
    }

    // ---------- clicks ----------
    public void click(InventoryClickEvent e) {
        int s = e.getRawSlot();
        if (s < 0 || s >= 54) return;
        if (s < 45) {
            Auction a = slots[s];
            if (a != null) auctionClick(a, e.getClick());
            return;
        }
        switch (s) {
            case 45 -> later(() -> new AhMenu(plugin, viewer, Type.SELLING, null, back).open());
            case 46 -> { sort = (sort + 1) % SORTS.length; page = 0; render(); }
            case 47 -> {
                if (type != Type.ALL) later(() -> new AhMenu(plugin, viewer, Type.ALL, null, back).open());
                else if (back) later(viewer::closeInventory);
            }
            case 48 -> { page--; render(); }
            case 49 -> render();
            case 50 -> { page++; render(); }
            default -> { }
        }
    }

    private void later(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }

    private void auctionClick(Auction a, ClickType ct) {
        AuctionManager m = plugin.manager();
        switch (type) {
            case ALL, SEARCH -> {
                if (ct == ClickType.SHIFT_RIGHT && viewer.hasPermission("auctionhouse.cancel.others")) {
                    m.cancel(a);
                    viewer.sendMessage(plugin.msg("cancelled-other"));
                } else {
                    String err = m.buy(viewer, a);
                    if (err != null) viewer.sendMessage(plugin.msg(err));
                    else viewer.sendMessage(plugin.msg("bought", "%item%", plugin.itemName(a.item), "%price%", plugin.fmt(a.price)));
                }
            }
            case SELLING -> {
                if (a.status == Auction.Status.ACTIVE) { m.cancel(a); viewer.sendMessage(plugin.msg("cancelled")); }
            }
            case EXPIRED -> {
                String err = m.returnOne(viewer, a);
                if (err != null) viewer.sendMessage(plugin.msg(err));
                else viewer.sendMessage(plugin.msg("returned", "%count%", "1"));
            }
            default -> { }
        }
        render();
    }
}

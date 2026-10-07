package net.auctionhouse;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class Auction {
    public enum Status { ACTIVE, SOLD, EXPIRED, CANCELLED }

    public int id;
    public UUID seller;          // null = server
    public String sellerName;
    public ItemStack item;
    public double price;
    public long created;
    public long expires;         // 0 = never
    public Status status = Status.ACTIVE;
    public boolean server;
    public boolean unlimited;
    public String buyerName;
    public long soldAt;
    public boolean notified;
}

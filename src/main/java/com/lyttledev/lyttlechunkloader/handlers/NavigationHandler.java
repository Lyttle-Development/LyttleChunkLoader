package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;

public class NavigationHandler implements Listener {
    private final LyttleChunkLoader plugin;

    public NavigationHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItem(event.getNewSlot());
        handleNavigationUpdate(player, heldItem, player.getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();
        handleNavigationUpdate(player, mainHand, offHand);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        setActionBar(false, player, null, null);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        setActionBar(false, player, null, null);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            handleNavigationUpdate(player, mainHand, offHand);
        });
    }

    private void handleNavigationUpdate(Player player, ItemStack mainHand, ItemStack offHand) {
        boolean mainActive = isNavigationItem(mainHand);
        boolean offActive = isNavigationItem(offHand);

        if (mainActive) {
            handleNavigation(player, mainHand, "main hand");
        } else if (offActive) {
            handleNavigation(player, offHand, "off hand");
        } else {
            setActionBar(false, player, null, null);
        }
    }

    private boolean isNavigationItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        // Check if the type is inside the config
        String message = (String) plugin.config.general.get(type.name());
        return message != null && !message.isEmpty();
    }

    private void handleNavigation(Player player, ItemStack item, String hand) {
        setActionBar(true, player, item, hand);
    }

    private final HashMap<Player, BukkitTask> activeActionBars = new HashMap();

    public void setActionBar(boolean active, Player player, ItemStack item, String hand) {
        BukkitTask oldActionBarTask = (BukkitTask) activeActionBars.get(player);
        if (oldActionBarTask != null) {
            oldActionBarTask.cancel();
            activeActionBars.remove(player);
        }

        if (active && item != null && item.getType() != Material.AIR && hand != null) {
            double updateRateInSeconds = (double) plugin.config.general.get("update_rate");
            long updateRate = Math.round(updateRateInSeconds * 20); // Convert seconds to ticks (20 ticks = 1 second)
            BukkitTask newActionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Replacements replacements = new Replacements.Builder()
                        .add("hand", hand)
                        .add("item", item.getType().name())
                        .add("<PLAYER_FACING>", getFacing(player))
                        .add("<SERVER_TIME_24_H>", getServerTime24H(player))
                        .add("<STATISTIC_TOTAL_SERVER_DAYS>", getTotalServerDays(player))
                        .build();
                String configMessage = (String) plugin.config.general.get(item.getType().name());
                Component message = plugin.message.getMessageRaw(configMessage, replacements, player);

                player.sendActionBar(message);
            }, 0L, updateRate);
            activeActionBars.put(player, newActionBarTask);
        }
    }

    private String getFacing(Player player) {
        // Get yaw in degrees, normalize to [0, 360)
        float yaw = player.getLocation().getYaw();
        yaw = (yaw % 360 + 360) % 360;
        // The following directions use Minecraft's standard compass layout
        if (yaw >= 337.5 || yaw < 22.5)
            return "South";
        if (yaw < 67.5)
            return "South-West";
        if (yaw < 112.5)
            return "West";
        if (yaw < 157.5)
            return "North-West";
        if (yaw < 202.5)
            return "North";
        if (yaw < 247.5)
            return "North-East";
        if (yaw < 292.5)
            return "East";
        if (yaw < 337.5)
            return "South-East";
        return "Unknown";
    }

    private String getServerTime24H(Player player) {
        // Returns the local time of day for the player's current world in 24h format
        long ticks = player.getWorld().getTime(); // 0–23999, 0 = 6:00
        long hours = (ticks / 1000 + 6) % 24;
        long minutes = (ticks % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hours, minutes);
    }

    private String getTotalServerDays(Player player) {
        // Returns the total days since world creation for the player's current world
        long totalTicks = player.getWorld().getFullTime(); // Cumulative ticks since world creation
        long totalDays = totalTicks / 24000;
        return String.valueOf(totalDays);
    }
}
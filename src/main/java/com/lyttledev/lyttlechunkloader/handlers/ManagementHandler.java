package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.WorldBorderChunkHighlighter;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ManagementHandler implements Listener {
    private final LyttleChunkLoader plugin;

    public ManagementHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        // Register the event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        checkBlock(event);
    }

    private void checkBlock(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.LODESTONE) {
            player.sendMessage("You interacted with a Lodestone!");
            plugin.borderHighlighter.sendBorders(player, clickedBlock.getLocation(), 2, 50); // Highlight 3x3 chunk area for 5 seconds
        }
    }
}
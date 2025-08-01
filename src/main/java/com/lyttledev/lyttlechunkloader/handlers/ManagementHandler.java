package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.WorldBorderChunkHighlighter;
import org.bukkit.Chunk;
import org.bukkit.Location;
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

    private String getChunkPlayer(Player player) {
        return player.getWorld().getName() + ":" + player.getUniqueId();
    }

    private String getChunkKey(Player player) {
        return getChunkPlayer(player) + ":" + player.getLocation().getChunk().getX() + ":" + player.getLocation().getChunk().getZ();
    }

    private Chunk[] getPlayerWorldChunks(Player player) {
            // Find all chunks loaded by the player in the current world
            String[] chunks = (String[]) plugin.config.chunks.getAll("");

            for (String chunkKey : chunks) {
                plugin.console.log(chunkKey);
            }
            return player.getWorld().getLoadedChunks();
        }

    private void checkBlock(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        Location location = clickedBlock.getLocation();

        if (item != null && item.getType() == Material.LODESTONE && location != null) {
            player.sendMessage("Put a lightning rod on this block to load the chunk. (will cost in game currency)");

            // Highlight 3x3 chunk area for 5 seconds
            plugin.borderHighlighter.sendBorders(player, location, 2, 50);
        }

        if (item != null && item.getType() == Material.LIGHTNING_ROD && location != null) {
//            getPlayerWorldChunks(player);
            loadCHunk(player, location);
        }
    }

    private void loadCHunk(Player player, Location location) {
        String chunkKey = getChunkKey(player);
        String playerChunks = (String) plugin.config.chunks.get(chunkKey);
        if (playerChunks == null) {
            playerChunks = "";
        }
        if (!playerChunks.contains(chunkKey)) {
            playerChunks += chunkKey + ",";
            plugin.config.chunks.set(chunkKey, playerChunks);
            player.sendMessage("Chunk loaded: " + chunkKey);
        } else {
            player.sendMessage("This chunk is already loaded: " + chunkKey);
        }
    }
}
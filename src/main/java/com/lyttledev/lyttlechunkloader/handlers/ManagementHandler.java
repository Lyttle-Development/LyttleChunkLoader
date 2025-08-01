package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ManagementHandler implements Listener {
    private final LyttleChunkLoader plugin;

    public ManagementHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        checkBlock(event);
    }

    private String getChunkKey(Location location) {
        Chunk chunk = location.getChunk();
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private String getPlayerKey(Player player) {
        return player.getUniqueId().toString();
    }

    private List<String> getPlayerChunks(Player player) {
        String playerKey = getPlayerKey(player);
        List<String> chunkList = (List<String>) plugin.config.chunks.get(playerKey);
        if (chunkList == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chunkList);
    }

    private void savePlayerChunks(Player player, List<String> chunkList) {
        String playerKey = getPlayerKey(player);
        plugin.config.chunks.set(playerKey, chunkList);
    }

    private void checkBlock(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (clickedBlock == null) return;
        Location location = clickedBlock.getLocation();

        if (item != null && item.getType() == Material.LODESTONE && location != null) {
            player.sendMessage("Put a lightning rod on this block to load the chunk. (will cost in game currency)");
            plugin.borderHighlighter.sendBorders(player, location, 2, 50);
        }

        if (item != null && item.getType() == Material.LIGHTNING_ROD && location != null) {
            loadChunkForPlayer(player, location);
        }
    }

    private void loadChunkForPlayer(Player player, Location location) {
        String chunkKey = getChunkKey(location);
        List<String> chunkList = getPlayerChunks(player);

        if (!chunkList.contains(chunkKey)) {
            chunkList.add(chunkKey);
            savePlayerChunks(player, chunkList);
            player.sendMessage("Chunk loaded: " + chunkKey);
        } else {
            player.sendMessage("This chunk is already loaded: " + chunkKey);
        }
    }
}
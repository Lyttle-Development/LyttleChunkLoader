package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttleutils.types.Config;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ManagementHandler is responsible for player chunk claiming and border highlighting logic.
 * Uses the Config utility for robust YAML config access.
 */
public class ManagementHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final int CLAIM_RADIUS = 2; // Default claim radius

    // Reference to the chunk config
    private final Config chunkConfig;

    /**
     * Registers the event listener and sets up config reference.
     * @param plugin Main plugin instance.
     */
    public ManagementHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        checkBlock(event);
    }

    /**
     * Constructs a chunk key for storage (world:x:z).
     */
    private String getChunkKey(Location location) {
        Chunk chunk = location.getChunk();
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    /**
     * Constructs player key (UUID as string).
     */
    private String getPlayerKey(Player player) {
        return player.getUniqueId().toString();
    }

    /**
     * Gets all claimed chunk keys for the given player.
     */
    private List<String> getPlayerChunks(Player player) {
        String playerKey = getPlayerKey(player);
        // Defensive: always use getStringList to avoid type issues.
        List<String> chunkList = chunkConfig.getStringList(playerKey);
        return chunkList != null ? chunkList : new ArrayList<>();
    }

    /**
     * Gets all claimed chunks by all players as a set of chunk keys.
     */
    private Set<String> getAllClaimedChunks() {
        Set<String> claimed = new HashSet<>();
        String[] allPlayers = chunkConfig.getKeys(""); // Top-level keys = player UUIDs
        if (allPlayers != null) {
            for (String playerKey : allPlayers) {
                List<String> chunks = chunkConfig.getStringList(playerKey);
                if (chunks != null) claimed.addAll(chunks);
            }
        }
        return claimed;
    }

    /**
     * Save a player's claimed chunks list to config.
     */
    private void savePlayerChunks(Player player, List<String> chunkList) {
        String playerKey = getPlayerKey(player);
        chunkConfig.set(playerKey, chunkList);
    }

    /**
     * Handles player interact event logic for chunk claiming.
     */
    private void checkBlock(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (clickedBlock == null) return;
        Location location = clickedBlock.getLocation();

        if (item != null && item.getType() == Material.LODESTONE && location != null) {
            player.sendMessage("Put a lightning rod on this block to load the chunk. (will cost in game currency)");
            plugin.borderHighlighter.sendBorders(player, location, CLAIM_RADIUS, 50);
        }

        if (item != null && item.getType() == Material.LIGHTNING_ROD && location != null) {
            loadChunkForPlayer(player, location);
        }
    }

    /**
     * Loads the chunk at the given location for the player.
     * Shows border map, plays sound, and persists claim.
     */
    private void loadChunkForPlayer(Player player, Location center) {
        String chunkKey = getChunkKey(center);
        List<String> chunkList = getPlayerChunks(player);
        Set<String> allClaimed = getAllClaimedChunks();

        // Build border display string
        String borderMap = buildBorderMap(center, chunkList, allClaimed);

        // Only allow if not already claimed
        if (!chunkList.contains(chunkKey)) {
            chunkList.add(chunkKey);
            savePlayerChunks(player, chunkList);
            player.sendMessage("Chunk loaded: " + chunkKey);
            player.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f); // Satisfying level-up sound
        } else {
            player.sendMessage("This chunk is already loaded: " + chunkKey);
        }
        player.sendMessage(borderMap);
        plugin.borderHighlighter.sendBorders(player, center, CLAIM_RADIUS, 50);
    }

    /**
     * Builds a text border map for visualizing chunk claim status.
     * Legend:
     *  Y = Claimable by player (already owned)
     *  O = Unclaimed but in claim radius
     *  X = Claimed by others
     *  L = Lodestone/center chunk
     *  . = Outside visual range
     */
    private String buildBorderMap(Location center, List<String> playerChunks, Set<String> allClaimed) {
        StringBuilder sb = new StringBuilder();
        int size = CLAIM_RADIUS * 2 + 3; // Space for border and center
        Chunk centerChunk = center.getChunk();

        for (int dz = -CLAIM_RADIUS - 1; dz <= CLAIM_RADIUS + 1; dz++) {
            sb.append("...");
            for (int dx = -CLAIM_RADIUS - 1; dx <= CLAIM_RADIUS + 1; dx++) {
                int cx = centerChunk.getX() + dx;
                int cz = centerChunk.getZ() + dz;
                String key = center.getWorld().getName() + ":" + cx + ":" + cz;
                if (dx == 0 && dz == 0) {
                    sb.append("L ");
                } else if (Math.abs(dx) == CLAIM_RADIUS + 1 || Math.abs(dz) == CLAIM_RADIUS + 1) {
                    sb.append(". ");
                } else if (playerChunks.contains(key)) {
                    sb.append("Y ");
                } else if (allClaimed.contains(key)) {
                    sb.append("X ");
                } else if (Math.abs(dx) <= CLAIM_RADIUS && Math.abs(dz) <= CLAIM_RADIUS) {
                    sb.append("O ");
                } else {
                    sb.append(". ");
                }
            }
            sb.append("...\n");
        }
        return sb.toString();
    }
}
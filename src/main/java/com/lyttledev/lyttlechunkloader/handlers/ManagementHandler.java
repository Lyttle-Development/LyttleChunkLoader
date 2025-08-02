package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.ChunkRangeUtil;
import com.lyttledev.lyttlechunkloader.utils.DoubleChunkLoaderEnforcer;
import com.lyttledev.lyttleutils.types.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.*;

/**
 * ManagementHandler for chunk claim/load logic.
 * - Claims (single chunk) when lightning rod is placed on lodestone (rod must be directly above lodestone)
 * - Removes claim if lodestone or rod is broken/removed (single chunk)
 * - Delegates grid visualization to ChunkRangeUtil.
 * - Manages actual chunk loading/unloading for redstone, commandblocks, etc.
 * - Uses plugin tickets to force-load chunks for redstone/commandblocks.
 */
public class ManagementHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final Config chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final DoubleChunkLoaderEnforcer doubleLoaderEnforcer;

    // Track loaded chunks by world and coordinates for unloading correctly
    private final Set<String> loadedChunkKeys = Collections.synchronizedSet(new HashSet<>());

    public ManagementHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = new ChunkRangeUtil(1, 4);
        this.doubleLoaderEnforcer = new DoubleChunkLoaderEnforcer(plugin, chunkRangeUtil, 1);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load all claimed chunks when plugin loads
        loadAllClaimedChunks();
    }

    private String getChunkKey(Location location) {
        return chunkRangeUtil.getChunkKey(location);
    }

    private String getChunkKey(World world, int cx, int cz) {
        return chunkRangeUtil.getChunkKey(world, cx, cz);
    }

    private String getPlayerKey(Player player) {
        return player.getUniqueId().toString();
    }

    private List<String> getPlayerChunks(Player player) {
        String playerKey = getPlayerKey(player);
        List<String> chunkList = chunkConfig.getStringList(playerKey);
        return chunkList != null ? chunkList : new ArrayList<>();
    }

    private Map<String, Set<String>> getAllClaimsByPlayer() {
        Map<String, Set<String>> map = new HashMap<>();
        String[] allPlayers = chunkConfig.getKeys("");
        if (allPlayers != null) {
            for (String playerKey : allPlayers) {
                List<String> chunks = chunkConfig.getStringList(playerKey);
                if (chunks != null && !chunks.isEmpty()) {
                    map.put(playerKey, new HashSet<>(chunks));
                }
            }
        }
        return map;
    }

    private void savePlayerChunks(Player player, List<String> chunkList) {
        String playerKey = getPlayerKey(player);
        chunkConfig.set(playerKey, chunkList);
    }

    /**
     * Loads all claimed chunks for all players (on plugin/server start).
     * Uses plugin tickets to force-load for redstone/commandblock activity.
     */
    public void loadAllClaimedChunks() {
        Map<String, Set<String>> allClaims = getAllClaimsByPlayer();
        for (Set<String> chunkKeys : allClaims.values()) {
            for (String chunkKey : chunkKeys) {
                loadChunkAndSurrounding(chunkKey);
            }
        }
    }

    /**
     * Unloads all chunks previously loaded by this plugin (on plugin/server disable).
     * Removes plugin tickets.
     */
    public void unloadAllClaimedChunks() {
        synchronized (loadedChunkKeys) {
            for (String chunkKey : loadedChunkKeys) {
                String[] parts = chunkKey.split(":");
                if (parts.length < 3) continue;
                String worldName = parts[0];
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                // Remove plugin chunk ticket
                world.removePluginChunkTicket(cx, cz, plugin);
            }
            loadedChunkKeys.clear();
        }
    }

    /**
     * Loads the chunk for the specified key and all surrounding area chunks using plugin tickets.
     */
    private void loadChunkAndSurrounding(String chunkKey) {
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) return;
        String worldName = parts[0];
        int cx = Integer.parseInt(parts[1]);
        int cz = Integer.parseInt(parts[2]);
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Set<String> areaKeys = chunkRangeUtil.getAreaChunkKeys(world, cx, cz);
        for (String areaKey : areaKeys) {
            String[] aParts = areaKey.split(":");
            if (aParts.length < 3) continue;
            int ax = Integer.parseInt(aParts[1]);
            int az = Integer.parseInt(aParts[2]);
            // Use plugin chunk tickets to force-load for redstone/commandblocks
            world.addPluginChunkTicket(ax, az, plugin);
            loadedChunkKeys.add(areaKey);
        }
    }

    /**
     * Unloads the chunk for the specified key and all surrounding area chunks.
     * Removes plugin tickets.
     */
    private void unloadChunkAndSurrounding(String chunkKey) {
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) return;
        String worldName = parts[0];
        int cx = Integer.parseInt(parts[1]);
        int cz = Integer.parseInt(parts[2]);
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Set<String> areaKeys = chunkRangeUtil.getAreaChunkKeys(world, cx, cz);
        for (String areaKey : areaKeys) {
            String[] aParts = areaKey.split(":");
            if (aParts.length < 3) continue;
            int ax = Integer.parseInt(aParts[1]);
            int az = Integer.parseInt(aParts[2]);
            // Remove plugin chunk ticket if tracked
            if (loadedChunkKeys.remove(areaKey)) {
                world.removePluginChunkTicket(ax, az, plugin);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        switch (block.getType()) {
            case Material.LODESTONE:
                Block above = block.getLocation().clone().add(0, 1, 0).getBlock();
                if (above.getType() == Material.LIGHTNING_ROD) {
                    doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnCreate(block.getLocation(), player);
                    claimChunkAt(block.getLocation(), player);
                }
                break;
            case Material.LIGHTNING_ROD:
                Block below = block.getLocation().clone().add(0, -1, 0).getBlock();
                if (below.getType() == Material.LODESTONE) {
                    doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnCreate(below.getLocation(), player);
                    claimChunkAt(below.getLocation(), player);
                }
                break;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        Player player = event.getPlayer();

        switch (block.getType()) {
            case LODESTONE:
                Block above = location.clone().add(0, 1, 0).getBlock();
                if (above.getType() == Material.LIGHTNING_ROD) {
                    doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnRemove(location, player);
                    removeClaimAt(location, player); // <-- Now always called
                }
                break;
            case LIGHTNING_ROD:
                Block below = location.clone().add(0, -1, 0).getBlock();
                if (below.getType() == Material.LODESTONE) {
                    doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnRemove(below.getLocation(), player);
                    removeClaimAt(below.getLocation(), player); // <-- Now always called
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Location lodestoneLocation = block.getLocation();

        switch (block.getType()) {
            case LODESTONE:
                Block above = lodestoneLocation.clone().add(0, 1, 0).getBlock();
                if (above.getType() == Material.LIGHTNING_ROD) {
                    sendVisualization(lodestoneLocation, player);
                }
                break;
            case LIGHTNING_ROD:
                Block below = lodestoneLocation.clone().add(0, -1, 0).getBlock();
                if (below.getType() == Material.LODESTONE) {
                    sendVisualization(below.getLocation(), player);
                }
                break;
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            loadAllClaimedChunks();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            unloadAllClaimedChunks();
        }
    }

    private void sendVisualization(Location lodestoneLocation, Player player) {
        String key = getChunkKey(lodestoneLocation);
        List<String> chunkList = getPlayerChunks(player);

        if (chunkList.contains(key)) {
            chunkRangeUtil.sendChunkGridVisualizer(
                lodestoneLocation,
                player,
                getPlayerKey(player),
                getAllClaimsByPlayer(),
                key
            );
            plugin.borderHighlighter.sendBorders(player, lodestoneLocation, 2, 100);
        } else {
            player.sendMessage(Component.text("This chunk is not claimed by you.", NamedTextColor.RED));
        }
    }

    private void claimChunkAt(Location lodestoneLocation, Player player) {
        Block lodestone = lodestoneLocation.getBlock();
        Block rod = lodestoneLocation.clone().add(0, 1, 0).getBlock();

        if (lodestone.getType() != Material.LODESTONE || rod.getType() != Material.LIGHTNING_ROD) {
            player.sendMessage(Component.text("You can only claim if a lightning rod is placed on top of a lodestone.", NamedTextColor.GRAY));
            return;
        }

        Chunk centerChunk = lodestoneLocation.getChunk();
        List<String> chunkList = getPlayerChunks(player);
        boolean alreadyClaimed = false;

        // Check if any chunk in the area is already claimed by this player
        Set<String> areaKeys = chunkRangeUtil.getAreaChunkKeys(
                lodestoneLocation.getWorld(),
                centerChunk.getX(),
                centerChunk.getZ()
        );
        for (String key : areaKeys) {
            if (chunkList.contains(key)) {
                alreadyClaimed = true;
                break;
            }
        }

        boolean claimedNow = false;
        String key = getChunkKey(lodestoneLocation);
        if (!alreadyClaimed) {
            // Only add the center chunk to player's claims
            if (!chunkList.contains(key)) {
                chunkList.add(key);
                claimedNow = true;
                savePlayerChunks(player, chunkList);
            }
        }
        // Always load the chunk and its area when claimed
        loadChunkAndSurrounding(key);

        sendVisualization(lodestoneLocation, player);

        // Satisfying level-up sound
        player.playSound(lodestoneLocation, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    // Always called on loader removal now, ensures chunk unload and config update
    private void removeClaimAt(Location lodestoneLocation, Player player) {
        String key = getChunkKey(lodestoneLocation);
        unloadChunkAndSurrounding(key);
    }
}
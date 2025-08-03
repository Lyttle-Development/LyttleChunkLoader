package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.ChunkRangeUtil;
import com.lyttledev.lyttleutils.types.Config;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.*;

/**
 * Handles chunk loading and unloading logic for all claims.
 * - Loads/unloads chunks using plugin tickets.
 * - Tracks loaded chunk keys.
 */
public class PaymentHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final Config chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final Set<String> loadedChunkKeys = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> loadedPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final int PAYMENT_CHECK_INTERVAL = 30 * 60; // 30 minutes in seconds


    public PaymentHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = new ChunkRangeUtil(1, 4);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPayments, 20L, 20L); // Check payments every second
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            unloadAllClaimedChunks();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playetUUID = event.getPlayer().getUniqueId();
        List<String> playerChunks = chunkConfig.getStringList(playetUUID.toString());
        if (playerChunks == null || playerChunks.isEmpty()) return;
        loadedPlayers.add(playetUUID);


    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        loadedPlayers.remove(playerUUID);
        // Unload all chunks for this player
        List<String> playerChunks = chunkConfig.getStringList(playerUUID.toString());
        if (playerChunks != null) {
            for (String chunkKey : playerChunks) {
                unloadChunkAndSurrounding(chunkKey);
            }
        }
    }

    private void checkPayments() {

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
    public void loadChunkAndSurrounding(String chunkKey) {
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
    public void unloadChunkAndSurrounding(String chunkKey) {
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
}
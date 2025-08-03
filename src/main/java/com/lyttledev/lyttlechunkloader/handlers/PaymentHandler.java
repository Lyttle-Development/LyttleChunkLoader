package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.ChunkRangeUtil;
import com.lyttledev.lyttleutils.types.Config;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

import java.util.*;

/**
 * Handles chunk loading and unloading logic for all claims.
 * - Loads/unloads chunks using plugin tickets.
 * - Tracks loaded chunk keys.
 * - Handles payment for chunk loader duty using Vault.
 */
public class PaymentHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final Config chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final Set<String> loadedChunkKeys = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> loadedPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final int PAYMENT_CHECK_INTERVAL = 30 * 60; // 30 minutes in seconds
    private static final double DUTY_PER_CHUNK = 30.0;
    private final Economy economy;

    public PaymentHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = new ChunkRangeUtil(1, 4);
        this.economy = plugin.economyImplementer; // Ensure plugin has a getEconomy() returning net.milkbowl.vault.economy.Economy
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Schedule payment checks every PAYMENT_CHECK_INTERVAL seconds
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPayments, 20L, PAYMENT_CHECK_INTERVAL * 20L);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            unloadAllClaimedChunks();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        List<String> playerChunks = chunkConfig.getStringList(playerUUID.toString());
        if (playerChunks == null || playerChunks.isEmpty()) return;
        loadedPlayers.add(playerUUID);
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

    /**
     * Checks payment for each player with claimed chunks.
     * If a player cannot pay, their chunks are deleted.
     */
    private void checkPayments() {
        Map<String, List<String>> allClaims = getAllPlayerClaims();
        for (Map.Entry<String, List<String>> entry : allClaims.entrySet()) {
            String playerKey = entry.getKey();
            UUID playerUUID;
            try {
                playerUUID = UUID.fromString(playerKey);
            } catch (IllegalArgumentException e) {
                continue; // Skip invalid UUID
            }
            List<String> chunks = entry.getValue();
            int chunkCount = chunks.size();
            double totalDuty = DUTY_PER_CHUNK * chunkCount;
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);

            if (economy.has(offlinePlayer, totalDuty)) {
                if (loadedPlayers.contains(playerUUID)) {
                    economy.withdrawPlayer(offlinePlayer, totalDuty);
                    for (String chunkKey : chunks) {
                        if (!loadedChunkKeys.contains(chunkKey)) {
                            loadChunkAndSurrounding(chunkKey);
                        }
                    }
                }
            } else {
                // Not enough funds: remove all claims and unload chunks
                for (String chunkKey : chunks) {
                    unloadChunkAndSurrounding(chunkKey);
                }
                chunkConfig.set(playerKey, new ArrayList<>());
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();
                    if (player != null) {
                        player.sendMessage("§cYour chunk loader claims have been removed due to insufficient funds.");
                    }
                }
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

    /**
     * Returns a map of player UUID (String) to a list of their directly claimed chunk keys.
     * Only the chunks they actually own, not all loaded chunks in the config.
     */
    private Map<String, List<String>> getAllPlayerClaims() {
        Map<String, List<String>> claims = new HashMap<>();
        String[] allPlayers = chunkConfig.getKeys("");
        if (allPlayers != null) {
            for (String playerKey : allPlayers) {
                List<String> chunkList = chunkConfig.getStringList(playerKey);
                if (chunkList != null && !chunkList.isEmpty()) {
                    claims.put(playerKey, new ArrayList<>(chunkList));
                }
            }
        }
        return claims;
    }
}
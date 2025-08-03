package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.ChunkRangeUtil;
import com.lyttledev.lyttlechunkloader.utils.DoubleChunkLoaderEnforcer;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PaymentHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final Config chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final Set<String> loadedChunkKeys = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> loadedPlayers = Collections.synchronizedSet(new HashSet<>());
    private static final int PAYMENT_CHECK_INTERVAL = 30 * 60; // 30 minutes in seconds
    private static final double DUTY_PER_CHUNK = 30.0;
    private final Economy economy;
    private final Map<UUID, BukkitTask> playerPaymentTasks = Collections.synchronizedMap(new HashMap<>());
    private final DoubleChunkLoaderEnforcer doubleLoaderEnforcer;

    public PaymentHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = new ChunkRangeUtil(1, 4);
        this.economy = plugin.economyImplementer;
        this.doubleLoaderEnforcer = new DoubleChunkLoaderEnforcer(plugin, chunkRangeUtil, 1); // radius=1, adjust if needed
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            unloadAllClaimedChunks();
            cancelAllPaymentTasks();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        List<String> playerChunks = chunkConfig.getStringList(playerUUID.toString());
        if (playerChunks == null || playerChunks.isEmpty()) return;
        loadedPlayers.add(playerUUID);
        // Load all claimed chunks for this player
        for (String chunkKey : playerChunks) {
            loadChunkAndSurrounding(chunkKey);
        }
        player.sendMessage("§aYour claimed chunks have been loaded. Payment will be checked every " + (PAYMENT_CHECK_INTERVAL / 60) + " minutes.");
        // Start per-player payment timer
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> checkPaymentsForPlayer(playerUUID), PAYMENT_CHECK_INTERVAL * 20L, PAYMENT_CHECK_INTERVAL * 20L);
        // Cancel any existing task for this player
        cancelPaymentTask(playerUUID);
        // Store the task for this player
        playerPaymentTasks.put(playerUUID, task);
        // Charge immediately for the first period after join
        checkPaymentsForPlayer(playerUUID);
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
        // Cancel payment task
        cancelPaymentTask(playerUUID);
    }

    /**
     * Checks payment for a single player with claimed chunks.
     * If a player cannot pay, their chunks are deleted and unloaded, payment task is canceled,
     * and all corresponding double chunk loaders are dropped in the world.
     */
    private void checkPaymentsForPlayer(UUID playerUUID) {
        List<String> chunks = chunkConfig.getStringList(playerUUID.toString());
        if (chunks == null || chunks.isEmpty()) return;
        int chunkCount = chunks.size();
        double totalDuty = DUTY_PER_CHUNK * chunkCount;
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);

        if (economy.has(offlinePlayer, totalDuty)) {
            if (loadedPlayers.contains(playerUUID)) {
                economy.withdrawPlayer(offlinePlayer, totalDuty);
                Player player = offlinePlayer.getPlayer();
                if (player != null && player.isOnline()) {
                    player.sendMessage("§aChunk loader fee of §e" + totalDuty + "§a has been paid for " + chunkCount + " chunks.");
                }
            }
        } else {
            // Not enough funds: remove all claims, unload chunks, and remove & drop chunk loaders
            for (String chunkKey : chunks) {
                unloadChunkAndSurrounding(chunkKey);
                // Remove and drop the physical double chunk loader structure for this chunk if present
                dropDoubleChunkLoaderAt(chunkKey);
            }
            chunkConfig.set(playerUUID.toString(), new ArrayList<>());
            Player player = offlinePlayer.getPlayer();
            if (player != null && player.isOnline()) {
                player.sendMessage("§cYour chunk loader claims have been removed due to insufficient funds. The chunk loaders were dropped at their locations.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f); // Danger sound
            }
            cancelPaymentTask(playerUUID);
        }
    }

    /**
     * Drops the double chunk loader (lodestone + lightning rod) at the physical location for the given chunkKey, if found.
     * Uses DoubleChunkLoaderEnforcer logic to ensure correct structure and drops.
     */
    private void dropDoubleChunkLoaderAt(String chunkKey) {
        String[] parts = chunkKey.split(":");
        if (parts.length < 3) return;
        String worldName = parts[0];
        int cx = Integer.parseInt(parts[1]);
        int cz = Integer.parseInt(parts[2]);
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Scan all y and chunk-local x/z for a double loader in this chunk (as in DoubleChunkLoaderEnforcer)
        for (int y = world.getMinHeight(); y < world.getMaxHeight() - 1; y++) {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int bx = (cx << 4) + lx;
                    int bz = (cz << 4) + lz;
                    org.bukkit.Location base = new org.bukkit.Location(world, bx, y, bz);
                    if (isPhysicalDoubleLoader(base)) {
                        breakDoubleChunkLoader(base, true);
                        return; // Only one per chunk, stop after found
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given Location is a Lodestone with Lightning Rod directly above.
     */
    private boolean isPhysicalDoubleLoader(org.bukkit.Location lodestoneLoc) {
        org.bukkit.block.Block base = lodestoneLoc.getBlock();
        org.bukkit.block.Block above = lodestoneLoc.clone().add(0, 1, 0).getBlock();
        return base.getType() == org.bukkit.Material.LODESTONE && above.getType() == org.bukkit.Material.LIGHTNING_ROD;
    }

    /**
     * Breaks the DOUBLE chunk loader at lodestoneLoc. Optionally drops items.
     */
    private void breakDoubleChunkLoader(org.bukkit.Location lodestoneLoc, boolean dropItems) {
        org.bukkit.block.Block base = lodestoneLoc.getBlock();
        org.bukkit.block.Block above = lodestoneLoc.clone().add(0, 1, 0).getBlock();

        if (base.getType() == org.bukkit.Material.LODESTONE) {
            if (dropItems) base.getWorld().dropItemNaturally(base.getLocation(), new org.bukkit.inventory.ItemStack(org.bukkit.Material.LODESTONE));
            base.setType(org.bukkit.Material.AIR);
        }
        if (above.getType() == org.bukkit.Material.LIGHTNING_ROD) {
            if (dropItems) above.getWorld().dropItemNaturally(above.getLocation(), new org.bukkit.inventory.ItemStack(org.bukkit.Material.LIGHTNING_ROD));
            above.setType(org.bukkit.Material.AIR);
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
            if (loadedChunkKeys.remove(areaKey)) {
                world.removePluginChunkTicket(ax, az, plugin);
            }
        }
    }

    /**
     * Cancels and removes a player's payment task.
     */
    private void cancelPaymentTask(UUID playerUUID) {
        BukkitTask task = playerPaymentTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Cancels all running payment tasks (e.g., on plugin disable).
     */
    private void cancelAllPaymentTasks() {
        for (BukkitTask task : playerPaymentTasks.values()) {
            task.cancel();
        }
        playerPaymentTasks.clear();
    }
}
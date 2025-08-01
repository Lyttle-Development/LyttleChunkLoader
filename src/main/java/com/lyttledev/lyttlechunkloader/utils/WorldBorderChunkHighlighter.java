package com.lyttledev.lyttlechunkloader.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderCenter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderSize;
import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends fake world border packets to highlight a chunk grid area around a given chunk.
 * Can be reused for different players/locations/radii.
 */
public class WorldBorderChunkHighlighter {

    private final LyttleChunkLoader plugin;
    // Track active reset tasks per player to allow cancellation
    private final Map<UUID, BukkitRunnable> activeResets = new ConcurrentHashMap<>();

    public WorldBorderChunkHighlighter(LyttleChunkLoader plugin) {
        this.plugin = plugin;
    }

    /**
     * Highlights a square chunk area centered on the provided location.
     * If called again for the same player, cancels the previous reset task.
     *
     * @param player        Player to send the border to.
     * @param center        Center location (anywhere in the chunk).
     * @param chunkRadius   Chunk radius (1 = just 1 chunk, 2 = 3x3, 3 = 5x5, ...)
     * @param durationTicks Duration in ticks before resetting to default world border.
     */
    public void sendBorders(Player player, Location center, int chunkRadius, int durationTicks) {
        // Cancel any previous scheduled reset for this player
        BukkitRunnable previous = activeResets.remove(player.getUniqueId());
        if (previous != null) previous.cancel();

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        Chunk centerChunk = center.getChunk();
        World world = center.getWorld();

        int minChunkX = centerChunk.getX() - (chunkRadius - 1);
        int maxChunkX = centerChunk.getX() + (chunkRadius - 1);
        int minChunkZ = centerChunk.getZ() - (chunkRadius - 1);
        int maxChunkZ = centerChunk.getZ() + (chunkRadius - 1);

        // The border will cover a square area of (chunkRadius * 2 - 1) chunks on each side.
        double borderCenterX = ((minChunkX + maxChunkX) * 16) / 2.0 + 8;
        double borderCenterZ = ((minChunkZ + maxChunkZ) * 16) / 2.0 + 8;
        double borderDiameter = (maxChunkX - minChunkX + 1) * 16;

        // Send center and size packets
        user.sendPacket(new WrapperPlayServerWorldBorderCenter(borderCenterX, borderCenterZ));
        user.sendPacket(new WrapperPlayServerWorldBorderSize(borderDiameter));

        // Schedule reset after specified duration
        BukkitRunnable resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                Location spawn = world.getSpawnLocation();
                user.sendPacket(new WrapperPlayServerWorldBorderCenter(spawn.getX(), spawn.getZ()));
                user.sendPacket(new WrapperPlayServerWorldBorderSize(29999984));
                activeResets.remove(player.getUniqueId());
            }
        };
        activeResets.put(player.getUniqueId(), resetTask);
        resetTask.runTaskLater(plugin, durationTicks);
    }
}
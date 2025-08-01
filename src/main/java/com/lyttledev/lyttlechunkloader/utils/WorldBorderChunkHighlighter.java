package com.lyttledev.lyttlechunkloader.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderCenter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderSize;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldBorderChunkHighlighter {

    private final int chunkRadius;
    private final double worldBorderSize;

    /**
     * @param chunkRadius the radius in chunks around the center chunk (e.g., 1 = 3x3, 2 = 5x5, etc.)
     * @param worldBorderSize world border diameter in blocks per chunk (use 16 for full chunk)
     */
    public WorldBorderChunkHighlighter(int chunkRadius, double worldBorderSize) {
        this.chunkRadius = chunkRadius;
        this.worldBorderSize = worldBorderSize;
    }

    /**
     * Send fake world borders to a player around all chunks in a square radius from the center chunk.
     * @param player The player to send packets to.
     * @param centerLocation The central location (e.g., the loadstone block).
     */
    public void sendBorders(Player player, Location centerLocation) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

        Chunk centerChunk = centerLocation.getChunk();
        int centerChunkX = centerChunk.getX();
        int centerChunkZ = centerChunk.getZ();

        int minX = centerChunkX - chunkRadius;
        int maxX = centerChunkX + chunkRadius;
        int minZ = centerChunkZ - chunkRadius;
        int maxZ = centerChunkZ + chunkRadius;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                double borderCenterX = (cx * 16) + 8;
                double borderCenterZ = (cz * 16) + 8;

                // Send CENTER and SIZE packets so the border is visible for the chunk
                WrapperPlayServerWorldBorderCenter centerPacket = new WrapperPlayServerWorldBorderCenter(borderCenterX, borderCenterZ);
                WrapperPlayServerWorldBorderSize sizePacket = new WrapperPlayServerWorldBorderSize(worldBorderSize);

                user.sendPacket(centerPacket);
                user.sendPacket(sizePacket);
            }
        }
    }
}
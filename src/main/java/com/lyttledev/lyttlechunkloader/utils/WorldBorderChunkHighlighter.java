package com.lyttledev.lyttlechunkloader.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderCenter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWorldBorderSize;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldBorderChunkHighlighter {

    private final int chunkRadius;
    private final double worldBorderSize;

    public WorldBorderChunkHighlighter(int chunkRadius, double worldBorderSize) {
        this.chunkRadius = chunkRadius;
        this.worldBorderSize = worldBorderSize;
    }

    public void sendBorders(Player player, Location centerLocation) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);

        Chunk centerChunk = centerLocation.getChunk();
        int centerChunkX = centerChunk.getX();
        int centerChunkZ = centerChunk.getZ();

        // Center the world border exactly on the player's block location (not chunk center)
        double borderCenterX = centerLocation.getX() + 0.5;
        double borderCenterZ = centerLocation.getZ() + 0.5;

        // The border should be sized to cover a (2*chunkRadius+1) x (2*chunkRadius+1) chunk area
        double size = worldBorderSize * (chunkRadius * 2 + 1);

        // Send packets for visual border
        user.sendPacket(new WrapperPlayServerWorldBorderCenter(borderCenterX, borderCenterZ));
        user.sendPacket(new WrapperPlayServerWorldBorderSize(size));

        // Reset after 5 seconds to default
        new BukkitRunnable() {
            @Override
            public void run() {
                // Default world border: center on world's spawn, size 29999984 (vanilla default)
                Location spawn = player.getWorld().getSpawnLocation();
                user.sendPacket(new WrapperPlayServerWorldBorderCenter(spawn.getX(), spawn.getZ()));
                user.sendPacket(new WrapperPlayServerWorldBorderSize(29999984));
            }
        }.runTaskLater(Bukkit.getPluginManager().getPlugin("LyttleChunkLoader"), 100L); // 100 ticks = 5 seconds
    }
}
package com.lyttledev.lyttlechunkloader.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Utility for chunk range calculations and visualization.
 * - Calculates center and configurable surrounding chunk keys.
 * - Determines ownership and overlap of chunk areas.
 * - Visualizes chunk ownership grid.
 */
public class ChunkRangeUtil {
    private final int areaRadius;      // Surrounding claim area (e.g. 1 for 3x3)
    private final int visualRadius;    // Visual grid radius (e.g. 4 for 9x9)

    public enum AreaRole {
        UNCLAIMED,
        JUST_CLAIMED_CENTER,
        JUST_CLAIMED_AREA,
        YOUR_CENTER,
        YOUR_AREA,
        OTHER_CENTER,
        OTHER_AREA
    }

    public ChunkRangeUtil(int areaRadius, int visualRadius) {
        this.areaRadius = areaRadius;
        this.visualRadius = visualRadius;
    }

    /**
     * Returns all chunk keys in a square area centered at (cx,cz) of the given world.
     */
    public Set<String> getAreaChunkKeys(World world, int cx, int cz) {
        Set<String> area = new HashSet<>();
        for (int dz = -areaRadius; dz <= areaRadius; dz++) {
            for (int dx = -areaRadius; dx <= areaRadius; dx++) {
                area.add(world.getName() + ":" + (cx + dx) + ":" + (cz + dz));
            }
        }
        return area;
    }

    /**
     * Returns the center chunk key for a location.
     */
    public String getChunkKey(Location location) {
        Chunk chunk = location.getChunk();
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    /**
     * Returns the center chunk key for a world, cx, cz.
     */
    public String getChunkKey(World world, int cx, int cz) {
        return world.getName() + ":" + cx + ":" + cz;
    }

    /**
     * Builds a map of center chunk key to its area chunk keys, for all claimed centers.
     */
    public Map<String, Set<String>> buildCentersToAreas(Map<String, Set<String>> allClaims) {
        Map<String, Set<String>> centerToArea = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : allClaims.entrySet()) {
            for (String centerKey : entry.getValue()) {
                String[] parts = centerKey.split(":");
                if (parts.length < 3) continue;
                String worldName = parts[0];
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                World world = null;
                for (World w : org.bukkit.Bukkit.getWorlds()) {
                    if (w.getName().equals(worldName)) {
                        world = w;
                        break;
                    }
                }
                if (world == null) continue;
                centerToArea.put(centerKey, getAreaChunkKeys(world, cx, cz));
            }
        }
        return centerToArea;
    }

    /**
     * Checks if a chunk key is inside the area for any of the given centers.
     */
    public boolean isInAnyArea(String chunkKey, Collection<Set<String>> allAreas) {
        for (Set<String> area : allAreas) {
            if (area.contains(chunkKey)) return true;
        }
        return false;
    }

    /**
     * Determines the AreaRole for a chunk key, given claims and options.
     */
    public AreaRole getAreaRole(
            String chunkKey,
            String selfKey,
            Map<String, Set<String>> centerToAreas,
            Map<String, String> centerToOwner,
            String justClaimedCenter
    ) {
        // 1. Just claimed center/area
        if (justClaimedCenter != null) {
            if (chunkKey.equals(justClaimedCenter)) return AreaRole.JUST_CLAIMED_CENTER;
            Set<String> justClaimedArea = centerToAreas.get(justClaimedCenter);
            if (justClaimedArea != null && justClaimedArea.contains(chunkKey))
                return AreaRole.JUST_CLAIMED_AREA;
        }
        // 2. Your claim center/area
        for (Map.Entry<String, String> e : centerToOwner.entrySet()) {
            String centerKey = e.getKey();
            String owner = e.getValue();
            if (owner.equals(selfKey)) {
                if (chunkKey.equals(centerKey)) return AreaRole.YOUR_CENTER;
                Set<String> area = centerToAreas.get(centerKey);
                if (area != null && area.contains(chunkKey)) return AreaRole.YOUR_AREA;
            }
        }
        // 3. Other's claim center/area
        for (Map.Entry<String, String> e : centerToOwner.entrySet()) {
            String centerKey = e.getKey();
            String owner = e.getValue();
            if (!owner.equals(selfKey)) {
                if (chunkKey.equals(centerKey)) return AreaRole.OTHER_CENTER;
                Set<String> area = centerToAreas.get(centerKey);
                if (area != null && area.contains(chunkKey)) return AreaRole.OTHER_AREA;
            }
        }
        // 4. Unclaimed
        return AreaRole.UNCLAIMED;
    }

    /**
     * Visualizes the chunk grid for a player with legend, using current area settings.
     * Always shows a grid of (visualRadius*2+1)x(visualRadius*2+1). Default: 9x9 (visualRadius=4).
     */
    public void sendChunkGridVisualizer(
            Location center,
            Player player,
            String selfKey,
            Map<String, Set<String>> allClaims,
            String justClaimedCenter
    ) {
        Chunk centerChunk = center.getChunk();
        World world = center.getWorld();
        int px = centerChunk.getX();
        int pz = centerChunk.getZ();

        // Map of centerKey -> owner
        Map<String, String> centerToOwner = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : allClaims.entrySet()) {
            String playerKey = entry.getKey();
            for (String centerKey : entry.getValue()) {
                centerToOwner.put(centerKey, playerKey);
            }
        }
        // Map of centerKey -> area keys
        Map<String, Set<String>> centerToAreas = buildCentersToAreas(allClaims);

        // Legend
        final Map<AreaRole, TextColor> COLORS = Map.of(
                AreaRole.UNCLAIMED, NamedTextColor.GRAY,
                AreaRole.JUST_CLAIMED_CENTER, NamedTextColor.DARK_GREEN,
                AreaRole.JUST_CLAIMED_AREA, NamedTextColor.GREEN,
                AreaRole.YOUR_CENTER, NamedTextColor.BLUE,
                AreaRole.YOUR_AREA, NamedTextColor.AQUA,
                AreaRole.OTHER_CENTER, NamedTextColor.DARK_RED,
                AreaRole.OTHER_AREA, NamedTextColor.RED
        );
        List<Component> legend = Arrays.asList(
                Component.text("Legend:", NamedTextColor.WHITE),
                Component.text("■ ", COLORS.get(AreaRole.JUST_CLAIMED_CENTER)).append(Component.text("Just Claimed Center", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.JUST_CLAIMED_AREA)).append(Component.text("Just Claimed Area", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.YOUR_CENTER)).append(Component.text("Your Claimed Center", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.YOUR_AREA)).append(Component.text("Your Claimed Area", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.OTHER_CENTER)).append(Component.text("Other's Claimed Center", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.OTHER_AREA)).append(Component.text("Other's Claimed Area", NamedTextColor.WHITE)),
                Component.text("■ ", COLORS.get(AreaRole.UNCLAIMED)).append(Component.text("Unclaimed", NamedTextColor.WHITE))
        );
        List<Component> lines = new ArrayList<>(legend);

        // Build grid (always visualRadius*2+1, e.g. 9x9 for visualRadius=4)
        for (int dz = -visualRadius; dz <= visualRadius; dz++) {
            TextComponent.Builder line = Component.text();
            for (int dx = -visualRadius; dx <= visualRadius; dx++) {
                int cx = px + dx;
                int cz = pz + dz;
                String chunkKey = world.getName() + ":" + cx + ":" + cz;
                boolean isPlayer = (dx == 0 && dz == 0);
                String symbol = isPlayer ? "+" : "■";

                AreaRole role = getAreaRole(
                    chunkKey,
                    selfKey,
                    centerToAreas,
                    centerToOwner,
                    justClaimedCenter
                );
                TextColor color = COLORS.get(role);
                line.append(Component.text(symbol + " ", color));
            }
            lines.add(line.build());
        }
        if (justClaimedCenter != null) {
            lines.add(Component.text(
                "Chunks loaded (" + (areaRadius * 2 + 1) + "x" + (areaRadius * 2 + 1) + " area) centered at: ", NamedTextColor.GREEN
            ).append(Component.text(justClaimedCenter, NamedTextColor.WHITE)));
        }
        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
    }
}
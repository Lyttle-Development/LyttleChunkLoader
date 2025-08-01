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
 * ChunkMessages: Shows claims as strict 3x3 areas per config entry.
 */
public class ChunkMessages {
    private final int gridRadius;

    public ChunkMessages(int gridRadius) {
        this.gridRadius = gridRadius;
    }

    public void sendVisualizer(
            Location center,
            Player player,
            String selfKey,
            Map<String, Set<String>> allClaims,
            String justClaimedRodCenter
    ) {
        // Prepare grid
        Chunk centerChunk = center.getChunk();
        World world = center.getWorld();
        int px = centerChunk.getX();
        int pz = centerChunk.getZ();

        // Define colors
        final TextColor UNCLAIMED = NamedTextColor.GRAY;
        final TextColor JUST_CLAIMED_CENTER = NamedTextColor.DARK_GREEN;
        final TextColor JUST_CLAIMED_AREA = NamedTextColor.GREEN;
        final TextColor YOUR_CENTER = NamedTextColor.BLUE;
        final TextColor YOUR_AREA = NamedTextColor.AQUA;
        final TextColor OTHER_CENTER = NamedTextColor.DARK_RED;
        final TextColor OTHER_AREA = NamedTextColor.RED;

        // Build list of centers and their 3x3 areas
        Map<String, Set<String>> centerTo3x3 = new HashMap<>();
        Map<String, String> centerToOwner = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : allClaims.entrySet()) {
            String playerKey = entry.getKey();
            for (String centerKey : entry.getValue()) {
                centerToOwner.put(centerKey, playerKey);

                // Calculate 3x3 area for this center
                String[] parts = centerKey.split(":");
                if (parts.length < 3) continue;

                String worldName = parts[0];
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);

                Set<String> area = new HashSet<>();
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        area.add(worldName + ":" + (cx + dx) + ":" + (cz + dz));
                    }
                }
                centerTo3x3.put(centerKey, area);
            }
        }

        // Legend
        List<Component> legend = Arrays.asList(
            Component.text("Legend:", NamedTextColor.WHITE),
            Component.text("■ ", JUST_CLAIMED_CENTER).append(Component.text("Just Claimed Center", NamedTextColor.WHITE)),
            Component.text("■ ", JUST_CLAIMED_AREA).append(Component.text("Just Claimed Area", NamedTextColor.WHITE)),
            Component.text("■ ", YOUR_CENTER).append(Component.text("Your Claimed Center", NamedTextColor.WHITE)),
            Component.text("■ ", YOUR_AREA).append(Component.text("Your Claimed Area", NamedTextColor.WHITE)),
            Component.text("■ ", OTHER_CENTER).append(Component.text("Other's Claimed Center", NamedTextColor.WHITE)),
            Component.text("■ ", OTHER_AREA).append(Component.text("Other's Claimed Area", NamedTextColor.WHITE)),
            Component.text("■ ", UNCLAIMED).append(Component.text("Unclaimed", NamedTextColor.WHITE))
        );
        List<Component> lines = new ArrayList<>(legend);

        // Build grid with strict 3x3 areas
        for (int dz = -gridRadius; dz <= gridRadius; dz++) {
            TextComponent.Builder line = Component.text();
            for (int dx = -gridRadius; dx <= gridRadius; dx++) {
                int cx = px + dx;
                int cz = pz + dz;
                String chunkKey = world.getName() + ":" + cx + ":" + cz;
                boolean isPlayer = (dx == 0 && dz == 0);
                String symbol = isPlayer ? "+" : "■";

                // Default to unclaimed
                TextColor color = UNCLAIMED;

                // Priority order: Just claimed > Your claim > Other's claim

                // 1. Check if just claimed
                if (justClaimedRodCenter != null) {
                    if (chunkKey.equals(justClaimedRodCenter)) {
                        color = JUST_CLAIMED_CENTER;
                        line.append(Component.text(symbol + " ", color));
                        continue;
                    }

                    Set<String> justClaimedArea = centerTo3x3.get(justClaimedRodCenter);
                    if (justClaimedArea != null && justClaimedArea.contains(chunkKey)) {
                        color = JUST_CLAIMED_AREA;
                        line.append(Component.text(symbol + " ", color));
                        continue;
                    }
                }

                // 2. Check if your claim
                boolean foundYours = false;
                for (String centerKey : centerToOwner.keySet()) {
                    String owner = centerToOwner.get(centerKey);
                    if (owner.equals(selfKey)) {
                        if (chunkKey.equals(centerKey)) {
                            color = YOUR_CENTER;
                            foundYours = true;
                            break;
                        }

                        Set<String> area = centerTo3x3.get(centerKey);
                        if (area != null && area.contains(chunkKey)) {
                            color = YOUR_AREA;
                            foundYours = true;
                            break;
                        }
                    }
                }

                if (foundYours) {
                    line.append(Component.text(symbol + " ", color));
                    continue;
                }

                // 3. Check if other's claim
                boolean foundOthers = false;
                for (String centerKey : centerToOwner.keySet()) {
                    String owner = centerToOwner.get(centerKey);
                    if (!owner.equals(selfKey)) {
                        if (chunkKey.equals(centerKey)) {
                            color = OTHER_CENTER;
                            foundOthers = true;
                            break;
                        }

                        Set<String> area = centerTo3x3.get(centerKey);
                        if (area != null && area.contains(chunkKey)) {
                            color = OTHER_AREA;
                            foundOthers = true;
                            break;
                        }
                    }
                }

                if (foundOthers) {
                    line.append(Component.text(symbol + " ", color));
                    continue;
                }

                // 4. Unclaimed (already default)
                line.append(Component.text(symbol + " ", color));
            }
            lines.add(line.build());
        }

        // Footer message if just claimed
        if (justClaimedRodCenter != null) {
            lines.add(Component.text("Chunks loaded (3x3 area) centered at: ", NamedTextColor.GREEN)
                    .append(Component.text(justClaimedRodCenter, NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
    }
}
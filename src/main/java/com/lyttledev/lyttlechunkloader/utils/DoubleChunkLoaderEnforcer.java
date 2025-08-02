package com.lyttledev.lyttlechunkloader.utils;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;

import com.lyttledev.lyttleutils.types.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Enforces that only one DOUBLE chunk loader (Lodestone + Lightning Rod stacked) exists per chunk in a
 * configurable radius. Removes all conflicting DOUBLE loaders in the area except the one just placed.
 * No item duplication: ensures items are only dropped for physically broken loaders, not those just removed logically.
 *
 * Assumes chunkConfig is structured as: {playerUUID: [chunkKey1, chunkKey2, ...]}, representing claimed chunks.
 * Assumes ChunkRangeUtil provides: getAreaChunkKeys(World, int, int) for chunk key set within radius.
 */
public class DoubleChunkLoaderEnforcer {

    private final LyttleChunkLoader plugin;
    private final Config chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final int enforceRadius; // Number of chunks radius to enforce uniqueness

    public DoubleChunkLoaderEnforcer(LyttleChunkLoader plugin, ChunkRangeUtil chunkRangeUtil, int enforceRadius) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = chunkRangeUtil;
        this.enforceRadius = enforceRadius;
    }

    /**
     * Called on creation of a DOUBLE chunk loader. Removes all other DOUBLE loaders in radius.
     *
     * @param placedLodestoneLoc The Location of the Lodestone block (the base of the stack)
     * @param placer The Player placing the loader (may be null for system action)
     */
    public void enforceUniqueDoubleChunkLoaderOnCreate(Location placedLodestoneLoc, Player placer) {
        World world = placedLodestoneLoc.getWorld();
        Chunk centerChunk = placedLodestoneLoc.getChunk();
        Set<String> areaChunkKeys = chunkRangeUtil.getAreaChunkKeys(world, centerChunk.getX(), centerChunk.getZ());

        // Collect all DOUBLE loaders in area
        List<Location> doubleLoaderBases = findPhysicalDoubleLoadersInChunks(world, areaChunkKeys);

        // Remove all except the newly placed one
        for (Location baseLoc : doubleLoaderBases) {
            if (baseLoc.equals(placedLodestoneLoc)) continue;
            breakDoubleChunkLoader(baseLoc, true); // Drop items for these removed loaders
            removeClaimForLoader(baseLoc);
        }

        // Ensure config only keeps the new loader in area
        cleanUpConfigForArea(world, areaChunkKeys, placedLodestoneLoc);
        // Always leave the newly placed loader intact and claimed
    }

    /**
     * Called on removal of a DOUBLE chunk loader. Only removes and drops items if the loader is in config and in valid radius.
     * Sends player feedback (message + sound) if removed for them.
     *
     * @param lodestoneLoc Location of the Lodestone to remove
     * @param remover Player or system removing the loader
     */
    public void enforceUniqueDoubleChunkLoaderOnRemove(Location lodestoneLoc, Player remover) {
        World world = lodestoneLoc.getWorld();
        Chunk chunk = lodestoneLoc.getChunk();
        String chunkKey = chunkRangeUtil.getChunkKey(world, chunk.getX(), chunk.getZ());

        // Find player owner from config
        String ownerKey = getLoaderOwnerForChunk(chunkKey);

        if (ownerKey != null) {
            // Only remove if it's in config (valid)
            if (isPhysicalDoubleLoader(lodestoneLoc)) {
                breakDoubleChunkLoader(lodestoneLoc, true);
            }
            removeClaimForLoader(lodestoneLoc);

            // Player feedback if relevant
            if (remover != null && ownerKey.equals(getPlayerKey(remover))) {
                remover.sendMessage(
                    Component.text("Chunk unloaded: ", NamedTextColor.GRAY)
                        .append(Component.text(chunkKey, NamedTextColor.WHITE)));
                remover.playSound(lodestoneLoc, Sound.BLOCK_ANVIL_LAND, SoundCategory.MASTER, 1.0f, 1.0f);
            }

            // Clean up: ensure no stray double loaders remain in area
            Set<String> areaChunkKeys = chunkRangeUtil.getAreaChunkKeys(world, chunk.getX(), chunk.getZ());
            List<Location> stray = findPhysicalDoubleLoadersInChunks(world, areaChunkKeys);
            for (Location strayBase : stray) {
                if (!strayBase.equals(lodestoneLoc)) {
                    breakDoubleChunkLoader(strayBase, false); // Don't drop items for logical cleanup
                    removeClaimForLoader(strayBase);
                }
            }
        }
    }

    /**
     * Finds all physical DOUBLE chunk loader bases (Lodestone with Lightning Rod above) in area.
     */
    private List<Location> findPhysicalDoubleLoadersInChunks(World world, Set<String> chunkKeys) {
        List<Location> bases = new ArrayList<>();
        for (String key : chunkKeys) {
            String[] parts = key.split(":");
            if (parts.length < 3) continue;
            int cx = Integer.parseInt(parts[1]);
            int cz = Integer.parseInt(parts[2]);
            Chunk chunk = world.getChunkAt(cx, cz);

            // Search all blocks in chunk for Lodestone+Lightning Rod stacks (Y 0-255)
            for (int y = world.getMinHeight(); y < world.getMaxHeight() - 1; y++) {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        Location base = new Location(world, chunk.getBlock(lx, y, lz).getX(), y, chunk.getBlock(lx, y, lz).getZ());
                        if (isPhysicalDoubleLoader(base)) {
                            bases.add(base);
                        }
                    }
                }
            }
        }
        return bases;
    }

    /**
     * Returns true if the given Location is a Lodestone with Lightning Rod directly above.
     */
    private boolean isPhysicalDoubleLoader(Location lodestoneLoc) {
        Block base = lodestoneLoc.getBlock();
        Block above = lodestoneLoc.clone().add(0, 1, 0).getBlock();
        return base.getType() == Material.LODESTONE && above.getType() == Material.LIGHTNING_ROD;
    }

    /**
     * Breaks the DOUBLE chunk loader at lodestoneLoc. Optionally drops items.
     */
    private void breakDoubleChunkLoader(Location lodestoneLoc, boolean dropItems) {
        Block base = lodestoneLoc.getBlock();
        Block above = lodestoneLoc.clone().add(0, 1, 0).getBlock();

        if (base.getType() == Material.LODESTONE) {
            if (dropItems) base.getWorld().dropItemNaturally(base.getLocation(), new ItemStack(Material.LODESTONE));
            base.setType(Material.AIR);
        }
        if (above.getType() == Material.LIGHTNING_ROD) {
            if (dropItems) above.getWorld().dropItemNaturally(above.getLocation(), new ItemStack(Material.LIGHTNING_ROD));
            above.setType(Material.AIR);
        }
    }

    /**
     * Removes the claim for this loader in the config.
     */
    private void removeClaimForLoader(Location lodestoneLoc) {
        String chunkKey = chunkRangeUtil.getChunkKey(lodestoneLoc);
        String[] allPlayers = chunkConfig.getKeys("");
        if (allPlayers == null) return;
        for (String playerKey : allPlayers) {
            List<String> chunks = chunkConfig.getStringList(playerKey);
            if (chunks != null && chunks.remove(chunkKey)) {
                chunkConfig.set(playerKey, chunks);
            }
        }
    }

    /**
     * Returns the player UUID string who owns this chunk loader, or null if none.
     */
    private String getLoaderOwnerForChunk(String chunkKey) {
        String[] allPlayers = chunkConfig.getKeys("");
        if (allPlayers == null) return null;
        for (String playerKey : allPlayers) {
            List<String> chunks = chunkConfig.getStringList(playerKey);
            if (chunks != null && chunks.contains(chunkKey)) {
                return playerKey;
            }
        }
        return null;
    }

    /**
     * Removes claims from config for all double loaders except the one at keepLoc in area.
     */
    private void cleanUpConfigForArea(World world, Set<String> areaChunkKeys, Location keepLoc) {
        String keepKey = chunkRangeUtil.getChunkKey(keepLoc);
        String[] allPlayers = chunkConfig.getKeys("");
        if (allPlayers == null) return;
        for (String playerKey : allPlayers) {
            List<String> chunks = chunkConfig.getStringList(playerKey);
            if (chunks == null) continue;
            boolean changed = false;
            Iterator<String> it = chunks.iterator();
            while (it.hasNext()) {
                String ckey = it.next();
                if (areaChunkKeys.contains(ckey) && !ckey.equals(keepKey)) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                chunkConfig.set(playerKey, chunks);
            }
        }
    }

    /**
     * Returns the UUID string for a player.
     */
    private String getPlayerKey(Player player) {
        return player != null ? player.getUniqueId().toString() : "";
    }
}
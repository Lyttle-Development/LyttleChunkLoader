package com.lyttledev.lyttlechunkloader.handlers;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttlechunkloader.utils.ChunkRangeUtil;
import com.lyttledev.lyttlechunkloader.utils.DoubleChunkLoaderEnforcer;
import com.lyttledev.lyttleutils.types.YamlConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

public class ManagementHandler implements Listener {
    private final LyttleChunkLoader plugin;
    private final YamlConfig chunkConfig;
    private final ChunkRangeUtil chunkRangeUtil;
    private final DoubleChunkLoaderEnforcer doubleLoaderEnforcer;
    private final PaymentHandler paymentHandler;

    public ManagementHandler(LyttleChunkLoader plugin) {
        this.plugin = plugin;
        this.chunkConfig = plugin.config.chunks;
        this.chunkRangeUtil = new ChunkRangeUtil(1, 4);
        this.doubleLoaderEnforcer = new DoubleChunkLoaderEnforcer(plugin, chunkRangeUtil, 1);
        this.paymentHandler = plugin.paymentHandler;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private String getChunkKey(Location location) {
        return chunkRangeUtil.getChunkKey(location);
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

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        boolean isLoader = false;
        Location baseLocation = null;
        if (block.getType() == Material.LODESTONE) {
            Block above = block.getLocation().clone().add(0, 1, 0).getBlock();
            if (above.getType() == Material.LIGHTNING_ROD) {
                isLoader = true;
                baseLocation = block.getLocation();
            }
        } else if (block.getType() == Material.LIGHTNING_ROD) {
            Block below = block.getLocation().clone().add(0, -1, 0).getBlock();
            if (below.getType() == Material.LODESTONE) {
                isLoader = true;
                baseLocation = below.getLocation();
            }
        }

        if (!isLoader) return;

        doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnCreate(baseLocation, player);

        // Only claim if NOT already claimed (prevents double claim and double charge)
        boolean claimed = claimChunkAt(baseLocation, player);
        if (claimed) {
            boolean paid = paymentHandler.chargeAndStartProcessOnCreate(player, getChunkKey(baseLocation), true);
            if (!paid) {
                removeDoubleChunkLoader(baseLocation);
                removeChunkClaim(baseLocation, player); // also ensures chunk is unloaded
                player.sendMessage(Component.text("You could not afford a chunk loader here. The block was removed.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        Player player = event.getPlayer();

        boolean isLoader = false;
        Location baseLocation = null;
        if (block.getType() == Material.LODESTONE) {
            Block above = location.clone().add(0, 1, 0).getBlock();
            if (above.getType() == Material.LIGHTNING_ROD) {
                isLoader = true;
                baseLocation = location;
            }
        } else if (block.getType() == Material.LIGHTNING_ROD) {
            Block below = location.clone().add(0, -1, 0).getBlock();
            if (below.getType() == Material.LODESTONE) {
                isLoader = true;
                baseLocation = below.getLocation();
            }
        }
        if (!isLoader) return;

        doubleLoaderEnforcer.enforceUniqueDoubleChunkLoaderOnRemove(baseLocation, player);
        removeChunkClaim(baseLocation, player); // always remove/unload, also if not owned
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Location lodestoneLocation = block.getLocation();

        if (block.getType() == Material.LODESTONE) {
            Block above = lodestoneLocation.clone().add(0, 1, 0).getBlock();
            if (above.getType() == Material.LIGHTNING_ROD) {
                sendVisualization(lodestoneLocation, player);
            }
        } else if (block.getType() == Material.LIGHTNING_ROD) {
            Block below = lodestoneLocation.clone().add(0, -1, 0).getBlock();
            if (below.getType() == Material.LODESTONE) {
                sendVisualization(below.getLocation(), player);
            }
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

    // Returns true if claim succeeded (new claim), false if already claimed
    private boolean claimChunkAt(Location lodestoneLocation, Player player) {
        Block lodestone = lodestoneLocation.getBlock();
        Block rod = lodestoneLocation.clone().add(0, 1, 0).getBlock();

        if (lodestone.getType() != Material.LODESTONE || rod.getType() != Material.LIGHTNING_ROD) {
            player.sendMessage(Component.text("You can only claim if a lightning rod is placed on top of a lodestone.", NamedTextColor.GRAY));
            return false;
        }

        Chunk centerChunk = lodestoneLocation.getChunk();
        List<String> chunkList = getPlayerChunks(player);

        Set<String> areaKeys = chunkRangeUtil.getAreaChunkKeys(
                lodestoneLocation.getWorld(),
                centerChunk.getX(),
                centerChunk.getZ()
        );
        for (String key : areaKeys) {
            if (chunkList.contains(key)) {
                // Already claimed by this player
                sendVisualization(lodestoneLocation, player);
                return false;
            }
        }

        String key = getChunkKey(lodestoneLocation);
        chunkList.add(key);
        savePlayerChunks(player, chunkList);

        sendVisualization(lodestoneLocation, player);
        player.playSound(lodestoneLocation, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
        return true;
    }

    private void removeChunkClaim(Location lodestoneLocation, Player player) {
        String key = getChunkKey(lodestoneLocation);
        List<String> chunkList = getPlayerChunks(player);
        if (chunkList.contains(key)) {
            chunkList.remove(key);
            savePlayerChunks(player, chunkList);
            paymentHandler.onChunkLoaderRemoved(player, key);
        } else {
            // Still ensure unload if not owned (safety)
            paymentHandler.unloadChunkAndSurrounding(key);
        }
    }

    private void removeDoubleChunkLoader(Location lodestoneLoc) {
        Block base = lodestoneLoc.getBlock();
        Block above = lodestoneLoc.clone().add(0, 1, 0).getBlock();
        if (base.getType() == Material.LODESTONE) base.setType(Material.AIR);
        if (above.getType() == Material.LIGHTNING_ROD) above.setType(Material.AIR);
    }
}
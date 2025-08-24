package com.lyttledev.lyttlechunkloader.types;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttleutils.types.YamlConfig;

public class Configs {
    private final LyttleChunkLoader plugin;

    // Configs
    public YamlConfig general;
    public YamlConfig chunks;
    public YamlConfig messages;

    // Default configs
    public YamlConfig defaultGeneral;
    public YamlConfig defaultChunks;
    public YamlConfig defaultMessages;


    public Configs(LyttleChunkLoader plugin) {
        this.plugin = plugin;

        // Configs
        general = new YamlConfig(plugin, "config.yml");
        chunks = new YamlConfig(plugin, "chunks.yml");
        messages = new YamlConfig(plugin, "messages.yml");

        // Default configs
        defaultGeneral = new YamlConfig(plugin, "#defaults/config.yml");
        defaultChunks = new YamlConfig(plugin, "#defaults/chunks.yml");
        defaultMessages = new YamlConfig(plugin, "#defaults/messages.yml");
    }

    public void reload() {
        general.reload();
        chunks.reload();
        messages.reload();

        plugin.reloadConfig();
    }

    private String getConfigPath(String path) {
        return plugin.getConfig().getString("configs." + path);
    }
}

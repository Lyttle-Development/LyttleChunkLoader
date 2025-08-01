package com.lyttledev.lyttlechunkloader.types;

import com.lyttledev.lyttlechunkloader.LyttleChunkLoader;
import com.lyttledev.lyttleutils.types.Config;

public class Configs {
    private final LyttleChunkLoader plugin;

    // Configs
    public Config general;
    public Config messages;

    // Default configs
    public Config defaultGeneral;
    public Config defaultMessages;


    public Configs(LyttleChunkLoader plugin) {
        this.plugin = plugin;

        // Configs
        general = new Config(plugin, "config.yml");
        messages = new Config(plugin, "messages.yml");

        // Default configs
        defaultGeneral = new Config(plugin, "#defaults/config.yml");
        defaultMessages = new Config(plugin, "#defaults/messages.yml");
    }

    public void reload() {
        general.reload();
        messages.reload();

        plugin.reloadConfig();
    }

    private String getConfigPath(String path) {
        return plugin.getConfig().getString("configs." + path);
    }
}

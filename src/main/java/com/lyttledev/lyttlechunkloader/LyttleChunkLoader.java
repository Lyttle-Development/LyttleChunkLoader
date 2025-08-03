package com.lyttledev.lyttlechunkloader;

import com.lyttledev.lyttlechunkloader.commands.LyttleChunkLoaderCommand;
import com.lyttledev.lyttlechunkloader.handlers.ManagementHandler;
import com.lyttledev.lyttlechunkloader.handlers.PaymentHandler;
import com.lyttledev.lyttlechunkloader.types.Configs;
import com.lyttledev.lyttlechunkloader.utils.MaterialExporter;
import com.lyttledev.lyttlechunkloader.utils.WorldBorderChunkHighlighter;
import com.lyttledev.lyttleutils.utils.communication.Console;
import com.lyttledev.lyttleutils.utils.communication.Message;
import com.lyttledev.lyttleutils.utils.storage.GlobalConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class LyttleChunkLoader extends JavaPlugin {
    public Configs config;
    public Console console;
    public Message message;
    public GlobalConfig global;
    public MiniMessage miniMessage = MiniMessage.miniMessage();
    public WorldBorderChunkHighlighter borderHighlighter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Setup config after creating the configs
        this.config = new Configs(this);
        this.global = new GlobalConfig(this);
        // Migrate config
        migrateConfig();

        // Plugin startup logic
        this.console = new Console(this);
        this.message = new Message(this, config.messages, global);
        this.borderHighlighter = new WorldBorderChunkHighlighter(this);

        // Commands
        new LyttleChunkLoaderCommand(this);

        // Handlers
        new PaymentHandler(this);
        new ManagementHandler(this);

        // Export Material list on plugin startup (only if file doesn't exist)
        File materialsFile = new File(getDataFolder(), "available_materials.txt");
        try {
            if (!materialsFile.exists()) {
                MaterialExporter.exportMaterials(materialsFile);
                getLogger().info("Exported available_materials.txt with all Material types.");
            }
        } catch (IOException e) {
            getLogger().warning("Failed to export available_materials.txt: " + e.getMessage());
        }
    }

    @Override
    public void saveDefaultConfig() {
        String configPath = "config.yml";
        if (!new File(getDataFolder(), configPath).exists())
            saveResource(configPath, false);

        String chunksPath = "chunks.yml";
        if (!new File(getDataFolder(), chunksPath).exists())
            saveResource(chunksPath, false);

        String messagesPath = "messages.yml";
        if (!new File(getDataFolder(), messagesPath).exists())
            saveResource(messagesPath, false);

        // Defaults:
        String defaultPath = "#defaults/";
        String defaultGeneralPath = defaultPath + configPath;
        saveResource(defaultGeneralPath, true);

        String defaultChunksPath = defaultPath + chunksPath;
        saveResource(defaultChunksPath, true);

        String defaultMessagesPath = defaultPath + messagesPath;
        saveResource(defaultMessagesPath, true);
    }

    private void migrateConfig() {
        if (!config.general.contains("config_version")) {
            config.general.set("config_version", 0);
        }

        switch (config.general.get("config_version").toString()) {
//            case "0":
//                // Migrate config entries.
//                // config.general.set("x", config.defaultGeneral.get("x"));
//
//                // Update config version.
//                config.general.set("config_version", 1);
//
//                // Recheck if the config is fully migrated.
//                migrateConfig();
//                break;
            default:
                break;
        }
    }
}

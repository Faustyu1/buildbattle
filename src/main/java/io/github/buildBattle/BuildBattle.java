package io.github.buildBattle;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class BuildBattle extends JavaPlugin {
    private static BuildBattle instance;
    private FileConfiguration config;
    private List<String> themes;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        themes = new ArrayList<>();
        gameManager = new GameManager(this);
        
        getCommand("buildbattle").setExecutor(new BuildBattleCommand(this));
        getServer().getPluginManager().registerEvents(new BuildBattleListener(this), this);
        
        getLogger().info("BuildBattle plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BuildBattle plugin has been disabled!");
    }

    public static BuildBattle getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public List<String> getThemes() {
        return themes;
    }
}

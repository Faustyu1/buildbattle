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
    private BuildBattleGame game;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();
        loadThemes();
        game = new BuildBattleGame(this);
        
        BuildBattleCommand commandHandler = new BuildBattleCommand(game);
        getCommand("buildbattle").setExecutor(commandHandler);
        getServer().getPluginManager().registerEvents(new BuildBattleListener(game, commandHandler), this);
        
        getLogger().info("BuildBattle plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BuildBattle plugin has been disabled!");
    }

    private void loadThemes() {
        themes = config.getStringList("themes");
        if (themes.isEmpty()) {
            getLogger().warning("Список тем пуст! Добавьте темы в config.yml");
        }
    }

    public static BuildBattle getInstance() {
        return instance;
    }

    public BuildBattleGame getGame() {
        return game;
    }

    public List<String> getThemes() {
        return themes;
    }
}

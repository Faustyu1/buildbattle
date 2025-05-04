package io.github.buildBattle;

import org.bukkit.plugin.java.JavaPlugin;

public class BuildBattlePlugin extends JavaPlugin {
    private BuildBattleGame game;
    private BuildBattleListener listener; // Сохраняем ссылку на слушатель

    @Override
    public void onEnable() {
        // Создаем папку для конфигурации, если она не существует
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        // Инициализируем игру
        game = new BuildBattleGame(this);

        // Регистрируем команды и таб-комплитер
        BuildBattleCommand commandHandler = new BuildBattleCommand(game);
        getCommand("buildbattle").setExecutor(commandHandler);
        getCommand("buildbattle").setTabCompleter(new BuildBattleTabCompleter(game));

        // Регистрируем слушатели событий
        listener = new BuildBattleListener(game, commandHandler);
        getServer().getPluginManager().registerEvents(listener, this);

        getLogger().info("BuildBattle успешно включен!");
    }

    @Override
    public void onDisable() {
        // Сохраняем данные при выключении плагина
        if (game != null) {
            game.saveArenas();
            game.savePlayers();
        }
        getLogger().info("BuildBattle выключен!");
    }
    
    /**
     * Получить экземпляр слушателя событий BuildBattleListener
     * @return экземпляр BuildBattleListener
     */
    public BuildBattleListener getListener() {
        return listener;
    }
} 
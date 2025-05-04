package io.github.buildBattle;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BuildBattleGame {
    private final JavaPlugin plugin;
    private final Map<String, BuildArea> arenas;
    private final File configFile;
    private final FileConfiguration config;
    private final File playersFile;
    private final FileConfiguration playersConfig;
    private final File rouletteFile;
    private final FileConfiguration rouletteConfig;
    private final Set<Player> judges;
    private final Set<Player> builders;
    private final Map<String, Set<String>> teams;
    private BuildArea currentArena;
    private boolean isJudgingPhase;
    private Location gatheringPoint;
    private Location rouletteLocation; // Локация для 3D рулетки
    private Location rouletteSpawnPoint; // Локация для телепортации игроков к рулетке
    private GameState gameState;
    private String currentTheme;
    private List<String> themes;
    private int buildTimeInMinutes = 10; // время на строительство в минутах
    private int judgeTimeInMinutes = 5;  // время на оценку в минутах
    private int timeRemaining;           // оставшееся время в секундах
    private BukkitTask timerTask;        // задача таймера
    
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_TEAMS = 8;

    private final Map<String, Map<Player, Integer>> teamScores; // Хранение оценок от жюри для каждой команды
    private List<String> teamVotingOrder; // Порядок оценки команд
    private int currentTeamIndex; // Индекс текущей оцениваемой команды

    private final Map<String, String> teamDisplayNames; // Хранение отображаемых названий команд
    private final Map<String, Material> teamFloors; // Хранение материалов пола для каждой команды

    private Location endLocation;

    private static final String MSG_GAME_START = "§6Игра началась! Тема: §e%s";
    private static final String MSG_BUILD_TIME = "§6Время на строительство: §e%d минут";
    private static final String MSG_FLOOR_HINT = "§6Подсказка: §eДержите блок в руке и используйте §6/buildbattle setfloor §eчтобы установить пол для вашей команды!";

    private BukkitTask rouletteTask;
    private static final double ROTATION_RADIUS = 2.0; // Радиус вращения
    private static final double ROTATION_HEIGHT = 1.0; // Высота бутылочки
    private static final int ROTATION_DURATION = 200; // Длительность вращения в тиках (10 секунд)

    public enum GameState {
        WAITING,
        BUILDING,
        JUDGING,
        ENDED
    }

    public BuildBattleGame(JavaPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new HashMap<>();
        this.configFile = new File(plugin.getDataFolder(), "arenas.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
        this.playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        this.rouletteFile = new File(plugin.getDataFolder(), "roulette.yml");
        this.rouletteConfig = YamlConfiguration.loadConfiguration(rouletteFile);
        this.judges = new HashSet<>();
        this.builders = new HashSet<>();
        this.teams = new HashMap<>();
        this.teamScores = new HashMap<>();
        this.teamDisplayNames = new HashMap<>();
        this.teamFloors = new HashMap<>();
        this.isJudgingPhase = false;
        this.gameState = GameState.WAITING;
        this.themes = new ArrayList<>();
        initializeTeams();
        loadArenas();
        loadPlayers();
        loadRouletteLocations();
        loadLocations();
        loadTeamNames();
        loadTeamFloors();
        loadThemes();
    }

    private void initializeTeams() {
        for (int i = 1; i <= MAX_TEAMS; i++) {
            String teamName = "team" + i;
            teams.put(teamName, new HashSet<>());
            teamScores.put(teamName, new HashMap<>());
        }
    }

    /**
     * Загружает данные игроков из файла players.yml
     */
    private void loadPlayers() {
        judges.clear();
        builders.clear();
        
        // Инициализируем команды, если они еще не созданы
        for (int i = 1; i <= MAX_TEAMS; i++) {
            String teamName = "team" + i;
            if (!teams.containsKey(teamName)) {
                teams.put(teamName, new HashSet<>());
                teamScores.put(teamName, new HashMap<>());
            }
        }

        // Загружаем жюри
        List<String> judgeNames = playersConfig.getStringList("judges");
        for (String name : judgeNames) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) {
                judges.add(player);
            }
        }

        // Загружаем команды
        ConfigurationSection teamsSection = playersConfig.getConfigurationSection("teams");
        if (teamsSection != null) {
            for (String teamName : teamsSection.getKeys(false)) {
                List<String> playerNames = teamsSection.getStringList(teamName);
                Set<String> team = teams.get(teamName);
                if (team != null) {
                    team.clear();
                    team.addAll(playerNames);
                }
            }
        }

        // Загружаем строителей, которые не в командах
        List<String> builderNames = playersConfig.getStringList("builders");
        for (String name : builderNames) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null && !isJudge(player)) {
                builders.add(player);
            }
        }
    }

    /**
     * Сохраняет данные игроков в файл players.yml
     */
    public void savePlayers() {
        // Сохраняем жюри
        List<String> judgeNames = new ArrayList<>();
        for (Player judge : judges) {
            judgeNames.add(judge.getName());
        }
        playersConfig.set("judges", judgeNames);

        // Сохраняем команды
        ConfigurationSection teamsSection = playersConfig.createSection("teams");
        for (Map.Entry<String, Set<String>> entry : teams.entrySet()) {
            String teamName = entry.getKey();
            Set<String> playerNames = entry.getValue();
            if (!playerNames.isEmpty()) {
                teamsSection.set(teamName, new ArrayList<>(playerNames));
            }
        }

        // Сохраняем строителей (всех, кроме жюри)
        List<String> builderNames = new ArrayList<>();
        for (Player builder : builders) {
            if (!isJudge(builder)) {
                builderNames.add(builder.getName());
            }
        }
        playersConfig.set("builders", builderNames);

        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить данные игроков: " + e.getMessage());
        }
    }

    /**
     * Добавляет игрока в команду
     * @param player игрок
     * @param teamName название команды
     * @return true если игрок успешно добавлен
     */
    public boolean addPlayerToTeam(Player player, String teamName) {
        if (!teams.containsKey(teamName)) {
            return false;
        }

        // Удаляем игрока из всех команд
        for (Set<String> team : teams.values()) {
            team.remove(player.getName());
        }

        // Добавляем в новую команду
        teams.get(teamName).add(player.getName());
        builders.add(player);
        savePlayers();
        return true;
    }

    /**
     * Удаляет игрока из команды
     * @param player игрок
     * @return true если игрок был удален
     */
    public boolean removePlayerFromTeam(Player player) {
        boolean removed = false;
        for (Set<String> team : teams.values()) {
            if (team.remove(player.getName())) {
                removed = true;
            }
        }
        if (removed) {
            builders.remove(player);
            savePlayers();
        }
        return removed;
    }

    /**
     * Получает команду игрока
     * @param player игрок
     * @return название команды или null если игрок не в команде
     */
    public String getPlayerTeam(Player player) {
        for (Map.Entry<String, Set<String>> entry : teams.entrySet()) {
            if (entry.getValue().contains(player.getName())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Получает список игроков команды
     * @param teamName название команды
     * @return множество игроков команды
     */
    public Set<Player> getTeamPlayers(String teamName) {
        Set<Player> result = new HashSet<>();
        Set<String> names = teams.getOrDefault(teamName, new HashSet<>());
        for (String name : names) {
            Player p = Bukkit.getPlayerExact(name);
            if (p != null) result.add(p);
        }
        return result;
    }

    /**
     * Добавляет игрока в жюри
     * @param player игрок
     */
    public void addJudge(Player player) {
        judges.add(player);
        // Удаляем из строителей и команды
        builders.remove(player);
        removePlayerFromTeam(player);
        savePlayers();
    }

    /**
     * Удаляет игрока из жюри
     * @param player игрок
     */
    public void removeJudge(Player player) {
        judges.remove(player);
        savePlayers();
    }

    /**
     * Проверяет, является ли игрок жюри
     * @param player игрок
     * @return true если игрок является жюри
     */
    public boolean isJudge(Player player) {
        return judges.contains(player);
    }

    /**
     * Проверяет, является ли игрок строителем
     * @param player игрок
     * @return true если игрок является строителем
     */
    public boolean isBuilder(Player player) {
        return builders.contains(player);
    }

    /**
     * Удаляет игрока из строителей
     * @param player игрок
     */
    public void removeBuilder(Player player) {
        builders.remove(player);
        // Удаляем игрока из команды
        removePlayerFromTeam(player);
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGatheringPoint(Location location) {
        this.gatheringPoint = location.clone();
        saveLocations();
    }

    public void setTeamArena(int teamNumber, Location corner1, Location corner2) {
        String arenaName = "team" + (teamNumber + 1);
        createArena(arenaName, corner1, corner2);
    }

    public boolean isInTeamArea(Player player, Location location) {
        for (BuildArea arena : arenas.values()) {
            if (arena.isInArea(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean canBreakBlock(Player player, Location location) {
        for (BuildArea arena : arenas.values()) {
            if (arena.isInArea(location) && !arena.isOriginalBlock(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean canStartGame() {
        // Проверяем, есть ли хотя бы одна команда с игроками
        boolean hasTeamsWithPlayers = false;
        int totalBuilders = 0;
        for (Set<String> team : teams.values()) {
            if (!team.isEmpty()) {
                hasTeamsWithPlayers = true;
                totalBuilders += team.size();
            }
        }
        if (!hasTeamsWithPlayers) {
            Bukkit.broadcastMessage("§cНет команд с игроками! Добавьте игроков в команды перед началом игры.");
            return false;
        }
        // Проверяем минимальное количество строителей по всем игрокам в командах
        if (totalBuilders < MIN_PLAYERS) {
            Bukkit.broadcastMessage("§cДля начала игры нужно минимум " + MIN_PLAYERS + " строителя!");
            return false;
        }
        return true;
    }

    public void startGame() {
        if (!canStartGame()) {
            Bukkit.broadcastMessage("§cДля начала игры нужно минимум " + MIN_PLAYERS + " строителя!");
            return;
        }

        // Удаляем все эффекты у всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        }

        // Выбираем случайную тему
        if (themes.isEmpty()) {
            Bukkit.broadcastMessage("§cНет доступных тем для строительства!");
            return;
        }
        
        Random random = new Random();
        currentTheme = themes.get(random.nextInt(themes.size()));
        
        gameState = GameState.BUILDING;
        
        // Устанавливаем время
        timeRemaining = buildTimeInMinutes * 60;
        
        // Оповещаем игроков
        Bukkit.broadcastMessage("§6Игра началась! Тема: §e" + currentTheme);
        Bukkit.broadcastMessage("§6Время на строительство: §e" + buildTimeInMinutes + " минут");
        
        // Отправляем title всем игрокам
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§6BuildBattle", "§eТема: " + currentTheme, 10, 60, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        // Телепортируем всех игроков на точки спавна их арен и выдаем им CREATIVE режим
        for (Map.Entry<String, BuildArea> entry : arenas.entrySet()) {
            String teamName = entry.getKey();
            BuildArea arena = entry.getValue();
            Location spawnLocation = arena.getSpawnPoint();
            for (Player player : getTeamPlayers(teamName)) {
                player.teleport(spawnLocation);
                player.setGameMode(org.bukkit.GameMode.CREATIVE);
                player.sendMessage("§aВам выдан творческий режим для строительства!");
            }
        }
        
        // Запускаем таймер
        startTimer();
    }

    /**
     * Загружает сохраненные материалы полов из конфигурации
     */
    private void loadTeamFloors() {
        ConfigurationSection floorsSection = config.getConfigurationSection("team_floors");
        if (floorsSection != null) {
            for (String teamId : floorsSection.getKeys(false)) {
                String materialName = floorsSection.getString(teamId);
                if (materialName != null) {
                    try {
                        Material material = Material.valueOf(materialName);
                        teamFloors.put(teamId, material);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Неверный материал для пола команды " + teamId + ": " + materialName);
                    }
                }
            }
        }
    }

    /**
     * Сохраняет материалы полов в конфигурацию
     */
    private void saveTeamFloors() {
        ConfigurationSection floorsSection = config.createSection("team_floors");
        for (Map.Entry<String, Material> entry : teamFloors.entrySet()) {
            floorsSection.set(entry.getKey(), entry.getValue().name());
        }
        saveConfig();
    }

    /**
     * Устанавливает материал пола для команды
     * @param teamId ID команды
     * @param material Материал пола
     */
    public void setTeamFloor(String teamId, Material material) {
        teamFloors.put(teamId, material);
        saveTeamFloors();
    }

    /**
     * Получает материал пола команды
     * @param teamId ID команды
     * @return Материал пола или null, если не установлен
     */
    public Material getTeamFloor(String teamId) {
        return teamFloors.get(teamId);
    }

    /**
     * Загружает темы из конфигурации
     */
    private void loadThemes() {
        themes.clear();
        List<String> configThemes = plugin.getConfig().getStringList("themes");
        if (configThemes.isEmpty()) {
            // Если темы не найдены в конфиге, используем дефолтные
            themes.addAll(Arrays.asList(
                "Замок", "Ферма", "Космический корабль", "Подводный мир", 
                "Фантастическое существо", "Лес", "Город будущего", "Парк развлечений"
            ));
            plugin.getLogger().warning("Темы не найдены в конфиге! Используются дефолтные темы.");
        } else {
            themes.addAll(configThemes);
            plugin.getLogger().info("Загружено " + themes.size() + " тем из конфига.");
        }
    }

    /**
     * Запускает игру с указанной темой (используется после рулетки)
     * @param theme выбранная тема для игры
     */
    public void startGameWithTheme(String theme) {
        if (!canStartGame()) {
            Bukkit.broadcastMessage("§cДля начала игры нужно минимум " + MIN_PLAYERS + " строителя!");
            return;
        }

        currentTheme = theme;
        gameState = GameState.BUILDING;
        timeRemaining = buildTimeInMinutes * 60;
        
        Bukkit.broadcastMessage(String.format(MSG_GAME_START, currentTheme));
        Bukkit.broadcastMessage(String.format(MSG_BUILD_TIME, buildTimeInMinutes));
        
        arenas.forEach((teamName, arena) -> {
            Set<Player> teamPlayers = getTeamPlayers(teamName);
            if (!teamPlayers.isEmpty()) {
                teamPlayers.stream()
                    .filter(Player::isOnline)
                    .forEach(player -> {
                        player.teleport(arena.getSpawnPoint());
                        player.setGameMode(org.bukkit.GameMode.CREATIVE);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        player.sendMessage(MSG_FLOOR_HINT);
                    });
            }
        });
        
        startTimer();
    }

    public void startJudgingPhase() {
        if (gameState != GameState.BUILDING && gameState != GameState.WAITING) {
            return;
        }
        
        gameState = GameState.JUDGING;
        isJudgingPhase = true;
        
        // Останавливаем таймер строительства, если он еще активен
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        
        // Очищаем предыдущие оценки
        for (Map<Player, Integer> scores : teamScores.values()) {
            scores.clear();
        }
        
        // Создаем порядок оценки команд от team1 до team8, но только те, где есть игроки
        teamVotingOrder = new ArrayList<>();
        for (int i = 1; i <= MAX_TEAMS; i++) {
            String teamName = "team" + i;
            Set<String> teamPlayers = teams.get(teamName);
            if (teamPlayers != null && !teamPlayers.isEmpty()) {
                teamVotingOrder.add(teamName);
            }
        }
        
        // Если нет команд для оценки, завершаем игру
        if (teamVotingOrder.isEmpty()) {
            Bukkit.broadcastMessage("§cНет команд для оценки!");
            gameState = GameState.ENDED;
            return;
        }
        
        // Начинаем с первой команды
        currentTeamIndex = -1; // будет увеличено до 0 в методе nextArena
        
        // Замораживаем всех строителей и делаем их невидимыми
        for (Player builder : builders) {
            if (builder.isOnline()) {
                builder.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                // Оставляем режим CREATIVE, но игроки не смогут ломать/ставить блоки (это контролируется в BuildBattleListener)
                builder.setGameMode(org.bukkit.GameMode.CREATIVE);
            }
        }
        
        // Устанавливаем время на оценку
        timeRemaining = judgeTimeInMinutes * 60;
        
        // Объявляем о начале фазы оценки
        Bukkit.broadcastMessage("§6Фаза строительства завершена! Начинается оценка построек!");
        
        // Устанавливаем творческий режим для жюри, чтобы они могли летать вокруг построек
        for (Player judge : judges) {
            if (judge.isOnline()) {
                judge.setGameMode(org.bukkit.GameMode.CREATIVE);
                judge.sendMessage("§aВам выдан творческий режим для осмотра построек!");
            }
        }
        
        // Перемещаем жюри к первой команде
        nextArena();
        
        // Запускаем таймер для оценки
        startTimer();
    }

    /**
     * Перейти к следующей арене для оценки
     */
    public void nextArena() {
        if (!isJudgingPhase || teamVotingOrder == null || teamVotingOrder.isEmpty()) {
            return;
        }
        
        currentTeamIndex++;
        
        // Если все команды оценены, показываем результаты и завершаем игру
        if (currentTeamIndex >= teamVotingOrder.size()) {
            showResults();
            return;
        }
        
        String nextTeam = teamVotingOrder.get(currentTeamIndex);
        BuildArea nextArena = getArena(nextTeam);
        
        if (nextArena == null) {
            // Если арены нет, пропускаем эту команду
            nextArena();
            return;
        }
        
        // Устанавливаем текущую арену
        currentArena = nextArena;
        
        // Получаем точку телепортации
        Location teleportLocation = nextArena.getSpawnPoint();
        
        // Создаем заголовок для всех игроков
        String title = "§6Оценка команды";
        String subtitle = "§e" + getTeamDisplayName(nextTeam);
        
        // Делаем видимыми игроков оцениваемой команды и невидимыми остальных
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                // Если игрок в оцениваемой команде или является жюри
                if (getTeamPlayers(nextTeam).contains(player) || isJudge(player)) {
                    // Удаляем эффект невидимости
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    // Если это игрок оцениваемой команды, добавляем эффект свечения
                    if (getTeamPlayers(nextTeam).contains(player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
                    }
                } else {
                    // Для всех остальных игроков добавляем эффект невидимости
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                }
            }
        }
        
        // Телепортируем ВСЕХ игроков (жюри и строителей) на арену для оценки
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline()) {
                player.teleport(teleportLocation);
                player.sendTitle(title, subtitle, 10, 60, 10);
                
                // Разные сообщения для разных ролей
                if (isJudge(player)) {
                    player.sendMessage("§6Вы оцениваете команду §e" + getTeamDisplayName(nextTeam) + "§6. Используйте §e/buildbattle score <1-10> §6для оценки.");
                } else if (getTeamPlayers(nextTeam).contains(player)) {
                    player.sendMessage("§6Жюри оценивает вашу команду! Удачи!");
                } else {
                    player.sendMessage("§6Жюри оценивает команду §e" + getTeamDisplayName(nextTeam));
                }
                
                // Проигрываем звук телепортации
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            }
        }
        
        // Оповещаем всех игроков в чате
        Bukkit.broadcastMessage("§6§l===== ОЦЕНКА =====");
        Bukkit.broadcastMessage("§6Жюри оценивает команду §e" + getTeamDisplayName(nextTeam));
        
        // Сбрасываем таймер
        timeRemaining = judgeTimeInMinutes * 60;
        
        // Запускаем таймер для текущей арены
        startTimer();
    }

    public void endGame() {
        // Меняем состояние игры
        gameState = GameState.ENDED;
        isJudgingPhase = false;
        
        // Останавливаем таймер, если он запущен
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        
        // Удаляем все эффекты у всех игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
            
            // Очищаем инвентарь и экипировку
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getEnderChest().clear();
        }
        
        // Переводим всех игроков в режим приключения и телепортируем на точку спавна
        if (gatheringPoint != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.teleport(gatheringPoint);
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        } else {
            plugin.getLogger().warning("Точка спавна не установлена! Игроки не будут телепортированы.");
        }
        
        // Отправляем title всем игрокам
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle("§6Игра окончена", "§eСпасибо за участие!", 10, 70, 20);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        }
        
        // Очищаем полы команд
        teamFloors.clear();
        saveTeamFloors();
        
        // Сбрасываем состояние игры
        currentArena = null;
        currentTheme = null;
        
        // Полностью очищаем все блоки на арене и заливаем пол белой терракотой
        for (BuildArea arena : arenas.values()) {
            int minX = Math.min(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            int maxX = Math.max(arena.getCorner1().getBlockX(), arena.getCorner2().getBlockX());
            int minY = Math.min(arena.getCorner1().getBlockY(), arena.getCorner2().getBlockY());
            int maxY = Math.max(arena.getCorner1().getBlockY(), arena.getCorner2().getBlockY());
            int minZ = Math.min(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
            int maxZ = Math.max(arena.getCorner1().getBlockZ(), arena.getCorner2().getBlockZ());
            World world = arena.getCorner1().getWorld();
            // Удаляем все блоки внутри арены
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
            // Заливаем пол белой терракотой на Y=4
            int y = 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.WHITE_TERRACOTTA);
                }
            }
        }
        
        // Сохраняем состояние игроков (их роли и команды)
        savePlayers();
        
        // Через 5 секунд переходим в режим ожидания
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gameState = GameState.WAITING;
            Bukkit.broadcastMessage("§aИгра завершена! Плагин готов к новой игре.");
        }, 100L); // 5 секунд (100 тиков)
    }

    public void createArena(String name, Location corner1, Location corner2) {
        BuildArea arena = new BuildArea(corner1, corner2);
        arenas.put(name, arena);
        saveArenas();
    }

    public BuildArea getArena(String name) {
        return arenas.get(name);
    }

    public void removeArena(String name) {
        arenas.remove(name);
        saveArenas();
    }

    public Set<String> getArenaNames() {
        return arenas.keySet();
    }

    public void saveArenas() {
        config.set("arenas", null);
        ConfigurationSection arenasSection = config.createSection("arenas");

        for (Map.Entry<String, BuildArea> entry : arenas.entrySet()) {
            String name = entry.getKey();
            BuildArea arena = entry.getValue();
            Location corner1 = arena.getCorner1();
            Location corner2 = arena.getCorner2();

            ConfigurationSection arenaSection = arenasSection.createSection(name);
            arenaSection.set("world", corner1.getWorld().getName());
            
            arenaSection.set("corner1.x", corner1.getX());
            arenaSection.set("corner1.y", corner1.getY());
            arenaSection.set("corner1.z", corner1.getZ());
            
            arenaSection.set("corner2.x", corner2.getX());
            arenaSection.set("corner2.y", corner2.getY());
            arenaSection.set("corner2.z", corner2.getZ());
        }

        // После сохранения арен, сохраняем все локации
        saveLocations();
    }

    private void startTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeRemaining <= 0) {
                    // Время вышло
                    this.cancel();
                    timerTask = null;
                    if (gameState == GameState.BUILDING) {
                        // Автоматически переходим к фазе оценки
                        Bukkit.getScheduler().runTask(plugin, () -> startJudgingPhase());
                    } else if (gameState == GameState.JUDGING) {
                        // В фазе оценки не переходим автоматически к следующей команде
                        // Жюри сами решают, когда перейти к следующей команде
                        return;
                    }
                    return;
                }
                // Отображаем оставшееся время в Action Bar только для строителей
                String message = "§6Осталось: §e" + formatTime(timeRemaining);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isBuilder(player)) { // Показываем таймер только строителям
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                    }
                }
                // Звуковые эффекты при приближении к концу времени только для строителей
                if (timeRemaining <= 10) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (isBuilder(player)) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                        }
                    }
                } else if (timeRemaining <= 30) {
                    if (timeRemaining % 5 == 0) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (isBuilder(player)) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f);
                            }
                        }
                    }
                } else if (timeRemaining <= 60) {
                    if (timeRemaining % 10 == 0) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (isBuilder(player)) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                            }
                        }
                    }
                } else {
                    if (timeRemaining % 60 == 0) {
                        int minutes = timeRemaining / 60;
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (isBuilder(player)) {
                                player.sendMessage("§6Осталось §e" + minutes + " §6" + 
                                    (minutes == 1 ? "минута" : (minutes < 5 ? "минуты" : "минут")));
                                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                            }
                        }
                    }
                }
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0, 20); // каждую секунду (20 тиков)
    }
    
    private void playTimerSound(Sound sound, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBuilder(player) || isJudge(player)) {
                player.playSound(player.getLocation(), sound, 1.0f, pitch);
            }
        }
    }
    
    // Форматирует время в формат минуты:секунды
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
    
    // Геттеры и сеттеры для времени
    public void setBuildTime(int minutes) {
        this.buildTimeInMinutes = minutes;
    }
    
    public void setJudgeTime(int minutes) {
        this.judgeTimeInMinutes = minutes;
    }
    
    public String getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * Устанавливает текущую тему для игры
     * @param theme тема для игры
     */
    public void setCurrentTheme(String theme) {
        this.currentTheme = theme;
    }
    
    public void addTheme(String theme) {
        if (!themes.contains(theme)) {
            themes.add(theme);
        }
    }
    
    public void removeTheme(String theme) {
        themes.remove(theme);
    }
    
    public List<String> getThemes() {
        return new ArrayList<>(themes);
    }

    public BuildArea getCurrentArena() {
        return currentArena;
    }
    
    public String getCurrentTeamName() {
        if (currentArena == null) {
            return null;
        }
        
        for (Map.Entry<String, BuildArea> entry : arenas.entrySet()) {
            if (entry.getValue() == currentArena) {
                return entry.getKey();
            }
        }
        
        return null;
    }
    
    /**
     * Получить экземпляр плагина
     * @return экземпляр JavaPlugin
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Добавляет оценку команде от жюри
     * @param teamName имя команды
     * @param judge член жюри
     * @param score оценка (1-10)
     * @return true если оценка успешно добавлена
     */
    public boolean addScore(String teamName, Player judge, int score) {
        if (!isJudge(judge) || !teams.containsKey(teamName) || !isJudgingPhase) {
            return false;
        }
        
        // Проверяем диапазон оценки
        if (score < 1 || score > 10) {
            return false;
        }
        
        // Инициализируем Map для оценок команды, если её ещё нет
        if (!teamScores.containsKey(teamName)) {
            teamScores.put(teamName, new HashMap<>());
        }
        
        // Добавляем или обновляем оценку от этого жюри для команды
        teamScores.get(teamName).put(judge, score);
        
        // Проверяем, все ли жюри уже оценили эту команду
        if (teamScores.get(teamName).size() >= judges.size()) {
            // Рассчитываем среднюю оценку
            double avgScore = getAverageScore(teamName);
            
            // Объявляем всем средний балл за эту команду
            Bukkit.broadcastMessage("§6Все жюри выставили оценки команде §e" + getTeamDisplayName(teamName));
            Bukkit.broadcastMessage("§6Средняя оценка: §a" + String.format("%.1f", avgScore) + " §6баллов");
            
            // Если оценка высокая, проигрываем хороший звук
            Sound scoreSound = avgScore >= 8 ? Sound.ENTITY_PLAYER_LEVELUP : 
                              (avgScore >= 5 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS);
            float pitch = avgScore >= 8 ? 1.0f : (avgScore >= 5 ? 0.8f : 0.5f);
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), scoreSound, 1.0f, pitch);
            }
            
            // Задержка перед переходом к следующей команде
            Bukkit.broadcastMessage("§6Переход к следующей команде через 3 секунды...");
            
            // Подсвечиваем оцениваемую команду эффектами
            Set<Player> teamPlayers = getTeamPlayers(teamName);
            for (Player teamPlayer : teamPlayers) {
                if (teamPlayer.isOnline()) {
                    Location loc = teamPlayer.getLocation();
                    for (int i = 0; i < 10; i++) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            teamPlayer.getWorld().spawnParticle(
                                avgScore >= 8 ? Particle.FLAME : (avgScore >= 5 ? Particle.HEART : Particle.CLOUD),
                                loc.getX(), loc.getY() + 1, loc.getZ(),
                                10, 0.5, 0.5, 0.5, 0.05
                            );
                        }, i * 5);
                    }
                }
            }
            
            // Переходим к следующей команде через 3 секунды
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                nextArena();
            }, 60); // 3 секунды = 60 тиков
        } else {
            // Сообщаем оставшимся жюри, сколько ещё нужно оценок
            int remainingVotes = judges.size() - teamScores.get(teamName).size();
            Bukkit.broadcastMessage("§6Осталось получить §e" + remainingVotes + " §6оценок от жюри");
        }
        
        return true;
    }
    
    /**
     * Рассчитывает среднюю оценку команды
     * @param teamName имя команды
     * @return средняя оценка
     */
    public double getAverageScore(String teamName) {
        if (!teamScores.containsKey(teamName) || teamScores.get(teamName).isEmpty()) {
            return 0.0;
        }
        
        Map<Player, Integer> scores = teamScores.get(teamName);
        if (scores.isEmpty()) {
            return 0.0;
        }
        
        int sum = 0;
        for (int score : scores.values()) {
            sum += score;
        }
        
        return (double) sum / scores.size();
    }
    
    /**
     * Получить количество выставленных оценок для команды
     * @param teamName имя команды
     * @return количество оценок
     */
    public int getVotesCount(String teamName) {
        if (!teamScores.containsKey(teamName)) {
            return 0;
        }
        return teamScores.get(teamName).size();
    }
    
    /**
     * Показывает итоговые результаты голосования
     */
    private void showResults() {
        Bukkit.broadcastMessage("§6§l===== ИТОГОВЫЕ РЕЗУЛЬТАТЫ =====");
        
        // Сортируем команды по средней оценке (от высшей к низшей)
        List<String> sortedTeams = new ArrayList<>(teamVotingOrder);
        sortedTeams.sort((team1, team2) -> Double.compare(getAverageScore(team2), getAverageScore(team1)));
        
        // Выводим результаты
        for (int i = 0; i < sortedTeams.size(); i++) {
            String team = sortedTeams.get(i);
            double score = getAverageScore(team);
            
            // Пропускаем команды без оценок
            if (score <= 0) {
                continue;
            }
            
            String place = (i == 0) ? "§6§l1" : String.valueOf(i + 1);
            String formattedScore = String.format("%.1f", score);
            String displayName = getTeamDisplayName(team);
            
            Bukkit.broadcastMessage("§e" + place + ". §a" + displayName + ": §e" + formattedScore + " §aбаллов");
            
            // Для победителя добавляем эффекты
            if (i == 0) {
                // Находим игроков команды и даем им эффекты
                for (Player player : getTeamPlayers(team)) {
                    if (player.isOnline()) {
                        // Показываем титул победителя
                        player.sendTitle("§6§lПОБЕДА!", "§eВаша команда заняла первое место!", 10, 100, 20);
                        
                        // Проигрываем звук победы
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        
                        // Создаем эффекты частиц вокруг игрока
                        Location loc = player.getLocation();
                        for (int j = 0; j < 20; j++) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                player.getWorld().spawnParticle(Particle.FLAME, 
                                    loc.getX(), loc.getY() + 1, loc.getZ(), 
                                    20, 0.5, 0.5, 0.5, 0.1);
                            }, j * 5);
                        }
                    }
                }
            }
        }
        
        // Завершаем фазу оценки
        isJudgingPhase = false;
        gameState = GameState.ENDED;
        
        // Возвращаем жюри на точку сбора
        if (gatheringPoint != null) {
            for (Player judge : judges) {
                if (judge.isOnline()) {
                    judge.teleport(gatheringPoint);
                }
            }
        }
        
        Bukkit.broadcastMessage("§6§lСоревнование завершено! Поздравляем победителей!");
        // После показа результатов завершаем игру и очищаем площадки
        endGame();
    }

    /**
     * Загружает сохраненные локации из конфигурации
     */
    private void loadLocations() {
        plugin.getLogger().info("Загрузка локаций из конфига...");
        
        ConfigurationSection locationsSection = config.getConfigurationSection("locations");
        if (locationsSection == null) {
            plugin.getLogger().warning("Секция locations не найдена в конфиге!");
            return;
        }
        
        // Загружаем точку сбора
        if (locationsSection.isSet("gathering")) {
            ConfigurationSection gatheringSection = locationsSection.getConfigurationSection("gathering");
            String worldName = gatheringSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                gatheringPoint = new Location(
                    world,
                    gatheringSection.getDouble("x"),
                    gatheringSection.getDouble("y"),
                    gatheringSection.getDouble("z"),
                    (float) gatheringSection.getDouble("yaw"),
                    (float) gatheringSection.getDouble("pitch")
                );
                plugin.getLogger().info("Загружена точка сбора: " + gatheringPoint.toString());
            } else {
                plugin.getLogger().warning("Не удалось загрузить точку сбора - мир не найден: " + worldName);
            }
        }
        
        // Загружаем локацию рулетки
        if (locationsSection.isSet("roulette")) {
            ConfigurationSection rouletteSection = locationsSection.getConfigurationSection("roulette");
            String worldName = rouletteSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                rouletteLocation = new Location(
                    world,
                    rouletteSection.getDouble("x"),
                    rouletteSection.getDouble("y"),
                    rouletteSection.getDouble("z"),
                    (float) rouletteSection.getDouble("yaw"),
                    (float) rouletteSection.getDouble("pitch")
                );
                plugin.getLogger().info("Загружена локация рулетки: " + rouletteLocation.toString());
            } else {
                plugin.getLogger().warning("Не удалось загрузить локацию рулетки - мир не найден: " + worldName);
            }
        } else {
            plugin.getLogger().warning("Локация рулетки не найдена в конфиге!");
        }
        
        // Загружаем точку спавна для рулетки
        if (locationsSection.isSet("rouletteSpawn")) {
            ConfigurationSection rouletteSpawnSection = locationsSection.getConfigurationSection("rouletteSpawn");
            String worldName = rouletteSpawnSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                rouletteSpawnPoint = new Location(
                    world,
                    rouletteSpawnSection.getDouble("x"),
                    rouletteSpawnSection.getDouble("y"),
                    rouletteSpawnSection.getDouble("z"),
                    (float) rouletteSpawnSection.getDouble("yaw"),
                    (float) rouletteSpawnSection.getDouble("pitch")
                );
                plugin.getLogger().info("Загружена точка спавна рулетки: " + rouletteSpawnPoint.toString());
            } else {
                plugin.getLogger().warning("Не удалось загрузить точку спавна рулетки - мир не найден: " + worldName);
            }
        } else {
            plugin.getLogger().warning("Точка спавна рулетки не найдена в конфиге!");
        }

        // Загружаем конечную локацию
        if (locationsSection.isSet("end")) {
            ConfigurationSection endSection = locationsSection.getConfigurationSection("end");
            String worldName = endSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                endLocation = new Location(
                    world,
                    endSection.getDouble("x"),
                    endSection.getDouble("y"),
                    endSection.getDouble("z"),
                    (float) endSection.getDouble("yaw"),
                    (float) endSection.getDouble("pitch")
                );
                plugin.getLogger().info("Загружена конечная локация: " + endLocation.toString());
            } else {
                plugin.getLogger().warning("Не удалось загрузить конечную локацию - мир не найден: " + worldName);
            }
        }
    }

    /**
     * Сохраняет все локации в конфигурацию
     */
    private void saveLocations() {
        // Создаем раздел для локаций в конфигурации
        ConfigurationSection locationsSection = config.createSection("locations");
        
        // Сохраняем точку сбора
        if (gatheringPoint != null) {
            ConfigurationSection gatheringSection = locationsSection.createSection("gathering");
            gatheringSection.set("world", gatheringPoint.getWorld().getName());
            gatheringSection.set("x", gatheringPoint.getX());
            gatheringSection.set("y", gatheringPoint.getY());
            gatheringSection.set("z", gatheringPoint.getZ());
            gatheringSection.set("yaw", gatheringPoint.getYaw());
            gatheringSection.set("pitch", gatheringPoint.getPitch());
            plugin.getLogger().info("Сохранена точка сбора: " + gatheringPoint.toString());
        }
        
        // Сохраняем локацию рулетки
        if (rouletteLocation != null) {
            ConfigurationSection rouletteSection = locationsSection.createSection("roulette");
            rouletteSection.set("world", rouletteLocation.getWorld().getName());
            rouletteSection.set("x", rouletteLocation.getX());
            rouletteSection.set("y", rouletteLocation.getY());
            rouletteSection.set("z", rouletteLocation.getZ());
            rouletteSection.set("yaw", rouletteLocation.getYaw());
            rouletteSection.set("pitch", rouletteLocation.getPitch());
            plugin.getLogger().info("Сохранена локация рулетки: " + rouletteLocation.toString());
        }
        
        // Сохраняем точку спавна для рулетки
        if (rouletteSpawnPoint != null) {
            ConfigurationSection rouletteSpawnSection = locationsSection.createSection("rouletteSpawn");
            rouletteSpawnSection.set("world", rouletteSpawnPoint.getWorld().getName());
            rouletteSpawnSection.set("x", rouletteSpawnPoint.getX());
            rouletteSpawnSection.set("y", rouletteSpawnPoint.getY());
            rouletteSpawnSection.set("z", rouletteSpawnPoint.getZ());
            rouletteSpawnSection.set("yaw", rouletteSpawnPoint.getYaw());
            rouletteSpawnSection.set("pitch", rouletteSpawnPoint.getPitch());
            plugin.getLogger().info("Сохранена точка спавна рулетки: " + rouletteSpawnPoint.toString());
        }

        // Сохраняем конечную локацию
        if (endLocation != null) {
            ConfigurationSection endSection = locationsSection.createSection("end");
            endSection.set("world", endLocation.getWorld().getName());
            endSection.set("x", endLocation.getX());
            endSection.set("y", endLocation.getY());
            endSection.set("z", endLocation.getZ());
            endSection.set("yaw", endLocation.getYaw());
            endSection.set("pitch", endLocation.getPitch());
            plugin.getLogger().info("Сохранена конечная локация: " + endLocation.toString());
        }
        
        try {
            config.save(configFile);
            plugin.getLogger().info("Файл локаций сохранен успешно: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить локации: " + e.getMessage());
        }
    }

    /**
     * Загружает локации рулетки из отдельного файла
     */
    private void loadRouletteLocations() {
        if (!rouletteFile.exists()) {
            plugin.getLogger().info("Файл локаций рулетки не существует, будет создан при сохранении");
            return;
        }
        // Загружаем локацию рулетки
        if (rouletteConfig.isSet("roulette")) {
            ConfigurationSection rouletteSection = rouletteConfig.getConfigurationSection("roulette");
            String worldName = rouletteSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                rouletteLocation = new Location(
                    world,
                    rouletteSection.getDouble("x"),
                    rouletteSection.getDouble("y"),
                    rouletteSection.getDouble("z"),
                    (float) rouletteSection.getDouble("yaw"),
                    (float) rouletteSection.getDouble("pitch")
                );
                plugin.getLogger().info("Локация рулетки успешно загружена.");
            } else {
                plugin.getLogger().warning("Не удалось загрузить локацию рулетки - мир не найден: " + worldName);
            }
        } else {
            plugin.getLogger().warning("Локация рулетки не найдена в файле roulette.yml!");
        }
        // Загружаем точку спавна для рулетки
        if (rouletteConfig.isSet("rouletteSpawn")) {
            ConfigurationSection rouletteSpawnSection = rouletteConfig.getConfigurationSection("rouletteSpawn");
            String worldName = rouletteSpawnSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                rouletteSpawnPoint = new Location(
                    world,
                    rouletteSpawnSection.getDouble("x"),
                    rouletteSpawnSection.getDouble("y"),
                    rouletteSpawnSection.getDouble("z"),
                    (float) rouletteSpawnSection.getDouble("yaw"),
                    (float) rouletteSpawnSection.getDouble("pitch")
                );
                plugin.getLogger().info("Точка спавна рулетки успешно загружена.");
            } else {
                plugin.getLogger().warning("Не удалось загрузить точку спавна рулетки - мир не найден: " + worldName);
            }
        } else {
            plugin.getLogger().warning("Точка спавна рулетки не найдена в файле roulette.yml!");
        }
    }

    /**
     * Сохраняет локации рулетки в отдельный файл
     */
    private void saveRouletteLocations() {
        // Сохраняем локацию рулетки
        if (rouletteLocation != null) {
            ConfigurationSection rouletteSection = rouletteConfig.createSection("roulette");
            rouletteSection.set("world", rouletteLocation.getWorld().getName());
            rouletteSection.set("x", rouletteLocation.getX());
            rouletteSection.set("y", rouletteLocation.getY());
            rouletteSection.set("z", rouletteLocation.getZ());
            rouletteSection.set("yaw", rouletteLocation.getYaw());
            rouletteSection.set("pitch", rouletteLocation.getPitch());
        }
        // Сохраняем точку спавна для рулетки
        if (rouletteSpawnPoint != null) {
            ConfigurationSection rouletteSpawnSection = rouletteConfig.createSection("rouletteSpawn");
            rouletteSpawnSection.set("world", rouletteSpawnPoint.getWorld().getName());
            rouletteSpawnSection.set("x", rouletteSpawnPoint.getX());
            rouletteSpawnSection.set("y", rouletteSpawnPoint.getY());
            rouletteSpawnSection.set("z", rouletteSpawnPoint.getZ());
            rouletteSpawnSection.set("yaw", rouletteSpawnPoint.getYaw());
            rouletteSpawnSection.set("pitch", rouletteSpawnPoint.getPitch());
        }
        try {
            rouletteConfig.save(rouletteFile);
            plugin.getLogger().info("Локации рулетки успешно сохранены.");
        } catch (IOException e) {
            plugin.getLogger().severe("Ошибка при сохранении локаций рулетки: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Устанавливает локацию для 3D рулетки
     * @param location Локация для рулетки
     */
    public void setRouletteLocation(Location location) {
        this.rouletteLocation = location.clone();
        plugin.getLogger().info("Установка локации рулетки: " + location.toString());
        
        // Сохраняем локации рулетки в отдельный файл
        saveRouletteLocations();
    }

    /**
     * Устанавливает точку спавна для игроков перед рулеткой
     * @param location Локация для спавна
     */
    public void setRouletteSpawnPoint(Location location) {
        this.rouletteSpawnPoint = location.clone();
        plugin.getLogger().info("Установка точки спавна рулетки: " + location.toString());
        
        // Сохраняем локации рулетки в отдельный файл
        saveRouletteLocations();
    }

    /**
     * Телепортирует всех игроков к точке спавна рулетки
     */
    public void teleportPlayersToRoulette() {
        if (rouletteSpawnPoint == null) {
            return;
        }

        // Телепортируем всех игроков к рулетке
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(rouletteSpawnPoint);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.setGameMode(org.bukkit.GameMode.CREATIVE); // Устанавливаем креативный режим
            player.sendMessage("§aВы телепортированы к рулетке выбора темы!");
        }

        // Проигрываем звук телепортации
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }
    }

    public Location getRouletteLocation() {
        return rouletteLocation;
    }

    public Location getRouletteSpawnPoint() {
        return rouletteSpawnPoint;
    }

    /**
     * Загружает сохраненные названия команд из конфигурации
     */
    private void loadTeamNames() {
        ConfigurationSection teamNamesSection = config.getConfigurationSection("team_names");
        if (teamNamesSection != null) {
            for (String teamId : teamNamesSection.getKeys(false)) {
                String displayName = teamNamesSection.getString(teamId);
                if (displayName != null) {
                    teamDisplayNames.put(teamId, displayName);
                }
            }
        }
    }

    /**
     * Сохраняет названия команд в конфигурацию
     */
    private void saveTeamNames() {
        ConfigurationSection teamNamesSection = config.createSection("team_names");
        for (Map.Entry<String, String> entry : teamDisplayNames.entrySet()) {
            teamNamesSection.set(entry.getKey(), entry.getValue());
        }
        saveConfig();
    }

    /**
     * Устанавливает отображаемое название для команды
     * @param teamId ID команды (например, "team1")
     * @param displayName Отображаемое название
     */
    public void setTeamDisplayName(String teamId, String displayName) {
        teamDisplayNames.put(teamId, displayName);
        saveTeamNames();
    }

    /**
     * Получает отображаемое название команды
     * @param teamId ID команды
     * @return Отображаемое название или ID команды, если название не установлено
     */
    public String getTeamDisplayName(String teamId) {
        return teamDisplayNames.getOrDefault(teamId, teamId);
    }

    /**
     * Сохраняет конфигурацию
     */
    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить конфигурацию: " + e.getMessage());
        }
    }

    /**
     * Перезагружает конфигурацию плагина
     */
    public void reloadConfig() {
        try {
            // Сохраняем текущих игроков перед перезагрузкой
            savePlayers();
            
            // Перезагружаем основной конфиг
            config.load(configFile);
            
            // Перезагружаем конфиг игроков
            playersConfig.load(playersFile);
            
            // Перезагружаем конфиг рулетки
            rouletteConfig.load(rouletteFile);
            
            // Очищаем текущие данные
            arenas.clear();
            judges.clear();
            builders.clear();
            teams.clear();
            teamScores.clear();
            teamDisplayNames.clear();
            teamFloors.clear();
            themes.clear();
            
            // Загружаем данные заново
            loadArenas();
            loadPlayers();
            loadRouletteLocations();
            loadLocations();
            loadTeamNames();
            loadTeamFloors();
            loadThemes();
            
            // Сбрасываем состояние игры
            gameState = GameState.WAITING;
            currentArena = null;
            currentTheme = null;
            isJudgingPhase = false;
            
            // Останавливаем таймер, если он активен
            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            
            // Оповещаем игроков о перезагрузке
            Bukkit.broadcastMessage("§6Плагин BuildBattle был перезагружен!");
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().severe("Ошибка при перезагрузке конфигурации: " + e.getMessage());
            Bukkit.broadcastMessage("§cОшибка при перезагрузке плагина: " + e.getMessage());
        }
    }

    public void setEndLocation(Location location) {
        this.endLocation = location.clone();
        saveLocations();
    }

    public Location getEndLocation() {
        return endLocation;
    }

    /**
     * Загружает арены из конфигурации
     */
    private void loadArenas() {
        ConfigurationSection arenasSection = config.getConfigurationSection("arenas");
        if (arenasSection == null) return;

        for (String name : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(name);
            if (arenaSection != null) {
                World world = Bukkit.getWorld(arenaSection.getString("world"));
                if (world == null) continue;

                Location corner1 = new Location(
                    world,
                    arenaSection.getDouble("corner1.x"),
                    arenaSection.getDouble("corner1.y"),
                    arenaSection.getDouble("corner1.z")
                );

                Location corner2 = new Location(
                    world,
                    arenaSection.getDouble("corner2.x"),
                    arenaSection.getDouble("corner2.y"),
                    arenaSection.getDouble("corner2.z")
                );

                createArena(name, corner1, corner2);
            }
        }
    }

    /**
     * Получает список названий команд
     * @return множество названий команд
     */
    public Set<String> getTeamNames() {
        return teams.keySet();
    }

    /**
     * Проверяет, находится ли игра в фазе оценки
     * @return true если игра в фазе оценки
     */
    public boolean isJudgingPhase() {
        return isJudgingPhase;
    }

    /**
     * Получает список имён игроков команды (онлайн и оффлайн)
     * @param teamName название команды
     * @return множество имён игроков
     */
    public Set<String> getTeamPlayerNames(String teamName) {
        return teams.getOrDefault(teamName, new HashSet<>());
    }

    public void startRoulette() {
        if (rouletteLocation == null) {
            Bukkit.broadcastMessage("§cЛокация рулетки не установлена!");
            return;
        }

        // Замораживаем всех игроков и делаем их невидимыми
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setWalkSpeed(0);
            player.setFlySpeed(0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.setCollidable(false); // Отключаем коллизию
            player.setInvulnerable(true); // Делаем неуязвимым
            player.teleport(rouletteLocation.clone().add(0, 2, 0));
        }

        // Создаем бутылочку
        Location bottleLoc = rouletteLocation.clone().add(0, ROTATION_HEIGHT, 0);
        org.bukkit.entity.Item item = rouletteLocation.getWorld().dropItem(bottleLoc, new org.bukkit.inventory.ItemStack(Material.POTION));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setGravity(false);
        item.setInvulnerable(true);

        // Запускаем анимацию вращения
        final double[] angle = {0};
        rouletteTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (angle[0] >= 360 * 5) { // 5 полных оборотов
                    this.cancel();
                    rouletteTask = null;
                    // Выбираем случайную тему
                    String selectedTheme = themes.get(new Random().nextInt(themes.size()));
                    // Удаляем бутылочку
                    item.remove();
                    // Размораживаем игроков и делаем их видимыми
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        player.setWalkSpeed(0.2f);
                        player.setFlySpeed(0.1f);
                        player.setCollidable(true); // Включаем коллизию обратно
                        player.setInvulnerable(false); // Отключаем неуязвимость
                    }
                    // Начинаем игру с выбранной темой
                    startGameWithTheme(selectedTheme);
                    return;
                }

                // Обновляем позицию бутылочки
                double x = ROTATION_RADIUS * Math.cos(Math.toRadians(angle[0]));
                double z = ROTATION_RADIUS * Math.sin(Math.toRadians(angle[0]));
                item.teleport(rouletteLocation.clone().add(x, ROTATION_HEIGHT, z));
                
                // Вращаем бутылочку
                item.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                item.setRotation((float)angle[0], 0);

                // Добавляем эффекты частиц
                rouletteLocation.getWorld().spawnParticle(
                    Particle.END_ROD,
                    item.getLocation(),
                    1, 0, 0, 0, 0
                );

                angle[0] += 5; // Скорость вращения
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Оповещаем игроков
        Bukkit.broadcastMessage("§6§lРулетка запущена!");
        Bukkit.broadcastMessage("§eОжидайте, пока бутылочка выберет тему...");
    }

    public void stopRoulette() {
        if (rouletteTask != null) {
            rouletteTask.cancel();
            rouletteTask = null;
        }
    }
} 
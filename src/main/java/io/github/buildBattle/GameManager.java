package io.github.buildBattle;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameManager {
    private final BuildBattle plugin;
    private GameState gameState;
    private List<Team> teams;
    private List<String> selectedThemes;
    private ThemeVoting themeVoting;
    private int currentRound;
    private int buildTime;
    private Map<Team, Integer> scores;
    private Map<Integer, BuildArea> teamArenas;
    private Location gatheringPoint;

    public GameManager(BuildBattle plugin) {
        this.plugin = plugin;
        this.gameState = GameState.WAITING;
        this.teams = new ArrayList<>();
        this.selectedThemes = new ArrayList<>();
        this.currentRound = 0;
        this.scores = new HashMap<>();
        this.teamArenas = new HashMap<>();
    }

    public void setGatheringPoint(Location location) {
        this.gatheringPoint = location;
    }

    public void setTeamArena(int teamIndex, Location corner1, Location corner2) {
        if (teamIndex < 0 || teamIndex >= 8) {
            throw new IllegalArgumentException("Индекс команды должен быть от 0 до 7");
        }
        BuildArea buildArea = new BuildArea(corner1, corner2);
        teamArenas.put(teamIndex, buildArea);
    }

    public void startGame() {
        if (gameState != GameState.WAITING) return;
        
        // Проверяем, установлены ли все арены
        if (teamArenas.size() < 8) {
            Bukkit.broadcastMessage("§cНе все арены установлены! Установлено: " + teamArenas.size() + "/8");
            return;
        }

        if (gatheringPoint == null) {
            Bukkit.broadcastMessage("§cТочка сбора не установлена!");
            return;
        }

        List<String> availableThemes = plugin.getThemes();
        if (availableThemes.size() < 3) {
            Bukkit.broadcastMessage("§cНедостаточно тем для голосования! Нужно минимум 3 темы.");
            return;
        }
        
        // Телепортируем всех игроков в точку сбора
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(gatheringPoint);
        }
        
        Bukkit.broadcastMessage("§aВсе игроки телепортированы в точку сбора!");
        Bukkit.broadcastMessage("§eНачинается голосование за тему!");
        
        gameState = GameState.SELECTING_THEME;
        currentRound = 1;
        selectRandomThemes();
        startThemeVoting();
    }

    private void selectRandomThemes() {
        List<String> allThemes = new ArrayList<>(plugin.getThemes());
        Collections.shuffle(allThemes);
        selectedThemes = allThemes.subList(0, Math.min(3, allThemes.size()));
        
        // Объявляем темы для голосования
        Bukkit.broadcastMessage("§6===== Темы для голосования =====");
        for (int i = 0; i < selectedThemes.size(); i++) {
            Bukkit.broadcastMessage("§e" + (i + 1) + ". " + selectedThemes.get(i));
        }
        Bukkit.broadcastMessage("§6Используйте команду §e/buildbattle vote <тема> §6для голосования");
    }

    private void startThemeVoting() {
        themeVoting = new ThemeVoting(plugin, selectedThemes);
        
        // Запускаем таймер для автоматического завершения голосования
        new BukkitRunnable() {
            @Override
            public void run() {
                if (themeVoting.isVotingActive()) {
                    themeVoting.endVoting();
                    distributePlayersToTeams();
                    startBuildRound();
                }
            }
        }.runTaskLater(plugin, 60 * 20L); // 60 секунд на голосование
    }

    public boolean voteForTheme(Player player, String theme) {
        if (gameState != GameState.SELECTING_THEME) {
            player.sendMessage("§cСейчас не время для голосования!");
            return false;
        }
        return themeVoting.vote(player, theme);
    }

    private void distributePlayersToTeams() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        
        for (int i = 0; i < 8; i++) {
            Team team = new Team("Команда " + (i + 1));
            team.setCurrentTheme(themeVoting.getSelectedTheme());
            
            // Устанавливаем заранее созданную арену для команды
            BuildArea arena = teamArenas.get(i);
            if (arena != null) {
                team.setBuildArea(arena);
                arena.protectArea();
            }
            
            for (int j = 0; j < 3; j++) {
                if (!players.isEmpty()) {
                    team.addPlayer(players.remove(0));
                }
            }
            teams.add(team);
        }

        teleportTeamsToAreas();
    }

    private void teleportTeamsToAreas() {
        for (Team team : teams) {
            team.teleportToBuildArea();
        }
        Bukkit.broadcastMessage("§aКоманды телепортированы на свои арены!");
    }

    public boolean isInTeamArea(Player player, Location location) {
        for (Team team : teams) {
            if (team.getPlayers().contains(player) && team.isInBuildArea(location)) {
                return true;
            }
        }
        return false;
    }

    public boolean canBreakBlock(Player player, Location location) {
        for (Team team : teams) {
            if (team.getPlayers().contains(player) && team.isInBuildArea(location)) {
                BuildArea area = team.getBuildArea();
                return area != null && !area.isOriginalBlock(location);
            }
        }
        return false;
    }

    private void startBuildRound() {
        buildTime = getBuildTimeForRound();
        gameState = GameState.BUILDING;
        
        Bukkit.broadcastMessage("§6Начался раунд " + currentRound + "!");
        Bukkit.broadcastMessage("§eТема: " + themeVoting.getSelectedTheme());
        Bukkit.broadcastMessage("§eВремя на строительство: " + buildTime + " минут");
        
        new BukkitRunnable() {
            @Override
            public void run() {
                endBuildRound();
            }
        }.runTaskLater(plugin, buildTime * 20L * 60L);
    }

    private int getBuildTimeForRound() {
        switch (currentRound) {
            case 1: return 1;
            case 2: return 3;
            case 3: return 5;
            default: return 0;
        }
    }

    private void endBuildRound() {
        gameState = GameState.VOTING;
        Bukkit.broadcastMessage("§6Время на строительство закончилось!");
        Bukkit.broadcastMessage("§eНачинается оценка построек!");
        
        // Здесь будет логика для голосования жюри
    }

    public void addScore(Team team, int score) {
        scores.put(team, scores.getOrDefault(team, 0) + score);
    }

    public GameState getGameState() {
        return gameState;
    }

    public enum GameState {
        WAITING,
        SELECTING_THEME,
        BUILDING,
        VOTING,
        FINISHED
    }
} 
package io.github.buildBattle;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ThemeVoting {
    private final BuildBattle plugin;
    private final List<String> themes;
    private final Map<String, Integer> votes;
    private final Set<Player> votedPlayers;
    private boolean isVotingActive;
    private String selectedTheme;

    public ThemeVoting(BuildBattle plugin, List<String> themes) {
        this.plugin = plugin;
        this.themes = themes;
        this.votes = new HashMap<>();
        this.votedPlayers = new HashSet<>();
        this.isVotingActive = true;
        
        // Инициализация голосов для каждой темы
        for (String theme : themes) {
            votes.put(theme, 0);
        }
    }

    public boolean vote(Player player, String theme) {
        if (!isVotingActive) {
            player.sendMessage("§cГолосование уже завершено!");
            return false;
        }

        if (votedPlayers.contains(player)) {
            player.sendMessage("§cВы уже проголосовали!");
            return false;
        }

        if (!themes.contains(theme)) {
            player.sendMessage("§cТакой темы нет в списке!");
            return false;
        }

        votes.put(theme, votes.get(theme) + 1);
        votedPlayers.add(player);
        player.sendMessage("§aВы проголосовали за тему: " + theme);
        
        // Проверяем, все ли игроки проголосовали
        if (votedPlayers.size() >= Bukkit.getOnlinePlayers().size()) {
            endVoting();
        }
        
        return true;
    }

    public void endVoting() {
        isVotingActive = false;
        
        // Находим тему с наибольшим количеством голосов
        String winningTheme = themes.stream()
                .max(Comparator.comparingInt(votes::get))
                .orElse(themes.get(0));
        
        selectedTheme = winningTheme;
        
        // Отправляем результаты всем игрокам
        Bukkit.broadcastMessage("§6===== Результаты голосования =====");
        for (String theme : themes) {
            Bukkit.broadcastMessage("§e" + theme + ": §a" + votes.get(theme) + " голосов");
        }
        Bukkit.broadcastMessage("§6Победила тема: §a" + winningTheme);
    }

    public boolean isVotingActive() {
        return isVotingActive;
    }

    public String getSelectedTheme() {
        return selectedTheme;
    }

    public List<String> getThemes() {
        return new ArrayList<>(themes);
    }
} 
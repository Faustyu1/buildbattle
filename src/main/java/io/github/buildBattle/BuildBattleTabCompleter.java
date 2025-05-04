package io.github.buildBattle;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BuildBattleTabCompleter implements TabCompleter {
    private final BuildBattleGame game;

    public BuildBattleTabCompleter(BuildBattleGame game) {
        this.game = game;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Основные команды
            completions.addAll(List.of("judge", "start", "judging", "vote", "end", "setgathering", "setarena", "score"));
            
            // Команды для жюри
            if (sender instanceof Player && game.isJudge((Player) sender)) {
                completions.addAll(List.of("tp", "teleport"));
            }
            
            // Команды для админов
            if (sender.hasPermission("buildbattle.admin")) {
                completions.addAll(List.of("team", "teams", "setbuildtime", "setjudgetime", "theme", "setspawnarena", "test",
                    "setroulette", "setroulettespawn", "startroulette"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "judge":
                    completions.addAll(List.of("add", "remove"));
                    break;
                case "team":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(List.of("add", "remove"));
                    }
                    break;
                case "vote":
                    completions.add("done");
                    break;
                case "tp":
                case "teleport":
                    if (sender instanceof Player && game.isJudge((Player) sender)) {
                        completions.addAll(game.getArenaNames());
                    }
                    break;
                case "setarena":
                    if (sender.hasPermission("buildbattle.admin")) {
                        for (int i = 1; i <= 8; i++) {
                            completions.add(String.valueOf(i));
                        }
                    }
                    break;
                case "score":
                    completions.addAll(game.getTeamNames());
                    break;
                case "setbuildtime":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(List.of("5", "10", "15", "20", "30", "45", "60"));
                    }
                    break;
                case "setjudgetime":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(List.of("1", "3", "5", "10", "15", "20", "30"));
                    }
                    break;
                case "theme":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(List.of("add", "remove", "list"));
                    }
                    break;
                case "setspawnarena":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(game.getTeamNames());
                    }
                    break;
                case "test":
                    if (sender.hasPermission("buildbattle.admin")) {
                        completions.addAll(List.of("1", "2", "holo", "3d"));
                    }
                    break;
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("team") && sender.hasPermission("buildbattle.admin")) {
                if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) {
                    // Предлагаем имена онлайн игроков
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
            } else if (args[0].equalsIgnoreCase("score")) {
                // Предлагаем оценки от 1 до 10
                for (int i = 1; i <= 10; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if (args[0].equalsIgnoreCase("theme") && args[1].equalsIgnoreCase("remove") && sender.hasPermission("buildbattle.admin")) {
                // Предлагаем существующие темы для удаления
                completions.addAll(game.getThemes());
            } else if (args[0].equalsIgnoreCase("judge") && sender.hasPermission("buildbattle.admin")) {
                if (args[1].equalsIgnoreCase("add")) {
                    // Предлагаем игроков, которые не являются жюри
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !game.isJudge(p))
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                } else if (args[1].equalsIgnoreCase("remove")) {
                    // Предлагаем только игроков, которые являются жюри
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .filter(game::isJudge)
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("team") && args[1].equalsIgnoreCase("add") && sender.hasPermission("buildbattle.admin")) {
                // Предлагаем названия команд
                completions.addAll(game.getTeamNames());
            }
        }

        // Фильтруем подсказки по введенному тексту
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
} 
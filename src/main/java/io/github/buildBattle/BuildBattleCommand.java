package io.github.buildBattle;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuildBattleCommand implements CommandExecutor {
    private final BuildBattle plugin;

    public BuildBattleCommand(BuildBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (player.hasPermission("buildbattle.admin")) {
                    plugin.getGameManager().startGame();
                } else {
                    player.sendMessage("§cУ вас нет прав для запуска игры!");
                }
                break;

            case "setgathering":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки точки сбора!");
                    return true;
                }
                plugin.getGameManager().setGatheringPoint(player.getLocation());
                player.sendMessage("§aТочка сбора установлена!");
                break;

            case "setarena":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки арены!");
                    return true;
                }
                if (args.length != 8) {
                    player.sendMessage("§cИспользование: /buildbattle setarena <номер_команды> <x1> <y1> <z1> <x2> <y2> <z2>");
                    return true;
                }
                try {
                    int teamNumber = Integer.parseInt(args[1]);
                    if (teamNumber < 1 || teamNumber > 8) {
                        player.sendMessage("§cНомер команды должен быть от 1 до 8!");
                        return true;
                    }

                    Location corner1 = new Location(
                        player.getWorld(),
                        Double.parseDouble(args[2]),
                        Double.parseDouble(args[3]),
                        Double.parseDouble(args[4])
                    );

                    Location corner2 = new Location(
                        player.getWorld(),
                        Double.parseDouble(args[5]),
                        Double.parseDouble(args[6]),
                        Double.parseDouble(args[7])
                    );

                    plugin.getGameManager().setTeamArena(teamNumber - 1, corner1, corner2);
                    player.sendMessage("§aАрена для команды " + teamNumber + " установлена!");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cВсе координаты должны быть числами!");
                }
                break;

            case "vote":
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /buildbattle vote <тема>");
                    return true;
                }
                // Здесь будет логика голосования за темы
                break;

            case "score":
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /buildbattle score <команда> <баллы>");
                    return true;
                }
                // Здесь будет логика выставления оценок жюри
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6===== Build Battle Help =====");
        player.sendMessage("§e/buildbattle start §7- Начать игру");
        player.sendMessage("§e/buildbattle setgathering §7- Установить точку сбора");
        player.sendMessage("§e/buildbattle setarena <номер> <x1> <y1> <z1> <x2> <y2> <z2> §7- Установить арену для команды");
        player.sendMessage("§e/buildbattle vote <тема> §7- Проголосовать за тему");
        player.sendMessage("§e/buildbattle score <команда> <баллы> §7- Выставить оценку команде");
    }
} 
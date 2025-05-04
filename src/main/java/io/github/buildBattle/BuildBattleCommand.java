package io.github.buildBattle;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.Listener;
import org.bukkit.World;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class BuildBattleCommand implements CommandExecutor {
    private final BuildBattleGame game;

    public BuildBattleCommand(BuildBattleGame game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Эта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "judge":
                if (args.length == 2) {
                    if (args[1].equalsIgnoreCase("add")) {
                        game.addJudge(player);
                        player.sendMessage("Вы теперь жюри!");
                    } else if (args[1].equalsIgnoreCase("remove")) {
                        game.removeJudge(player);
                        player.sendMessage("Вы больше не жюри!");
                    }
                } else if (args.length == 3) {
                    // Назначение жюри по имени игрока (только для администраторов)
                    if (!player.hasPermission("buildbattle.admin")) {
                        player.sendMessage("§cУ вас нет прав для назначения жюри!");
                        return true;
                    }
                    
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage("§cИгрок " + args[2] + " не найден или не в сети!");
                        return true;
                    }
                    
                    if (args[1].equalsIgnoreCase("add")) {
                        game.addJudge(target);
                        player.sendMessage("§aИгрок " + target.getName() + " назначен жюри!");
                        target.sendMessage("§aВы были назначены жюри!");
                    } else if (args[1].equalsIgnoreCase("remove")) {
                        game.removeJudge(target);
                        player.sendMessage("§aИгрок " + target.getName() + " больше не жюри!");
                        target.sendMessage("§aВы больше не жюри!");
                    }
                }
                break;
            case "team":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для управления командами!");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cИспользование: /buildbattle team <add/remove/name> <игрок/номер> [команда/название]");
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("name")) {
                    // Обработка команды изменения названия
                    if (args.length < 4) {
                        player.sendMessage("§cИспользование: /buildbattle team name <номер> <название>");
                        return true;
                    }
                    
                    try {
                        int teamNumber = Integer.parseInt(args[2]);
                        if (teamNumber < 1 || teamNumber > 8) {
                            player.sendMessage("§cНомер команды должен быть от 1 до 8!");
                            return true;
                        }
                        
                        // Собираем название команды из оставшихся аргументов
                        StringBuilder nameBuilder = new StringBuilder();
                        for (int i = 3; i < args.length; i++) {
                            if (i > 3) nameBuilder.append(" ");
                            nameBuilder.append(args[i]);
                        }
                        String teamName = nameBuilder.toString();
                        
                        // Устанавливаем новое название
                        String teamId = "team" + teamNumber;
                        game.setTeamDisplayName(teamId, teamName);
                        
                        player.sendMessage("§aНазвание команды §e" + teamId + " §aизменено на §e" + teamName);
                        
                        // Оповещаем всех игроков
                        Bukkit.broadcastMessage("§6Команда §e" + teamId + " §6теперь называется §e" + teamName);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cНомер команды должен быть числом!");
                    }
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cИгрок не найден!");
                    return true;
                }
                if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 4) {
                        player.sendMessage("§cУкажите команду: /buildbattle team add <игрок> <команда>");
                        return true;
                    }
                    String teamName = args[3].toLowerCase();
                    if (!teamName.startsWith("team")) {
                        teamName = "team" + teamName;
                    }
                    if (game.addPlayerToTeam(target, teamName)) {
                        player.sendMessage("§aИгрок " + target.getName() + " добавлен в команду " + teamName);
                        target.sendMessage("§aВы добавлены в команду " + teamName);
                    } else {
                        player.sendMessage("§cКоманда не найдена!");
                    }
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (game.removePlayerFromTeam(target)) {
                        player.sendMessage("§aИгрок " + target.getName() + " удален из команды");
                        target.sendMessage("§aВы удалены из команды");
                    } else {
                        player.sendMessage("§cИгрок не состоит ни в одной команде!");
                    }
                }
                break;
            case "teams":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для просмотра команд!");
                    return true;
                }
                player.sendMessage("§6=== Список команд ===");
                for (String teamName : game.getTeamNames()) {
                    Set<String> playerNames = game.getTeamPlayerNames(teamName);
                    String displayName = game.getTeamDisplayName(teamName);
                    if (displayName.equals(teamName)) {
                        player.sendMessage("§e" + teamName + ": §7" +
                            (playerNames.isEmpty() ? "нет игроков" : String.join(", ", playerNames)));
                    } else {
                        player.sendMessage("§e" + teamName + " (§f" + displayName + "§e): §7" +
                            (playerNames.isEmpty() ? "нет игроков" : String.join(", ", playerNames)));
                    }
                }
                break;
            case "start":
                if (!game.canStartGame()) {
                    player.sendMessage("§cДля начала игры нужно минимум 2 строителя!");
                    return true;
                }
                
                // Проверяем, установлены ли нужные локации для рулетки
                if (game.getRouletteLocation() == null) {
                    player.sendMessage("§cСначала установите локацию для рулетки: /buildbattle setroulette");
                    return true;
                }
                
                if (game.getRouletteSpawnPoint() == null) {
                    player.sendMessage("§cСначала установите точку спавна для рулетки: /buildbattle setroulettespawn");
                    return true;
                }
                
                // Телепортируем всех игроков к рулетке
                game.teleportPlayersToRoulette();
                
                // Получаем список доступных тем
                List<String> startThemes = game.getThemes();
                if (startThemes.isEmpty()) {
                    player.sendMessage("§cНет доступных тем для рулетки!");
                    return true;
                }
                
                // Запускаем 3D рулетку
                create3DRouletteAtLocation(game.getRouletteLocation(), startThemes);
                
                player.sendMessage("§aИгра началась! Запущена рулетка выбора темы!");
                break;
            case "judging":
                if (game.isJudge(player)) {
                    if (!game.canStartGame()) {
                        player.sendMessage("§cДля начала оценки нужно минимум 2 строителя!");
                        return true;
                    }
                    game.startJudgingPhase();
                    player.sendMessage("Фаза оценки началась!");
                } else {
                    player.sendMessage("Только жюри может начать фазу оценки!");
                }
                break;
            case "vote":
                if (args.length == 2 && args[1].equalsIgnoreCase("done")) {
                    if (game.isJudge(player)) {
                        game.nextArena();
                        player.sendMessage("Переход к следующей арене!");
                    } else {
                        player.sendMessage("Только жюри может завершить голосование!");
                    }
                }
                break;
            case "end":
                if (!player.hasPermission("buildbattle.admin") && !game.isJudge(player)) {
                    player.sendMessage("§cТолько администраторы и жюри могут завершить игру!");
                    return true;
                }
                game.endGame();
                Bukkit.broadcastMessage("§6" + player.getName() + " §eзавершил игру!");
                break;
            case "setgathering":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки точки сбора!");
                    return true;
                }
                game.setGatheringPoint(player.getLocation());
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

                    game.setTeamArena(teamNumber - 1, corner1, corner2);
                    player.sendMessage("§aАрена для команды " + teamNumber + " установлена!");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cВсе координаты должны быть числами!");
                }
                break;

            case "tp":
            case "teleport":
                if (!game.isJudge(player)) {
                    player.sendMessage("§cТолько жюри может использовать эту команду!");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§cУкажите команду для телепортации: /buildbattle tp <команда>");
                    return true;
                }
                
                String targetTeam = args[1].toLowerCase();
                if (!targetTeam.startsWith("team")) {
                    targetTeam = "team" + targetTeam;
                }
                
                BuildArea arena = game.getArena(targetTeam);
                if (arena == null) {
                    player.sendMessage("§cКоманда или арена не найдена!");
                    return true;
                }
                
                Location center = arena.calculateCenter();
                player.teleport(center);
                player.sendMessage("§aВы телепортированы на арену команды " + targetTeam);
                break;

            case "setbuildtime":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для изменения времени строительства!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cУкажите время в минутах: /buildbattle setbuildtime <минуты>");
                    return true;
                }
                try {
                    int minutes = Integer.parseInt(args[1]);
                    if (minutes < 1 || minutes > 60) {
                        player.sendMessage("§cВремя должно быть от 1 до 60 минут!");
                        return true;
                    }
                    game.setBuildTime(minutes);
                    player.sendMessage("§aВремя строительства установлено: " + minutes + " минут");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cУкажите корректное число!");
                }
                break;

            case "setjudgetime":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для изменения времени оценки!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cУкажите время в минутах: /buildbattle setjudgetime <минуты>");
                    return true;
                }
                try {
                    int minutes = Integer.parseInt(args[1]);
                    if (minutes < 1 || minutes > 30) {
                        player.sendMessage("§cВремя должно быть от 1 до 30 минут!");
                        return true;
                    }
                    game.setJudgeTime(minutes);
                    player.sendMessage("§aВремя оценки установлено: " + minutes + " минут");
                } catch (NumberFormatException e) {
                    player.sendMessage("§cУкажите корректное число!");
                }
                break;

            case "theme":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для управления темами!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cИспользование: /buildbattle theme <add/remove/list> [тема]");
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("list")) {
                    List<String> themes = game.getThemes();
                    player.sendMessage("§6=== Список тем ===");
                    if (themes.isEmpty()) {
                        player.sendMessage("§cНет доступных тем");
                    } else {
                        for (int i = 0; i < themes.size(); i++) {
                            player.sendMessage("§e" + (i + 1) + ". §7" + themes.get(i));
                        }
                    }
                    return true;
                }
                
                if (args.length < 3) {
                    player.sendMessage("§cУкажите тему: /buildbattle theme <add/remove> <тема>");
                    return true;
                }
                
                // Соединяем все аргументы, начиная с третьего, чтобы получить полное название темы
                StringBuilder themeBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i > 2) themeBuilder.append(" ");
                    themeBuilder.append(args[i]);
                }
                String theme = themeBuilder.toString();
                
                if (args[1].equalsIgnoreCase("add")) {
                    game.addTheme(theme);
                    player.sendMessage("§aТема добавлена: " + theme);
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (game.getThemes().contains(theme)) {
                        game.removeTheme(theme);
                        player.sendMessage("§aТема удалена: " + theme);
                    } else {
                        player.sendMessage("§cТема не найдена!");
                    }
                } else {
                    player.sendMessage("§cИспользование: /buildbattle theme <add/remove/list> [тема]");
                }
                break;

            case "setspawnarena":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки точки спавна арены!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cУкажите команду: /buildbattle setspawnarena <команда>");
                    return true;
                }
                
                String arenaName = args[1].toLowerCase();
                if (!arenaName.startsWith("team")) {
                    arenaName = "team" + arenaName;
                }
                
                BuildArea targetArena = game.getArena(arenaName);
                if (targetArena == null) {
                    player.sendMessage("§cАрена не найдена!");
                    return true;
                }
                
                // Проверяем, находится ли игрок в области арены
                if (!targetArena.isInArea(player.getLocation())) {
                    player.sendMessage("§cВы должны находиться в области арены, чтобы установить точку спавна!");
                    return true;
                }
                
                // Устанавливаем точку спавна
                targetArena.setSpawnPoint(player.getLocation());
                player.sendMessage("§aТочка спавна для арены " + arenaName + " установлена!");
                break;

            case "score":
                if (!game.isJudge(player)) {
                    player.sendMessage("§cТолько жюри может оценивать постройки!");
                    return true;
                }
                
                if (!game.isJudgingPhase()) {
                    player.sendMessage("§cСейчас не время для оценки!");
                    return true;
                }
                
                if (args.length != 2) {
                    player.sendMessage("§cИспользование: /buildbattle score <1-10>");
                    return true;
                }
                
                try {
                    int score = Integer.parseInt(args[1]);
                    String currentTeam = game.getCurrentTeamName();
                    
                    if (currentTeam == null) {
                        player.sendMessage("§cСейчас нет активной команды для оценки!");
                        return true;
                    }
                    
                    if (game.addScore(currentTeam, player, score)) {
                        player.sendMessage("§aВы оценили команду §e" + currentTeam + "§a на §e" + score + "§a баллов!");
                    } else {
                        player.sendMessage("§cОценка должна быть от 1 до 10!");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cОценка должна быть числом от 1 до 10!");
                }
                return true;

            case "test":
                // Эта команда больше не нужна, так как мы используем стационарную рулетку
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для запуска тестовой рулетки!");
                    return true;
                }
                
                player.sendMessage("§cЭта команда устарела. Используйте вместо нее:");
                player.sendMessage("§e/buildbattle setroulette §7- установить место для рулетки");
                player.sendMessage("§e/buildbattle setroulettespawn §7- установить точку спавна для рулетки");
                player.sendMessage("§e/buildbattle startroulette §7- запустить рулетку и телепортировать игроков");
                break;

            case "setroulette":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки локации рулетки!");
                    return true;
                }
                game.setRouletteLocation(player.getLocation());
                player.sendMessage("§aЛокация для 3D рулетки установлена!");
                break;

            case "setroulettespawn":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для установки точки спавна для рулетки!");
                    return true;
                }
                game.setRouletteSpawnPoint(player.getLocation());
                player.sendMessage("§aТочка спавна для рулетки установлена!");
                break;

            case "startroulette":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для запуска рулетки!");
                    return true;
                }
                
                // Проверяем, установлены ли нужные локации
                if (game.getRouletteLocation() == null) {
                    player.sendMessage("§cСначала установите локацию для рулетки: /buildbattle setroulette");
                    return true;
                }

                if (game.getRouletteSpawnPoint() == null) {
                    player.sendMessage("§cСначала установите точку спавна для рулетки: /buildbattle setroulettespawn");
                    return true;
                }
                
                List<String> availableThemes = game.getThemes();
                if (availableThemes.isEmpty()) {
                    player.sendMessage("§cНет доступных тем для рулетки!");
                    return true;
                }
                
                // Телепортируем всех игроков к рулетке
                game.teleportPlayersToRoulette();
                
                // Запускаем 3D рулетку на установленной позиции
                create3DRouletteAtLocation(game.getRouletteLocation(), availableThemes);
                
                player.sendMessage("§aРулетка запущена!");
                break;

            case "setfloor":
                // Проверяем, находится ли игрок в команде
                String playerTeam = game.getPlayerTeam(player);
                if (playerTeam == null) {
                    player.sendMessage("§cВы должны быть в команде, чтобы установить пол!");
                    return true;
                }
                // Проверяем, держит ли игрок блок в руке
                Material heldItem = player.getInventory().getItemInMainHand().getType();
                if (!heldItem.isBlock()) {
                    player.sendMessage("§cДержите блок в руке, чтобы установить его как пол!");
                    return true;
                }
                // Меняем пол на территории арены только на высоте Y=4
                BuildArea teamArena = game.getArena(playerTeam);
                if (teamArena != null) {
                    int minX = Math.min(teamArena.getCorner1().getBlockX(), teamArena.getCorner2().getBlockX());
                    int maxX = Math.max(teamArena.getCorner1().getBlockX(), teamArena.getCorner2().getBlockX());
                    int minZ = Math.min(teamArena.getCorner1().getBlockZ(), teamArena.getCorner2().getBlockZ());
                    int maxZ = Math.max(teamArena.getCorner1().getBlockZ(), teamArena.getCorner2().getBlockZ());
                    int y = 4;
                    World world = teamArena.getCorner1().getWorld();
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            world.getBlockAt(x, y, z).setType(heldItem);
                        }
                    }
                }
                // Оповещаем игрока
                player.sendMessage("§aПол для вашей команды установлен на §e" + heldItem.name());
                // Оповещаем всех игроков команды
                for (Player teamPlayer : game.getTeamPlayers(playerTeam)) {
                    if (teamPlayer.isOnline() && !teamPlayer.equals(player)) {
                        teamPlayer.sendMessage("§6" + player.getName() + " §eустановил пол для команды на §6" + heldItem.name());
                    }
                }
                return true;

            case "reload":
                if (!player.hasPermission("buildbattle.admin")) {
                    player.sendMessage("§cУ вас нет прав для перезагрузки плагина!");
                    return true;
                }
                
                // Проверяем, не идет ли сейчас игра
                if (game.getGameState() != BuildBattleGame.GameState.WAITING) {
                    player.sendMessage("§cНельзя перезагрузить плагин во время игры!");
                    return true;
                }
                
                // Перезагружаем конфигурацию
                try {
                    game.reloadConfig();
                    player.sendMessage("§aПлагин успешно перезагружен!");
                    
                    // Оповещаем всех игроков
                    Bukkit.broadcastMessage("§6Плагин BuildBattle был перезагружен администратором.");
                    
                    // Проигрываем звук успешной перезагрузки
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                    }
                } catch (Exception e) {
                    player.sendMessage("§cОшибка при перезагрузке плагина: " + e.getMessage());
                    game.getPlugin().getLogger().severe("Ошибка при перезагрузке плагина: " + e.getMessage());
                }
                return true;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== BuildBattle Помощь ===");
        player.sendMessage("§e/buildbattle judge add §7- стать жюри");
        player.sendMessage("§e/buildbattle judge remove §7- перестать быть жюри");
        if (player.hasPermission("buildbattle.admin")) {
            player.sendMessage("§6=== Команды администратора ===");
            player.sendMessage("§e/buildbattle judge add <игрок> §7- назначить игрока жюри");
            player.sendMessage("§e/buildbattle judge remove <игрок> §7- удалить игрока из жюри");
            player.sendMessage("§e/buildbattle team add <игрок> <команда> §7- добавить игрока в команду");
            player.sendMessage("§e/buildbattle team remove <игрок> §7- удалить игрока из команды");
            player.sendMessage("§e/buildbattle teams §7- список команд и их игроков");
            player.sendMessage("§e/buildbattle setarena <номер> <x1> <y1> <z1> <x2> <y2> <z2> §7- установить арену");
            player.sendMessage("§e/buildbattle setspawnarena <team> §7- установить точку спавна арены");
            player.sendMessage("§e/buildbattle setgathering §7- установить точку сбора");
            player.sendMessage("§e/buildbattle setbuildtime <минут> §7- установить время строительства");
            player.sendMessage("§e/buildbattle setjudgetime <минут> §7- установить время оценки");
            player.sendMessage("§e/buildbattle theme add <тема> §7- добавить тему");
            player.sendMessage("§e/buildbattle theme remove <тема> §7- удалить тему");
            player.sendMessage("§e/buildbattle theme list §7- список тем");
            player.sendMessage("§e/buildbattle start §7- начать игру");
            player.sendMessage("§e/buildbattle end §7- завершить игру");
            player.sendMessage("§e/buildbattle setroulette §7- установить место для рулетки");
            player.sendMessage("§e/buildbattle setroulettespawn §7- установить точку спавна для рулетки");
            player.sendMessage("§e/buildbattle startroulette §7- запустить рулетку и телепортировать игроков");
            player.sendMessage("§e/buildbattle reload §7- перезагрузить плагин");
        }
        if (game.isJudge(player) || player.hasPermission("buildbattle.admin")) {
            player.sendMessage("§6=== Команды жюри ===");
            player.sendMessage("§e/buildbattle judging §7- начать фазу оценки");
            player.sendMessage("§e/buildbattle score <команда> <1-10> §7- оценить команду");
            player.sendMessage("§e/buildbattle vote done §7- перейти к следующей арене");
            player.sendMessage("§e/buildbattle tp <команда> §7- телепортироваться к арене команды");
        }
    }
    
    /**
     * Создает 3D рулетку в указанной локации
     * @param center локация для рулетки
     * @param themes список тем для рулетки
     */
    private void create3DRouletteAtLocation(final Location center, List<String> themes) {
        final World world = center.getWorld();
        final JavaPlugin plugin = game.getPlugin();
        final Random random = new Random();
        final int themeCount = themes.size();
        final double radius = 6.0;
        final int y = center.getBlockY() + 4;
        final int centerX = center.getBlockX();
        final int centerZ = center.getBlockZ();

        // Сохраняем арморстенды для тем
        List<ArmorStand> themeStands = new ArrayList<>();

        // Ставим темы по кругу
        for (int i = 0; i < themeCount; i++) {
            double angle = 2 * Math.PI * i / themeCount;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);
            Location themeLoc = new Location(world, x + 0.5, y, z + 0.5);
            ArmorStand stand = world.spawn(themeLoc, ArmorStand.class);
            stand.setCustomName("§e" + themes.get(i));
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setVisible(false);
            stand.setSmall(true);
            themeStands.add(stand);
        }

        // Ставим бутылочку в центр круга
        Location bottleLoc = new Location(world, centerX + 0.5, y + 0.2, centerZ + 0.5);
        ArmorStand bottle = world.spawn(bottleLoc, ArmorStand.class);
        bottle.setVisible(false);
        bottle.setSmall(true);
        bottle.setGravity(false);
        bottle.setCustomName("§aБутылочка");
        bottle.setCustomNameVisible(true);
        bottle.setHelmet(new org.bukkit.inventory.ItemStack(Material.GLASS_BOTTLE));
        bottle.setRightArmPose(new org.bukkit.util.EulerAngle(0, 0, 0)); // Вертикально

        // Воспроизводим звук
        world.playSound(center, Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        Bukkit.broadcastMessage("§6Крутим бутылочку! Ожидайте...");

        // Вращение бутылочки по кругу
        new BukkitRunnable() {
            int ticks = 0;
            int spinTicks = 60 + random.nextInt(80); // 3-7 секунд
            double currentAngle = 0;
            double speed = 0.35 + random.nextDouble() * 0.15; // радиан/тик
            double deceleration = 0.98 + random.nextDouble() * 0.01;
            int selectedIndex = -1;

            @Override
            public void run() {
                if (ticks < spinTicks) {
                    // Вращаем бутылочку по кругу
                    currentAngle += speed;
                    speed *= deceleration;
                    // Координаты бутылочки на окружности
                    double bx = centerX + radius * Math.cos(currentAngle);
                    double bz = centerZ + radius * Math.sin(currentAngle);
                    Location newLoc = new Location(world, bx + 0.5, y + 0.2, bz + 0.5);
                    bottle.teleport(newLoc);
                    // Поворачиваем бутылочку так, чтобы "носик" указывал наружу
                    float yaw = (float) Math.toDegrees(-currentAngle) + 90;
                    bottle.setRotation(yaw, 0f);
                    // Частицы
                    if (ticks % 5 == 0) {
                        world.spawnParticle(Particle.CRIT, newLoc.clone().add(0, 0.5, 0), 5, 0.1, 0.1, 0.1, 0.01);
                        world.playSound(newLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                    }
                    ticks++;
                } else {
                    // Останавливаем вращение
                    // Определяем, на какую тему указывает бутылочка
                    double normalizedAngle = (currentAngle % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                    int index = (int) Math.round(normalizedAngle / (2 * Math.PI) * themeCount) % themeCount;
                    if (index < 0) index += themeCount;
                    selectedIndex = index;
                    // Ставим бутылочку напротив выбранной темы
                    double angle = 2 * Math.PI * selectedIndex / themeCount;
                    double bx = centerX + radius * Math.cos(angle);
                    double bz = centerZ + radius * Math.sin(angle);
                    Location finalLoc = new Location(world, bx + 0.5, y + 0.2, bz + 0.5);
                    bottle.teleport(finalLoc);
                    float yaw = (float) Math.toDegrees(-angle) + 90;
                    bottle.setRotation(yaw, 0f);
                    // Эффекты и сообщение
                    ArmorStand selectedStand = themeStands.get(selectedIndex);
                    Location themeLoc = selectedStand.getLocation();
                    world.spawnParticle(Particle.HAPPY_VILLAGER, themeLoc.clone().add(0, 0.5, 0), 30, 0.2, 0.2, 0.2, 0.05);
                    world.playSound(themeLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    Bukkit.broadcastMessage("§aБутылочка выбрала тему: §e" + themes.get(selectedIndex));
                    // Удаляем арморстенды через 3 секунды
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (ArmorStand s : themeStands) s.remove();
                            bottle.remove();
                        }
                    }.runTaskLater(plugin, 60);
                    // Запускаем игру с выбранной темой через 2 секунды
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            game.setCurrentTheme(themes.get(selectedIndex));
                            if (game.canStartGame()) {
                                game.startGameWithTheme(themes.get(selectedIndex));
                            }
                        }
                    }.runTaskLater(plugin, 40);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
} 
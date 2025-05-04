package io.github.buildBattle;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildBattleListener implements Listener {
    private final BuildBattleGame game;
    private final BuildBattleCommand commandHandler;
    
    // Для хранения ожидающих выбора типа рулетки
    private final Map<Player, List<String>> pendingRouletteChoices = new HashMap<>();

    public BuildBattleListener(BuildBattleGame game, BuildBattleCommand commandHandler) {
        this.game = game;
        this.commandHandler = commandHandler;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (game.getGameState() == BuildBattleGame.GameState.BUILDING) {
            if (!game.isBuilder(player)) {
                event.setCancelled(true);
                return;
            }

            Location blockLoc = event.getBlock().getLocation();
            String playerTeam = game.getPlayerTeam(player);
            BuildArea playerArea = game.getArena(playerTeam);

            if (playerArea != null && !playerArea.isInArea(blockLoc)) {
                // Игрок пытается ставить блоки вне своей зоны
                event.setCancelled(true);
                player.sendMessage("§cВы можете ставить блоки только в своей зоне!");
            }
        } else if (game.getGameState() == BuildBattleGame.GameState.JUDGING) {
            // Во время фазы оценки только жюри может ставить блоки (для осмотра)
            if (!game.isJudge(player)) {
                event.setCancelled(true);
                if (game.isBuilder(player)) {
                    player.sendMessage("§cВы не можете ставить блоки во время фазы оценки!");
                }
            }
        } else if (game.getGameState() != BuildBattleGame.GameState.WAITING) {
            // В других фазах игры запрещаем ставить блоки
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (game.getGameState() == BuildBattleGame.GameState.BUILDING) {
            if (!game.isBuilder(player)) {
                event.setCancelled(true);
                return;
            }

            Location blockLoc = event.getBlock().getLocation();
            String playerTeam = game.getPlayerTeam(player);
            BuildArea playerArea = game.getArena(playerTeam);

            if (playerArea != null && !playerArea.isInArea(blockLoc)) {
                // Игрок пытается ломать блоки вне своей зоны
                event.setCancelled(true);
                player.sendMessage("§cВы можете ломать блоки только в своей зоне!");
            }
        } else if (game.getGameState() == BuildBattleGame.GameState.JUDGING) {
            // Во время фазы оценки только жюри может ломать блоки (для осмотра)
            if (!game.isJudge(player)) {
                event.setCancelled(true);
                if (game.isBuilder(player)) {
                    player.sendMessage("§cВы не можете ломать блоки во время фазы оценки!");
                }
            } else {
                // Проверяем, не пытается ли жюри ломать блоки у команды, которую оценивает
                Location blockLoc = event.getBlock().getLocation();
                String currentTeam = game.getCurrentTeamName();
                if (currentTeam != null) {
                    BuildArea currentArena = game.getArena(currentTeam);
                    if (currentArena != null && currentArena.isInArea(blockLoc)) {
                        event.setCancelled(true);
                        player.sendMessage("§cВы не можете ломать блоки у команды, которую оцениваете!");
                    }
                }
            }
        } else if (game.getGameState() != BuildBattleGame.GameState.WAITING) {
            // В других фазах игры запрещаем ломать блоки
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Если игра активна (строительство или оценка)
        if (game.getGameState() == BuildBattleGame.GameState.BUILDING || 
            game.getGameState() == BuildBattleGame.GameState.JUDGING) {
            
            // Проверяем, был ли игрок жюри
            if (game.isJudge(player)) {
                player.sendMessage("§aВы вернулись как член жюри!");
                
                // Если идет фаза оценки, телепортируем игрока к текущей арене и выдаём CREATIVE
                if (game.isJudgingPhase() && game.getCurrentArena() != null) {
                    player.teleport(game.getCurrentArena().getSpawnPoint());
                    player.setGameMode(org.bukkit.GameMode.CREATIVE);
                    
                    // Сообщаем, какую команду оценивает жюри
                    String currentTeam = game.getCurrentTeamName();
                    if (currentTeam != null) {
                        player.sendMessage("§6Вы оцениваете постройку команды §e" + currentTeam);
                        player.sendTitle("§6Оценка", "§eКоманда: " + currentTeam, 10, 40, 10);
                    }
                }
            } 
            // Проверяем, был ли игрок строителем
            else if (game.isBuilder(player)) {
                String teamName = game.getPlayerTeam(player);
                
                if (teamName != null) {
                    BuildArea playerArea = game.getArena(teamName);
                    
                    if (playerArea != null) {
                        player.sendMessage("§aВы вернулись в свою команду §e" + teamName + "§a!");
                        
                        // Телепортируем игрока на его площадку
                        player.teleport(playerArea.getSpawnPoint());
                        
                        // Если фаза строительства, выдаем CREATIVE режим
                        if (game.getGameState() == BuildBattleGame.GameState.BUILDING) {
                            player.setGameMode(org.bukkit.GameMode.CREATIVE);
                            player.sendMessage("§aВам выдан творческий режим для строительства!");
                        }
                        // Если фаза оценки, делаем игрока невидимым и оставляем режим CREATIVE
                        else if (game.isJudgingPhase()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                            player.setGameMode(org.bukkit.GameMode.CREATIVE);
                        }
                        
                        // Показываем тему
                        String currentTheme = game.getCurrentTheme();
                        if (currentTheme != null) {
                            player.sendMessage("§aТекущая тема: §e" + currentTheme);
                            player.sendTitle("§6BuildBattle", "§eТема: " + currentTheme, 10, 60, 20);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Не удаляем игрока из списка жюри или команды при выходе
        // Это позволит восстановить его статус при повторном входе
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (game.getGameState() == BuildBattleGame.GameState.BUILDING) {
            Player player = event.getPlayer();
            
            // Проверяем только если игрок изменил координаты X, Y или Z
            // Это предотвращает срабатывание события при повороте головы
            if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
                return;
            }
            
            // Жюри может свободно перемещаться
            if (game.isJudge(player)) {
                return;
            }
            
            if (game.isBuilder(player)) {
                Location to = event.getTo();
                String playerTeam = game.getPlayerTeam(player);
                
                // Если игрок не в команде, не ограничиваем его движение
                if (playerTeam == null) {
                    return;
                }
                
                BuildArea playerArea = game.getArena(playerTeam);
                
                if (playerArea != null && !playerArea.isInArea(to)) {
                    // Игрок пытается выйти за пределы своей зоны
                    event.setCancelled(true);
                    player.sendMessage("§cВы не можете выходить за пределы своей зоны строительства!");
                }
            }
        }
    }

    /**
     * Регистрирует игрока для выбора типа рулетки
     * @param player игрок, который должен выбрать тип рулетки
     * @param themes список тем для рулетки
     */
    public void registerForRouletteChoice(Player player, List<String> themes) {
        pendingRouletteChoices.put(player, themes);
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем, ожидает ли игрок выбора типа рулетки
        if (pendingRouletteChoices.containsKey(player)) {
            String message = event.getMessage().trim();
            
            // Проверяем, является ли сообщение числом 1 или 2
            if (message.equals("1") || message.equals("2")) {
                event.setCancelled(true); // Отменяем отправку сообщения в чат
                
                final List<String> themes = pendingRouletteChoices.get(player);
                
                // Удаляем игрока из списка ожидающих
                pendingRouletteChoices.remove(player);
                
                // Запускаем обработку выбора в основном потоке
                game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
                    // Сообщаем игроку о выборе, но не вызываем несуществующий метод
                    player.sendMessage("§aВаш выбор: " + message);
                });
            }
        }
    }
} 
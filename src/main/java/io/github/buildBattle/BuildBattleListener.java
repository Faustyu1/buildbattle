package io.github.buildBattle;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class BuildBattleListener implements Listener {
    private final BuildBattle plugin;

    public BuildBattleListener(BuildBattle plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getGameManager().getGameState() != GameManager.GameState.BUILDING) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getGameManager().isInTeamArea(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы можете строить только на своей площадке!");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().getGameState() != GameManager.GameState.BUILDING) {
            event.setCancelled(true);
            return;
        }

        if (!plugin.getGameManager().canBreakBlock(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cВы не можете ломать оригинальные блоки арены!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Здесь будет логика для обработки выхода игрока из игры
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Предотвращаем выход игроков за пределы их площадок во время строительства
        if (plugin.getGameManager().getGameState() == GameManager.GameState.BUILDING) {
            if (!plugin.getGameManager().isInTeamArea(event.getPlayer(), event.getTo())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cВы не можете покидать свою площадку во время строительства!");
            }
        }
    }
} 
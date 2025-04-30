package io.github.buildBattle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BuildArea {
    private final Location corner1;
    private final Location corner2;
    private final World world;
    private final List<Location> buildBlocks;
    private final Set<Location> originalBlocks;
    private boolean isProtected;

    public BuildArea(Location corner1, Location corner2) {
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.world = corner1.getWorld();
        this.buildBlocks = new ArrayList<>();
        this.originalBlocks = new HashSet<>();
        this.isProtected = false;
        calculateBuildBlocks();
        saveOriginalBlocks();
    }

    private void calculateBuildBlocks() {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    buildBlocks.add(new Location(world, x, y, z));
                }
            }
        }
    }

    private void saveOriginalBlocks() {
        for (Location loc : buildBlocks) {
            if (loc.getBlock().getType() != Material.AIR) {
                originalBlocks.add(loc);
            }
        }
    }

    public boolean isInArea(Location location) {
        return buildBlocks.contains(location);
    }

    public boolean isOriginalBlock(Location location) {
        return originalBlocks.contains(location);
    }

    public void protectArea() {
        isProtected = true;
        createWalls();
    }

    private void createWalls() {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX()) - 1;
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX()) + 1;
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ()) - 1;
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ()) + 1;

        // Создаем стены из стекла
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                world.getBlockAt(x, y, minZ).setType(Material.GLASS);
                world.getBlockAt(x, y, maxZ).setType(Material.GLASS);
            }
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(minX, y, z).setType(Material.GLASS);
                world.getBlockAt(maxX, y, z).setType(Material.GLASS);
            }
        }
    }

    public void clearArea() {
        for (Location loc : buildBlocks) {
            if (!originalBlocks.contains(loc)) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    public void teleportTeam(Team team) {
        Location center = calculateCenter();
        for (Player player : team.getPlayers()) {
            player.teleport(center);
        }
    }

    private Location calculateCenter() {
        int centerX = (corner1.getBlockX() + corner2.getBlockX()) / 2;
        int centerY = corner1.getBlockY();
        int centerZ = (corner1.getBlockZ() + corner2.getBlockZ()) / 2;
        return new Location(world, centerX, centerY, centerZ);
    }

    public boolean isProtected() {
        return isProtected;
    }
} 
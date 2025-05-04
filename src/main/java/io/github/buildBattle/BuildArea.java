package io.github.buildBattle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.util.*;

@SerializableAs("BuildArea")
public class BuildArea implements ConfigurationSerializable {
    private static final Material WALL_MATERIAL = Material.GLASS;
    private static final Material AIR_MATERIAL = Material.AIR;
    private static final int WALL_OFFSET = 1;
    
    private final Location corner1;
    private final Location corner2;
    private final World world;
    private final List<Location> buildBlocks;
    private final Set<Location> originalBlocks;
    private boolean isProtected;
    private Location spawnPoint;
    
    private int minX, maxX, minY, maxY, minZ, maxZ;

    public BuildArea(Location corner1, Location corner2) {
        if (corner1 == null || corner2 == null) {
            throw new IllegalArgumentException("Corner locations cannot be null");
        }
        if (!corner1.getWorld().equals(corner2.getWorld())) {
            throw new IllegalArgumentException("Corners must be in the same world");
        }
        
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.world = corner1.getWorld();
        this.buildBlocks = new ArrayList<>();
        this.originalBlocks = new HashSet<>();
        this.isProtected = false;
        
        calculateBounds();
        this.spawnPoint = calculateCenter();
        calculateBuildBlocks();
        saveOriginalBlocks();
    }

    public BuildArea(Map<String, Object> map) {
        this.world = (World) map.get("world");
        this.corner1 = (Location) map.get("corner1");
        this.corner2 = (Location) map.get("corner2");
        
        if (this.world == null || this.corner1 == null || this.corner2 == null) {
            throw new IllegalArgumentException("Required map values cannot be null");
        }
        
        this.buildBlocks = new ArrayList<>();
        this.originalBlocks = new HashSet<>();
        this.isProtected = (boolean) map.getOrDefault("isProtected", false);
        this.spawnPoint = (Location) map.getOrDefault("spawnPoint", null);
        
        calculateBounds();
        calculateBuildBlocks();
        saveOriginalBlocks();
        
        if (this.spawnPoint == null) {
            this.spawnPoint = calculateCenter();
        }
    }

    private void calculateBounds() {
        this.minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        this.maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        this.minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        this.maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        this.minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        this.maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("world", world);
        map.put("corner1", corner1);
        map.put("corner2", corner2);
        map.put("isProtected", isProtected);
        if (spawnPoint != null) {
            map.put("spawnPoint", spawnPoint);
        }
        return map;
    }

    public static BuildArea deserialize(Map<String, Object> map) {
        return new BuildArea(map);
    }

    public Location getCorner1() {
        return corner1;
    }

    public Location getCorner2() {
        return corner2;
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(Location spawnPoint) {
        if (spawnPoint != null && isInArea(spawnPoint)) {
            this.spawnPoint = spawnPoint.clone();
        }
    }

    public Location calculateCenter() {
        return new Location(
            world,
            (corner1.getBlockX() + corner2.getBlockX()) / 2.0,
            corner1.getBlockY(),
            (corner1.getBlockZ() + corner2.getBlockZ()) / 2.0
        );
    }

    private void calculateBuildBlocks() {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    buildBlocks.add(new Location(world, x, y, z));
                }
            }
        }
    }

    private void saveOriginalBlocks() {
        buildBlocks.stream()
            .filter(loc -> loc.getBlock().getType() != AIR_MATERIAL)
            .forEach(originalBlocks::add);
    }

    public boolean isInArea(Location location) {
        if (location == null || !location.getWorld().equals(world)) {
            return false;
        }
        
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }

    public boolean isOriginalBlock(Location location) {
        return location != null && originalBlocks.contains(location);
    }

    public void protectArea() {
        if (!isProtected) {
            isProtected = true;
            createWalls();
        }
    }

    private void createWalls() {
        int wallMinX = minX - WALL_OFFSET;
        int wallMaxX = maxX + WALL_OFFSET;
        int wallMinZ = minZ - WALL_OFFSET;
        int wallMaxZ = maxZ + WALL_OFFSET;

        for (int y = minY; y <= maxY; y++) {
            // Создаем стены по X
            for (int x = wallMinX; x <= wallMaxX; x++) {
                world.getBlockAt(x, y, wallMinZ).setType(WALL_MATERIAL);
                world.getBlockAt(x, y, wallMaxZ).setType(WALL_MATERIAL);
            }
            // Создаем стены по Z
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                world.getBlockAt(wallMinX, y, z).setType(WALL_MATERIAL);
                world.getBlockAt(wallMaxX, y, z).setType(WALL_MATERIAL);
            }
        }
    }

    public void clearArea() {
        buildBlocks.stream()
            .filter(loc -> !originalBlocks.contains(loc))
            .forEach(loc -> loc.getBlock().setType(AIR_MATERIAL));
    }

    public void teleportTeam(Team team) {
        if (team != null) {
            Location center = calculateCenter();
            team.getPlayers().forEach(player -> player.teleport(center));
        }
    }

    public boolean isProtected() {
        return isProtected;
    }
} 
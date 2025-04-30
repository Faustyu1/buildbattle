package io.github.buildBattle;

import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class Team {
    private final String name;
    private final List<Player> players;
    private Location buildLocation;
    private String currentTheme;
    private BuildArea buildArea;

    public Team(String name) {
        this.name = name;
        this.players = new ArrayList<>();
    }

    public void addPlayer(Player player) {
        if (players.size() < 3) {
            players.add(player);
        }
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public String getName() {
        return name;
    }

    public void setBuildLocation(Location location) {
        this.buildLocation = location;
    }

    public Location getBuildLocation() {
        return buildLocation;
    }

    public void setCurrentTheme(String theme) {
        this.currentTheme = theme;
    }

    public String getCurrentTheme() {
        return currentTheme;
    }

    public boolean isFull() {
        return players.size() >= 3;
    }

    public void setBuildArea(BuildArea buildArea) {
        this.buildArea = buildArea;
    }

    public BuildArea getBuildArea() {
        return buildArea;
    }

    public void teleportToBuildArea() {
        if (buildArea != null) {
            buildArea.teleportTeam(this);
        }
    }

    public boolean isInBuildArea(Location location) {
        return buildArea != null && buildArea.isInArea(location);
    }
} 
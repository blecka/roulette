package bg.roulette.model;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RouletteTable {

    public enum TableState {
        WAITING,
        SPINNING,
        RESULT
    }

    private final String name;
    private final Location location;
    private int size;

    private TableState state = TableState.WAITING;
    private final Map<UUID, Bet> activeBets = new HashMap<>();
    private final List<Location> placedBlocks = new ArrayList<>();

    private TextDisplay hologramEntity;
    private BukkitTask activeTask;
    private int remainingSeconds;
    private int currentSpinNumber;
    private int winningNumber;

    public RouletteTable(String name, Location location, int size) {
        this.name = name;
        this.location = location;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public TableState getState() {
        return state;
    }

    public void setState(TableState state) {
        this.state = state;
    }

    public Map<UUID, Bet> getActiveBets() {
        return activeBets;
    }

    public void addBet(Bet bet) {
        activeBets.put(bet.getPlayerUuid(), bet);
    }

    public boolean hasPlayerBet(UUID uuid) {
        return activeBets.containsKey(uuid);
    }

    public void clearBets() {
        activeBets.clear();
    }

    public List<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public TextDisplay getHologramEntity() {
        return hologramEntity;
    }

    public void setHologramEntity(TextDisplay hologramEntity) {
        this.hologramEntity = hologramEntity;
    }

    public BukkitTask getActiveTask() {
        return activeTask;
    }

    public void setActiveTask(BukkitTask activeTask) {
        if (this.activeTask != null && !this.activeTask.isCancelled()) {
            this.activeTask.cancel();
        }
        this.activeTask = activeTask;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int getCurrentSpinNumber() {
        return currentSpinNumber;
    }

    public void setCurrentSpinNumber(int currentSpinNumber) {
        this.currentSpinNumber = currentSpinNumber;
    }

    public int getWinningNumber() {
        return winningNumber;
    }

    public void setWinningNumber(int winningNumber) {
        this.winningNumber = winningNumber;
    }
}

package bg.roulette.model;

import java.util.UUID;

public class Bet {

    public enum BetTargetType {
        RED,
        BLACK,
        GREEN,
        NUMBER
    }

    private final UUID playerUuid;
    private final String playerName;
    private final double amount;
    private final BetTargetType targetType;
    private final int targetNumber; // used if targetType == NUMBER (0 - 36)

    public Bet(UUID playerUuid, String playerName, double amount, BetTargetType targetType, int targetNumber) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.amount = amount;
        this.targetType = targetType;
        this.targetNumber = targetNumber;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getAmount() {
        return amount;
    }

    public BetTargetType getTargetType() {
        return targetType;
    }

    public int getTargetNumber() {
        return targetNumber;
    }

    public String getFormattedTarget(String redText, String blackText, String greenText, String numberTemplate) {
        return switch (targetType) {
            case RED -> redText;
            case BLACK -> blackText;
            case GREEN -> greenText;
            case NUMBER -> {
                String colorCode = (targetNumber == 0) ? "&a" : (isRed(targetNumber) ? "&c" : "&8");
                yield numberTemplate.replace("{color}", colorCode).replace("{number}", String.valueOf(targetNumber));
            }
        };
    }

    private boolean isRed(int number) {
        return switch (number) {
            case 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36 -> true;
            default -> false;
        };
    }
}

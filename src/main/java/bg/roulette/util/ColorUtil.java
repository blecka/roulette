package bg.roulette.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.text.DecimalFormat;

public class ColorUtil {

    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat INT_FORMAT = new DecimalFormat("0");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");

    /**
     * Formats monetary amounts based on whether integer currency is enabled.
     */
    public static String formatMoney(double amount, boolean useIntegerCurrency) {
        if (useIntegerCurrency) {
            return INT_FORMAT.format(Math.round(amount));
        } else {
            return DECIMAL_FORMAT.format(amount);
        }
    }

    /**
     * Converts a string with color codes ('&' or '§') to an Adventure Component.
     */
    public static Component component(String message) {
        if (message == null) {
            return Component.empty();
        }
        return SECTION_SERIALIZER.deserialize(color(message));
    }

    /**
     * Translates '&' color codes to '§' for legacy String methods.
     */
    public static String color(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}

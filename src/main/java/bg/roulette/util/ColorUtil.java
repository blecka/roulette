package bg.roulette.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public class ColorUtil {

    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Converts a string with '&' color codes to an Adventure Component.
     */
    public static Component component(String message) {
        if (message == null) {
            return Component.empty();
        }
        return AMPERSAND_SERIALIZER.deserialize(message);
    }

    /**
     * Translates '&' color codes for legacy String methods.
     */
    public static String color(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}

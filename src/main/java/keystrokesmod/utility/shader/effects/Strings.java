package keystrokesmod.utility.shader.effects;

import keystrokesmod.module.impl.client.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public final class Strings {
    private static final Minecraft mc = Minecraft.getInstance();
    public static final char SECTION = '§';
    public static final char HEART = '❤';
    public static final String RED = "§c";
    public static final String GOLD = "§6";
    public static final String YELLOW = "§e";
    public static final String GREEN = "§a";
    public static final String DARK_GREEN = "§2";

    private Strings() {}

    public static float health(LivingEntity e) {
        return e.getHealth() + e.getAbsorption();
    }

    public static double round(double d, int n) {
        if (n == 0) {
            return Math.round(d);
        }

        double d2 = Math.pow(10.0, n);
        return Math.round(d * d2) / d2;
    }

    public static void heart(StringBuilder sb) {
        sb.append('§');
        sb.append('c');
        sb.append('❤');
    }

    public static StringBuilder reset(StringBuilder sb) {
        sb.append('§').append('r').append(' ');
        return sb;
    }

    public static String health(double d, double d2) {
        String prefix = d < 0.3 ? "§c" : (d < 0.5 ? "§6" : (d < 0.7 ? "§e" : "§a"));
        if (Settings.showHealthAsHearts.isToggled()) {
            d2 /= 2.0;
        }

        String s = formatNumber(round(d2, 1));
        StringBuilder sb = new StringBuilder(16);
        sb.append(prefix);
        sb.append(s);
        if (Settings.showHeartSymbol.isToggled()) {
            heart(sb);
        }

        return sb.toString();
    }

    public static String health(PlayerEntity p) {
        float f = health((LivingEntity) p);
        return health(Math.min(f / p.getMaxHealth(), 1.0F), f);
    }

    public static String healthDifference(float f, boolean heart) {
        String prefix = f >= 8.0F ? "§2" : (f > 2.0F ? "§a" : (f > -2.0F ? "§e" : (f > -6.0F ? "§6" : "§c")));
        if (Settings.showHealthAsHearts.isToggled()) {
            f /= 2.0F;
        }

        String s = formatNumber(round(f, 1));
        StringBuilder sb = new StringBuilder(16);
        sb.append(prefix);
        if (f >= 0.0F) {
            sb.append('+');
        }

        sb.append(s);
        if (heart && Settings.showHeartSymbol.isToggled()) {
            heart(sb);
        }

        return sb.toString();
    }

    public static int healthColor(double d) {
        return d < 0.3 ? -55770 : (d < 0.5 ? -12250 : (d < 0.7 ? -218 : -14221530));
    }

    public static String formatNumber(double d) {
        return d == Math.floor(d) ? Long.toString((long) d) : Double.toString(d);
    }
}

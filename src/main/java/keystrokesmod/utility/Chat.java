package keystrokesmod.utility;

import keystrokesmod.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.text.LiteralText;

public final class Chat {
    private static final Minecraft mc = Minecraft.getInstance();
    public static final String COLOR = "§";
    public static final String PREFIX = "&7[&dF&7]&r ";
    public static final char SECTION = '§';

    private Chat() {}

    public static boolean inWorld() {
        return mc.player != null && mc.world != null;
    }

    public static String color(String s) {
        return s.isEmpty() ? s : s.replace("&", "§");
    }

    public static String strip(String s) {
        if (s != null && !s.isEmpty()) {
            StringBuilder sb = new StringBuilder(s.length());
            boolean skip = false;

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (skip) {
                    skip = false;
                } else if (c == 167) {
                    skip = true;
                } else if (c > 20 && c < 127) {
                    sb.append(c);
                }
            }

            return sb.toString();
        } else {
            return "";
        }
    }

    public static void print(String s) {
        if (inWorld()) {
            mc.player.sendMessage(new LiteralText(color("&7[&dF&7]&r " + s)));
        }
    }

    public static void moduleMessage(Module m, String s) {
        print("&b" + m.getName() + "&7: &r" + s);
    }

    public static void raw(String s) {
        if (inWorld()) {
            mc.player.sendMessage(new LiteralText(color(s)));
        }
    }
}

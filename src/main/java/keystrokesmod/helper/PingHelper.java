package keystrokesmod.helper;

import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;

/** Estimates ping by sending an unknown command and timing the "Unknown command" reply (.ping). */
public class PingHelper {
    private static boolean waiting = false;
    private static long sendTime = 0L;

    @ForgeEvents.SubscribeEvent
    public void onChatMessageRecieved(ForgeEvents.ClientChatReceivedEvent event) {
        if (waiting
                && Utils.nullCheck()
                && Utils.stripColor(event.message.getString()).startsWith("Unknown")) {
            event.setCanceled(true);
            int ping = Math.max(0, (int) (System.currentTimeMillis() - sendTime) - 20);
            Utils.sendMessage("&7Your ping: &b" + ping + "&7ms.");
            reset();
        }
    }

    public static void checkPing() {
        Utils.sendMessage("&7Checking ping...");
        if (waiting) {
            Utils.sendMessage("&7Please wait before checking again.");
        } else {
            Utils.mc.player.sendChat("/...");
            waiting = true;
            sendTime = System.currentTimeMillis();
        }
    }

    public static void reset() {
        waiting = false;
        sendTime = 0L;
    }
}

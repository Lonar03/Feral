// Ported from Meowtils (wtf.tatp.meowtils), modified for Meower and Feral
package keystrokesmod.module.impl.other;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.Utils;

/** Switches Hypixel's chat channel to party when you join a party and back to all when it's gone. */
public class AutoChannel extends Module {
    public AutoChannel() {
        super("AutoChannel", Module.category.other);
        this.registerSetting(new DescriptionSetting("Swaps /chat party <-> all."));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || e.message == null || !Utils.isHypixel()) {
            return;
        }

        String msg = Utils.stripColor(e.message.getString()).trim();
        // Player chat always has a colon; party announcements don't.
        if (msg.contains(":")) {
            return;
        }

        if (msg.startsWith("You have joined") && msg.endsWith("party!") || msg.endsWith("joined the party.")) {
            mc.player.sendChat("/chat party");
        } else if (msg.endsWith("has disbanded the party!")
                || msg.equals("The party was disbanded because all invites expired and the party was empty.")
                || msg.equals("You left the party.")
                || msg.startsWith("You have been kicked from the party")
                || msg.equals("You are not in a party right now.")) {
            mc.player.sendChat("/chat all");
        }
    }
}

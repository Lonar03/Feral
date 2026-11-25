package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.player.PlayerEntity;

public class Track extends Command {
    private String tracking;

    public Track() {
        super("track");
        ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() != 1) {
            if (this.tracking == null) {
                this.respond("&cEnter player name.");
            } else {
                this.clearTarget();
                this.respond("&7Cleared tracking target.");
            }
        } else {
            String playerName = input.getArgument(0);
            if (!this.validName(playerName)) {
                this.respond("&cInvalid username.");
            } else if (this.tracking != null && this.tracking.equalsIgnoreCase(playerName)) {
                this.respond("&cAlready tracking target.");
            } else if (!Utils.nullCheck()) {
                this.respond("&cInvalid player.");
            } else {
                for (PlayerEntity player : mc.world.players) {
                    if (player != mc.player && player.getName().equalsIgnoreCase(playerName)) {
                        this.tracking = player.getName();
                        this.respond(
                                "&7Tracking &b" + this.tracking + " " + this.distance(mc.player.distanceTo(player)));
                        return;
                    }
                }

                this.respond("&cInvalid player.");
            }
        }
    }

    public void clearTarget() {
        this.tracking = null;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent event) {
        if (Utils.nullCheck() && this.tracking != null) {
            for (PlayerEntity player : mc.world.players) {
                if (player != mc.player
                        && player.deathTicks == 0
                        && player.getName().equals(this.tracking)) {
                    RenderUtils.renderEntity(
                            player, 2, 0.0, 0.0, Theme.getGradient(Theme.DEFAULT_THEME_INDEX, 0.0), false, 0.37F);
                    return;
                }
            }
        }
    }

    private boolean validName(String name) {
        if (name.length() > 16) {
            return false;
        }

        for (char character : name.toCharArray()) {
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return false;
            }
        }

        return true;
    }

    private String distance(double distance) {
        char color = (char)
                (distance <= 10.0
                        ? 99
                        : (distance <= 25.0 ? 54 : (distance <= 50.0 ? 101 : (distance <= 100.0 ? 97 : 50))));
        String text = "&" + color + (int) distance + "m";
        if (Settings.addBracketsToDistance.isToggled()) {
            text = "&7(" + text + "&7)";
        }

        return text;
    }

    private void respond(String message) {
        this.replyWithHeader("&7[&f" + this.getName() + "&7] &r" + message);
    }
}

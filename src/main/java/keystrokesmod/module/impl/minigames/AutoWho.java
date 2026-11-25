package keystrokesmod.module.impl.minigames;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.scoreboard.team.Team;

public class AutoWho extends Module {
    private ButtonSetting artifical;
    private ButtonSetting hideMessage;
    private ButtonSetting removeBots;
    private int lastTrackedGameStatus = Integer.MIN_VALUE;

    public AutoWho() {
        super("Auto Who", Module.category.minigames);
        this.registerSetting(new DescriptionSetting("Automatically execute /who."));
        this.registerSetting(new DescriptionSetting(Utils.formatColor("Use '&enick [nick]&r' when nicked.")));
        this.registerSetting(this.artifical = new ButtonSetting("Artificial", false));
        this.registerSetting(this.hideMessage = new ButtonSetting("Hide message", false));
        this.registerSetting(this.removeBots = new ButtonSetting("Remove bots", true));
    }

    @Override
    public void onDisable() {
        this.lastTrackedGameStatus = Integer.MIN_VALUE;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            this.lastTrackedGameStatus = Integer.MIN_VALUE;
        } else {
            int trackedGameStatus = this.getTrackedGameStatus();
            if (trackedGameStatus == 2 && this.lastTrackedGameStatus != 2) {
                this.artificial();
            }

            this.lastTrackedGameStatus = trackedGameStatus;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChatReceive(ForgeEvents.ClientChatReceivedEvent e) {
        if (e.type != 2 && Utils.nullCheck()) {
            String r = Utils.stripColor(e.message.getString());
            if (!r.isEmpty()) {
                if (this.hideMessage.isToggled() && r.startsWith("ONLINE: ")) {
                    e.setCanceled(true);
                    Utils.log.info("[CHAT] " + r);
                }
            }
        }
    }

    private int getTrackedGameStatus() {
        int bedwarsStatus = Utils.getBedwarsStatus();
        if (bedwarsStatus != -1) {
            return bedwarsStatus;
        }

        int skywarsStatus = Utils.getSkyWarsStatus();
        return skywarsStatus != -1 ? skywarsStatus : 0;
    }

    private void artificial() {
        if (this.artifical.isToggled()) {
            String online = this.hideMessage.isToggled() ? "ONLINE: " : "&b&lONLINE: &r";

            for (PlayerInfo networkPlayerInfo : Utils.getTablist(true)) {
                if (!this.removeBots.isToggled() || networkPlayerInfo.getPing() <= 1) {
                    if (this.hideMessage.isToggled()) {
                        online = online + networkPlayerInfo.getProfile().getName() + ", ";
                    } else {
                        online = online
                                + Team.getMemberDisplayName(
                                        networkPlayerInfo.getTeam(),
                                        networkPlayerInfo.getProfile().getName())
                                + "�7, ";
                    }
                }
            }

            if (this.hideMessage.isToggled()) {
                Utils.log.info("[CHAT] " + online + mc.player.getName());
                return;
            }

            Utils.sendRawMessage(online + mc.player.getDisplayName().getFormattedString());
        } else {
            mc.player.sendChat("/who");
        }
    }
}

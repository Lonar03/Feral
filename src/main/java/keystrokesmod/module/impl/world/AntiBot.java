package keystrokesmod.module.impl.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import keystrokesmod.Feral;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Chat;
import keystrokesmod.utility.Utils;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.HoverEvent.Action;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;

public class AntiBot extends Module {
    private static final HashMap<PlayerEntity, Long> entities = new HashMap<>();
    private static SliderSetting spawnDelay;
    private static SliderSetting respawnDelay;
    private static SliderSetting pitSpawn;
    private static ButtonSetting tablist;
    private ButtonSetting printWorldJoin;
    private static long respawnTime;

    public AntiBot() {
        super("Anti Bot", Module.category.world);
        this.registerSetting(spawnDelay = new SliderSetting("Delay (spawn)", true, -1.0, 0.5, 15.0, 0.5, "Delay"));
        spawnDelay.setSuffix(" second");
        this.registerSetting(
                respawnDelay = new SliderSetting("Delay (respawn)", " second", true, -1.0, 0.5, 15.0, 0.5));
        this.registerSetting(pitSpawn = new SliderSetting("Pit spawn", true, -1.0, 70.0, 120.0, 1.0));
        this.registerSetting(tablist = new ButtonSetting("Tab list", false));
        this.registerSetting(this.printWorldJoin = new ButtonSetting("Print world join", false));
        this.closetModule = true;
        this.liteModule = true;
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if ((e.entity instanceof PlayerEntity || Feral.DEBUG) && e.entity != mc.player) {
            if (spawnDelay.getInput() != -1.0 && e.entity instanceof PlayerEntity) {
                entities.put((PlayerEntity) e.entity, System.currentTimeMillis());
            }

            if (this.printWorldJoin.isToggled()) {
                this.printJoin((PlayerEntity) e.entity, false);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent e) {
        if (e.type != 2 && Utils.nullCheck() && respawnDelay.getInput() != -1.0) {
            if (Utils.stripColor(e.message.getString()).equals("You have respawned!")) {
                respawnTime = System.currentTimeMillis();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload e) {
        if (e.world == mc.world) {
            entities.clear();
            respawnTime = 0L;
        }
    }

    @Override
    public void onUpdate() {
        if (spawnDelay.getInput() != -1.0 && !entities.isEmpty()) {
            long cutoff = System.currentTimeMillis() - (long) (spawnDelay.getInput() * 1000.0);
            entities.values().removeIf(n -> n <= cutoff);
        }
    }

    @Override
    public void onDisable() {
        entities.clear();
        respawnTime = 0L;
    }

    public static boolean isBot(Entity entity) {
        return isBot(entity, true);
    }

    public static boolean isBot(Entity entity, boolean useDelays) {
        if (!ModuleManager.antiBot.isEnabled()) {
            return false;
        }

        if (Freecam.freeEntity != null && Freecam.freeEntity == entity) {
            return true;
        }

        if (!(entity instanceof PlayerEntity)) {
            return true;
        }

        PlayerEntity entityPlayer = (PlayerEntity) entity;
        if (useDelays && spawnDelay.getInput() != -1.0 && !entities.isEmpty() && entities.containsKey(entityPlayer)) {
            return true;
        }

        if (useDelays
                && respawnDelay.getInput() != -1.0
                && respawnTime != 0L
                && System.currentTimeMillis() - respawnTime < respawnDelay.getInput() * 1000.0) {
            return true;
        }

        if (entityPlayer.removed) {
            return true;
        }

        if (Utils.removeFormatCodes(entityPlayer.getName()).trim().isEmpty()) {
            return true;
        }

        if (tablist.isToggled() && !getTablist().contains(entityPlayer.getName())) {
            return true;
        }

        if (entityPlayer.getHealth() != 20.0F && entityPlayer.getName().startsWith("§c")) {
            return true;
        }

        if (pitSpawn.getInput() != -1.0 && entityPlayer.y >= pitSpawn.getInput()) {
            return true;
        }

        if (entityPlayer.damagedTime == 0) {
            if (entityPlayer.getHealth() == 20.0F) {
                String unformattedText = entityPlayer.getDisplayName().getString();
                if (unformattedText.length() == 10 && unformattedText.charAt(0) != 167) {
                    return true;
                }

                if (unformattedText.length() == 12 && entityPlayer.isSleeping() && unformattedText.charAt(0) == 167) {
                    return true;
                }

                if (unformattedText.length() >= 7
                        && unformattedText.charAt(2) == '['
                        && unformattedText.charAt(3) == 'N'
                        && unformattedText.charAt(6) == ']') {
                    return true;
                }

                if (entityPlayer.getName().contains(" ")) {
                    return true;
                }
            } else if (entityPlayer.isInvisible()) {
                String unformattedText = entityPlayer.getDisplayName().getString();
                if (unformattedText.length() >= 3
                        && unformattedText.charAt(0) == 167
                        && unformattedText.charAt(1) == 'c') {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> getTablist() {
        List<String> tab = new ArrayList<>();

        for (PlayerInfo networkPlayerInfo : Utils.getTablist(true)) {
            if (networkPlayerInfo != null) {
                tab.add(networkPlayerInfo.getProfile().getName());
            }
        }

        return tab;
    }

    public void printJoin(PlayerEntity p, boolean watchdog) {
        String s = "&7Entity &b" + p.getNetworkId() + " &7joined: &r"
                + p.getDisplayName().getString() + (watchdog ? " &7(&cwatchdog&7)" : "");

        try {
            LiteralText text = new LiteralText(Chat.color("&7[&dR&7]&r " + s));
            String hover = "&ename: &r"
                    + p.getName()
                    + "\n&edisplay name: &r"
                    + p.getDisplayName().getString()
                    + "\n&estripped: &r"
                    + Chat.strip(p.getDisplayName().getString())
                    + "\n&euuid: &7"
                    + p.getUuid()
                    + "\n&ex: &7"
                    + p.x
                    + "\n&ey: &7"
                    + p.y
                    + "\n&ez: &7"
                    + p.z
                    + "\n&eyaw: &7"
                    + p.yaw
                    + "\n&epitch: &7"
                    + p.pitch
                    + "\n&ehealth: &7"
                    + p.getHealth()
                    + "\n&ehurt time: &7"
                    + p.damagedTimer
                    + "/"
                    + p.damagedTime
                    + "\n&einvisible: &7"
                    + p.isInvisible()
                    + "\n&eitem: &7"
                    + (p.getDisplayItemInHand() == null
                            ? "null"
                            : p.getDisplayItemInHand().getHoverName())
                    + (watchdog ? "" : "\n&ebot: &7" + isBot(p, false));
            AbstractTeam team = p.getScoreboardTeam();
            if (team != null) {
                hover = hover + "\n&eteam: &7" + team.getName();
            } else {
                hover = hover + "\n&eteam: &7null";
            }

            PlayerInfo info = Utils.info(p);
            if (info != null) {
                hover = hover + "\n&etablist: &7true";
                Team t = info.getTeam();
                hover = hover + "\n&enetwork team: &7" + (t == null ? "null" : t.getDisplayName()) + "\n&eping: &7"
                        + info.getPing();
            } else {
                hover = hover + "\ntablist: false";
            }

            Style style = new Style();
            style.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new LiteralText(Chat.color(hover))));
            text.setStyle(style);
            mc.player.sendMessage(text);
        } catch (Exception ex) {
            Chat.moduleMessage(this, s + " &7(&c" + ex.getClass().getSimpleName() + "&7)");
        }
    }
}

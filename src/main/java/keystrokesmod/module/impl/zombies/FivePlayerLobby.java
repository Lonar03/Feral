package keystrokesmod.module.impl.zombies;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import keystrokesmod.Feral;
import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.WorldEvent.Load;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Vec3d;

/**
 * "Quintet": tools for trying to reproduce the Hypixel Zombies five player lobby glitch, where the
 * first player in a lobby is counted as 0/4 so a fifth player fits in.
 *
 * <p>Theory being tested: the glitch was seen together with "Cannot interact with self!" (the kick
 * Spigot gives a non-spectator that uses/attacks its own entity) and "You were both spectator and
 * player". Interacting with your own player (freecam + clicking your own body, or the self-interact
 * packet below) at the right moment might desync the lobby's player count. See extras/meow.txt for
 * what was tried.
 */
public class FivePlayerLobby extends Module {
    private static final String[] MAP_NAMES = {"Dead End", "Bad Blood", "Alien Arcadium", "Prison"};
    private static final String[] MAP_COMMANDS = {
        "/play arcade_zombies_dead_end",
        "/play arcade_zombies_bad_blood",
        "/play arcade_zombies_alien_arcadium",
        "/play arcade_zombies_prison"
    };
    private static final String[] PACKET_NAMES = {"Right click", "Left click", "Both"};
    private static final String[] TIMING_NAMES = {"Before warp", "On world load"};
    private static final Pattern JOIN_PATTERN = Pattern.compile("^(\\S+) has joined \\((\\d+)/(\\d+)\\)!$");
    private static final String TAG = "§d[Quintet] ";

    public static SliderSetting role;
    public static SliderSetting delaySec;
    private final TextSetting keyword;
    private final SliderSetting map;
    private final SliderSetting timing;
    private final SliderSetting kickDelaySec;
    private final SliderSetting actionDelayMs;
    private final SliderSetting packet;
    private final KeySetting selfInteractKey;
    private final ButtonSetting alerts;
    private final ButtonSetting debugLog;

    private int commandCountdownTicks = 0;
    private String pendingCommand = null;
    /** Limbo / Self kick: keyword seen, act on the next world load. */
    private boolean armed = false;
    /** System time to run the Limbo / Self kick action at, 0 if none is scheduled. */
    private long pendingActionAt = 0L;

    private long worldLoadedAt = System.currentTimeMillis();
    private boolean keyWasDown = false;

    public FivePlayerLobby() {
        super("Five Player Lobby", Module.category.zombies);
        this.registerSetting(new DescriptionSetting("§c§lEXPERIMENTAL! Do NOT test on main accounts."));
        this.registerSetting(
                new DescriptionSetting("§cSelf interacting gets you kicked (\"Cannot interact with self!\")."));
        this.registerSetting(
                role = new SliderSetting(
                        "Role", FivePlayerLobby.Role.Leader.ordinal(), Utils.enumNames(FivePlayerLobby.Role.class)));
        this.registerSetting(this.keyword = new TextSetting("Keyword", "frty1", "party chat trigger", 32));
        this.registerSetting(this.map = new SliderSetting("Map", 1, MAP_NAMES));
        this.registerSetting(delaySec = new SliderSetting("Delay (sec)", 3.0, 0.5, 10.0, 0.1));
        this.registerSetting(this.timing = new SliderSetting("Timing", 0, TIMING_NAMES));
        this.registerSetting(this.kickDelaySec = new SliderSetting("Kick delay (sec)", 1.5, 0.0, 10.0, 0.1));
        this.registerSetting(this.actionDelayMs = new SliderSetting("Action delay", " ms", 0.0, 0.0, 5000.0, 50.0));
        this.registerSetting(this.packet = new SliderSetting("Self interact", 0, PACKET_NAMES));
        this.registerSetting(this.selfInteractKey = new KeySetting("Self interact key", 0));
        this.registerSetting(this.alerts = new ButtonSetting("Lobby alerts", true));
        this.registerSetting(this.debugLog = new ButtonSetting("Debug log", true));
    }

    @Override
    public void guiUpdate() {
        FivePlayerLobby.Role r = this.getRole();
        this.map.setVisible(r == FivePlayerLobby.Role.Leader, this);
        delaySec.setVisible(r == FivePlayerLobby.Role.Leader, this);
        boolean acts = r == FivePlayerLobby.Role.Limbo || r == FivePlayerLobby.Role.SelfKick;
        this.timing.setVisible(acts, this);
        this.kickDelaySec.setVisible(acts && this.isBeforeWarp(), this);
        this.actionDelayMs.setVisible(acts && !this.isBeforeWarp(), this);
        this.keyword.setVisible(r != FivePlayerLobby.Role.Watch, this);
    }

    @Override
    public String getInfo() {
        return this.getRole().name();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.reset();
    }

    private void reset() {
        this.commandCountdownTicks = 0;
        this.pendingCommand = null;
        this.armed = false;
        this.pendingActionAt = 0L;
    }

    /** Before warp: act a set time after the keyword, while still in the old lobby. */
    private boolean isBeforeWarp() {
        return (int) this.timing.getInput() == 0;
    }

    private FivePlayerLobby.Role getRole() {
        return Utils.enumOption(FivePlayerLobby.Role.class, role);
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.type != 0 && event.type != 1) {
            return;
        }
        String raw = event.message.getString();
        if (raw == null) {
            return;
        }
        String string = Utils.stripColor(raw).trim();
        String lower = string.toLowerCase(Locale.ROOT);

        this.watchLobby(string, lower);

        String trigger = this.keyword.getText().trim().toLowerCase(Locale.ROOT);
        if (!trigger.isEmpty() && lower.contains(trigger)) {
            this.arm("keyword");
        }
    }

    /** Lobby join counts and the glitch messages, so a test run shows what happened when. */
    private void watchLobby(String string, String lower) {
        Matcher join = JOIN_PATTERN.matcher(string);
        if (join.matches()) {
            int count = Integer.parseInt(join.group(2));
            int max = Integer.parseInt(join.group(3));
            this.log("join " + join.group(1) + " " + count + "/" + max);
            if (this.alerts.isToggled() && (count == 0 || count > max)) {
                this.alert("§a§lQUINTET? §e" + join.group(1) + " joined as §b" + count + "/" + max);
            }
            return;
        }

        if (lower.contains("cannot interact with self") || lower.contains("both spectator and player")) {
            if (this.alerts.isToggled()) {
                this.alert("§eGlitch message: §f" + string);
            }
            this.log("glitch message \"" + string + "\"");
        } else if (lower.contains("kick") || lower.contains("has quit")) {
            this.log("\"" + string + "\"");
        }
    }

    /** Runs the role's action, as if the keyword was just seen. */
    private void arm(String reason) {
        FivePlayerLobby.Role r = this.getRole();
        if (r == FivePlayerLobby.Role.Leader) {
            int index = Math.max(0, Math.min(MAP_COMMANDS.length - 1, (int) this.map.getInput()));
            this.pendingCommand = MAP_COMMANDS[index];
            this.commandCountdownTicks = Math.max(1, (int) Math.round(delaySec.getInput() * 20.0));
            this.log(reason + ": " + this.pendingCommand + " in " + delaySec.getInput() + "s");
        } else if ((r == FivePlayerLobby.Role.Limbo || r == FivePlayerLobby.Role.SelfKick) && this.isBeforeWarp()) {
            this.armed = false;
            this.pendingActionAt = System.currentTimeMillis() + (long) (this.kickDelaySec.getInput() * 1000.0);
            this.log(reason + ": " + r.name() + " in " + this.kickDelaySec.getInput() + "s (before warp)");
        } else if (r == FivePlayerLobby.Role.Limbo || r == FivePlayerLobby.Role.SelfKick) {
            this.armed = true;
            this.log(reason + ": armed, " + r.name() + " on next world load");
        }
    }

    @SubscribeEvent
    public void onWorldLoad(Load event) {
        if (event.world == null || !event.world.isClient) {
            return;
        }
        this.worldLoadedAt = System.currentTimeMillis();
        this.log("world load");
        if (!this.armed) {
            return;
        }
        FivePlayerLobby.Role r = this.getRole();
        if (r != FivePlayerLobby.Role.Limbo && r != FivePlayerLobby.Role.SelfKick) {
            this.armed = false;
            return;
        }
        this.armed = false;
        long delay = (long) this.actionDelayMs.getInput();
        if (r == FivePlayerLobby.Role.Limbo && delay <= 0L) {
            // Same as before the rework: disconnect the moment the new world loads.
            this.doDisconnect();
        } else {
            this.pendingActionAt = this.worldLoadedAt + delay;
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !Utils.nullCheck()) {
            return;
        }

        boolean keyDown = mc.screen == null && this.selfInteractKey.isPressed();
        if (keyDown && !this.keyWasDown) {
            this.sendSelfInteract("key");
        }
        this.keyWasDown = keyDown;

        if (this.pendingActionAt != 0L && System.currentTimeMillis() >= this.pendingActionAt) {
            this.pendingActionAt = 0L;
            if (this.getRole() == FivePlayerLobby.Role.SelfKick) {
                this.sendSelfInteract("self kick");
            } else if (this.getRole() == FivePlayerLobby.Role.Limbo) {
                this.doDisconnect();
            }
        }

        if (this.pendingCommand != null && --this.commandCountdownTicks <= 0) {
            mc.player.sendChat(this.pendingCommand);
            this.log("sent " + this.pendingCommand);
            this.pendingCommand = null;
        }
    }

    /**
     * Uses / attacks your own player entity, the same packets as clicking your own body in freecam.
     * A Spigot server kicks a non-spectator for this ("Cannot interact with self!").
     */
    private void sendSelfInteract(String reason) {
        if (!Utils.nullCheck() || mc.getNetworkHandler() == null) {
            return;
        }
        LocalClientPlayerEntity self = mc.player;
        int mode = (int) this.packet.getInput();
        if (mode == 0 || mode == 2) {
            // Vanilla right click on an entity: INTERACT_AT (hit point relative to the entity), then INTERACT.
            PacketUtils.sendPacketNoEvent(
                    new PlayerInteractEntityC2SPacket(self, new Vec3d(0.0, self.height / 2.0, 0.0)));
            PacketUtils.sendPacketNoEvent(
                    new PlayerInteractEntityC2SPacket(self, PlayerInteractEntityC2SPacket.Action.INTERACT));
        }
        if (mode == 1 || mode == 2) {
            // Vanilla left click on an entity: swing, then ATTACK.
            PacketUtils.sendPacketNoEvent(new ArmSwingC2SPacket());
            PacketUtils.sendPacketNoEvent(
                    new PlayerInteractEntityC2SPacket(self, PlayerInteractEntityC2SPacket.Action.ATTACK));
        }
        this.log(reason + ": sent self interact (" + PACKET_NAMES[Math.max(0, Math.min(2, mode))] + ")");
    }

    private void doDisconnect() {
        this.log("disconnecting");
        if (Utils.nullCheck()
                && mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getConnection() != null) {
            try {
                mc.getNetworkHandler().getConnection().disconnect(new LiteralText("Disconnected"));
            } catch (Throwable ignored) {
            }
        }
    }

    private void alert(String message) {
        Utils.sendRawMessage(TAG + message);
        if (Utils.nullCheck()) {
            mc.player.playSound("note.pling", 1.0F, 1.0F);
        }
    }

    /** Debug line with the time since the last world load, to line up timings between test runs. */
    private void log(String message) {
        if (this.debugLog.isToggled()) {
            long ms = System.currentTimeMillis() - this.worldLoadedAt;
            Utils.sendRawMessage(TAG + "§7+" + ms + "ms §f" + message);
        }
    }

    public static List<Command> createCommands() {
        return Collections.singletonList(new FivePlayerLobby.QuintetCommand());
    }

    public enum Role {
        Leader,
        Limbo,
        SelfKick,
        Watch;
    }

    /** {@code .quintet [arm|kick|disarm]}: test the roles without a party / keyword. */
    private static class QuintetCommand extends Command {
        private QuintetCommand() {
            super("quintet");
        }

        @Override
        public void execute(CommandInput input) {
            Module module = ModuleManager.getModule(FivePlayerLobby.class);
            if (!(module instanceof FivePlayerLobby) || !module.isEnabled()) {
                Utils.sendRawMessage(TAG + "§cEnable Five Player Lobby first.");
                return;
            }
            FivePlayerLobby quintet = (FivePlayerLobby) module;
            String arg = input.argumentCount() == 0 ? "" : input.getArgument(0).toLowerCase(Locale.ROOT);
            switch (arg) {
                case "arm":
                    quintet.arm("command");
                    break;
                case "kick":
                    quintet.sendSelfInteract("command");
                    break;
                case "disarm":
                    quintet.reset();
                    Utils.sendRawMessage(TAG + "§fDisarmed.");
                    break;
                default:
                    Utils.sendRawMessage(TAG + "§fRole §e" + quintet.getRole().name() + "§f, armed §e" + quintet.armed
                            + "§f. Usage: §e" + Feral.commandManager.getPrefix() + "quintet <arm|kick|disarm>");
            }
        }

        @Override
        public List<String> suggest(CommandInput input) {
            return this.filterSuggestions(input, "arm", "kick", "disarm");
        }
    }
}

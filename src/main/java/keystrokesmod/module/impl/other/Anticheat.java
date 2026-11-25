package keystrokesmod.module.impl.other;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import keystrokesmod.event.AntiCheatFlagEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockMiningProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Anticheat extends Module {
    public static SliderSetting flagInterval;
    public static ButtonSetting addCheatersAsEnemy;
    public static ButtonSetting autoReport;
    public static ButtonSetting disableOutsideMinigames;
    public static ButtonSetting hideChatAlerts;
    public static ButtonSetting ignoreTeammates;
    public static ButtonSetting sendPartyChat;
    public static ButtonSetting shouldPing;
    private static final Mutable below = new Mutable();
    private static final HashMap<UUID, Anticheat.Tracker> trackers = new HashMap<>();
    private static final HashMap<UUID, HashMap<Anticheat.Check, Long>> lastFlags = new HashMap<>();
    private static final HashMap<UUID, BlockMiningProgressS2CPacket> breakingPlayers = new HashMap<>();
    private static final HashMap<BlockPos, BlockMiningProgressS2CPacket> breakingBlocks = new HashMap<>();
    private static final HashSet<String> reported = new HashSet<>();
    private static String partyMessage;
    private static String reportName;
    private static long lastPing;
    private static volatile long lastClientBoundPacket;

    public Anticheat() {
        super("AntiCheat", Module.category.other);
        this.liteModule = true;
        this.closetModule = true;
        this.registerSetting(new DescriptionSetting("Detects other cheaters."));
        this.registerSetting(flagInterval = new SliderSetting("Flag interval", " second", 5.0, 0.0, 30.0, 1.0));
        this.registerSetting(addCheatersAsEnemy = new ButtonSetting("Add cheaters as enemy", false));
        this.registerSetting(autoReport = new ButtonSetting("Auto report", false));
        this.registerSetting(disableOutsideMinigames = new ButtonSetting("Disable outside minigames", false));
        this.registerSetting(hideChatAlerts = new ButtonSetting("Hide chat alerts", false));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", false));
        this.registerSetting(sendPartyChat = new ButtonSetting("Send party chat", false));
        this.registerSetting(shouldPing = new ButtonSetting("Should ping", false));
        this.registerSetting(new DescriptionSetting("Detected cheats"));
        boolean experimental = false;

        for (Anticheat.Check check : Anticheat.Check.values()) {
            if (check != Anticheat.Check.BedNuker) {
                if (check.experimental && !experimental) {
                    this.registerSetting(new DescriptionSetting("Experimental"));
                    experimental = true;
                }

                String legacyName = check == Anticheat.Check.AutoBlock
                        ? "Autoblock"
                        : (check == Anticheat.Check.LegitScaffold ? "Legit scaffold" : check.displayName);
                this.registerSetting(
                        check.button = new ButtonSetting(check.displayName, !check.experimental, legacyName));
            }
        }
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void check(PlayerEntity p, Anticheat.Tracker t) {
        Minecraft mc = mc();
        int n = p.ticks;
        boolean adventure = !mc.player.abilities.canModifyWorld;
        if (Anticheat.Check.AimAssist.enabled()
                && p.pitch > -80.0F
                && p.pitch < 80.0F
                && t.yawTicks >= 25
                && n - t.lastPitchTick >= 25) {
            flag(p, Anticheat.Check.AimAssist, "&b" + t.yawTicks + " " + (n - t.lastPitchTick));
        } else {
            ItemStack held;
            if (!Anticheat.Check.AutoClicker.enabled()
                    || adventure
                    || t.quickSwings < 10
                    || !(mc.player.squaredDistanceTo(p) < 700.0)
                    || (held = p.getDisplayItemInHand()) != null && held.getItem() == Items.FISHING_ROD) {
                if (Anticheat.Check.AutoBlock.enabled() && t.swingBlockTicks >= 15) {
                    flag(p, Anticheat.Check.AutoBlock, "&b" + t.swingBlockTicks);
                } else if (Anticheat.Check.Blink.enabled()
                        && !adventure
                        && t.lastSprintTicks >= 2
                        && t.lastRestTicks >= 6
                        && t.maxHorizontal > 0.5
                        && t.maxHorizontal < 3.0
                        && System.currentTimeMillis() - lastClientBoundPacket < 100L) {
                    flag(
                            p,
                            Anticheat.Check.Blink,
                            "&b" + t.lastSprintTicks + " " + t.lastRestTicks + " " + t.maxHorizontal);
                } else if (Anticheat.Check.LegitScaffold.enabled() && t.perfectSneaks >= 3) {
                    flag(p, Anticheat.Check.LegitScaffold, "&b" + t.perfectSneaks);
                } else {
                    if (Anticheat.Check.NoFall.enabled()) {
                        if (t.stillTicks == 10 && t.belowBlock == Blocks.AIR) {
                            t.fallStart = new Vec3d(p.x, p.y, p.z);
                        }

                        if (t.fallStart != null) {
                            if (p.damagedTimer != 0) {
                                t.fallStart = null;
                            } else if (t.lastFallingTicks > 0
                                    && t.lastFallingTicks < 6
                                    && t.belowBlock != Blocks.AIR
                                    && !AnticheatContext.isItem(
                                            p.getDisplayItemInHand(), Item.byBlock(Blocks.LADDER))) {
                                double dx = p.x - t.fallStart.x;
                                double dz = p.z - t.fallStart.z;
                                double h = dx * dx + dz * dz;
                                double dy = p.y - t.fallStart.y;
                                t.fallStart = null;
                                if (h < 144.0
                                        && dy <= -5.0
                                        && AnticheatContext.detect() != AnticheatContext.State.LOBBY
                                        && AnticheatContext.detect() != AnticheatContext.State.PIT
                                        && AnticheatContext.detect() != AnticheatContext.State.REPLAY
                                        && !AnticheatContext.skywars()
                                        && AnticheatContext.buildBattle() == 0) {
                                    flag(p, Anticheat.Check.NoFall, "&b" + dy + "m");
                                    return;
                                }
                            }
                        }
                    }

                    if (Anticheat.Check.NoSlow.enabled()
                            && t.useSprintTicks >= 11
                            && t.maxHorizontal >= 0.08
                            && n - t.lastStopUsingTick >= 35) {
                        flag(p, Anticheat.Check.NoSlow, "&b" + t.useSprintTicks + " " + (n - t.lastStopUsingTick));
                    } else {
                        if (Anticheat.Check.Scaffold.enabled()
                                && t.scaffoldTicks > 0
                                && n - t.lastSneakTick >= 25
                                && (n - t.lastDeltaYTick >= 20 || t.scaffoldTicks > 25)
                                && AnticheatContext.buildBattle() == 0) {
                            boolean air = true;
                            BlockPos pos = p.getCommandSourceBlockPos().down(2);

                            for (int i = 0; i < 4; i++) {
                                if (!(BlockUtils.getBlock(pos) instanceof AirBlock)) {
                                    air = false;
                                    break;
                                }

                                pos = pos.down();
                            }

                            if (air) {
                                flag(p, Anticheat.Check.Scaffold, "&b" + t.scaffoldTicks + " " + (n - t.lastSneakTick));
                                return;
                            }
                        }

                        if (Anticheat.Check.Protocol.enabled() && Math.abs(p.pitch) >= 92.0F) {
                            flag(p, Anticheat.Check.Protocol, "&bp:" + p.pitch);
                        }
                    }
                }
            } else {
                flag(p, Anticheat.Check.AutoClicker, "&b" + t.quickSwings);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        Minecraft mc = mc();
        if (active()) {
            if (partyMessage != null) {
                mc.player.sendChat("/pc " + partyMessage);
                partyMessage = null;
            } else if (reportName != null) {
                mc.player.sendChat("/report " + reportName + " cheating");
                reportName = null;
            }

            for (PlayerEntity p : mc.world.players) {
                if (!ignored(p)) {
                    Anticheat.Tracker t = trackers.get(p.getUuid());
                    if (t == null) {
                        t = new Anticheat.Tracker();
                    }

                    t.update(p);
                    check(p, t);
                    t.endTick(p);
                    trackers.put(p.getUuid(), t);
                }
            }
        }
    }

    @Override
    public void onDisable() {
        trackers.clear();
        lastFlags.clear();
        breakingPlayers.clear();
        breakingBlocks.clear();
        reported.clear();
        partyMessage = null;
        reportName = null;
        lastPing = 0L;
        lastClientBoundPacket = 0L;
    }

    private static boolean ignored(PlayerEntity p) {
        return p == null || p == mc().player || p.deathTicks != 0 || AntiBot.isBot(p);
    }

    @ForgeEvents.SubscribeEvent
    public void onJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc().player) {
            this.onDisable();
        }
    }

    private static boolean active() {
        return Utils.nullCheck()
                && !mc().isSingleplayer()
                && (!disableOutsideMinigames.isToggled()
                        || AnticheatContext.onHypixel() && AnticheatContext.detect() != AnticheatContext.State.LOBBY);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        lastClientBoundPacket = System.currentTimeMillis();
        Packet<?> packet = event.getPacket();
        if (packet instanceof BlockMiningProgressS2CPacket
                || packet instanceof BlockUpdateS2CPacket
                || packet instanceof BlocksUpdateS2CPacket) {
            ClientWorld world = mc.world;
            if (world != null) {
                if (mc.isOnSameThread()) {
                    processPacket(packet);
                } else {
                    mc.executeTask(() -> {
                        if (this.isEnabled() && mc.world == world) {
                            processPacket(packet);
                        }
                    });
                }
            }
        }
    }

    private static boolean processPacket(Packet<?> packet) {
        Minecraft mc = mc();
        if (!active()) {
            return true;
        }

        Entity entity;
        if (packet instanceof BlockMiningProgressS2CPacket
                && (Anticheat.Check.AutoClicker.enabled() || Anticheat.Check.FastMine.enabled())
                && (entity = mc.world.getEntity(((BlockMiningProgressS2CPacket) packet).getPlayerId()))
                        instanceof PlayerEntity) {
            Anticheat.Tracker t = trackers.get(entity.getUuid());
            if (t != null) {
                t.onBreakAnim((PlayerEntity) entity, (BlockMiningProgressS2CPacket) packet);
            }
        }

        if (packet instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket p = (BlockUpdateS2CPacket) packet;
            if (p.getBlockState().getBlock() instanceof AirBlock) {
                BlockPos pos = p.getPos();
                Block block = BlockUtils.getBlock(pos);
                if (block == Blocks.AIR
                        || block == Blocks.BED
                        || block == Blocks.LADDER
                        || block instanceof PlantBlock
                        || block instanceof AbstractLeavesBlock) {
                    return true;
                }

                PlayerEntity breaker = null;
                BlockMiningProgressS2CPacket anim = breakingBlocks.get(pos);
                if (anim != null) {
                    Entity e = mc.world.getEntity(anim.getPlayerId());
                    if (e instanceof PlayerEntity) {
                        breaker = (PlayerEntity) e;
                        if (anim.getProgress() > 4 && anim.getProgress() < 10) {
                            flag(breaker, Anticheat.Check.FastMine, "&b" + anim.getProgress());
                        }
                    }

                    breakingBlocks.remove(anim);
                }

                if (Anticheat.Check.Nuker.enabled()) {
                    if (AnticheatContext.exposed(pos)) {
                        return true;
                    }

                    double d = -1.0;
                    if (breaker == null
                            && (ModuleManager.bedAura == null
                                    || !ModuleManager.bedAura.isEnabled()
                                    || !(mc.player.getSquaredDistanceTo(pos) <= 36.0))) {
                        for (PlayerEntity other : mc.world.players) {
                            if (!ignored(other)) {
                                double d2 = other.getSquaredDistanceTo(pos);
                                if (d == -1.0 || d2 < d) {
                                    d = d2;
                                    breaker = other;
                                }
                            }
                        }
                    }

                    if (breaker != null) {
                        flag(
                                breaker,
                                Anticheat.Check.Nuker,
                                "&b"
                                        + ClientPlatform.registryName(block)
                                        + " "
                                        + pos.getX()
                                        + ","
                                        + pos.getY()
                                        + ","
                                        + pos.getZ()
                                        + ","
                                        + Utils.round(d, 3));
                    }
                }
            }
        } else if (packet instanceof BlocksUpdateS2CPacket && Anticheat.Check.Nuker.enabled()) {
            BlocksUpdateS2CPacket p = (BlocksUpdateS2CPacket) packet;
            if (p.getUpdates().length != 2) {
                return true;
            }

            if (!(p.getUpdates()[0].getBlockState().getBlock() instanceof AirBlock)) {
                return true;
            }

            BlockPos a = p.getUpdates()[0].getBlockPos();
            BlockPos b = p.getUpdates()[1].getBlockPos();
            if (AnticheatContext.exposed(a) || AnticheatContext.exposed(b)) {
                return true;
            }

            PlayerEntity breaker = null;

            for (PlayerEntity other : mc.world.players) {
                if (other == mc.player
                        && ModuleManager.bedAura != null
                        && ModuleManager.bedAura.isEnabled()
                        && (other.getSquaredDistanceTo(a) <= 36.0 || other.getSquaredDistanceTo(b) <= 36.0)) {
                    break;
                }

                if (!ignored(other)
                        && (!(other.getSquaredDistanceTo(a) > 36.0) || !(other.getSquaredDistanceTo(b) > 36.0))) {
                    breaker = other;
                    break;
                }
            }

            if (breaker != null) {
                flag(breaker, Anticheat.Check.BedNuker, "");
            }
        }

        return true;
    }

    private static void flag(PlayerEntity p, Anticheat.Check check, String info) {
        Minecraft mc = mc();
        if (!Utils.isFriended(p) && (!ignoreTeammates.isToggled() || !AnticheatContext.sameTeam(p))) {
            if (addCheatersAsEnemy.isToggled()) {
                Utils.addEnemy(p.getName());
            }

            long now = System.currentTimeMillis();
            if (flagInterval.getInput() > 0.0) {
                HashMap<Anticheat.Check, Long> flags = lastFlags.get(p.getUuid());
                if (flags == null) {
                    flags = new HashMap<>();
                } else {
                    Long last = flags.get(check);
                    if (last != null && Math.abs(now - last) <= flagInterval.getInput() * 1000.0) {
                        return;
                    }
                }

                flags.put(check, now);
                lastFlags.put(p.getUuid(), flags);
            }

            boolean hypixel = AnticheatContext.onHypixel();
            String name = p.getName();
            if (hypixel) {
                if (name.endsWith("§r")) {
                    name = Utils.stripColor(p.getDisplayName().getString()).trim();
                }

                if (autoReport.isToggled() && !reported.contains(name) && !AnticheatContext.screenOpen()) {
                    reportName = name;
                    reported.add(reportName);
                }
            }

            double d = mc.player.distanceTo(p);
            String s = p.getDisplayName().getString() + " &7detected for &d" + check.displayName + " "
                    + AnticheatContext.distance(d);
            LiteralText text = new LiteralText(Utils.formatColor("&7[&dR&7]&r " + s));
            if (hypixel && sendPartyChat.isToggled()) {
                partyMessage = Utils.stripColor(text.getString());
            }

            if (!hideChatAlerts.isToggled()) {
                if (hypixel) {
                    Style style = new Style();
                    style.setHoverEvent(new HoverEvent(
                            net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                            new LiteralText(Utils.formatColor("&7/report &c" + name + " &7cheating"))));
                    style.setClickEvent(new ClickEvent(
                            net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/report " + name + " cheating"));
                    text.append(new LiteralText(Utils.formatColor(" &7[&cWDR&7]")).setStyle(style));
                }

                mc.player.sendMessage(text);
            }

            if (shouldPing.isToggled() && now - lastPing > 1500L) {
                mc.player.playSound("note.pling", 1.0F, 1.0F);
                lastPing = now;
            }

            postAntiCheatFlagEvent(check.displayName, p);
        }
    }

    public static void postAntiCheatFlagEvent(String flag, Entity entity) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new AntiCheatFlagEvent(flag, entity));
    }

    public enum Check {
        AutoBlock("AutoBlock", false),
        LegitScaffold("Legit Scaffold", false),
        NoSlow("NoSlow", false),
        Nuker("Nuker", false),
        BedNuker("Bed Nuker", false),
        Scaffold("Scaffold", false),
        AimAssist("AimAssist", true),
        AutoClicker("AutoClicker", true),
        Blink("Blink", true),
        FastMine("FastMine", true),
        NoFall("NoFall", true),
        Protocol("Protocol", true);

        public final String displayName;
        public final boolean experimental;
        ButtonSetting button;

        Check(String displayName, boolean experimental) {
            this.displayName = displayName;
            this.experimental = experimental;
        }

        public boolean enabled() {
            return this.button != null && this.button.isToggled();
        }
    }

    static class Tracker {
        Block belowBlock;
        int quickSwings;
        int lastPitchTick;
        int lastSwing;
        int lastMoveTick;
        int yawTicks;
        int restTicks;
        int sprintTicks;
        int lastRestTicks;
        int lastSprintTicks;
        int lastStopUsingTick;
        int lastFallingTicks;
        int lastDeltaYTick;
        int stillTicks;
        int lastBreakAnim = -1;
        int lastSneakTick;
        int swingBlockTicks;
        int useSprintTicks;
        int lastHorizontalMoveTick;
        int perfectSneaks;
        int fallingTicks;
        int scaffoldTicks;
        int horizontalTicks;
        boolean wasUsing;
        boolean wasSneaking;
        double maxHorizontal;
        final double[] motionX = new double[1];
        final double[] motionY = new double[1];
        final double[] motionZ = new double[1];
        Vec3d fallStart;

        void update(PlayerEntity p) {
            int n = p.ticks;
            this.updateMotion(p);
            Anticheat.below.set(MathHelper.floor(p.x), MathHelper.floor(p.y - 1.0), MathHelper.floor(p.z));
            this.belowBlock = BlockUtils.getBlock(Anticheat.below);
            this.maxHorizontal = Math.max(Math.abs(this.motionX[0]), Math.abs(this.motionZ[0]));
            if (this.maxHorizontal == 0.0 && this.motionY[0] == 0.0) {
                this.restTicks++;
            } else {
                this.restTicks = 0;
                this.lastMoveTick = n;
            }

            if (this.maxHorizontal >= 0.07) {
                this.horizontalTicks++;
                this.lastHorizontalMoveTick = n;
            } else {
                this.horizontalTicks = 0;
            }

            if (Math.abs(this.motionY[0]) >= 0.1) {
                this.lastDeltaYTick = n;
            }

            this.fallingTicks = this.motionY[0] < 0.0 && this.belowBlock == Blocks.AIR ? ++this.fallingTicks : 0;
            if (p.isSneaking()) {
                this.lastSneakTick = n;
            }

            if (p.pitch != p.lastPitch) {
                this.lastPitchTick = n;
            }

            if (!p.hasItemInUse() && this.wasUsing) {
                this.lastStopUsingTick = n;
            }

            this.yawTicks = Math.abs(p.yaw - p.lastYaw) >= 1.0F ? ++this.yawTicks : 0;
            this.swingBlockTicks = p.armSwinging && p.isSwordBlocking() ? ++this.swingBlockTicks : 0;
            this.sprintTicks = p.isSprinting() ? ++this.sprintTicks : 0;
            this.useSprintTicks =
                    p.isSprinting() && p.hasItemInUse() && !p.isRiding() && !p.isInWater() ? ++this.useSprintTicks : 0;
            if (p.armSwinging && p.pitch >= 65.0F && AnticheatContext.isBlock(p.getDisplayItemInHand())) {
                this.scaffoldTicks = this.horizontalTicks >= 15 ? ++this.scaffoldTicks : 0;
                if (p.armSwingingTicks == 1) {
                    this.perfectSneaks = !this.wasSneaking && p.isSneaking() ? ++this.perfectSneaks : 0;
                }
            } else {
                this.perfectSneaks = 0;
                this.scaffoldTicks = 0;
            }

            if (p.armSwinging && (this.lastBreakAnim == -1 || this.lastBreakAnim == 255)) {
                if (p.armSwingingTicks == 0) {
                    this.quickSwings = this.lastSwing == 3 ? ++this.quickSwings : 0;
                }
            } else {
                this.quickSwings = 0;
            }

            this.stillTicks = !p.isSneaking()
                            && !p.armSwinging
                            && this.maxHorizontal == 0.0
                            && this.motionY[0] == 0.0
                            && p.yaw == p.lastYaw
                            && p.pitch == p.lastPitch
                    ? ++this.stillTicks
                    : 0;
        }

        void endTick(PlayerEntity p) {
            this.lastRestTicks = this.restTicks;
            this.lastSprintTicks = this.sprintTicks;
            this.lastFallingTicks = this.fallingTicks;
            this.wasSneaking = p.isSneaking();
            this.lastSwing = p.armSwingingTicks;
            this.wasUsing = p.hasItemInUse();
        }

        void onBreakAnim(PlayerEntity p, BlockMiningProgressS2CPacket anim) {
            this.lastBreakAnim = anim.getProgress();
            if (Anticheat.Check.FastMine.enabled()) {
                BlockMiningProgressS2CPacket previous = Anticheat.breakingPlayers.get(p.getUuid());
                if (previous != null && anim.getProgress() == 255) {
                    Anticheat.breakingBlocks.put(anim.getPos(), previous);
                }

                Anticheat.breakingPlayers.put(p.getUuid(), anim);
            }
        }

        void updateMotion(PlayerEntity p) {
            this.motionX[0] = p.x - p.prevX;
            this.motionY[0] = p.y - p.prevY;
            this.motionZ[0] = p.z - p.prevZ;
        }
    }
}

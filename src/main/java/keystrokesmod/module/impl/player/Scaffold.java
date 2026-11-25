package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.scaffold.ScaffoldSupport;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class Scaffold extends Module {
    public static Scaffold instance;
    public static SliderSetting motion;
    public static SliderSetting rotationSetting;
    public static SliderSetting fakeRotationSetting;
    public static SliderSetting fastScaffoldSetting;
    public static SliderSetting fastScaffoldRmbSetting;
    public static SliderSetting fastScaffoldMotion;
    public static SliderSetting multiPlaceSetting;
    public static SliderSetting precisionSetting;
    public static SliderSetting sneakEvery;
    public static SliderSetting speedBoost;
    public static SliderSetting highlightTheme;
    private static ButtonSetting spoofItem;
    private static ButtonSetting disableInInventory;
    public static ButtonSetting autoSwap;
    public static ButtonSetting cancelKnockback;
    public static ButtonSetting highlightBlocks;
    public static ButtonSetting jumpFacingForward;
    public static ButtonSetting legitMotion;
    public static ButtonSetting safeWalk;
    public static ButtonSetting showBlockCount;
    public static ButtonSetting silentSwing;
    public static ButtonSetting slowOnEnable;
    public static ButtonSetting slowSpeedPotion;
    public static ButtonSetting tower;
    public static Scaffold.Rotation rotation = Scaffold.Rotation.Strict;
    public static ScaffoldSupport.FakeRotation fakeRotation = ScaffoldSupport.FakeRotation.None;
    public static Scaffold.FastScaffold fastScaffold = Scaffold.FastScaffold.Disabled;
    private static Scaffold.FastScaffold lastFastScaffold = Scaffold.FastScaffold.Disabled;
    public static Scaffold.MultiPlace multiPlace = Scaffold.MultiPlace.Disabled;
    public static ScaffoldSupport.RotationUtils.Precision precision = ScaffoldSupport.RotationUtils.Precision.High;
    public static Theme theme = Theme.Default;
    public static boolean active;
    public static boolean disablingModule;
    private static boolean fastEnabled;
    private static boolean rightHeld;
    public static int keptY = -1;
    private static int searchMode;
    public static int legitTicks;
    public static int delay;
    public static int enableTick;
    public static int jumpState;
    public static int startTick;
    private static int jumpTick;
    private static int keepYJumpTick = -2;
    private static int airBelowTicks;
    private static int placedCount;
    public static int pausedTicks;
    private static boolean sneakQueued;
    private static boolean smoothSnap;
    private static BlockPos lastPlaced;
    private static float aimYaw;
    private static float aimPitch = 100.0F;
    private static float smoothLimit;
    private static float legitYaw;
    private static float restPitch;
    private static double yawOffset;
    private static long placedAt;
    public static long startedAt;
    public static float startPitch;
    private static long stoppedAt;
    private static long smoothAt;
    private static final HashMap<BlockPos, Long> placed = new HashMap<>();
    public static String countText;
    private static int originalSlot = -1;
    private static boolean hasSwapped;
    private static boolean sentCloseWindow;
    private static boolean sentUseOrDig;
    private static float lastPlayerYaw;
    private static Float smoothYaw;
    private static LocalClientPlayerEntity sessionPlayer;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;
    private static final Direction[] SIDES =
            new Direction[] {Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST};
    private static final Mutable SCRATCH = new Mutable();

    public Scaffold() {
        super("Scaffold", Module.category.player);
        instance = this;
        this.liteModule = true;
        this.closetModule = true;
        this.alwaysOn = true;
        this.registerSetting(motion = new SliderSetting("Motion", "x", 1.0, 0.5, 1.2, 0.01));
        this.registerSetting(
                rotationSetting = new SliderSetting(
                        "Rotation", 1, new String[] {"None", "Strict", "Smooth", "Simple", "Lazy", "Offset"}));
        this.registerSetting(
                fakeRotationSetting =
                        new SliderSetting("Rotation (fake)", 0, new String[] {"None", "Smooth", "Forward", "Spin"}));
        this.registerSetting(
                fastScaffoldSetting = new SliderSetting("Fast scaffold", 0, new String[] {
                    "Disabled",
                    "Vanilla",
                    "Constant A",
                    "Constant B",
                    "Float A",
                    "Float B",
                    "Jump A",
                    "Jump B",
                    "Jump C",
                    "Jump D",
                    "Keep-Y A",
                    "Keep-Y B",
                    "Side"
                }));
        this.registerSetting(
                fastScaffoldRmbSetting = new SliderSetting("Fast scaffold RMB", 0, new String[] {
                    "Disabled",
                    "Vanilla",
                    "Constant A",
                    "Constant B",
                    "Float A",
                    "Float B",
                    "Jump A",
                    "Jump B",
                    "Jump C",
                    "Jump D",
                    "Keep-Y A",
                    "Keep-Y B",
                    "Side"
                }));
        this.registerSetting(fastScaffoldMotion = new SliderSetting("Fast scaffold motion", "x", 1.0, 0.5, 1.2, 0.01));
        this.registerSetting(
                multiPlaceSetting =
                        new SliderSetting("Multi-place", 0, new String[] {"Disabled", "1 extra", "2 extra"}));
        this.registerSetting(
                precisionSetting =
                        new SliderSetting("Precision", 2, new String[] {"Low", "Medium", "High", "Very high"}));
        this.registerSetting(sneakEvery = new SliderSetting("Sneak every", " blocks", 0.0, 0.0, 20.0, 1.0));
        this.registerSetting(speedBoost = new SliderSetting("Speed boost", "", 1.3, 0.0, 2.0, 0.05));
        this.registerSetting(
                highlightTheme = new SliderSetting("Highlight theme", 0, new String[] {
                    "Default",
                    "Rainbow",
                    "Aurora",
                    "Cherry",
                    "Cotton candy",
                    "Flare",
                    "Flower",
                    "Forest",
                    "Frost",
                    "Gold",
                    "Grayscale",
                    "Inferno",
                    "Royal",
                    "Sandstorm",
                    "Sky",
                    "Vine"
                }));
        rotationSetting.setMinString("§cNone");
        fakeRotationSetting.setMinString("§cNone");
        fastScaffoldSetting.setMinString("§cDisabled");
        fastScaffoldRmbSetting.setMinString("§cDisabled");
        multiPlaceSetting.setMinString("§cDisabled");
        sneakEvery.setMinString("§cDisabled");
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", false));
        this.registerSetting(cancelKnockback = new ButtonSetting("Cancel knockback", false));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(highlightBlocks = new ButtonSetting("Highlight blocks", true));
        this.registerSetting(jumpFacingForward = new ButtonSetting("Jump facing forward", true));
        this.registerSetting(legitMotion = new ButtonSetting("Legit motion", true));
        this.registerSetting(safeWalk = new ButtonSetting("SafeWalk", false));
        this.registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        this.registerSetting(slowOnEnable = new ButtonSetting("Slow on enable", false));
        this.registerSetting(slowSpeedPotion = new ButtonSetting("Slow speed potion", false));
        this.registerSetting(tower = new ButtonSetting("Tower", false));
        this.registerSetting(spoofItem = new ButtonSetting("Spoof item", false));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new Scaffold.EditScreen())));
        ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.buttonstate && e.button >= 0 && this.isEnabled() && active && mc.screen == null) {
            int keyCode = e.button - 100;
            if (keyCode == mc.options.attackKey.getKeyCode() || keyCode == mc.options.useKey.getKeyCode()) {
                e.setCanceled(true);
            }
        }
    }

    @Override
    public void onEnable() {
        clearState();
        if (Utils.nullCheck()) {
            sessionPlayer = mc.player;
            this.guiUpdate();
            placed.clear();
            sentCloseWindow = false;
            sentUseOrDig = false;
            lastPlayerYaw = mc.player.yaw;
            if (autoSwap.isToggled()) {
                rememberSlot();
            }

            restPitch = randomFloat(84.5F, 87.5F);
            yawOffset = ScaffoldSupport.RotationUtils.randomOffset();
            if (!mc.player.onGround) {
                delay = 3;
            } else if (ScaffoldSupport.PlayerUtils.potionLevel(StatusEffect.JUMP_BOOST) <= 3
                    && ScaffoldSupport.PlayerUtils.nearEdge(0.2)
                    && countBlocks() > 0) {
                jumpState = 2;
                jumpTick = mc.player.ticks;
                delay = 4;
            }
        }
    }

    @Override
    public void onDisable() {
        if (!Utils.nullCheck()) {
            clearState();
        } else {
            disablingModule = true;
            stop();
            legitTicks = 0;
            delay = 0;
            airBelowTicks = 0;
            pausedTicks = 0;
            placedCount = 0;
            placedAt = 0L;
            double d = ScaffoldSupport.MoveUtils.speed();
            if (d > 0.34) {
                multiplyMotion((0.29 + 0.02 * Math.random()) / d, false);
            }

            disablingModule = false;
            restoreSlot();
            Tower.reset();
        }
    }

    @Override
    public void guiUpdate() {
        rotation = Scaffold.Rotation.values()[(int) rotationSetting.getInput()];
        fakeRotation = ScaffoldSupport.FakeRotation.values()[(int) fakeRotationSetting.getInput()];
        fastScaffold = Scaffold.FastScaffold.values()[
                (int) (rightHeld ? fastScaffoldRmbSetting.getInput() : fastScaffoldSetting.getInput())];
        multiPlace = Scaffold.MultiPlace.values()[(int) multiPlaceSetting.getInput()];
        precision = ScaffoldSupport.RotationUtils.Precision.values()[(int) precisionSetting.getInput()];
        theme = Theme.values()[(int) highlightTheme.getInput()];
        Tower.setFromScaffold(tower);
    }

    @Override
    public String getInfo() {
        if (fastScaffold == Scaffold.FastScaffold.Disabled) {
            return rotationSetting.getSelectedOption();
        } else if (fastScaffold.jump) {
            return "Jump";
        } else if (fastScaffold.keepY) {
            return "Keep-Y";
        } else if (fastScaffold == Scaffold.FastScaffold.Constant_A
                || fastScaffold == Scaffold.FastScaffold.Constant_B) {
            return "Constant";
        } else {
            return fastScaffold != Scaffold.FastScaffold.Float_A && fastScaffold != Scaffold.FastScaffold.Float_B
                    ? fastScaffold.name().replace("_", " ")
                    : "Float";
        }
    }

    private static void updateCount() {
        int n = countBlocks();
        String color = null;
        if (n <= 5) {
            color = "§c";
        } else if (n <= 15) {
            color = "§6";
        } else if (n <= 25) {
            color = "§e";
        }

        countText = color == null ? n + " blocks" : color + n + "§f blocks";
    }

    public static void renderPlaced() {
        if (!placed.isEmpty()) {
            long l = System.currentTimeMillis();
            Iterator<Entry<BlockPos, Long>> iterator = placed.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<BlockPos, Long> entry = iterator.next();
                long l2 = l - Math.abs(entry.getValue());
                float f = (float) l2 / 500.0F;
                if (f >= 1.0F) {
                    iterator.remove();
                } else if (highlightBlocks.isToggled()) {
                    float f2 = 255.0F - Math.min(f * 255.0F, 255.0F);
                    RenderUtils.renderBlock(
                            entry.getKey(),
                            Utils.mergeAlpha(Theme.getGradient((int) highlightTheme.getInput(), 0.0), (int) f2),
                            true,
                            false);
                }
            }
        }
    }

    private static BlockPos place(BlockPos last, ItemStack itemStack) {
        BlockPos target = search(last);
        if (target == null) {
            return null;
        }

        Direction facing = faceFor(target, null);
        if (facing == null) {
            return null;
        }

        BlockPos against = placementAnchor(target, facing);
        if (airBelowTicks > 0) {
            float yaw = aimYaw;
            float pitch = aimPitch;
            HitResult hit = aim(against, facing, -170.0F, RotationUtils.serverRotations[0]);
            if (hit == null) {
                return last == null ? null : place(null, itemStack);
            }

            switch (rotation) {
                case Simple:
                    float f3 = ScaffoldSupport.PlayerUtils.movementAngle();
                    if (Math.abs(f3 - aimYaw) < 100.0F) {
                        float f4 = aimYaw - f3;
                        f4 = (f4 + 540.0F) % 360.0F - 180.0F;
                        float f6 = 0.0F;
                        if (Math.abs(f4) >= 10.0F) {
                            f6 = Math.max(-10.0F, Math.min(f4, 10.0F));
                        }

                        aimYaw = f3 + f6;
                        if (mc.player.onGround) {
                            aimPitch = 82.5F;
                        }
                    }
                    break;
                case Lazy:
                    if (pitch != 100.0F
                            && ScaffoldSupport.PlayerUtils.rayTraceBlock(
                                            against, null, mc.interactionManager.getReach(), yaw, pitch)
                                    != null) {
                        aimYaw = yaw;
                        aimPitch = pitch;
                    }
            }

            Vec3d vec3 = hit.facePos;
            if (mc.interactionManager.useBlock(mc.player, mc.world, itemStack, against, facing, vec3)) {
                ScaffoldSupport.PlayerUtils.swing(silentSwing.isToggled(), true);
                if (searchMode == 1 || searchMode == 2 && fastScaffold.keepY) {
                    fastScaffold.ticks = mc.player.ticks;
                }

                lastPlaced = target;
                placedAt = System.currentTimeMillis();
                placed.put(target, placedAt);
                airBelowTicks = 0;
                return target;
            }
        } else if (ScaffoldSupport.PlayerUtils.isMoving() && Math.random() > 0.8) {
            ScaffoldSupport.PlayerUtils.syncCurrentPlayItem();
            mc.player.networkHandler.sendPacket(new PlayerUseC2SPacket(itemStack));
        }

        return null;
    }

    private static BlockPos search(BlockPos last) {
        ArrayList<Vec3d> list = new ArrayList<>();
        searchMode = 0;
        if (keptY != -1 && fastScaffold.canFastScaffold()) {
            int decimals = ScaffoldSupport.PlayerUtils.posYDecimals();
            if (fastScaffold == Scaffold.FastScaffold.Jump_A) {
                if (decimals != 1661 && (decimals != 2492 || !(mc.player.velocityY > 0.0))) {
                    searchMode = 2;
                } else {
                    searchMode = 1;
                }
            } else if (fastScaffold == Scaffold.FastScaffold.Jump_B
                    || fastScaffold == Scaffold.FastScaffold.Jump_C
                    || fastScaffold == Scaffold.FastScaffold.Jump_D) {
                if (mc.player.ticks - fastScaffold.ticks > 4
                        && mc.player.y - keptY < fastScaffold.height
                        && mc.player.velocityY < -0.1) {
                    searchMode = 1;
                    if (fastScaffold.ticks == 0) {
                        multiplyMotion(0.4, false);
                    }
                } else {
                    searchMode = 2;
                }
            } else if (fastScaffold.keepY) {
                searchMode = 2;
            } else if (fastScaffold == Scaffold.FastScaffold.Float_A) {
                if (mc.player.onGround && mc.player.ticks - enableTick > 0) {
                    keptY = -1;
                } else {
                    searchMode = 2;
                }
            }
        }

        return candidates(list, searchMode, keptY) ? firstPlaceable(list, last) : null;
    }

    private static boolean candidates(List<Vec3d> list, int mode, int y) {
        for (int i = -5; i <= 5; i++) {
            for (int j = -5; j <= 5; j++) {
                for (int k = -3; k <= -1; k++) {
                    int n4 = MathHelper.floor(mc.player.y) + k;
                    if (mode <= 0 || n4 == y) {
                        double d2 = mc.player.x + i;
                        double d = mc.player.z + j;
                        SCRATCH.set(MathHelper.floor(d2), n4, MathHelper.floor(d));
                        if (ScaffoldSupport.BlockUtils.isReplaceableSnow(SCRATCH)) {
                            list.add(new Vec3d(d2, n4, d));
                        } else if (!ScaffoldSupport.BlockUtils.isReplaceable(SCRATCH)) {
                            for (int n3 = -1; n3 <= 1; n3 += 2) {
                                switch (mode) {
                                    case 0:
                                        list.add(new Vec3d(d2 + n3, n4, d));
                                        list.add(new Vec3d(d2, n4, d + n3));
                                    case 1:
                                        list.add(new Vec3d(d2, n4 + n3, d));
                                        break;
                                    case 2:
                                        list.add(new Vec3d(d2 + n3, n4, d));
                                        list.add(new Vec3d(d2, n4, d + n3));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (list.isEmpty()) {
            return false;
        }

        HashSet<Long> seen = new HashSet<>(list.size() * 2);
        ArrayList<Vec3d> unique = new ArrayList<>(list.size());

        for (Vec3d vec3 : list) {
            long l = Double.doubleToLongBits(vec3.x)
                    ^ Double.doubleToLongBits(vec3.y) * 31L
                    ^ Double.doubleToLongBits(vec3.z) * 31L * 31L;
            if (seen.add(l)) {
                unique.add(vec3);
            }
        }

        list.clear();
        list.addAll(unique);
        double x = mc.player.x;
        double y2 = mc.player.y - 1.0;
        double z = mc.player.z;
        if (!mc.player.onGround && !ScaffoldSupport.PlayerUtils.jumpKeyDown() && !fastScaffold.canFastScaffold()) {
            y2--;
        }

        double fy = y2;
        Comparator<Vec3d> comparator =
                Comparator.comparingDouble(p -> ScaffoldSupport.WorldUtils.distanceSq(x, fy, z, p));
        if (mc.player.ticks - startTick <= 3) {
            double yaw = mc.player.yaw * (float) (Math.PI / 180.0);
            double dirX = -Math.sin(yaw);
            double dirZ = Math.cos(yaw);
            comparator = comparator.thenComparingDouble(p -> ScaffoldSupport.WorldUtils.behind(x, z, dirX, dirZ, p));
        }

        list.sort(comparator);
        return true;
    }

    private static BlockPos firstPlaceable(List<Vec3d> list, BlockPos last) {
        for (Vec3d vec3 : list) {
            SCRATCH.set(MathHelper.floor(vec3.x), MathHelper.floor(vec3.y), MathHelper.floor(vec3.z));
            if (ScaffoldSupport.BlockUtils.isReplaceable(SCRATCH) && faceFor(SCRATCH, last) != null) {
                return new BlockPos(SCRATCH);
            }
        }

        return null;
    }

    private static float[] rotations(float f, float f2) {
        if (active && rotation != Scaffold.Rotation.None) {
            long l = sincePlace();
            if (rotation == Scaffold.Rotation.Offset) {
                float f6 = (float) ScaffoldSupport.RotationUtils.inputAngleSigned();
                float f7 = mc.player.yaw + f6;
                float f8 = (f7 % 360.0F + 360.0F) % 360.0F;
                float f9 = f8 % 90.0F;
                float f10;
                if (!(f9 >= 80.0F) && !(f9 < 10.0F)) {
                    f10 = f9 >= 10.0F && f9 < 45.0F ? 45.0F : -45.0F;
                } else {
                    f10 = -45.0F;
                }

                if (!mc.player.onGround && rotation.groundSide != 0.0F) {
                    f10 = rotation.groundSide;
                } else {
                    rotation.groundSide = f10;
                }

                if (f10 != rotation.side) {
                    rotation.side = f10;
                    rotation.sideJitter = (float) (Math.signum(f10) * ScaffoldSupport.randomBetween(0.02, 0.08));
                }

                aimYaw = ScaffoldSupport.PlayerUtils.movementAngle() + (f10 + rotation.sideJitter);
            }

            boolean jumpRotation = false;
            if (legitTicks <= 0) {
                if (fastScaffold == Scaffold.FastScaffold.Float_A
                        && fastScaffold.ticks == 1
                        && mc.player.ticks == enableTick) {
                    jumpRotation = true;
                }
            } else {
                legitTicks--;
                jumpRotation = true;
            }

            if (jumpRotation) {
                float f7 = -180.0F;
                float f8 = aimPitch == 100.0F ? mc.player.pitch : aimPitch;
                if (legitMotion.isToggled()) {
                    if (legitTicks == 4) {
                        if (mc.player.yaw - lastPlayerYaw < -0.7) {
                            f7 = -f7;
                        }

                        legitYaw = f7;
                        delay--;
                        jumpRotation = false;
                    } else if (legitTicks == 3) {
                        if (legitYaw > 0.0F) {
                            f7 = -f7;
                        }

                        if (rotation == Scaffold.Rotation.Smooth) {
                            legitTicks = Math.min(legitTicks, 1);
                            if (delay > legitTicks) {
                                delay = legitTicks;
                            }

                            smoothSnap = true;
                        }

                        f8 = randomFloat(20.0F, 25.0F);
                    } else if (legitTicks == 2) {
                        legitYaw = legitYaw + randomFloat(105.0F, 120.0F) * -Math.signum(legitYaw);
                        f7 = legitYaw;
                        f8 = randomFloat(40.0F, 45.0F);
                    } else if (legitTicks == 1) {
                        legitYaw = legitYaw + randomFloat(33.0F, 35.0F) * -Math.signum(legitYaw);
                        f7 = legitYaw;
                        if (aimPitch == 100.0F || rotation == Scaffold.Rotation.Simple) {
                            f8 = randomFloat(50.0F, 55.0F);
                        }
                    } else if (legitTicks == 0) {
                        legitYaw = legitYaw + randomFloat(29.0F, 33.0F) * -Math.signum(legitYaw);
                        f7 = legitYaw;
                        if (aimPitch == 100.0F || rotation == Scaffold.Rotation.Simple) {
                            f8 = randomFloat(65.0F, 70.0F);
                        }
                    }
                }

                if (rotation == Scaffold.Rotation.Offset && rotation.side > 0.0F) {
                    f7 = -f7;
                }

                if (jumpRotation) {
                    return ScaffoldSupport.RotationUtils.fixSensitivity(
                            ScaffoldSupport.PlayerUtils.movementAngle()
                                    + f7
                                    + ScaffoldSupport.RotationUtils.randomOffset(),
                            f8,
                            f,
                            f2,
                            false);
                }
            }

            if (rotation == Scaffold.Rotation.Smooth
                    && lastPlaced != null
                    && ScaffoldSupport.PlayerUtils.groundTicks <= 3L) {
                float yaw = aimYaw;
                float pitch = aimPitch;
                float f7 = smoothLimit;
                if (smoothSnap) {
                    smoothSnap = false;
                    f7 = 81.0F;
                    f7 += randomFloat(-2.0F, 2.0F);
                }

                float f8 = MathHelper.wrapDegrees(yaw - f);
                if (Math.abs(f8) > f7) {
                    yaw = f + (!(f8 > 0.0F) ? -f7 : f7);
                }

                float[] r = ScaffoldSupport.RotationUtils.fixSensitivity(yaw, pitch, f, f2, false);
                return new float[] {r[0], r[1]};
            } else {
                float yaw;
                float pitch;
                if (aimPitch != 100.0F && l < holdTime()) {
                    yaw = aimYaw;
                    pitch = aimPitch;
                    if (l > 5L && ScaffoldSupport.PlayerUtils.isMoving() && Math.random() > 0.3) {
                        yaw = (float)
                                (yaw + ScaffoldSupport.randomBetween(0.05, 0.5) * (!(Math.random() < 0.5) ? 1 : -1));
                        pitch += ScaffoldSupport.RotationUtils.randomOffset();
                    }

                    float[] r = ScaffoldSupport.RotationUtils.fixSensitivity(yaw, pitch, f, f2, false);
                    yaw = r[0];
                    pitch = r[1];
                } else {
                    if (aimPitch != 100.0F) {
                        aimPitch = 100.0F;
                    }

                    int n = mc.player.ticks - startTick;
                    float f8 = ScaffoldSupport.PlayerUtils.movementAngle();
                    float f9 = rotation != Scaffold.Rotation.Simple ? restPitch : 82.5F;
                    f8 += (float) yawOffset;
                    f9 += ScaffoldSupport.RotationUtils.randomOffset();
                    if (n <= 2 && !mc.player.onGround && mc.player.ticks - jumpTick > 3) {
                        BlockPos target = search(null);
                        if (target != null) {
                            Direction facing = faceFor(target, null);
                            if (facing != null && facing != Direction.UP) {
                                BlockPos against = target.offset(facing.getOpposite());
                                float[] r = ScaffoldSupport.RotationUtils.searchBlockRotation(
                                        against, facing, -170.0F, precision, f, false);
                                if (r != null) {
                                    f8 = r[0];
                                    f9 = r[1];
                                }
                            }
                        }
                    } else if (rotation == Scaffold.Rotation.Offset) {
                        f8 = aimYaw;
                    }

                    yaw = f8;
                    pitch = f9;
                    if (n <= 2) {
                        float f10 = n != 0 ? (n != 2 ? 0.8F : 0.55F) : 0.3F;
                        f10 = (float) (f10 + ScaffoldSupport.randomBetween(-0.02, 0.02));
                        yaw = f + f10 * (f8 - f);
                        pitch = f2 + f10 * (f9 - f2);
                        if (Math.abs(yaw - f8) < 5.0F) {
                            delay = 0;
                        }
                    }

                    float[] r = ScaffoldSupport.RotationUtils.fixSensitivity(yaw, pitch, f, f2, false);
                    yaw = r[0];
                    if (yaw != f) {
                        pitch = r[1];
                        yawOffset = ScaffoldSupport.RotationUtils.randomOffset();
                    } else {
                        pitch = f2;
                    }
                }

                return new float[] {yaw, pitch};
            }
        } else {
            return null;
        }
    }

    private static void jumpWith(double d) {
        if (!legitMotion.isToggled()) {
            double previousY = mc.player.velocityY;
            ScaffoldSupport.PlayerUtils.jump(boosted(d));
            if (fastScaffold == Scaffold.FastScaffold.Keep_Y_A && mc.player.velocityY > previousY) {
                keepYJumpTick = mc.player.ticks;
            }
        }
    }

    public static boolean consumeKeepYJump() {
        int age = mc.player.ticks - keepYJumpTick;
        if (active
                && fastScaffold == Scaffold.FastScaffold.Keep_Y_A
                && age >= 0
                && age <= 1
                && !(mc.player.velocityY <= 0.0)) {
            keepYJumpTick = -2;
            return true;
        } else {
            return false;
        }
    }

    private static boolean placeExtra(ItemStack itemStack) {
        BlockPos target = search(lastPlaced);
        if (target == null) {
            return false;
        }

        Direction facing = faceFor(target, lastPlaced);
        if (facing == null) {
            return false;
        }

        BlockPos against = placementAnchor(target, facing);
        float[] r = ScaffoldSupport.RotationUtils.searchBlockRotation(
                against, facing, -170.0F, precision, RotationUtils.serverRotations[0], false);
        if (r == null) {
            return false;
        }

        HitResult hit =
                ScaffoldSupport.PlayerUtils.rayTraceBlock(against, null, mc.interactionManager.getReach(), r[0], r[1]);
        if (hit == null) {
            return false;
        }

        ScaffoldSupport.PlayerUtils.place(hit, itemStack);
        ScaffoldSupport.PlayerUtils.swing(silentSwing.isToggled(), true);
        lastPlaced = target;
        placedAt = System.currentTimeMillis();
        placed.put(target, placedAt);
        return true;
    }

    private static void fastScaffoldTick() {
        rightHeld = Mouse.isButtonDown(1) && mc.screen == null;
        fastScaffold = Scaffold.FastScaffold.values()[
                (int) (!rightHeld ? fastScaffoldSetting.getInput() : fastScaffoldRmbSetting.getInput())];
        boolean moving = ScaffoldSupport.PlayerUtils.isMoving();
        boolean groundMoving = mc.player.onGround && moving;
        boolean can = fastScaffold.canFastScaffold();
        boolean sprintForward = fastScaffold.sprint && ScaffoldSupport.PlayerUtils.direction().forward;
        boolean applied = false;
        if (fastScaffold == lastFastScaffold) {
            if (!can) {
                if (fastEnabled) {
                    fastEnabled = false;
                    fastScaffold.onDisable(false);
                }
            } else if (!fastEnabled) {
                fastEnabled = true;
                fastScaffold.onEnable();
            }
        } else {
            if (fastEnabled && lastFastScaffold != null) {
                lastFastScaffold.onDisable(false);
            }

            if (can) {
                fastScaffold.onEnable();
            }

            fastEnabled = can;
            lastFastScaffold = fastScaffold;
        }

        if (!can) {
            if (!Tower.towering && legitTicks > 0 && mc.player.onGround) {
                delay = 0;
                legitTicks = 0;
            }
        } else {
            switch (fastScaffold) {
                case Constant_A:
                    if (groundMoving) {
                        double d = 0.153 + ScaffoldSupport.PlayerUtils.perSpeedLevel(0.034);
                        if (ScaffoldSupport.PlayerUtils.speedLevel() == 0 && ScaffoldSupport.PlayerUtils.isDiagonal()) {
                            d *= 0.87;
                        }

                        ScaffoldSupport.MoveUtils.strafe(d);
                    }
                    break;
                case Constant_B:
                    if (groundMoving) {
                        ScaffoldSupport.MoveUtils.strafe(0.181);
                    }
                case Float_A:
                case Float_B:
                default:
                    break;
                case Jump_A:
                case Jump_B:
                case Jump_C:
                case Jump_D:
                    if (!groundMoving) {
                        if (moving && jumpFacingForward.isToggled() && mc.player.velocityY < 0.0) {
                            double d = Math.floor(mc.player.y);
                            if (d - 2.0 == keptY && mc.player.y + mc.player.velocityY < d) {
                                legitTicks = !legitMotion.isToggled() ? 1 : 5;
                            }
                        }
                        break;
                    }

                    if (fastScaffold.state != 2) {
                        boolean newY = false;
                        if (mc.player.ticks - fastScaffold.ticks > 10) {
                            keptY = (int) Math.floor(mc.player.y) - 1;
                            newY = true;
                        }

                        if ((jumpFacingForward.isToggled()
                                        || Scaffold.FastScaffold.Float_A.state == 1
                                        || Scaffold.FastScaffold.Float_B.state == 1)
                                && newY) {
                            if (jumpFacingForward.isToggled()) {
                                legitTicks = !legitMotion.isToggled() ? 1 : 5;
                            }

                            fastScaffold.state = 1;
                        }

                        if (fastScaffold.state == 0) {
                            double speed;
                            if (fastScaffold != Scaffold.FastScaffold.Jump_A) {
                                if (!sprintForward) {
                                    speed = !newY ? 0.545 : 0.4;
                                } else {
                                    speed = !newY ? 0.3 : 0.25;
                                }
                            } else {
                                speed = !ScaffoldSupport.PlayerUtils.isDiagonal() ? 0.53 : 0.37;
                            }

                            jumpWith(speed);
                        }
                    } else {
                        fastScaffold.state = 0;
                    }

                    multiplyMotion(0.95, false);
                    break;
                case Keep_Y_A:
                case Keep_Y_B:
                    if (!groundMoving) {
                        if (moving && jumpFacingForward.isToggled() && mc.player.velocityY < 0.0) {
                            double d = Math.floor(mc.player.y);
                            if (d - 1.0 == keptY && mc.player.y + mc.player.velocityY < d) {
                                legitTicks = !legitMotion.isToggled() ? 1 : 5;
                            }
                        }
                    } else {
                        if (fastScaffold.state != 2) {
                            boolean newY = false;
                            if (mc.player.ticks - fastScaffold.ticks > 10) {
                                keptY = (int) Math.floor(mc.player.y) - 1;
                                newY = true;
                            }

                            if ((jumpFacingForward.isToggled()
                                            || Scaffold.FastScaffold.Float_A.state == 1
                                            || Scaffold.FastScaffold.Float_B.state == 1)
                                    && newY) {
                                if (jumpFacingForward.isToggled() && legitTicks == 0) {
                                    legitTicks = !legitMotion.isToggled() ? 1 : 5;
                                }

                                fastScaffold.state = 1;
                                multiplyMotion(ScaffoldSupport.PlayerUtils.speedLevel() <= 0 ? 0.8 : 0.4, false);
                            }

                            if (fastScaffold.state == 0) {
                                double speed;
                                if (!sprintForward) {
                                    speed = !newY ? 0.545 : 0.54;
                                } else {
                                    speed = !newY ? 0.3 : 0.25;
                                }

                                jumpWith(speed);
                            }
                        } else {
                            fastScaffold.state = 0;
                        }

                        multiplyMotion(0.95, false);
                    }
            }

            if (!mc.player.onGround
                    && (fastScaffold == Scaffold.FastScaffold.Jump_D || fastScaffold == Scaffold.FastScaffold.Keep_Y_B)
                    && ScaffoldSupport.PlayerUtils.motionYDecimals() == 30) {
                mc.player.velocityY -= 0.14;
            }

            if (groundMoving) {
                double d = fastScaffoldMotion.getInput();
                if (!legitMotion.isToggled() && ScaffoldSupport.PlayerUtils.direction().diagonal) {
                    d -= 0.02;
                }

                multiplyMotion(d, true);
                applied = true;
            }
        }

        if (!applied && groundMoving) {
            double d = motion.getInput();
            if (!legitMotion.isToggled()) {
                if (slowSpeedPotion.isToggled()) {
                    d -= ScaffoldSupport.PlayerUtils.perSpeedLevel(0.17);
                }

                if (ScaffoldSupport.PlayerUtils.direction().diagonal) {
                    d -= 0.02;
                }

                if (mc.player.ticks - startTick <= 2) {
                    d -= 0.05;
                }
            }

            multiplyMotion(d, true);
        }
    }

    private static long holdTime() {
        if (mc.player.isSprinting()) {
            return 710L;
        } else {
            return !ScaffoldSupport.PlayerUtils.isMoving() ? 460L : 110L;
        }
    }

    private static void stop() {
        if (mc.player == null) {
            clearState();
        } else {
            if (active) {
                stoppedAt = System.currentTimeMillis();
                rotation.onDisable();
                if (mc.screen == null) {
                    restoreSlot();
                }

                if (fastEnabled) {
                    fastScaffold.onDisable(true);
                    fastEnabled = false;
                }

                Tower.stop();
                sneakQueued = false;
                smoothSnap = false;
                rightHeld = false;
                active = false;
                smoothYaw = null;
                jumpState = 1;
                if (mc.player.onGround && rotation != Scaffold.Rotation.Offset) {
                    multiplyMotion(0.75, false);
                }
            }

            keptY = -1;
            lastPlaced = null;
            aimPitch = 100.0F;
        }
    }

    public static void resetKeepY() {
        keptY = -1;
    }

    private static boolean stopOnEdge() {
        if (active) {
            if (safeWalk.isToggled()) {
                if (!fastScaffold.jump && !fastScaffold.keepY) {
                    return true;
                }

                return !fastScaffold.canFastScaffold();
            }

            if ((fastScaffold == Scaffold.FastScaffold.Float_A || fastScaffold == Scaffold.FastScaffold.Float_B)
                    && fastScaffold.canFastScaffold()) {
                return true;
            }
        }

        return false;
    }

    public static Direction faceFor(BlockPos pos, BlockPos only) {
        if (!ScaffoldSupport.BlockUtils.isReplaceableSnow(pos)
                || only != null
                        && (only.getY() > pos.getY() || pos.squaredDistanceTo(only) != 1.0 || !canPlaceAgainst(only))) {
            BlockPos down = pos.down();
            if (!canPlaceAgainst(down) || only != null && !down.equals(only)) {
                double d = mc.player.x - (pos.getX() + 0.5);
                double d2 = mc.player.z - (pos.getZ() + 0.5);
                Direction facing = Math.abs(d) > Math.abs(d2)
                        ? (d > 0.0 ? Direction.EAST : Direction.WEST)
                        : (d2 > 0.0 ? Direction.SOUTH : Direction.NORTH);
                BlockPos toward = pos.offset(facing);
                if (!canPlaceAgainst(toward) || only != null && !toward.equals(only)) {
                    for (Direction side : SIDES) {
                        if (side != facing) {
                            BlockPos other = pos.offset(side);
                            if (canPlaceAgainst(other) && (only == null || other.equals(only))) {
                                return side.getOpposite();
                            }
                        }
                    }

                    return null;
                } else {
                    return facing.getOpposite();
                }
            } else {
                return Direction.UP;
            }
        } else {
            return Direction.UP;
        }
    }

    private static BlockPos placementAnchor(BlockPos target, Direction facing) {
        return facing == Direction.UP && ScaffoldSupport.BlockUtils.isReplaceableSnow(target)
                ? target
                : target.offset(facing.getOpposite());
    }

    public static void multiplyMotion(double d, boolean always) {
        if (always || !legitMotion.isToggled()) {
            ScaffoldSupport.PlayerUtils.multiplyMotion(d);
        }
    }

    private static double speedBonus() {
        int n = ScaffoldSupport.PlayerUtils.speedLevel();
        switch (n) {
            case 0:
                return 0.0;
            case 1:
                return 0.05;
            case 2:
                return speedBoost.getInput() / 10.0;
            default:
                return 0.06 * n;
        }
    }

    private static void afterPlace(BlockPos pos, ItemStack itemStack) {
        if (fastScaffold == Scaffold.FastScaffold.Side
                && fastScaffold.canFastScaffold()
                && (fastScaffold.ticks == -1 || ++fastScaffold.ticks == 2 || fastScaffold.ticks >= 5)) {
            if (fastScaffold.ticks >= 5) {
                fastScaffold.ticks = 0;
            }

            double cx = pos.getX() + 0.5;
            double cz = pos.getZ() + 0.5;
            Direction facing = null;
            switch (ScaffoldSupport.PlayerUtils.facing()) {
                case NORTH:
                case SOUTH:
                    facing = mc.player.x > cx ? Direction.EAST : Direction.WEST;
                    break;
                case WEST:
                case EAST:
                    facing = mc.player.z > cz ? Direction.SOUTH : Direction.NORTH;
            }

            Vec3d vec3 = ScaffoldSupport.BlockUtils.hitVec(pos, facing);
            HitResult hit = new HitResult(Type.BLOCK, vec3, facing, pos);
            if (ScaffoldSupport.BlockUtils.isReplaceable(ScaffoldSupport.BlockUtils.placedPos(hit))) {
                ScaffoldSupport.PlayerUtils.place(hit, itemStack);
                ScaffoldSupport.PlayerUtils.swing(silentSwing.isToggled(), true);
                placedAt = System.currentTimeMillis();
                placed.put(pos, placedAt);
                if (fastScaffold.ticks == -1) {
                    fastScaffold.ticks = 0;
                }

                return;
            }

            fastScaffold.ticks = -1;
        }

        if (multiPlace != Scaffold.MultiPlace.Disabled
                && itemStack.size > 0
                && (Tower.towering || fastScaffold.canFastScaffold())
                && placeExtra(itemStack)
                && multiPlace == Scaffold.MultiPlace._2_extra
                && itemStack.size > 0) {
            placeExtra(itemStack);
        }
    }

    private static boolean canPlaceAgainst(BlockPos pos) {
        if (!placed.isEmpty() && !placed.containsKey(pos) && sincePlace() < 250L) {
            boolean near = false;

            for (Entry<BlockPos, Long> entry : placed.entrySet()) {
                if (mc.player.getSquaredDistanceTo(entry.getKey()) < 12.0) {
                    near = true;
                    break;
                }
            }

            if (near) {
                return false;
            }
        }

        return !ScaffoldSupport.BlockUtils.isReplaceable(pos)
                && !ScaffoldSupport.BlockUtils.isInteractable(ScaffoldSupport.BlockUtils.getBlock(pos));
    }

    private static void postPlayerInput() {
        if (sneakQueued && sincePlace() > 90L) {
            sneakQueued = false;
            ScaffoldSupport.PlayerUtils.setSneak(true);
        }

        if (jumpState == 1) {
            if (ScaffoldSupport.PlayerUtils.jumpKeyDown()) {
                ScaffoldSupport.PlayerUtils.setJump(false);
                jumpState = 2;
            } else {
                jumpState = 0;
            }
        } else if (jumpState == 2) {
            ScaffoldSupport.PlayerUtils.setJump(true);
            jumpState = 0;
        }

        if (fastScaffold != Scaffold.FastScaffold.Float_A && fastScaffold != Scaffold.FastScaffold.Float_B) {
            if ((fastScaffold.jump || fastScaffold.keepY)
                    && legitMotion.isToggled()
                    && fastScaffold.canFastScaffold()
                    && mc.player.onGround
                    && fastScaffold.state == 0) {
                ScaffoldSupport.PlayerUtils.setJump(true);
            }
        } else if (!fastScaffold.canFastScaffold() && fastScaffold.state == 1) {
            fastScaffold.state = 0;
        }
    }

    private static double boosted(double d) {
        d += speedBonus();
        d += 1.0E-4 * Math.random();
        if (ScaffoldSupport.sinceSetback(500L)) {
            d *= ScaffoldSupport.PlayerUtils.speedLevel() > 0 ? 0.85 : 0.95;
        }

        return d;
    }

    private static void update() {
        if (showBlockCount.isToggled()) {
            updateCount();
        }

        if (pausedTicks > 0 && --pausedTicks != 0) {
            stop();
        } else if (!sentCloseWindow
                && (!disableInInventory.isToggled() || mc.screen == null)
                && !ScaffoldSupport.PlayerUtils.isAdventure()) {
            if (autoSwap.isToggled()) {
                selectBlocks();
            }

            ItemStack itemStack = mc.player.getDisplayItemInHand();
            if (!ScaffoldSupport.ItemUtils.isScaffoldBlock(itemStack)) {
                stop();
            } else {
                long l = System.currentTimeMillis();
                if (!active) {
                    startTick = mc.player.ticks;
                    startedAt = l;
                    startPitch = mc.player.pitch;
                    releaseKey(mc.options.attackKey);
                    releaseKey(mc.options.useKey);
                    if (slowOnEnable.isToggled()) {
                        multiplyMotion(0.2, true);
                    }
                }

                active = true;
                fastScaffoldTick();
                if (delay > 0) {
                    delay--;
                } else if (!sentUseOrDig) {
                    BlockPos below = ScaffoldSupport.BlockUtils.below(1.0);
                    airBelowTicks = ScaffoldSupport.BlockUtils.isReplaceable(below) ? ++airBelowTicks : 0;
                    BlockPos pos = place(null, itemStack);
                    if (pos != null) {
                        Tower.onBlockPlaced();
                        placedCount++;
                        if (sneakEvery.getInput() != sneakEvery.getMin()
                                && placedCount % (int) sneakEvery.getInput() == 0) {
                            sneakQueued = true;
                        }

                        if (itemStack.size > 0) {
                            afterPlace(pos, itemStack);
                        }
                    }
                }
            }
        } else {
            stop();
        }
    }

    private static HitResult aim(BlockPos pos, Direction facing, float offset, float lastYaw) {
        float[] r = ScaffoldSupport.RotationUtils.searchBlockRotation(pos, facing, offset, precision, lastYaw, false);
        if (r == null) {
            return null;
        }

        aimYaw = r[0];
        aimPitch = r[1];
        if (rotation == Scaffold.Rotation.Smooth
                && lastPlaced != null
                && ScaffoldSupport.PlayerUtils.groundTicks <= 3L) {
            smoothLimit = 35.0F + (float) (1.0 * Math.random());
            smoothAt = System.currentTimeMillis();
            float f3 = MathHelper.wrapDegrees(aimYaw - lastYaw);
            if (Math.abs(f3) > smoothLimit) {
                return null;
            }
        }

        return ScaffoldSupport.PlayerUtils.rayTraceBlock(pos, null, mc.interactionManager.getReach(), aimYaw, aimPitch);
    }

    private static long sincePlace() {
        return Math.abs(System.currentTimeMillis() - placedAt);
    }

    public static boolean towerAllowed() {
        long l;
        return instance != null
                && instance.isEnabled()
                && tower.isToggled()
                && active
                && ((l = sincePlace()) < 400L || l > 700L);
    }

    private static void preMotion(PreMotionEvent e) {
        boolean floatWasSet = Scaffold.FastScaffold.Float_A.state == 1 || Scaffold.FastScaffold.Float_B.state == 1;
        Scaffold.FastScaffold.Float_A.state = 0;
        Scaffold.FastScaffold.Float_B.state = 0;
        if (fastScaffold.canFastScaffold()) {
            if (fastScaffold == Scaffold.FastScaffold.Float_A) {
                if (e.posY % 1.0 == 0.0 && mc.player.velocityY == -0.0784000015258789) {
                    if (Utils.isHypixel()) {
                        e.posY += 1.0E-13;
                    }

                    fastScaffold.state = 1;
                    if (ScaffoldSupport.PlayerUtils.isMoving()
                            && !mc.player.isSneaking()
                            && !ScaffoldSupport.sinceSetback(3000L)) {
                        double d = fastScaffold.sprint
                                ? 0.2
                                        + ScaffoldSupport.PlayerUtils.perSpeedLevel(
                                                ScaffoldSupport.PlayerUtils.isDiagonal() ? 0.035 : 0.043)
                                : 0.23
                                        + ScaffoldSupport.PlayerUtils.perSpeedLevel(
                                                ScaffoldSupport.PlayerUtils.isDiagonal() ? 0.042 : 0.05);
                        if (ScaffoldSupport.PlayerUtils.speedLevel() == 0 && ScaffoldSupport.PlayerUtils.isDiagonal()) {
                            d *= 0.87;
                        }

                        ScaffoldSupport.MoveUtils.strafe(d);
                        if (!fastScaffold.sprint) {
                            mc.player.setSprinting(true);
                            mc.player.tickSprintingEffect();
                        }
                    }
                }
            } else if (fastScaffold == Scaffold.FastScaffold.Float_B) {
                if (e.posY % 1.0 == 0.0
                        && mc.player.velocityY == -0.0784000015258789
                        && ScaffoldSupport.PlayerUtils.isMoving()
                        && !mc.player.isSneaking()
                        && !ScaffoldSupport.sinceSetback(3000L)) {
                    double d;
                    if (!floatWasSet
                            && (ScaffoldSupport.PlayerUtils.isDiagonal()
                                    || !ScaffoldSupport.PlayerUtils.isAirBelow())) {
                        if (Utils.isHypixel()) {
                            e.posY = e.posY
                                    + (0.001 + Double.parseDouble("0.0000" + ScaffoldSupport.PlayerUtils.idOffset()));
                            e.setOnGround(false);
                        }

                        fastScaffold.state = 1;
                        d = 0.153;
                    } else {
                        d = 0.245 + ScaffoldSupport.PlayerUtils.perSpeedLevel(0.025);
                    }

                    ScaffoldSupport.MoveUtils.strafe(d);
                }
            } else if ((fastScaffold.jump || fastScaffold.keepY)
                    && mc.player.onGround
                    && (jumpFacingForward.isToggled() || floatWasSet)) {
                if (fastScaffold.state == 1) {
                    fastScaffold.state = 2;
                    double d = fastScaffold.sprint && ScaffoldSupport.PlayerUtils.direction().forward
                            ? 0.25
                            : (fastScaffold.keepY ? 0.54 : 0.4);
                    jumpWith(d);
                }

                delay = legitTicks;
            }
        }
    }

    private static float randomFloat(float a, float b) {
        return (float) (a + (b - a) * Math.random());
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (this.isEnabled()) {
            if (!Utils.nullCheck()) {
                clearState();
            } else {
                if (sessionPlayer != mc.player) {
                    this.onEnable();
                }

                this.guiUpdate();
                if (active && rotation != Scaffold.Rotation.None) {
                    RotationHelper.get()
                            .withRotations(
                                    RotationUtils.serverRotations[0],
                                    RotationUtils.serverRotations[1],
                                    Scaffold::update);
                } else {
                    update();
                }

                float[] result = rotations(RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
                if (result != null) {
                    RotationHelper.get().setRotations(result[0], result[1]);
                }

                if (this.getSpoofedSlot() != -1 && originalSlot != mc.player.inventory.selectedSlot) {
                    ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelUpdate(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onInput(PostPlayerInputEvent event) {
        if (!Utils.nullCheck()) {
            clearState();
        } else {
            if (this.isEnabled()) {
                if (sessionPlayer != mc.player) {
                    this.onEnable();
                }

                this.guiUpdate();
                postPlayerInput();
                Tower.handleInput();
            } else if (jumpState != 0) {
                postPlayerInput();
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onMotion(PreMotionEvent event) {
        ScaffoldSupport.updateTicks();
        if (this.isEnabled() && Utils.nullCheck() && sessionPlayer == mc.player) {
            Tower.handleMotion(event);
            preMotion(event);
            lastPlayerYaw = mc.player.yaw;
            if (active) {
                if (!this.canSprint()) {
                    mc.player.setSprinting(false);
                    event.setSprinting(false);
                } else {
                    event.setSprinting(mc.player.isSprinting());
                }

                if (fakeRotation != ScaffoldSupport.FakeRotation.None && rotation != Scaffold.Rotation.None) {
                    float yaw = mc.player.yaw;
                    float pitch = mc.player.pitch;
                    if (fakeRotation != ScaffoldSupport.FakeRotation.Forward) {
                        float progress = Math.min((float) (System.currentTimeMillis() - startedAt) / 140.0F, 1.0F);
                        pitch = startPitch + progress * (84.5F - startPitch);
                        if (fakeRotation == ScaffoldSupport.FakeRotation.Spin) {
                            yaw += (float) ((System.currentTimeMillis() - startedAt) % 750L) / 750.0F * 360.0F;
                        } else {
                            if (smoothYaw == null) {
                                smoothYaw = yaw;
                            }

                            smoothYaw = smoothYaw
                                    + MathHelper.wrapDegrees(
                                                    ScaffoldSupport.PlayerUtils.movementAngle() - 0.01F - smoothYaw)
                                            * 0.4F;
                            yaw = smoothYaw;
                        }
                    }

                    RotationUtils.setFakeRotations(yaw, pitch);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onSendPacket(SendPacketEvent event) {
        if (!event.isCanceled()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof PlayerMoveC2SPacket) {
                sentCloseWindow = false;
                sentUseOrDig = false;
            } else if (packet instanceof CloseInventoryMenuC2SPacket) {
                sentCloseWindow = true;
            } else if (packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerHandActionC2SPacket) {
                sentUseOrDig = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onDispatchPacket(DispatchPacketEvent event) {
        if (this.isEnabled()
                && Utils.nullCheck()
                && sessionPlayer == mc.player
                && event.getPacket() instanceof PlayerMoveC2SPacket) {
            Tower.handleMovementPacket((PlayerMoveC2SPacket) event.getPacket());
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (Utils.nullCheck()) {
            if (event.getPacket() instanceof PlayerMoveS2CPacket) {
                ScaffoldSupport.recordSetback();
            }

            if (active
                    && cancelKnockback.isToggled()
                    && (Tower.towering || ScaffoldSupport.PlayerUtils.isMoving())
                    && (event.getPacket() instanceof ExplosionS2CPacket
                            || event.getPacket() instanceof EntityVelocityS2CPacket
                                    && ((EntityVelocityS2CPacket) event.getPacket()).getId()
                                            == mc.player.getNetworkId())) {
                event.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload event) {
        if (event.world == mc.world) {
            clearState();
            ScaffoldSupport.resetTicks();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent event) {
        if (Utils.nullCheck()) {
            renderPlaced();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END
                && Utils.nullCheck()
                && mc.screen == null
                && showBlockCount.isToggled()) {
            long elapsed = System.currentTimeMillis() - (active ? startedAt : stoppedAt);
            int alpha = active
                    ? (int) (Math.min((float) elapsed / 125.0F, 1.0F) * 240.0F)
                    : (int) (Math.max(1.0F - (float) elapsed / 125.0F, 0.0F) * 240.0F);
            if (alpha > 0) {
                this.renderBlockCount(alpha);
            }
        }
    }

    public boolean isAlwaysSprinting() {
        return active
                && this.isEnabled()
                && this.canSprint()
                && fastScaffold.canFastScaffold()
                && fastScaffold.sprint
                && (mc.options.sprintKey.isPressed()
                        || ModuleManager.sprint != null && ModuleManager.sprint.isEnabled());
    }

    public boolean canSprint() {
        return !active || !this.isEnabled() || (Tower.towering ? Tower.allowsSprinting() : fastScaffold.canSprint());
    }

    public static boolean canSafeWalk() {
        return instance != null
                && instance.isEnabled()
                && Utils.nullCheck()
                && ScaffoldSupport.PlayerUtils.isAirBelow()
                && (!Tower.towering || !Tower.disableSafewalk.isToggled())
                && stopOnEdge();
    }

    public int getSpoofedSlot() {
        return this.isEnabled() && active && autoSwap.isToggled() && spoofItem.isToggled() && hasSwapped
                ? originalSlot
                : -1;
    }

    private static void rememberSlot() {
        if (originalSlot == -1 && mc.player != null) {
            originalSlot = mc.player.inventory.selectedSlot;
        }
    }

    private static void restoreSlot() {
        if (originalSlot != -1 && mc.player != null && autoSwap.isToggled() && mc.screen == null) {
            Utils.switchSlot(originalSlot, false);
        }

        hasSwapped = false;
        originalSlot = -1;
    }

    private static void selectBlocks() {
        if (ModuleManager.autoSwap != null) {
            rememberSlot();
            if (ModuleManager.autoSwap.swapForScaffold()) {
                hasSwapped = true;
            }
        }
    }

    private static int countBlocks() {
        int count = 0;
        if (mc.player != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.inventory.getItem(i);
                if (ScaffoldSupport.ItemUtils.isScaffoldBlock(stack)) {
                    count += ModuleManager.autoSwap == null
                            ? stack.size
                            : ModuleManager.autoSwap.getBlockCount(i, stack);
                }
            }
        }

        return count;
    }

    public int getTotalBlocks() {
        return countBlocks();
    }

    private static void releaseKey(KeyBinding binding) {
        KeyBinding.set(binding.getKeyCode(), false);
    }

    private static void clearState() {
        keepYJumpTick = -2;
        active = false;
        fastEnabled = false;
        rightHeld = false;
        disablingModule = false;
        keptY = -1;
        lastPlaced = null;
        aimPitch = 100.0F;
        placedCount = 0;
        pausedTicks = 0;
        airBelowTicks = 0;
        jumpState = 0;
        delay = 0;
        legitTicks = 0;
        stoppedAt = 0L;
        startedAt = 0L;
        placedAt = 0L;
        originalSlot = -1;
        sentCloseWindow = false;
        sentUseOrDig = false;
        smoothSnap = false;
        sneakQueued = false;
        hasSwapped = false;
        smoothYaw = null;
        sessionPlayer = null;
        countText = null;
        placed.clear();

        for (Scaffold.Rotation value : Scaffold.Rotation.values()) {
            value.onDisable();
        }

        for (Scaffold.FastScaffold value : Scaffold.FastScaffold.values()) {
            value.state = value.ticks = 0;
            value.height = 0.0;
        }

        lastFastScaffold = Scaffold.FastScaffold.Disabled;
        Tower.reset();
    }

    public float getPosX() {
        this.syncPosition();
        return this.posX;
    }

    public float getPosY() {
        this.syncPosition();
        return this.posY;
    }

    public float getRelativePosX() {
        this.syncPosition();
        return this.relativePosX;
    }

    public float getRelativePosY() {
        this.syncPosition();
        return this.relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.relativePosX = normalizedX;
        this.relativePosY = normalizedY;
        this.syncPosition();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        this.setAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        this.relativePosX = absoluteX / w;
        this.relativePosY = absoluteY / h;
    }

    public void resetPosition() {
        this.relativePosX = Float.NaN;
        this.relativePosY = Float.NaN;
        this.posX = Float.NaN;
        this.posY = Float.NaN;
        this.syncPosition();
    }

    private void syncPosition() {
        this.syncPosition(new Window(mc));
    }

    private void syncPosition(Window resolution) {
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (Float.isNaN(this.posX) || Float.isNaN(this.posY)) {
                this.posX = w / 2.0F + 9.0F;
                this.posY = h / 2.0F - mc.textRenderer.fontHeight / 2.0F + 1.0F;
            }

            this.relativePosX = this.posX / w;
            this.relativePosY = this.posY / h;
        }

        this.posX = this.relativePosX * w;
        this.posY = this.relativePosY * h;
    }

    private void renderBlockCount(int alpha) {
        this.renderBlockCount(alpha, false);
    }

    private void renderBlockCount(int alpha, boolean editing) {
        int blocks = this.getTotalBlocks();
        if (editing && blocks == 0) {
            blocks = 64;
        }

        String color = "§";
        if (blocks <= 5) {
            color = color + "c";
        } else if (blocks <= 15) {
            color = color + "6";
        } else if (blocks <= 25) {
            color = color + "e";
        } else {
            color = "";
        }

        int colorAlpha = Utils.mergeAlpha(-1, alpha);
        Window scaledResolution = new Window(mc);
        this.syncPosition(scaledResolution);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        mc.textRenderer.drawWithShadow(
                color + blocks + " §rblock" + (blocks == 1 ? "" : "s"), this.posX, this.posY, colorAlpha);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private class EditScreen extends Screen {
        private ButtonWidget resetBtn;
        private boolean dragging;
        private float actualX;
        private float actualY;
        private float lastActualX;
        private float lastActualY;
        private int lastMouseX;
        private int lastMouseY;

        private EditScreen() {}

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetBtn = new ButtonWidget(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            Scaffold.this.syncPosition(new Window(this.minecraft));
            this.actualX = Scaffold.this.posX;
            this.actualY = Scaffold.this.posY;
        }

        public void render(int mouseX, int mouseY, float partialTicks) {
            Window resolution = new Window(this.minecraft);
            if (!this.dragging) {
                Scaffold.this.syncPosition(resolution);
                this.actualX = Scaffold.this.posX;
                this.actualY = Scaffold.this.posY;
            }

            fill(0, 0, this.width, this.height, -1308622848);
            Scaffold.this.setAbsolutePosition(this.actualX, this.actualY, resolution);
            Scaffold.this.renderBlockCount(255, true);
            this.actualX = Scaffold.this.posX;
            this.actualY = Scaffold.this.posY;
            String message = "Edit the position by dragging.";
            int textX = resolution.getWidth() / 2 - this.textRenderer.getWidth(message) / 2;
            int textY = resolution.getHeight() / 2 - 20;
            RenderUtils.drawColoredString(message, '-', textX, textY, 2L, 0L, true, this.minecraft.textRenderer);
            ClientPlatform.handleInput(this);
            super.render(mouseX, mouseY, partialTicks);
        }

        protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
            if (button == 0) {
                if (this.dragging) {
                    this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                    this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
                } else {
                    int blocks = Scaffold.this.getTotalBlocks();
                    if (blocks == 0) {
                        blocks = 64;
                    }

                    String text = blocks + " block" + (blocks == 1 ? "" : "s");
                    int textWidth = this.textRenderer.getWidth(text);
                    int textHeight = this.textRenderer.fontHeight;
                    float minX = Scaffold.this.posX - 2.0F;
                    float minY = Scaffold.this.posY - 2.0F;
                    float maxX = Scaffold.this.posX + textWidth + 2.0F;
                    float maxY = Scaffold.this.posY + textHeight + 2.0F;
                    if (mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY) {
                        this.dragging = true;
                        this.lastMouseX = mouseX;
                        this.lastMouseY = mouseY;
                        this.lastActualX = this.actualX;
                        this.lastActualY = this.actualY;
                    }
                }
            }
        }

        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        public void buttonClicked(ButtonWidget button) {
            if (button == this.resetBtn) {
                Scaffold.this.resetPosition();
                this.actualX = Scaffold.this.posX;
                this.actualY = Scaffold.this.posY;
            }
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }

    public enum FastScaffold {
        Disabled(false, false, false),
        Vanilla(true, false, false),
        Constant_A(true, false, false),
        Constant_B(false, false, false),
        Float_A(true, false, false),
        Float_B(true, false, false),
        Jump_A(false, true, false),
        Jump_B(false, true, false),
        Jump_C(true, true, false),
        Jump_D(true, true, false),
        Keep_Y_A(false, false, true),
        Keep_Y_B(false, false, true),
        Side(true, false, false);

        public final boolean sprint;
        public final boolean jump;
        public final boolean keepY;
        public int state;
        public int ticks;
        public double height;

        FastScaffold(boolean sprint, boolean jump, boolean keepY) {
            this.sprint = sprint;
            this.jump = jump;
            this.keepY = keepY;
        }

        public boolean canSprint() {
            if (!this.canFastScaffold()) {
                return Scaffold.mc.player.ticks == Scaffold.startTick && Scaffold.jumpState == 2
                        ? false
                        : ModuleManager.movementFix != null && ModuleManager.movementFix.isEnabled();
            } else if (!Scaffold.legitMotion.isToggled() || !this.jump && !this.keepY) {
                return !this.sprint ? false : this != Side || this.ticks != -1;
            } else {
                return Scaffold.legitTicks > 0;
            }
        }

        public void onEnable() {
            Scaffold.enableTick = Scaffold.mc.player.ticks;
            this.state = 0;
            if (this == Float_A) {
                if (Scaffold.mc.player.onGround) {
                    this.ticks = 1;
                    Scaffold.delay = 1;
                    if (Scaffold.mc.player.velocityY == -0.0784000015258789) {
                        ScaffoldSupport.PlayerUtils.jump();
                    }

                    Scaffold.keptY = (int) Math.floor(Scaffold.mc.player.y) - 1;
                }
            } else if (this == Jump_B || this == Jump_C) {
                this.height = 2.2;
            } else if (this == Jump_D) {
                this.height = 2.5;
            } else if (this == Side) {
                this.ticks = 4;
            }
        }

        public boolean canFastScaffold() {
            if (!Scaffold.active || this == Disabled) {
                return false;
            }

            if (!Tower.towering && !ScaffoldSupport.PlayerUtils.jumpKeyDown()) {
                if (this != Float_A) {
                    if (!ScaffoldSupport.PlayerUtils.isMoving() || Scaffold.mc.player.isSneaking()) {
                        return false;
                    }

                    if (this.sprint && !this.jump && !ScaffoldSupport.PlayerUtils.direction().forward) {
                        return false;
                    }
                }

                return this == Side ? !ScaffoldSupport.PlayerUtils.isDiagonal() : true;
            } else {
                return false;
            }
        }

        public void onDisable(boolean disabling) {
            if (this.jump || this.keepY) {
                Scaffold.keptY = -1;
            }

            if (this.jump && !disabling && !Tower.towering && Tower.slowedTicks.getInput() > 0.0) {
                ScaffoldSupport.MoveUtils.setSpeed(0.05);
            }

            if (this == Float_A) {
                if (Scaffold.disablingModule) {
                    if (Scaffold.mc.player.onGround && ScaffoldSupport.PlayerUtils.isAirBelow()) {
                        ScaffoldSupport.PlayerUtils.jump();
                    }

                    if (Scaffold.rotation != Scaffold.Rotation.Offset) {
                        Scaffold.multiplyMotion(0.58, false);
                    }
                }
            } else if (this == Float_B && Scaffold.disablingModule) {
                Scaffold.multiplyMotion(0.85, false);
            }

            this.ticks = 0;
        }
    }

    public enum MultiPlace {
        Disabled,
        _1_extra,
        _2_extra;
    }

    public enum Rotation {
        None,
        Strict,
        Smooth,
        Simple,
        Lazy,
        Offset;

        public float side;
        public float sideJitter;
        public float groundSide;

        public void onDisable() {
            this.groundSide = 0.0F;
            this.sideJitter = 0.0F;
            this.side = 0.0F;
        }
    }
}

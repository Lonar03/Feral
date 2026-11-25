package keystrokesmod.module.impl.network;

import keystrokesmod.Feral;
import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Autoblock;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class LagRange extends Module {
    private static final double MINIMUM_DISTANCE_SQ = 9.0;
    private static final long INDICATOR_INTERP_MS = 80L;
    private static final double POS_EPS = 1.0E-6;
    private final SliderSetting range;
    private final SliderSetting maximumDelay;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting sprintReset;
    private final ButtonSetting blockSword;
    private final ButtonSetting usedSplashPotion;
    private final ButtonSetting holdingWeapon;
    private final ButtonSetting realPositionIndicator;
    private final ButtonSetting showInFirstPerson;
    private final ColorSetting indicatorColor;
    private final SliderSetting indicatorLineWidth;
    private final ButtonSetting indicatorFilled;
    private PlayerEntity currentTarget;
    private double lastDistSq = -1.0;
    private boolean isLagging;
    private int lastSelfHurtTime;
    private int lastTargetHurtTime;
    private int hitMarkedEntityId;
    private boolean lastSprintState;
    private boolean lastBlockingState;
    private LagRequest outboundLag;
    private Vec3d indicatorInterpFrom;
    private Vec3d indicatorInterpTo;
    private long indicatorInterpStartMs;

    public LagRange() {
        super("Lag Range", Module.category.network);
        this.liteModule = true;
        this.registerSetting(this.range = new SliderSetting("Range", 6.0, 3.0, 10.0, 0.1));
        this.registerSetting(this.maximumDelay = new SliderSetting("Maximum delay", "ms", 200.0, 50.0, 1000.0, 10.0));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(new DescriptionSetting("Flush conditions"));
        this.registerSetting(this.sprintReset = new ButtonSetting("Sprint reset", true));
        this.registerSetting(this.blockSword = new ButtonSetting("Block sword", true));
        this.registerSetting(this.usedSplashPotion = new ButtonSetting("Used splash potion", true));
        this.registerSetting(new DescriptionSetting("Indicator"));
        this.registerSetting(this.realPositionIndicator = new ButtonSetting("Real position indicator", true));
        this.registerSetting(this.showInFirstPerson = new ButtonSetting("Show in first person", false));
        this.registerSetting(this.indicatorColor = new ColorSetting("Indicator color", 255, 0, 0, 100));
        this.registerSetting(this.indicatorLineWidth = new SliderSetting("Indicator line width", 2.0, 1.0, 5.0, 0.5));
        this.registerSetting(this.indicatorFilled = new ButtonSetting("Indicator filled", false));
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(this.holdingWeapon = new ButtonSetting("Holding a weapon", true));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        this.flushLag();
        this.resetState();
    }

    @Override
    public String getInfo() {
        return (int) this.maximumDelay.getInput() + "ms";
    }

    public Vec3d getLagRangePosition() {
        return !this.isLagging ? null : Feral.lagHandler.getLastReleasedServerPosition();
    }

    public PlayerEntity getLagRangeTarget() {
        return this.isLagging && this.currentTarget != null && !this.currentTarget.removed ? this.currentTarget : null;
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (Utils.nullCheck() && !mc.player.removed && mc.world != null) {
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.isActivelyMining()) {
                if (this.isLagging) {
                    this.flushLag();
                }
            } else {
                Autoblock autoblock = (Autoblock) ModuleManager.getModule(Autoblock.class);
                if (autoblock != null && autoblock.isActive()) {
                    if (this.isLagging) {
                        this.flushLag();
                    }
                } else {
                    double rangeSq = this.range.getInput() * this.range.getInput();
                    boolean moving = this.isMoving();
                    PlayerEntity nextTarget = CombatTargeting.findTarget(rangeSq, this.ignoreTeammates.isToggled());
                    if (!this.sameTarget(nextTarget)) {
                        if (this.isLagging) {
                            this.flushLag();
                        }

                        this.lastDistSq = -1.0;
                        this.hitMarkedEntityId = -1;
                        this.lastTargetHurtTime = nextTarget != null ? nextTarget.damagedTimer : 0;
                    }

                    this.currentTarget = nextTarget;
                    if (this.currentTarget != null) {
                        double distSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(this.currentTarget);
                        if (this.isLagging) {
                            if (distSq > rangeSq) {
                                this.flushLag();
                                this.lastDistSq = distSq;
                                this.hitMarkedEntityId = -1;
                                this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                return;
                            }

                            if (this.lastDistSq >= 0.0 && distSq >= this.lastDistSq) {
                                boolean hitHold = this.hitMarkedEntityId == this.currentTarget.getNetworkId()
                                        && distSq <= 9.0
                                        && mc.player.damagedTimer == 0;
                                if (!hitHold) {
                                    this.flushLag();
                                    this.lastDistSq = distSq;
                                    this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                    return;
                                }
                            }

                            int hurtTime = mc.player.damagedTimer;
                            if (hurtTime > this.lastSelfHurtTime) {
                                this.flushLag();
                                this.hitMarkedEntityId = -1;
                                this.lastSelfHurtTime = hurtTime;
                                this.lastDistSq = distSq;
                                this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                return;
                            }

                            this.lastSelfHurtTime = hurtTime;
                            Feral.lagHandler.releaseExpiredPackets(
                                    EnumLagDirection.OUTBOUND, (long) this.maximumDelay.getInput());
                            if (this.holdingWeapon.isToggled() && !Utils.holdingWeapon()) {
                                this.flushLag();
                                this.lastDistSq = distSq;
                                this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                return;
                            }

                            if (this.sprintReset.isToggled()) {
                                boolean sprintingNow = mc.player.isSprinting();
                                if (sprintingNow && !this.lastSprintState) {
                                    this.flushLag();
                                    this.lastSprintState = sprintingNow;
                                    this.lastDistSq = distSq;
                                    this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                    return;
                                }

                                this.lastSprintState = sprintingNow;
                            }

                            if (this.blockSword.isToggled()) {
                                boolean blockingNow = mc.player.isSwordBlocking();
                                if (blockingNow && !this.lastBlockingState) {
                                    this.flushLag();
                                    this.lastBlockingState = blockingNow;
                                    this.lastDistSq = distSq;
                                    this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                    return;
                                }

                                this.lastBlockingState = blockingNow;
                            }

                            if (this.usedSplashPotion.isToggled() && mc.player.hasItemInUse()) {
                                ItemStack held = mc.player.getDisplayItemInHand();
                                if (held != null
                                        && held.getItem() instanceof PotionItem
                                        && PotionItem.isSplashPotion(held.getMetadata())) {
                                    this.flushLag();
                                    this.lastDistSq = distSq;
                                    this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                                    return;
                                }
                            }

                            this.lastDistSq = distSq;
                            this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                            return;
                        }

                        int hurtTime = mc.player.damagedTimer;
                        if (hurtTime > this.lastSelfHurtTime) {
                            this.hitMarkedEntityId = -1;
                        }

                        this.lastSelfHurtTime = hurtTime;
                        this.lastSprintState = mc.player.isSprinting();
                        this.lastBlockingState = mc.player.isSwordBlocking();
                        if (hurtTime == 0 && this.lastTargetHurtTime == 0 && this.currentTarget.damagedTimer > 0) {
                            this.hitMarkedEntityId = this.currentTarget.getNetworkId();
                        }

                        this.lastTargetHurtTime = this.currentTarget.damagedTimer;
                        boolean closing = this.lastDistSq >= 0.0 && distSq < this.lastDistSq;
                        boolean outsideMinDist = distSq > 9.0;
                        boolean weaponOk = !this.holdingWeapon.isToggled() || Utils.holdingWeapon();
                        boolean hitMarkedHere = this.hitMarkedEntityId == this.currentTarget.getNetworkId();
                        boolean hitStart = hitMarkedHere && distSq <= 9.0 && hurtTime == 0 && moving && weaponOk;
                        this.lastDistSq = distSq;
                        if (hurtTime == 0 && weaponOk && moving && (closing && outsideMinDist || hitStart)) {
                            this.startLag();
                        }
                    } else {
                        if (this.isLagging) {
                            this.flushLag();
                        }

                        this.lastDistSq = -1.0;
                        this.hitMarkedEntityId = -1;
                        this.lastTargetHurtTime = 0;
                    }
                }
            }
        } else {
            if (this.isLagging) {
                this.flushLag();
            }

            this.resetState();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onAttackEvent(AttackEvent e) {
        if (this.isLagging && e.attacker == mc.player) {
            this.flushLag();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGameTick(GameTickEvent e) {
        if (mc.screen != null) {
            if (this.isLagging) {
                this.flushLag();
            }

            this.resetState();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (!this.isLagging) {
                this.clearIndicatorInterp();
            } else if (this.realPositionIndicator.isToggled()) {
                if (mc.options.perspective != 0 || this.showInFirstPerson.isToggled()) {
                    Vec3d delayedPos = Feral.lagHandler.getLastReleasedServerPosition();
                    if (delayedPos == null) {
                        this.clearIndicatorInterp();
                    } else {
                        long nowMs = System.currentTimeMillis();
                        if (this.indicatorInterpTo == null) {
                            this.indicatorInterpFrom = delayedPos;
                            this.indicatorInterpTo = delayedPos;
                            this.indicatorInterpStartMs = nowMs;
                        } else if (serverPosChanged(delayedPos, this.indicatorInterpTo)) {
                            double te = Math.min(1.0, (nowMs - this.indicatorInterpStartMs) / 80.0);
                            this.indicatorInterpFrom = lerpVec3(this.indicatorInterpFrom, this.indicatorInterpTo, te);
                            this.indicatorInterpTo = delayedPos;
                            this.indicatorInterpStartMs = nowMs;
                        }

                        double t = Math.min(1.0, (nowMs - this.indicatorInterpStartMs) / 80.0);
                        Vec3d drawPos = lerpVec3(this.indicatorInterpFrom, this.indicatorInterpTo, t);
                        double viewX = mc.getEntityRenderDispatcher().cameraX;
                        double viewY = mc.getEntityRenderDispatcher().cameraY;
                        double viewZ = mc.getEntityRenderDispatcher().cameraZ;
                        float halfW = mc.player.width / 2.0F;
                        float height = mc.player.height;
                        Box worldBox = new Box(
                                drawPos.x - halfW,
                                drawPos.y,
                                drawPos.z - halfW,
                                drawPos.x + halfW,
                                drawPos.y + height,
                                drawPos.z + halfW);
                        Vec3d cameraPos = Utils.getCameraPos(e.partialTicks);
                        if (!worldBox.contains(cameraPos)) {
                            Box box = worldBox.moved(-viewX, -viewY, -viewZ);
                            float r = this.indicatorColor.getRed() / 255.0F;
                            float g = this.indicatorColor.getGreen() / 255.0F;
                            float b = this.indicatorColor.getBlue() / 255.0F;
                            float a = this.indicatorColor.getAlpha() / 255.0F;
                            GL11.glPushMatrix();
                            GlStateManager.enableBlend();
                            GlStateManager.disableTexture();
                            GlStateManager.disableDepthTest();
                            GlStateManager.depthMask(false);
                            GL11.glBlendFunc(770, 771);
                            if (this.indicatorFilled.isToggled()) {
                                RenderUtils.drawBoundingBox(box, r, g, b, a);
                            }

                            GL11.glLineWidth((float) this.indicatorLineWidth.getInput());
                            GL11.glColor4f(r, g, b, a);
                            WorldRenderer.renderOutlineShape(box);
                            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                            GlStateManager.enableDepthTest();
                            GlStateManager.depthMask(true);
                            GlStateManager.enableTexture();
                            GlStateManager.disableBlend();
                            GL11.glPopMatrix();
                        }
                    }
                }
            }
        }
    }

    private void startLag() {
        this.outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Feral.lagHandler.requestLag(this.outboundLag);
        this.isLagging = true;
    }

    private void flushLag() {
        if (this.isLagging) {
            if (this.outboundLag != null) {
                this.outboundLag.getTimeout().forceTimeOut();
                this.outboundLag = null;
            }

            this.isLagging = false;
            this.clearIndicatorInterp();
        }
    }

    private void resetState() {
        this.currentTarget = null;
        this.lastDistSq = -1.0;
        this.isLagging = false;
        this.lastSelfHurtTime = 0;
        this.lastTargetHurtTime = 0;
        this.hitMarkedEntityId = -1;
        this.lastSprintState = false;
        this.lastBlockingState = false;
        this.outboundLag = null;
        this.clearIndicatorInterp();
    }

    private void clearIndicatorInterp() {
        this.indicatorInterpFrom = null;
        this.indicatorInterpTo = null;
        this.indicatorInterpStartMs = 0L;
    }

    private static boolean serverPosChanged(Vec3d a, Vec3d b) {
        return Math.abs(a.x - b.x) > 1.0E-6 || Math.abs(a.y - b.y) > 1.0E-6 || Math.abs(a.z - b.z) > 1.0E-6;
    }

    private static Vec3d lerpVec3(Vec3d from, Vec3d to, double t) {
        if (t <= 0.0) {
            return from;
        } else {
            return t >= 1.0
                    ? to
                    : new Vec3d(
                            from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t);
        }
    }

    private boolean sameTarget(PlayerEntity nextTarget) {
        return this.currentTarget != null && nextTarget != null
                ? this.currentTarget.getNetworkId() == nextTarget.getNetworkId()
                : this.currentTarget == nextTarget;
    }

    private boolean isMoving() {
        return mc.player.forwardSpeed != 0.0F || mc.player.sidewaysSpeed != 0.0F;
    }
}

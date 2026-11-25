package keystrokesmod.module.impl.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import keystrokesmod.Feral;
import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.displace.VoidDisplacePlanner;
import keystrokesmod.utility.displace.VoidDisplaceSearch;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class Displace extends Module {
    private static final int DISPLACE_WINDOW_TICKS = 10;
    private static final int DISPLACEMENT_LOCK_IDLE_TICKS = 20;
    private static final long VOID_DEBUG_DURATION_MS = 30000L;
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_BASE_GAP = 0.3;
    private static final long ARROW_NUDGE_MS = 320L;
    private static final double ARROW_NUDGE_DISTANCE = 0.3;
    private static final double ARROW_NUDGE_DECAY = 6.0;
    private static final double ARROW_NUDGE_FADE_START = 0.58;
    private static final double ARROW_NUDGE_FADE_DECAY = 5.0;
    private static final int OVERRIDE_MAX_FLICK_TICKS = 5;
    private static final int OVERRIDE_ATTACK_TICKS = 2;
    private static final int OVERRIDE_RESTORE_TICKS = 2;
    private static final float OVERRIDE_FLICK_TOLERANCE = 6.0F;
    private static final double OVERRIDE_TARGET_RANGE_SQ = 25.0;
    private final SliderSetting mode;
    private final SliderSetting yawOffset;
    private final SliderSetting scanRadius;
    private final SliderSetting delay;
    private final SliderSetting direction;
    private final ButtonSetting findVoid;
    private final ButtonSetting ignoreOneBlockWall;
    private final ButtonSetting predictMovement;
    private final ButtonSetting blink;
    private final ButtonSetting overrideAttack;
    private final ButtonSetting renderArrow;
    private final ButtonSetting onlyKnockbackItems;
    private final ButtonSetting requireSprinting;
    private final ButtonSetting ignoreTeammates;
    private final ButtonSetting weaponOnly;
    private boolean displaceThisTick = false;
    private boolean active = false;
    private boolean hasKB = false;
    private boolean compensateNextTick = false;
    private boolean displaceLeft = false;
    private boolean wasDisplacingLastTick = false;
    private boolean releaseBlinkNextGameTick = false;
    private PlayerEntity arrowPlayer;
    private float arrowYaw;
    private float arrowFadeStartAlpha;
    private float arrowFadeEndAlpha;
    private long arrowFadeStartMs;
    private boolean arrowVisible;
    private long arrowNudgeStartMs = -1L;
    private int arrowNudgePlayerId = -1;
    private float arrowNudgeYaw;
    private int tickCounter;
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();
    private final Map<Integer, Displace.DisplacementLock> targetDisplacementLocks = new HashMap<>();
    private LagRequest outboundBlink;
    private final VoidDisplaceSearch voidSearch = new VoidDisplaceSearch();
    private VoidDisplacePlanner.Result lastVoidResult;
    private int lastVoidTargetId = -1;
    private Displace.VoidDebugScan latestVoidDebugScan;
    private Displace.VoidDebugScan frozenVoidDebugScan;
    private long frozenVoidDebugExpiresAtMs;
    private Displace.OverrideAttackState overrideAttackState = Displace.OverrideAttackState.IDLE;
    private PlayerEntity overrideTarget;
    private float overrideTargetYaw;
    private float overrideTargetPitch;
    private float overrideFlickYaw;
    private float overrideFlickOffset;
    private boolean overrideAbsoluteFlickYaw;
    private boolean overrideFlickSkipped;
    private boolean overrideAwayPacketSent;
    private boolean overrideAttackInProgress;
    private boolean overrideAttackPacketSent;
    private boolean overrideFromKillAura;
    private boolean suppressUseForOverrideAttackTick;
    private int overrideStateTicks;
    private long lastOverrideFlickMs = -1L;
    private static final String[] MODES = new String[] {"Offset", "Void"};
    private static final String[] DIRECTIONS = new String[] {"Left", "Right"};

    public Displace() {
        super("Displace", Module.category.combat);
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(this.yawOffset = new SliderSetting("Yaw offset", 90.0, 0.0, 180.0, 1.0));
        this.registerSetting(this.scanRadius = new SliderSetting("Scan radius", " block", 6.0, 1.0, 12.0, 0.5));
        this.registerSetting(this.delay = new SliderSetting("Delay", "ms", 0.0, 0.0, 500.0, 50.0));
        this.registerSetting(this.direction = new SliderSetting("Direction", 0, DIRECTIONS));
        this.registerSetting(this.findVoid = new ButtonSetting("Find void", false));
        this.registerSetting(this.blink = new ButtonSetting("Blink", false));
        this.registerSetting(this.ignoreOneBlockWall = new ButtonSetting("Ignore one block wall", false));
        this.registerSetting(this.predictMovement = new ButtonSetting("Predict movement", false));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(
                this.onlyKnockbackItems = new ButtonSetting("Only knockback items", false, "Has knockback"));
        this.registerSetting(this.requireSprinting = new ButtonSetting("Require sprinting", true));
        this.registerSetting(this.overrideAttack = new ButtonSetting("Override attack", false));
        this.registerSetting(this.renderArrow = new ButtonSetting("Render arrow", true));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.liteModule = true;
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        boolean offsetMode = !this.isVoidMode();
        this.direction.setVisible(offsetMode, this);
        this.findVoid.setVisible(offsetMode, this);
        this.scanRadius.setVisible(!offsetMode, this);
        this.ignoreOneBlockWall.setVisible(!offsetMode, this);
        this.predictMovement.setVisible(!offsetMode, this);
    }

    @Override
    public String getInfo() {
        return MODES[(int) this.mode.getInput()];
    }

    @Override
    public void onEnable() {
        this.displaceThisTick = false;
        this.active = false;
        this.hasKB = false;
        this.compensateNextTick = false;
        this.wasDisplacingLastTick = false;
        this.releaseBlinkNextGameTick = false;
        this.clearArrow();
        this.clearVoidDebugState();
        this.tickCounter = 0;
        this.targetWindowStartTicks.clear();
        this.targetDisplacementLocks.clear();
        this.resetOverrideAttackState();
        this.lastOverrideFlickMs = -1L;
        this.releaseBlink();
    }

    @Override
    public void onDisable() {
        this.active = false;
        this.compensateNextTick = false;
        this.wasDisplacingLastTick = false;
        this.releaseBlinkNextGameTick = false;
        this.clearArrow();
        this.clearVoidDebugState();
        this.targetWindowStartTicks.clear();
        this.targetDisplacementLocks.clear();
        this.resetOverrideAttackState();
    }

    @Override
    public void guiButtonToggled(ButtonSetting button) {
        if (button == this.overrideAttack) {
            this.resetOverrideAttackState();
            this.releaseBlinkNextGameTick = false;
        }
    }

    private static int msToTicks(double ms) {
        return ms <= 0.0 ? 0 : (int) Math.ceil(ms / 50.0);
    }

    private boolean anyMovementKey() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    private boolean isVoidMode() {
        return this.mode.getInput() == 1.0;
    }

    private boolean tryFindVoidDirection(PlayerEntity target) {
        double dx = target.x - mc.player.x;
        double dz = target.z - mc.player.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) {
            return false;
        }

        dx /= dist;
        dz /= dist;
        double rightX = -dz;
        double rightZ = dx;
        double eyeY = target.y + target.getEyeHeight();
        int leftVoidCount = 0;
        int rightVoidCount = 0;

        for (int i = 1; i <= 12; i++) {
            double off = i * 0.5;
            double rx = target.x + rightX * off;
            double rz = target.z + rightZ * off;
            if (mc.world.rayTrace(new Vec3d(rx, eyeY, rz), new Vec3d(rx, eyeY - 10.0, rz)) == null) {
                rightVoidCount++;
            }

            double lx = target.x - rightX * off;
            double lz = target.z - rightZ * off;
            if (mc.world.rayTrace(new Vec3d(lx, eyeY, lz), new Vec3d(lx, eyeY - 10.0, lz)) == null) {
                leftVoidCount++;
            }
        }

        if (leftVoidCount == 0 && rightVoidCount == 0) {
            return false;
        }

        if (leftVoidCount != rightVoidCount) {
            this.displaceLeft = leftVoidCount > rightVoidCount;
        }

        return true;
    }

    private Float previousVoidYaw(PlayerEntity target) {
        Displace.DisplacementLock lock = this.targetDisplacementLocks.get(target.getNetworkId());
        if (lock != null) {
            return lock.yaw;
        } else {
            return this.lastVoidTargetId == target.getNetworkId() && this.lastVoidResult != null
                    ? this.lastVoidResult.best.yaw
                    : null;
        }
    }

    private void prepareVoidSearch(float playerYaw) {
        if (!this.isVoidMode()) {
            this.voidSearch.clear();
        } else if (mc.screen != null) {
            this.voidSearch.forgetTarget();
        } else {
            PlayerEntity target = this.overrideTarget;
            int lead = 1;
            if (target == null) {
                LivingEntity auraTarget =
                        ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() ? KillAura.target : null;
                if (auraTarget != null) {
                    if (auraTarget instanceof PlayerEntity
                            && this.isOverrideTargetValid((PlayerEntity) auraTarget, true)) {
                        target = (PlayerEntity) auraTarget;
                    }
                } else {
                    target = CombatTargeting.getMouseOverTarget(
                            this.overrideAttack.isToggled() ? 25.0 : 9.0, this.ignoreTeammates.isToggled());
                }

                lead = !this.overrideAttack.isToggled() && auraTarget == null ? 1 : 2;
            }

            if (target == null) {
                this.voidSearch.forgetTarget();
            } else {
                this.voidSearch.prepare(
                        mc.world,
                        target,
                        mc.player,
                        playerYaw,
                        (float) this.yawOffset.getInput(),
                        this.scanRadius.getInput(),
                        this.ignoreOneBlockWall.isToggled(),
                        this.predictMovement.isToggled(),
                        this.requireSprinting.isToggled(),
                        lead,
                        this.previousVoidYaw(target),
                        Feral.DEBUG);
            }
        }
    }

    public void invalidateVoidTerrain(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.voidSearch.invalidate(world, x1, y1, z1, x2, y2, z2);
    }

    private Float findBestVoidYaw(PlayerEntity target, float playerYaw, int ticksUntilHit) {
        VoidDisplacePlanner.Result result = this.voidSearch.resolve(
                mc.world,
                target,
                mc.player,
                playerYaw,
                (float) this.yawOffset.getInput(),
                this.scanRadius.getInput(),
                this.ignoreOneBlockWall.isToggled(),
                this.predictMovement.isToggled(),
                ticksUntilHit,
                this.previousVoidYaw(target),
                Feral.DEBUG);
        this.latestVoidDebugScan =
                result != null && Feral.DEBUG ? new Displace.VoidDebugScan(mc.world, target, result) : null;
        if (result == null) {
            return null;
        }

        this.lastVoidResult = result;
        this.lastVoidTargetId = target.getNetworkId();
        return result.best.yaw;
    }

    private void updateDisplaceSide(float playerYaw, float displaceYaw) {
        this.displaceLeft = MathHelper.wrapDegrees(displaceYaw - playerYaw) < 0.0F;
    }

    private void pruneTargetDelayStates() {
        if (mc.world == null) {
            this.targetWindowStartTicks.clear();
            this.targetDisplacementLocks.clear();
        } else {
            Iterator<Entry<Integer, Integer>> iterator =
                    this.targetWindowStartTicks.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Integer, Integer> entry = iterator.next();
                Entity entity = mc.world.getEntity(entry.getKey());
                if (!(entity instanceof PlayerEntity) || entity.removed || ((PlayerEntity) entity).deathTicks != 0) {
                    iterator.remove();
                }
            }

            Iterator<Entry<Integer, Displace.DisplacementLock>> lockIterator =
                    this.targetDisplacementLocks.entrySet().iterator();

            while (lockIterator.hasNext()) {
                Entry<Integer, Displace.DisplacementLock> entry = lockIterator.next();
                Entity entity = mc.world.getEntity(entry.getKey());
                if (entity instanceof PlayerEntity && !entity.removed && ((PlayerEntity) entity).deathTicks == 0) {
                    PlayerEntity player = (PlayerEntity) entity;
                    Displace.DisplacementLock lock = entry.getValue();
                    if (!player.onGround) {
                        lock.airborne = true;
                    } else if (lock.airborne || this.tickCounter - lock.lastUseTick >= 20) {
                        lockIterator.remove();
                    }
                } else {
                    lockIterator.remove();
                }
            }
        }
    }

    private float lockDisplacementYaw(PlayerEntity target, float yaw) {
        int targetId = target.getNetworkId();
        Displace.DisplacementLock lock = this.targetDisplacementLocks.get(targetId);
        if (this.mode.getInput() == 1.0
                && lock != null
                && this.lastVoidTargetId == targetId
                && this.lastVoidResult != null
                && this.lastVoidResult.best != null
                && Math.abs(MathHelper.wrapDegrees(lock.yaw - yaw)) > 0.01F) {
            this.targetDisplacementLocks.remove(targetId);
            lock = null;
        }

        if (lock != null && (lock.airborne && target.onGround || this.tickCounter - lock.lastUseTick >= 20)) {
            this.targetDisplacementLocks.remove(targetId);
            lock = null;
        }

        if (lock == null) {
            lock = new Displace.DisplacementLock(yaw, this.tickCounter, !target.onGround);
            this.targetDisplacementLocks.put(targetId, lock);
        } else {
            lock.lastUseTick = this.tickCounter;
            if (!target.onGround) {
                lock.airborne = true;
            }
        }

        return lock.yaw;
    }

    private float getLockedDisplacementYaw(PlayerEntity target, float fallbackYaw) {
        Displace.DisplacementLock lock = this.targetDisplacementLocks.get(target.getNetworkId());
        return lock == null ? fallbackYaw : lock.yaw;
    }

    private boolean shouldDisplaceInCurrentWindow(PlayerEntity target, int currentTick, boolean startWindow) {
        if (target == null) {
            return true;
        }

        int targetId = target.getNetworkId();
        int delayTicks = msToTicks(this.delay.getInput());
        int windowTicks = Math.max(10, delayTicks + 1);
        Integer windowStartTick = this.targetWindowStartTicks.get(targetId);
        if (windowStartTick != null && currentTick - windowStartTick < windowTicks) {
            if (delayTicks <= 0) {
                return true;
            }

            int elapsed = currentTick - windowStartTick;
            return elapsed >= delayTicks;
        } else {
            if (startWindow) {
                this.targetWindowStartTicks.put(targetId, currentTick);
            }

            return true;
        }
    }

    private void releaseBlink() {
        if (this.outboundBlink != null) {
            this.outboundBlink.getTimeout().forceTimeOut();
            this.outboundBlink = null;
        }
    }

    private boolean isOverrideAttackEnabled() {
        return this.overrideAttack.isToggled() || this.overrideFromKillAura;
    }

    private boolean isOverrideTargetValid(PlayerEntity target) {
        return this.isOverrideTargetValid(target, this.overrideFromKillAura);
    }

    private boolean isOverrideTargetValid(PlayerEntity target, boolean fromKillAura) {
        return fromKillAura
                ? target != null
                        && target.world == mc.world
                        && CombatTargeting.isTrackablePlayer(target, this.ignoreTeammates.isToggled())
                        && ModuleManager.killAura != null
                        && ModuleManager.killAura.canDisplaceAttack(target)
                : target != null
                        && target.world == mc.world
                        && CombatTargeting.asValidPlayer(target, 25.0, this.ignoreTeammates.isToggled()) != null;
    }

    private boolean isTargetHittable(PlayerEntity target) {
        return target != null && target.damagedTimer <= Settings.hittableHurtTime.getInput();
    }

    private boolean passesDisplaceConditions() {
        if (this.requireSprinting.isToggled() && !mc.player.isSprinting()) {
            return false;
        } else {
            return this.onlyKnockbackItems.isToggled() && EnchantmentHelper.getKnockbackLevel(mc.player) <= 0
                    ? false
                    : !this.weaponOnly.isToggled() || Utils.holdingWeapon();
        }
    }

    private boolean canStartOverrideAttack(PlayerEntity target, boolean fromKillAura) {
        if (this.overrideAttackState == Displace.OverrideAttackState.IDLE
                && Utils.nullCheck()
                && mc.screen == null
                && this.isOverrideTargetValid(target, fromKillAura)
                && this.passesDisplaceConditions()
                && this.isTargetHittable(target)) {
            long configuredDelay = (long) this.delay.getInput();
            return this.lastOverrideFlickMs < 0L
                    || configuredDelay <= 0L
                    || System.currentTimeMillis() - this.lastOverrideFlickMs >= configuredDelay;
        } else {
            return false;
        }
    }

    public boolean handleKillAuraAttack(LivingEntity target) {
        if (!this.isEnabled() || !Utils.nullCheck()) {
            return false;
        } else if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
            return true;
        } else if (target instanceof PlayerEntity
                && this.isOverrideTargetValid((PlayerEntity) target, true)
                && this.passesDisplaceConditions()) {
            PlayerEntity player = (PlayerEntity) target;
            return !this.canStartOverrideAttack(player, true)
                    ? this.overrideAttack.isToggled()
                    : this.startOverrideAttack(player, true);
        } else {
            return false;
        }
    }

    public boolean processKillAuraAttack() {
        if (this.isEnabled() && this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
            if (this.overrideFromKillAura && this.overrideAttackState == Displace.OverrideAttackState.ATTACKING) {
                this.performOverrideAttack();
            }

            return true;
        } else {
            return false;
        }
    }

    public void cancelKillAuraAttack() {
        if (this.overrideFromKillAura) {
            this.resetOverrideAttackState();
        }
    }

    private float[] getOverrideTargetRotations(
            PlayerEntity target, float baseYaw, float basePitch, boolean fromKillAura) {
        return fromKillAura
                ? ModuleManager.killAura.getDisplaceRotations(baseYaw, basePitch)
                : RotationUtils.getRotations(target, 100.0, 100.0, baseYaw, basePitch);
    }

    private void startOverrideBlink() {
        this.releaseBlink();
        this.releaseBlinkNextGameTick = false;
        if (this.blink.isToggled()) {
            this.outboundBlink = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
            Feral.lagHandler.requestLag(this.outboundBlink);
        }
    }

    private void releaseUseForOverrideAttack() {
        if (Utils.nullCheck() && mc.interactionManager != null) {
            if (mc.player.hasItemInUse()) {
                mc.interactionManager.stopUsingHand(mc.player);
            } else if (ModuleUtils.isBlocked) {
                PacketUtils.sendReleasePacket();
            }
        }
    }

    private boolean startOverrideAttack(PlayerEntity target) {
        return this.startOverrideAttack(target, false);
    }

    private boolean startOverrideAttack(PlayerEntity target, boolean fromKillAura) {
        if (!this.canStartOverrideAttack(target, fromKillAura)) {
            return false;
        }

        float baseYaw = RotationUtils.serverRotations[0];
        float basePitch = RotationUtils.serverRotations[1];
        float[] targetRotations = this.getOverrideTargetRotations(target, baseYaw, basePitch, fromKillAura);
        if (targetRotations == null) {
            return false;
        }

        this.overrideTargetYaw = targetRotations[0];
        this.overrideTargetPitch = targetRotations[1];
        this.overrideAbsoluteFlickYaw = this.isVoidMode();
        if (this.overrideAbsoluteFlickYaw) {
            Float bestVoidYaw = this.findBestVoidYaw(target, this.overrideTargetYaw, 2);
            if (bestVoidYaw == null) {
                return false;
            }

            this.overrideFlickYaw = bestVoidYaw;
            this.overrideFlickOffset = 0.0F;
            this.updateDisplaceSide(this.overrideTargetYaw, this.overrideFlickYaw);
        } else {
            if (!this.findVoid.isToggled() || !this.tryFindVoidDirection(target)) {
                this.displaceLeft = this.direction.getInput() == 0.0;
            }

            this.overrideFlickOffset =
                    this.displaceLeft ? -((float) this.yawOffset.getInput()) : (float) this.yawOffset.getInput();
            this.overrideFlickYaw = this.overrideTargetYaw + this.overrideFlickOffset;
        }

        this.overrideFlickYaw = this.lockDisplacementYaw(target, this.overrideFlickYaw);
        this.updateDisplaceSide(this.overrideTargetYaw, this.overrideFlickYaw);
        this.overrideTarget = target;
        this.overrideFromKillAura = fromKillAura;
        this.overrideAttackState = Displace.OverrideAttackState.FLICKING_AWAY;
        this.overrideStateTicks = 0;
        this.overrideAwayPacketSent = false;
        this.overrideAttackInProgress = false;
        this.overrideAttackPacketSent = false;
        this.suppressUseForOverrideAttackTick = false;
        this.lastOverrideFlickMs = System.currentTimeMillis();
        this.active = true;
        this.displaceThisTick = true;
        this.hasKB = EnchantmentHelper.getKnockbackLevel(mc.player) > 0;
        this.compensateNextTick = false;
        this.wasDisplacingLastTick = false;
        this.showArrow(target);
        this.releaseUseForOverrideAttack();
        this.startOverrideBlink();
        return true;
    }

    private void resetOverrideAttackState() {
        this.releaseBlink();
        this.overrideAttackState = Displace.OverrideAttackState.IDLE;
        this.overrideFromKillAura = false;
        this.overrideTarget = null;
        this.overrideTargetYaw = 0.0F;
        this.overrideTargetPitch = 0.0F;
        this.overrideFlickYaw = 0.0F;
        this.overrideFlickOffset = 0.0F;
        this.overrideAbsoluteFlickYaw = false;
        this.overrideFlickSkipped = false;
        this.overrideAwayPacketSent = false;
        this.overrideAttackInProgress = false;
        this.overrideAttackPacketSent = false;
        this.suppressUseForOverrideAttackTick = false;
        this.overrideStateTicks = 0;
        this.active = false;
        this.displaceThisTick = false;
        this.compensateNextTick = false;
        this.wasDisplacingLastTick = false;
        this.hideArrow();
    }

    private boolean refreshOverrideRotations(float baseYaw, float basePitch) {
        float[] targetRotations =
                this.getOverrideTargetRotations(this.overrideTarget, baseYaw, basePitch, this.overrideFromKillAura);
        if (targetRotations == null) {
            this.resetOverrideAttackState();
            return false;
        }

        this.overrideTargetYaw = targetRotations[0];
        this.overrideTargetPitch = targetRotations[1];
        if (this.overrideAbsoluteFlickYaw) {
            if (this.overrideAttackState == Displace.OverrideAttackState.FLICKING_AWAY
                    && !this.overrideAwayPacketSent) {
                Float bestVoidYaw = this.findBestVoidYaw(this.overrideTarget, this.overrideTargetYaw, 1);
                if (bestVoidYaw != null) {
                    this.overrideFlickYaw = this.lockDisplacementYaw(this.overrideTarget, bestVoidYaw);
                } else {
                    this.overrideFlickSkipped = true;
                    this.overrideAttackState = Displace.OverrideAttackState.ATTACKING;
                    this.overrideStateTicks = 0;
                    this.releaseBlink();
                    this.hideArrow();
                }
            }
        } else {
            this.overrideFlickYaw = this.getLockedDisplacementYaw(
                    this.overrideTarget, this.overrideTargetYaw + this.overrideFlickOffset);
        }

        this.updateDisplaceSide(this.overrideTargetYaw, this.overrideFlickYaw);
        this.hasKB = EnchantmentHelper.getKnockbackLevel(mc.player) > 0;
        return true;
    }

    private void updateOverrideRotation(ClientRotationEvent event) {
        if (!this.isOverrideTargetValid(this.overrideTarget)) {
            this.resetOverrideAttackState();
        } else {
            float baseYaw = event.yaw != null ? event.yaw : RotationUtils.serverRotations[0];
            float basePitch = event.pitch != null ? event.pitch : RotationUtils.serverRotations[1];
            if (this.refreshOverrideRotations(baseYaw, basePitch)) {
                if (this.overrideAttackState == Displace.OverrideAttackState.FLICKING_AWAY) {
                    event.yaw = this.overrideFlickYaw;
                    this.displaceThisTick = true;
                } else {
                    event.yaw = this.overrideTargetYaw;
                    this.displaceThisTick = false;
                }

                event.pitch = this.overrideTargetPitch;
                this.active = true;
                if (!this.overrideFlickSkipped) {
                    this.showArrow(this.overrideTarget);
                }

                RotationHelper.get().forceMovementFix = true;
            }
        }
    }

    private void advanceOverrideAttackState() {
        if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
            if (Utils.nullCheck() && mc.screen == null && this.isOverrideTargetValid(this.overrideTarget)) {
                if (this.overrideAttackState == Displace.OverrideAttackState.RESTORING
                        || this.passesDisplaceConditions() && this.isTargetHittable(this.overrideTarget)) {
                    this.overrideStateTicks++;
                    switch (this.overrideAttackState) {
                        case FLICKING_AWAY:
                            if (this.overrideAwayPacketSent || this.overrideStateTicks >= 5) {
                                this.overrideAttackState = Displace.OverrideAttackState.ATTACKING;
                                this.overrideStateTicks = 0;
                                this.displaceThisTick = false;
                            }
                            break;
                        case ATTACKING:
                            if (this.overrideStateTicks >= 2) {
                                this.resetOverrideAttackState();
                            }
                            break;
                        case RESTORING:
                            if (this.overrideStateTicks >= 2) {
                                this.resetOverrideAttackState();
                            }
                    }
                } else {
                    this.resetOverrideAttackState();
                }
            } else {
                this.resetOverrideAttackState();
            }
        }
    }

    private void clearVoidDebugState() {
        this.voidSearch.clear();
        this.lastVoidResult = null;
        this.lastVoidTargetId = -1;
        this.latestVoidDebugScan = null;
        this.frozenVoidDebugScan = null;
        this.frozenVoidDebugExpiresAtMs = 0L;
    }

    private void showArrow(PlayerEntity player) {
        long now = System.currentTimeMillis();
        boolean playerChanged = this.arrowPlayer != player;
        float currentAlpha = playerChanged ? 0.0F : this.getArrowAlpha(now);
        if (playerChanged) {
            this.clearArrowNudge();
            this.arrowYaw = RotationUtils.serverRotations[0];
        }

        this.arrowPlayer = player;
        if (!this.arrowVisible || playerChanged) {
            this.arrowFadeStartAlpha = currentAlpha;
            this.arrowFadeEndAlpha = 1.0F;
            this.arrowFadeStartMs = now;
        }

        this.arrowVisible = true;
    }

    private void hideArrow() {
        if (this.arrowPlayer != null && this.arrowVisible) {
            long now = System.currentTimeMillis();
            this.arrowFadeStartAlpha = this.getArrowAlpha(now);
            this.arrowFadeEndAlpha = 0.0F;
            this.arrowFadeStartMs = now;
            this.arrowVisible = false;
        }
    }

    private float getArrowAlpha(long now) {
        float progress = Math.min(1.0F, Math.max(0.0F, (float) (now - this.arrowFadeStartMs) / 250.0F));
        return this.arrowFadeStartAlpha + (this.arrowFadeEndAlpha - this.arrowFadeStartAlpha) * progress;
    }

    private void triggerArrowNudge(PlayerEntity player) {
        if (player != null && this.arrowPlayer == player) {
            long now = System.currentTimeMillis();
            if (this.arrowNudgePlayerId != player.getNetworkId() || now - this.arrowNudgeStartMs >= 320L) {
                this.arrowNudgePlayerId = player.getNetworkId();
                this.arrowNudgeStartMs = now;
                this.arrowNudgeYaw = RotationUtils.serverRotations[0];
            }
        }
    }

    private double getArrowNudgeProgress(PlayerEntity player, long now) {
        if (player != null && this.arrowNudgePlayerId == player.getNetworkId() && this.arrowNudgeStartMs >= 0L) {
            double progress = (now - this.arrowNudgeStartMs) / 320.0;
            return Math.min(1.0, Math.max(0.0, progress));
        } else {
            return -1.0;
        }
    }

    private double getArrowNudgeOffset(double progress) {
        if (progress < 0.0) {
            return 0.0;
        }

        double endValue = Math.exp(-6.0);
        double movement = (1.0 - Math.exp(-6.0 * progress)) / (1.0 - endValue);
        return movement * 0.3;
    }

    private float getArrowNudgeAlpha(double progress) {
        if (!(progress < 0.0) && !(progress <= 0.58)) {
            double fadeProgress = (progress - 0.58) / 0.42000000000000004;
            double endValue = Math.exp(-5.0);
            double alpha = (Math.exp(-5.0 * fadeProgress) - endValue) / (1.0 - endValue);
            return (float) alpha;
        } else {
            return 1.0F;
        }
    }

    private void clearArrowNudge() {
        this.arrowNudgeStartMs = -1L;
        this.arrowNudgePlayerId = -1;
        this.arrowNudgeYaw = 0.0F;
    }

    private void clearArrow() {
        this.arrowPlayer = null;
        this.arrowFadeStartAlpha = 0.0F;
        this.arrowFadeEndAlpha = 0.0F;
        this.arrowFadeStartMs = 0L;
        this.arrowVisible = false;
        this.clearArrowNudge();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPostMotion(PostMotionEvent e) {
        if (this.active && this.displaceThisTick && this.arrowPlayer != null && Utils.nullCheck()) {
            this.arrowYaw = RotationUtils.serverRotations[0];
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            this.clearArrow();
            this.clearVoidDebugState();
        } else {
            this.renderFrozenVoidDebug();
            this.voidSearch.advance(mc.world);
            if (!this.renderArrow.isToggled()) {
                this.clearArrow();
            } else if (this.arrowPlayer != null && !this.arrowPlayer.removed && this.arrowPlayer.deathTicks == 0) {
                long now = System.currentTimeMillis();
                double nudgeProgress = this.getArrowNudgeProgress(this.arrowPlayer, now);
                float alpha = this.getArrowAlpha(now) * this.getArrowNudgeAlpha(nudgeProgress);
                if (alpha <= 0.0F) {
                    if (!this.arrowVisible) {
                        this.clearArrow();
                    }
                } else {
                    float yaw = nudgeProgress >= 0.0 ? this.arrowNudgeYaw : this.arrowYaw;
                    this.drawArrow(
                            this.arrowPlayer, yaw, e.partialTicks, alpha, this.getArrowNudgeOffset(nudgeProgress));
                }
            } else {
                this.clearArrow();
            }
        }
    }

    private void renderFrozenVoidDebug() {
        Displace.VoidDebugScan scan = this.frozenVoidDebugScan;
        if (Feral.DEBUG && scan != null) {
            if (System.currentTimeMillis() < this.frozenVoidDebugExpiresAtMs && scan.world == mc.world) {
                double viewerX = mc.getEntityRenderDispatcher().cameraX;
                double viewerY = mc.getEntityRenderDispatcher().cameraY;
                double viewerZ = mc.getEntityRenderDispatcher().cameraZ;
                GL11.glPushMatrix();
                GL11.glPushAttrib(1048575);
                GL11.glEnable(3042);
                GL11.glBlendFunc(770, 771);
                GL11.glDisable(3553);
                GL11.glDisable(2896);
                GL11.glDisable(2929);
                GL11.glDepthMask(false);

                for (VoidDisplacePlanner.Candidate candidate : scan.result.candidates) {
                    boolean best = candidate == scan.bestCandidate;
                    GL11.glLineWidth(best ? 3.5F : 1.0F);
                    if (best) {
                        GL11.glColor4f(0.15F, 1.0F, 0.3F, 1.0F);
                    } else if (candidate.valid) {
                        GL11.glColor4f(0.3F, 0.75F, 1.0F, 0.35F);
                    } else {
                        GL11.glColor4f(1.0F, 0.25F, 0.2F, 0.35F);
                    }

                    this.drawVoidTrace(candidate.nominal, viewerX, viewerY, viewerZ);
                }

                VoidDisplacePlanner.Candidate best = scan.bestCandidate;
                if (best != null) {
                    GL11.glLineWidth(2.0F);
                    GL11.glColor4f(1.0F, 0.65F, 0.15F, 0.9F);

                    for (int index = 1; index < best.responses.size(); index++) {
                        this.drawVoidTrace(best.responses.get(index), viewerX, viewerY, viewerZ);
                    }

                    GL11.glLineWidth(1.25F);
                    GL11.glColor4f(1.0F, 0.2F, 0.2F, 0.65F);
                    GL11.glBegin(1);

                    for (VoidDisplacePlanner.SurfaceProbe probe : best.nominal.surfaces) {
                        this.voidDebugVertex(probe.eye.x, probe.eye.y, probe.eye.z, viewerX, viewerY, viewerZ);
                        this.voidDebugVertex(
                                probe.surface.x, probe.surface.y, probe.surface.z, viewerX, viewerY, viewerZ);
                    }

                    GL11.glEnd();
                }

                GL11.glPopAttrib();
                GL11.glPopMatrix();
                if (best != null) {
                    String text = String.format(
                            Locale.ROOT,
                            "Void: push %.1f / attack %.1f | escape %d/%d | safety %.2f | clearance %.2f",
                            best.pushYaw,
                            best.yaw,
                            best.escapes,
                            best.responses.size(),
                            best.safety,
                            best.clearance);
                    this.renderVoidDebugLabel(text, scan.originX, scan.originY + 2.35, scan.originZ, 5635976, 0.0125F);
                    this.renderVoidDebugLabel(
                            "Orange: recovery paths | Red: nearby clutch surfaces",
                            scan.originX,
                            scan.originY + 2.1,
                            scan.originZ,
                            16777215,
                            0.011F);
                }
            } else {
                this.frozenVoidDebugScan = null;
                this.frozenVoidDebugExpiresAtMs = 0L;
            }
        }
    }

    private void drawVoidTrace(VoidDisplacePlanner.Trace trace, double viewerX, double viewerY, double viewerZ) {
        GL11.glBegin(3);

        for (Vec3d point : trace.points) {
            this.voidDebugVertex(point.x, point.y + 0.05, point.z, viewerX, viewerY, viewerZ);
        }

        GL11.glEnd();
    }

    private void renderVoidDebugLabel(String text, double x, double y, double z, int color, float scale) {
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        GlStateManager.pushMatrix();
        GlStateManager.translated(x - renderManager.cameraX, y - renderManager.cameraY, z - renderManager.cameraZ);
        GlStateManager.rotatef(-renderManager.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(
                (mc.options.perspective == 2 ? -1.0F : 1.0F) * renderManager.cameraPitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.scalef(-scale, -scale, scale);
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        int textX = -mc.textRenderer.getWidth(text) / 2;
        mc.textRenderer.draw(text, textX, 0.0F, 0xFF000000 | color, true);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.enableLighting();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void voidDebugVertex(double x, double y, double z, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y - viewerY, z - viewerZ);
    }

    private void drawArrow(PlayerEntity player, float yaw, float partialTicks, float alpha, double nudgeOffset) {
        double x = player.prevX + (player.x - player.prevX) * partialTicks;
        double y = player.prevY + (player.y - player.prevY) * partialTicks + player.height * 0.5;
        double z = player.prevZ + (player.z - player.prevZ) * partialTicks;
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double startDistance = player.width * 0.5 + 0.3 + nudgeOffset;
        double startX = x + forwardX * startDistance;
        double startZ = z + forwardZ * startDistance;
        double bodyX = startX + forwardX * 0.74;
        double bodyZ = startZ + forwardZ * 0.74;
        double headBaseX = startX + forwardX * 0.56;
        double headBaseZ = startZ + forwardZ * 0.56;
        double tipX = bodyX + forwardX * 0.52;
        double tipZ = bodyZ + forwardZ * 0.52;
        double viewerX = mc.getEntityRenderDispatcher().cameraX;
        double viewerY = mc.getEntityRenderDispatcher().cameraY;
        double viewerZ = mc.getEntityRenderDispatcher().cameraZ;
        GL11.glPushMatrix();
        GL11.glPushAttrib(1048575);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(3553);
        GL11.glDisable(2896);
        GL11.glDisable(2929);
        GL11.glDisable(2884);
        GL11.glDepthMask(false);
        GL11.glEnable(2848);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.82F * alpha);
        GL11.glBegin(4);
        this.vertex(startX, y, startZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, -0.08, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, 0.08, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, -0.08, viewerX, viewerY, viewerZ);
        this.vertex(headBaseX, y, headBaseZ, -0.3, viewerX, viewerY, viewerZ);
        this.vertex(tipX, y, tipZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, -0.08, viewerX, viewerY, viewerZ);
        this.vertex(tipX, y, tipZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, 0.08, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, 0.08, viewerX, viewerY, viewerZ);
        this.vertex(tipX, y, tipZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(headBaseX, y, headBaseZ, 0.3, viewerX, viewerY, viewerZ);
        GL11.glEnd();
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.95F * alpha);
        GL11.glBegin(2);
        this.vertex(startX, y, startZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, -0.08, viewerX, viewerY, viewerZ);
        this.vertex(headBaseX, y, headBaseZ, -0.3, viewerX, viewerY, viewerZ);
        this.vertex(tipX, y, tipZ, 0.0, viewerX, viewerY, viewerZ);
        this.vertex(headBaseX, y, headBaseZ, 0.3, viewerX, viewerY, viewerZ);
        this.vertex(bodyX, y, bodyZ, 0.08, viewerX, viewerY, viewerZ);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private void vertex(double x, double y, double z, double yOffset, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + yOffset - viewerY, z - viewerZ);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onGameTick(GameTickEvent e) {
        this.suppressUseForOverrideAttackTick = false;
        if (this.isOverrideAttackEnabled()) {
            if (this.releaseBlinkNextGameTick) {
                this.releaseBlink();
                this.releaseBlinkNextGameTick = false;
            }

            this.advanceOverrideAttackState();
        } else {
            if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
                this.resetOverrideAttackState();
            }

            if (this.releaseBlinkNextGameTick) {
                this.releaseBlink();
                this.releaseBlinkNextGameTick = false;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent event) {
        if (this.isOverrideAttackEnabled()) {
            if (this.overrideAttackState == Displace.OverrideAttackState.FLICKING_AWAY
                    || this.overrideAttackState == Displace.OverrideAttackState.ATTACKING
                    || this.suppressUseForOverrideAttackTick) {
                event.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreAttack(PreAttackEvent event) {
        if (this.isOverrideAttackEnabled()) {
            if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
                event.setCanceled(true);
            } else {
                HitResult mouseOver = event.objectMouseOver;
                PlayerEntity target = mouseOver != null && mouseOver.type == Type.ENTITY
                        ? CombatTargeting.asValidPlayer(mouseOver.entity, 25.0, this.ignoreTeammates.isToggled())
                        : null;
                if (target != null && this.startOverrideAttack(target)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (this.isOverrideAttackEnabled() && event.attacker == mc.player && !this.overrideAttackInProgress) {
            if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
                event.setCanceled(true);
            } else {
                PlayerEntity target =
                        CombatTargeting.asValidPlayer(event.target, 25.0, this.ignoreTeammates.isToggled());
                if (target != null && this.startOverrideAttack(target)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (this.isOverrideAttackEnabled()
                && this.overrideAttackState == Displace.OverrideAttackState.ATTACKING
                && !this.overrideAttackInProgress
                && !this.overrideFromKillAura) {
            this.performOverrideAttack();
        }
    }

    private void performOverrideAttack() {
        if (this.isOverrideTargetValid(this.overrideTarget)
                && this.passesDisplaceConditions()
                && this.isTargetHittable(this.overrideTarget)) {
            this.overrideAttackPacketSent = false;
            boolean silentSwing = this.overrideFromKillAura
                    && ModuleManager.killAura != null
                    && ModuleManager.killAura.shouldSwingSilentlyWhileBlocking();
            this.releaseUseForOverrideAttack();
            this.suppressUseForOverrideAttackTick = true;
            this.overrideAttackInProgress = true;

            try {
                Utils.attackEntity(this.overrideTarget, !silentSwing, silentSwing);
            } finally {
                this.overrideAttackInProgress = false;
            }

            if (!this.overrideAttackPacketSent) {
                this.resetOverrideAttackState();
            } else {
                this.overrideAttackState = Displace.OverrideAttackState.RESTORING;
                this.overrideStateTicks = 0;
                this.displaceThisTick = false;
            }
        } else {
            this.resetOverrideAttackState();
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPostInput(PostPlayerInputEvent e) {
        if (!Utils.nullCheck() || !this.active) {
            this.compensateNextTick = false;
        } else if (this.compensateNextTick && !this.displaceThisTick) {
            this.compensateNextTick = false;
            if (this.displaceLeft) {
                mc.player.input.movementSideways = -1.0F;
            } else {
                mc.player.input.movementSideways = 1.0F;
            }
        } else if (this.displaceThisTick && (!this.hasKB || this.requireSprinting.isToggled())) {
            if (this.anyMovementKey()) {
                mc.player.input.movementForward = 1.0F;
                this.compensateNextTick = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (!this.isOverrideAttackEnabled() && this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
            this.resetOverrideAttackState();
        }

        if (this.isOverrideAttackEnabled() && this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
            if (!e.isCanceled()) {
                if (this.overrideAttackState == Displace.OverrideAttackState.FLICKING_AWAY
                        && e.getPacket() instanceof PlayerMoveC2SPacket) {
                    PlayerMoveC2SPacket movementPacket = (PlayerMoveC2SPacket) e.getPacket();
                    if (movementPacket.hasAngles()) {
                        float yawError =
                                Math.abs(MathHelper.wrapDegrees(movementPacket.getYaw() - this.overrideFlickYaw));
                        if (yawError <= 6.0F) {
                            this.overrideAwayPacketSent = true;
                        }
                    }
                }

                if (this.overrideAttackInProgress
                        && e.getPacket() instanceof PlayerInteractEntityC2SPacket
                        && ((PlayerInteractEntityC2SPacket) e.getPacket()).getAction() == Action.ATTACK) {
                    this.overrideAttackPacketSent = true;
                    this.releaseBlink();
                }
            }
        } else if (this.blink.isToggled() && this.active && this.displaceThisTick && !this.releaseBlinkNextGameTick) {
            if (e.getPacket() instanceof PlayerMoveC2SPacket) {
                if (this.outboundBlink == null) {
                    this.outboundBlink = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
                    Feral.lagHandler.requestLag(this.outboundBlink);
                    this.releaseBlinkNextGameTick = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onArrowNudgeAttackPacket(SendPacketEvent e) {
        if (this.active
                && this.renderArrow.isToggled()
                && Utils.nullCheck()
                && !e.isCanceled()
                && e.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) e.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                Entity attackedEntity = packet.getInteractTarget(mc.world);
                if (attackedEntity == this.arrowPlayer) {
                    this.triggerArrowNudge(this.arrowPlayer);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onVoidDebugAttackPacket(SendPacketEvent e) {
        if (Feral.DEBUG
                && this.isVoidMode()
                && this.active
                && Utils.nullCheck()
                && !e.isCanceled()
                && e.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) e.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                Entity attackedEntity = packet.getInteractTarget(mc.world);
                Displace.VoidDebugScan scan = this.latestVoidDebugScan;
                if (attackedEntity instanceof PlayerEntity
                        && scan != null
                        && scan.bestCandidate != null
                        && scan.world == mc.world
                        && attackedEntity.getNetworkId() == scan.targetEntityId) {
                    this.frozenVoidDebugScan = scan;
                    this.frozenVoidDebugExpiresAtMs = System.currentTimeMillis() + 30000L;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) {
            this.resetOverrideAttackState();
            this.active = false;
            this.compensateNextTick = false;
            this.wasDisplacingLastTick = false;
            this.targetDisplacementLocks.clear();
            this.clearArrow();
            this.clearVoidDebugState();
        } else {
            this.tickCounter++;
            int currentTick = this.tickCounter;
            this.pruneTargetDelayStates();
            float playerYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
            this.prepareVoidSearch(playerYaw);
            if (!this.isOverrideAttackEnabled()
                    && (ModuleManager.killAura == null
                            || !ModuleManager.killAura.isEnabled()
                            || KillAura.target == null)) {
                if (this.overrideAttackState != Displace.OverrideAttackState.IDLE) {
                    this.resetOverrideAttackState();
                }

                if (!Feral.DEBUG || !this.isVoidMode()) {
                    this.latestVoidDebugScan = null;
                }

                if (!this.passesDisplaceConditions()) {
                    this.active = false;
                    this.displaceThisTick = false;
                    this.compensateNextTick = false;
                    this.wasDisplacingLastTick = false;
                    this.hideArrow();
                } else {
                    PlayerEntity target = null;
                    if (Mouse.isButtonDown(0)) {
                        target = CombatTargeting.getMouseOverTarget(9.0, this.ignoreTeammates.isToggled());
                    }

                    boolean hasKBEnchant = EnchantmentHelper.getKnockbackLevel(mc.player) > 0;
                    this.active = this.isTargetHittable(target);
                    if (!this.active) {
                        this.displaceThisTick = false;
                        this.compensateNextTick = false;
                        this.wasDisplacingLastTick = false;
                        this.hideArrow();
                    } else {
                        boolean voidMode = this.isVoidMode();
                        this.displaceThisTick =
                                !this.displaceThisTick || voidMode && this.lastVoidTargetId != target.getNetworkId();
                        if (this.displaceThisTick
                                && !this.shouldDisplaceInCurrentWindow(target, currentTick, !voidMode)) {
                            this.displaceThisTick = false;
                            this.compensateNextTick = false;
                            this.wasDisplacingLastTick = false;
                            this.hideArrow();
                        } else {
                            float displaceYaw;
                            if (voidMode) {
                                displaceYaw = playerYaw;
                                if (this.displaceThisTick) {
                                    Float bestVoidYaw = this.findBestVoidYaw(target, playerYaw, 1);
                                    if (bestVoidYaw == null) {
                                        this.active = false;
                                        this.displaceThisTick = false;
                                        this.compensateNextTick = false;
                                        this.wasDisplacingLastTick = false;
                                        this.hideArrow();
                                        return;
                                    }

                                    displaceYaw = bestVoidYaw;
                                    this.shouldDisplaceInCurrentWindow(target, currentTick, true);
                                }
                            } else {
                                if (!this.findVoid.isToggled() || !this.tryFindVoidDirection(target)) {
                                    this.displaceLeft = this.direction.getInput() == 0.0;
                                }

                                float offset = (float) this.yawOffset.getInput();
                                displaceYaw = this.displaceLeft ? playerYaw - offset : playerYaw + offset;
                            }

                            this.hasKB = hasKBEnchant;
                            if (this.displaceThisTick) {
                                displaceYaw = this.lockDisplacementYaw(target, displaceYaw);
                                this.updateDisplaceSide(playerYaw, displaceYaw);
                            } else {
                                displaceYaw = this.getLockedDisplacementYaw(target, displaceYaw);
                            }

                            this.showArrow(target);
                            if (!this.displaceThisTick && this.wasDisplacingLastTick) {
                                int key = mc.options.attackKey.getKeyCode();
                                if (key != 0) {
                                    KeyBinding.click(key);
                                }
                            }

                            this.wasDisplacingLastTick = this.displaceThisTick;
                            if (this.displaceThisTick) {
                                e.yaw = displaceYaw;
                                RotationHelper.get().forceMovementFix = true;
                            }
                        }
                    }
                }
            } else if (this.overrideAttackState == Displace.OverrideAttackState.IDLE) {
                this.active = false;
                this.displaceThisTick = false;
                this.compensateNextTick = false;
                this.wasDisplacingLastTick = false;
                this.hideArrow();
            } else {
                this.updateOverrideRotation(e);
            }
        }
    }

    private static final class DisplacementLock {
        private final float yaw;
        private int lastUseTick;
        private boolean airborne;

        private DisplacementLock(float yaw, int lastUseTick, boolean airborne) {
            this.yaw = yaw;
            this.lastUseTick = lastUseTick;
            this.airborne = airborne;
        }
    }

    private enum OverrideAttackState {
        IDLE,
        FLICKING_AWAY,
        ATTACKING,
        RESTORING;
    }

    private static final class VoidDebugScan {
        private final Object world;
        private final int targetEntityId;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final VoidDisplacePlanner.Result result;
        private final VoidDisplacePlanner.Candidate bestCandidate;

        private VoidDebugScan(Object world, PlayerEntity target, VoidDisplacePlanner.Result result) {
            this.world = world;
            this.targetEntityId = target.getNetworkId();
            this.originX = target.x;
            this.originY = target.y;
            this.originZ = target.z;
            this.result = result;
            this.bestCandidate = result.best;
        }
    }
}

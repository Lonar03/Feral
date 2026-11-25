package keystrokesmod.module.impl.combat;

import keystrokesmod.Feral;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class Autoblock extends Module {
    private static final String[] MODES = new String[] {"Vanilla", "Lag", "Post"};
    private static final String[] UNBLOCK_OUT_OF_RANGE_MODES = new String[] {"Once", "Always", "Fake"};
    private static final int UNBLOCK_ONCE = 0;
    private static final int UNBLOCK_ALWAYS = 1;
    private static final int UNBLOCK_ALWAYS_FAKE = 2;
    private final SliderSetting mode;
    private final SliderSetting range;
    private final SliderSetting maxHurtTimeMs;
    private final SliderSetting maxHoldMs;
    private final SliderSetting cooldownMs;
    private final SliderSetting unblockOutOfRange;
    private final ButtonSetting requireLmb;
    private final ButtonSetting requireRmb;
    private final ButtonSetting onlyWhenDamaged;
    private final ButtonSetting ignoreTeammates;
    private final SliderSetting lagChance;
    private final SliderSetting lagMaxDuration;
    private final ButtonSetting preventDelayAttacks;
    private final ButtonSetting blockAgainImmediately;
    private final ButtonSetting forceBlockAnimation;
    private final ButtonSetting forceAttack;
    private final ButtonSetting workWithNoSlow;
    private final ButtonSetting sendInteractAt;
    private int pendingForceAttacks;
    private boolean forceAttackUnblocked;
    private boolean isBlocking;
    private boolean postBlocking;
    private boolean manualBlock;
    private boolean targetWasInRange;
    private boolean unblockedAfterLeavingRange;
    private boolean allowingInteraction;
    private int blockStartTick = -1;
    private long lastBlockEndTimeMs;
    private PlayerEntity currentTarget;
    private int lastSelfHurtTime;
    private boolean isLagging;
    private int lagStartTick = -1;
    private LagRequest outboundLag;
    private int tickCounter;

    public Autoblock() {
        super("Auto Block", Module.category.combat);
        this.liteModule = true;
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, MODES));
        this.registerSetting(this.range = new SliderSetting("Range", 4.0, 2.0, 6.0, 0.1));
        this.registerSetting(
                this.maxHurtTimeMs = new SliderSetting("Maximum hurt time", "ms", 200.0, 50.0, 500.0, 50.0));
        this.registerSetting(
                this.maxHoldMs = new SliderSetting("Maximum hold duration", "ms", 150.0, 50.0, 500.0, 50.0));
        this.registerSetting(this.cooldownMs = new SliderSetting("Cooldown", "ms", 0.0, 0.0, 500.0, 50.0));
        this.registerSetting(this.lagChance = new SliderSetting("Lag chance", "%", 100.0, 0.0, 100.0, 5.0));
        this.registerSetting(
                this.lagMaxDuration = new SliderSetting("Lag max duration", "ms", 200.0, 50.0, 500.0, 50.0));
        this.registerSetting(
                this.unblockOutOfRange =
                        new SliderSetting("Unblock out of range", true, 0, UNBLOCK_OUT_OF_RANGE_MODES));
        this.registerSetting(this.preventDelayAttacks = new ButtonSetting("Prevent delaying attacks", true));
        this.registerSetting(this.blockAgainImmediately = new ButtonSetting("Block again immediately", true));
        this.registerSetting(this.forceAttack = new ButtonSetting("Force attack", false));
        this.registerSetting(this.forceBlockAnimation = new ButtonSetting("Force block animation", true));
        this.registerSetting(this.requireLmb = new ButtonSetting("Require left mouse", true));
        this.registerSetting(this.requireRmb = new ButtonSetting("Require right mouse", false));
        this.registerSetting(this.onlyWhenDamaged = new ButtonSetting("Damaged", false));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(this.sendInteractAt = new ButtonSetting("Send interact at", false));
        this.registerSetting(this.workWithNoSlow = new ButtonSetting("Work with NoSlow", false));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return MODES[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean lagMode = this.isLagMode();
        boolean postMode = this.isPostMode();
        this.maxHurtTimeMs.setVisible(!postMode, this);
        this.maxHoldMs.setVisible(!postMode, this);
        this.cooldownMs.setVisible(!postMode, this);
        this.onlyWhenDamaged.setVisible(!postMode, this);
        this.forceAttack.setVisible(!postMode, this);
        this.forceBlockAnimation.setVisible(!postMode, this);
        this.workWithNoSlow.setVisible(postMode, this);
        this.sendInteractAt.setVisible(postMode, this);
        this.lagChance.setVisible(lagMode, this);
        this.lagMaxDuration.setVisible(lagMode, this);
        this.preventDelayAttacks.setVisible(lagMode, this);
        this.blockAgainImmediately.setVisible(lagMode, this);
    }

    @Override
    public void onEnable() {
        this.tickCounter = 0;
        this.resetState(false);
    }

    private static int msToTicks(double ms) {
        return ms <= 0.0 ? 0 : (int) Math.ceil(ms / 50.0);
    }

    @Override
    public void onDisable() {
        this.resetState(true);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (Utils.nullCheck() && Utils.holdingSword()) {
            if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock()) {
                if (e.button == 1) {
                    if (this.isAlwaysUnblockMode() || this.unblockOutOfRange.getInput() < 0.0) {
                        if (!e.buttonstate && this.allowingInteraction) {
                            this.allowingInteraction = false;
                            return;
                        }

                        if (e.buttonstate && this.canInteract()) {
                            this.allowingInteraction = true;
                            this.releaseLag();
                            if (mc.player.hasItemInUse()) {
                                mc.interactionManager.stopUsingHand(mc.player);
                                this.postBlocking = false;
                            }

                            this.stopBlocking(true);
                            this.manualBlock = false;
                            return;
                        }
                    }

                    if (this.unblockOutOfRange.getInput() < 0.0) {
                        this.currentTarget = CombatTargeting.findTarget(
                                this.range.getInput() * this.range.getInput(), this.ignoreTeammates.isToggled());
                        if (this.isVanillaUseOutOfRange()) {
                            return;
                        }
                    }

                    e.setCanceled(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent e) {
        if (this.shouldBlockVanillaUse()) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onUseItem(UseItemEvent e) {
        if (this.allowingInteraction || this.shouldBlockVanillaUse() || this.isFakeBlockOutOfRange()) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.START) {
            if (!Utils.nullCheck()) {
                this.syncBlockAnimation();
            } else if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldSuspendAutoblock()) {
                ReflectionUtils.setItemInUse(false);
            } else if (mc.screen == null || !this.isBlocking && !this.isLagging && !this.allowingInteraction) {
                this.syncBlockAnimation();
            } else {
                this.resetState(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldSuspendAutoblock()) {
            this.releaseLag();
        } else if (!this.isPostMode() && this.isLagging && this.preventDelayAttacks.isToggled()) {
            if (e.getPacket() instanceof PlayerInteractEntityC2SPacket) {
                if (((PlayerInteractEntityC2SPacket) e.getPacket()).getAction() == Action.ATTACK) {
                    this.releaseLag();
                    if (this.blockAgainImmediately.isToggled() && Utils.holdingSword()) {
                        this.startBlocking(this.tickCounter);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        this.forceAttackUnblocked = false;
        if (!Utils.nullCheck() || mc.player.removed || mc.screen != null) {
            this.resetState(true);
        } else if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldSuspendAutoblock()) {
            this.resetState(true);
        } else {
            int selfHurtTime = mc.player.damagedTimer;
            boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
            this.lastSelfHurtTime = selfHurtTime;
            if (!Utils.holdingSword()) {
                this.resetState(false);
            } else {
                if (!this.isPostMode() && this.postBlocking) {
                    this.stopBlocking(true);
                    this.manualBlock = false;
                }

                if (this.allowingInteraction && !Mouse.isButtonDown(1)) {
                    this.allowingInteraction = false;
                }

                if (this.allowingInteraction) {
                    this.releaseLag();
                    this.stopBlocking(true);
                    this.manualBlock = false;
                } else {
                    this.tickCounter++;
                    int currentTick = this.tickCounter;
                    this.currentTarget = CombatTargeting.findTarget(
                            this.range.getInput() * this.range.getInput(), this.ignoreTeammates.isToggled());
                    if (this.isPostMode() || !this.forceAttack.isToggled() || !mc.focused) {
                        this.pendingForceAttacks = 0;
                    }

                    if (this.pendingForceAttacks > 0) {
                        this.forceAttackUnblocked = true;
                        this.releaseLag();
                        this.stopBlocking(true);
                        this.manualBlock = false;
                    } else {
                        if (!this.isLagMode() && this.isLagging) {
                            this.releaseLag();
                        }

                        boolean killAuraAttacking = ModuleManager.killAura != null
                                && ModuleManager.killAura.isEnabled()
                                && !ModuleManager.killAura.isRequireMouseDown()
                                && this.currentTarget != null;
                        boolean rmbDown = Mouse.isButtonDown(1);
                        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;
                        boolean hasTarget = this.currentTarget != null;
                        boolean conditionsMet = hasTarget && this.checkConditions(lmbDown, rmbDown);
                        boolean leftTargetRange = this.targetWasInRange && !hasTarget;
                        this.targetWasInRange = hasTarget;
                        int unblockMode = (int) this.unblockOutOfRange.getInput();
                        if (unblockMode != 0 || !rmbDown || hasTarget) {
                            this.unblockedAfterLeavingRange = false;
                        }

                        if (this.isVanillaUseOutOfRange()) {
                            boolean wasActive = this.isBlocking || this.isLagging || this.postBlocking;
                            this.releaseLag();
                            if (this.isBlocking || this.postBlocking) {
                                this.stopBlocking(true);
                            }

                            this.manualBlock = false;
                            int keyCode = mc.options.useKey.getKeyCode();
                            KeyBinding.set(keyCode, rmbDown);
                            if (wasActive && rmbDown && !mc.player.hasItemInUse()) {
                                KeyBinding.click(keyCode);
                            }
                        } else if (this.isAlwaysUnblockMode() && !hasTarget) {
                            this.releaseLag();
                            this.stopBlocking(true);
                            this.manualBlock = false;
                        } else if (unblockMode == 0 && rmbDown && leftTargetRange) {
                            if (this.isLagging) {
                                this.releaseLag();
                            }

                            this.stopBlocking(true);
                            this.manualBlock = false;
                            this.unblockedAfterLeavingRange = true;
                        } else if (this.isPostMode()) {
                            if (conditionsMet || rmbDown && !this.unblockedAfterLeavingRange) {
                                KeyBinding.set(mc.options.useKey.getKeyCode(), false);
                                this.isBlocking = true;
                                this.manualBlock = !conditionsMet;
                                this.syncBlockAnimation();
                            } else {
                                this.stopBlocking(true);
                                this.manualBlock = false;
                            }
                        } else {
                            if (hurtAgain) {
                                this.releaseLag();
                                this.stopBlocking(true);
                                this.manualBlock = false;
                            }

                            if (!conditionsMet && rmbDown) {
                                if (!this.unblockedAfterLeavingRange) {
                                    if (this.isLagging) {
                                        this.releaseLag();
                                    }

                                    if (!this.isBlocking) {
                                        this.startBlocking(currentTick);
                                    }

                                    this.manualBlock = true;
                                }
                            } else {
                                if (this.manualBlock) {
                                    this.stopBlocking(true);
                                    this.manualBlock = false;
                                }

                                if (this.isLagging) {
                                    int lagMaxTicks = msToTicks(this.lagMaxDuration.getInput());
                                    boolean lagExpired = lagMaxTicks > 0
                                            && this.lagStartTick >= 0
                                            && currentTick - this.lagStartTick >= lagMaxTicks;
                                    if (lagExpired || !conditionsMet) {
                                        this.releaseLag();
                                        if (lagExpired && this.blockAgainImmediately.isToggled() && conditionsMet) {
                                            this.startBlocking(currentTick);
                                        }
                                    }
                                }

                                if (!conditionsMet) {
                                    this.stopBlocking(true);
                                } else {
                                    if (!this.isBlocking && !this.isLagging && this.shouldPredictiveBlock()) {
                                        this.startBlocking(currentTick);
                                    }

                                    if (this.isBlocking) {
                                        int maxHoldTicks = msToTicks(this.maxHoldMs.getInput());
                                        boolean timeExpired = maxHoldTicks > 0
                                                && this.blockStartTick >= 0
                                                && currentTick - this.blockStartTick >= maxHoldTicks;
                                        if (timeExpired) {
                                            if (this.shouldStartLag()) {
                                                this.startLag(currentTick);
                                            }

                                            this.stopBlocking(true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (this.isPostMode() && this.isBlocking) {
            if (Utils.nullCheck()
                    && !mc.player.removed
                    && mc.screen == null
                    && Utils.holdingSword()
                    && (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock())) {
                if (this.sendInteractAt.isToggled()
                        && CombatTargeting.isValidPlayer(
                                this.currentTarget,
                                this.range.getInput() * this.range.getInput(),
                                this.ignoreTeammates.isToggled())
                        && mc.world.getEntity(this.currentTarget.getNetworkId()) == this.currentTarget) {
                    Vec3d eyes = mc.player.getEyePosition(1.0F);
                    Vec3d look = RotationUtils.getVectorForRotation(
                            RotationUtils.serverRotations[1], RotationUtils.serverRotations[0]);
                    double distance = RotationUtils.distanceFromEyeToClosestOnAABB(this.currentTarget);
                    Vec3d projected = eyes.add(look.x * distance, look.y * distance, look.z * distance);
                    Box bounds = this.currentTarget.getShape();
                    Vec3d hitVec = new Vec3d(
                            MathHelper.clamp(projected.x, bounds.minX, bounds.maxX) - this.currentTarget.x,
                            MathHelper.clamp(projected.y, bounds.minY, bounds.maxY) - this.currentTarget.y,
                            MathHelper.clamp(projected.z, bounds.minZ, bounds.maxZ) - this.currentTarget.z);
                    mc.player.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(this.currentTarget, hitVec));
                }

                mc.player.networkHandler.sendPacket(new PlayerUseC2SPacket(mc.player.getDisplayItemInHand()));
                this.postBlocking = true;
                this.syncBlockAnimation();
            } else {
                this.resetState(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onForceAttack(PrePlayerInteractEvent e) {
        if (!this.isPostMode()
                && this.forceAttack.isToggled()
                && Utils.nullCheck()
                && !mc.player.removed
                && mc.screen == null
                && mc.focused
                && Utils.holdingSword()) {
            if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock()) {
                if (this.allowingInteraction) {
                    this.pendingForceAttacks = 0;
                } else {
                    KeyBinding attackKey = mc.options.attackKey;
                    if (this.pendingForceAttacks > 0 && !mc.player.hasItemInUse()) {
                        for (int i = 0; i < this.pendingForceAttacks; i++) {
                            KeyBinding.click(attackKey.getKeyCode());
                        }

                        this.pendingForceAttacks = 0;
                    } else if (this.isBlocking
                            || this.isLagging
                            || mc.player.isSwordBlocking()
                            || this.pendingForceAttacks != 0) {
                        boolean attackPressed = attackKey.consumeClick();
                        if (attackPressed || this.pendingForceAttacks != 0) {
                            if (mc.player.hasItemInUse()) {
                                if (attackPressed) {
                                    this.pendingForceAttacks++;
                                }

                                while (attackKey.consumeClick()) {
                                    this.pendingForceAttacks++;
                                }
                            } else {
                                KeyBinding.click(attackKey.getKeyCode());
                            }

                            this.forceAttackUnblocked = true;
                            this.releaseLag();
                            this.stopBlocking(true);
                            this.manualBlock = false;
                        }
                    }
                }
            }
        }
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        return this.requireLmb.isToggled() && !lmbDown ? false : !this.requireRmb.isToggled() || rmbDown;
    }

    private boolean shouldPredictiveBlock() {
        int ourHurtTime = mc.player.damagedTimer;
        int triggerTick = (int) Math.round(this.maxHurtTimeMs.getInput() / 50.0);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return ourHurtTime == triggerTick || !this.onlyWhenDamaged.isToggled() && ourHurtTime == 0;
    }

    private boolean shouldBlockVanillaUse() {
        return this.isEnabled()
                && (this.isPostMode() && !this.allowingInteraction && !this.isVanillaUseOutOfRange()
                        || this.isLagging
                        || this.forceAttackUnblocked
                        || ModuleManager.bedAura != null && ModuleManager.bedAura.shouldSuspendAutoblock())
                && Utils.nullCheck()
                && Utils.holdingSword()
                && mc.screen == null;
    }

    private void startBlocking(int currentTick) {
        if (!this.forceAttackUnblocked && Utils.holdingSword() && !this.isCooldownActive()) {
            int keyCode = mc.options.useKey.getKeyCode();
            KeyBinding.set(keyCode, true);
            KeyBinding.click(keyCode);
            this.isBlocking = true;
            this.blockStartTick = currentTick;
            this.syncBlockAnimation();
        }
    }

    private void stopBlocking(boolean forceRelease) {
        if (this.isBlocking || forceRelease) {
            boolean wasBlocking = this.isBlocking;
            int keyCode = mc.options.useKey.getKeyCode();
            KeyBinding.set(keyCode, false);
            this.isBlocking = false;
            this.blockStartTick = -1;
            if (wasBlocking) {
                this.lastBlockEndTimeMs = System.currentTimeMillis();
            }

            if (this.postBlocking) {
                this.postBlocking = false;
                if (Utils.nullCheck()) {
                    PacketUtils.sendReleasePacket();
                }
            }

            this.syncBlockAnimation();
        }
    }

    private boolean isCooldownActive() {
        double cooldown = this.cooldownMs.getInput();
        return cooldown > 0.0
                && this.lastBlockEndTimeMs > 0L
                && System.currentTimeMillis() - this.lastBlockEndTimeMs < cooldown;
    }

    private boolean shouldStartLag() {
        if (!this.isLagMode()) {
            return false;
        } else {
            double chance = this.lagChance.getInput();
            if (chance <= 0.0) {
                return false;
            } else {
                return chance >= 100.0 ? true : Math.random() * 100.0 < chance;
            }
        }
    }

    private void startLag(int currentTick) {
        if (!this.isLagging) {
            int lagReferenceTick = this.blockStartTick >= 0 ? this.blockStartTick : currentTick;
            int lagMaxTicks = msToTicks(this.lagMaxDuration.getInput());
            if (lagMaxTicks <= 0 || currentTick - lagReferenceTick < lagMaxTicks) {
                this.outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
                Feral.lagHandler.requestLag(this.outboundLag);
                this.isLagging = true;
                this.lagStartTick = lagReferenceTick;
                this.syncBlockAnimation();
            }
        }
    }

    private void releaseLag() {
        if (this.isLagging) {
            if (this.outboundLag != null) {
                this.outboundLag.getTimeout().forceTimeOut();
                this.outboundLag = null;
            }

            this.isLagging = false;
            this.lagStartTick = -1;
            this.syncBlockAnimation();
        }
    }

    public void prepareForBedMining() {
        if (this.isEnabled() && Utils.nullCheck()) {
            boolean releaseVanillaUse = !this.postBlocking && mc.player.hasItemInUse();
            this.releaseLag();
            if (releaseVanillaUse) {
                mc.interactionManager.stopUsingHand(mc.player);
            }

            this.stopBlocking(true);
            this.manualBlock = false;
        }
    }

    public boolean isActive() {
        return this.isEnabled() && (this.isBlocking || this.isLagging);
    }

    public boolean isBlocking() {
        return this.isEnabled() && this.isBlocking;
    }

    public boolean isBlockingForSwing() {
        return this.isEnabled() && (this.isBlocking || this.forceAttackUnblocked);
    }

    public boolean shouldApplyNoSlow() {
        return this.isEnabled()
                && this.isPostMode()
                && this.workWithNoSlow.isToggled()
                && this.isBlocking
                && !this.allowingInteraction
                && Utils.nullCheck()
                && !mc.player.removed
                && mc.screen == null
                && Utils.holdingSword()
                && (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock());
    }

    private boolean isLagMode() {
        return this.mode.getInput() == 1.0;
    }

    private boolean isPostMode() {
        return this.mode.getInput() == 2.0;
    }

    private boolean isAlwaysUnblockMode() {
        int unblockMode = (int) this.unblockOutOfRange.getInput();
        return unblockMode == 1 || unblockMode == 2;
    }

    private boolean isVanillaUseOutOfRange() {
        return this.unblockOutOfRange.getInput() < 0.0 && this.currentTarget == null;
    }

    private boolean isFakeBlockOutOfRange() {
        return this.isEnabled()
                && (int) this.unblockOutOfRange.getInput() == 2
                && this.currentTarget == null
                && Utils.nullCheck()
                && !mc.player.removed
                && mc.screen == null
                && Utils.holdingSword()
                && (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock());
    }

    private boolean canInteract() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null) {
            return false;
        } else if (hit.type == Type.BLOCK) {
            return BlockUtils.isInteractable(hit);
        } else if (hit.type == Type.ENTITY && hit.entity != null) {
            Entity entity = hit.entity;
            return !(entity instanceof PlayerEntity) || AntiBot.isBot(entity);
        } else {
            return false;
        }
    }

    private void syncBlockAnimation() {
        boolean killAuraAttacking = ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && !ModuleManager.killAura.isRequireMouseDown()
                && this.currentTarget != null;
        boolean requiredMouseButtonsDown =
                this.checkConditions(Mouse.isButtonDown(0) || killAuraAttacking, Mouse.isButtonDown(1));
        boolean continuousUndamagedBlock =
                !this.onlyWhenDamaged.isToggled() && this.currentTarget != null && !this.allowingInteraction;
        boolean shouldAnimate = (this.isPostMode() || this.forceBlockAnimation.isToggled())
                && Utils.nullCheck()
                && (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldSuspendAutoblock())
                && mc.screen == null
                && Utils.holdingSword()
                && (this.isPostMode()
                        ? this.isBlocking
                        : requiredMouseButtonsDown && (continuousUndamagedBlock || this.isBlocking || this.isLagging));
        boolean fakeBlock = this.isFakeBlockOutOfRange()
                && mc.focused
                && !this.allowingInteraction
                && Utils.isBindDown(mc.options.useKey);
        ReflectionUtils.setItemInUse(shouldAnimate || fakeBlock);
    }

    private void resetState(boolean releaseUseKey) {
        this.pendingForceAttacks = 0;
        this.forceAttackUnblocked = false;
        boolean restorePhysicalUse = this.isBlocking
                && (this.isPostMode() || mc.options.useKey.isPressed())
                && Mouse.isButtonDown(1)
                && mc.screen == null;
        this.releaseLag();
        this.stopBlocking(releaseUseKey);
        this.manualBlock = false;
        this.targetWasInRange = false;
        this.unblockedAfterLeavingRange = false;
        this.allowingInteraction = false;
        this.lastBlockEndTimeMs = 0L;
        this.currentTarget = null;
        this.lastSelfHurtTime = 0;
        this.syncBlockAnimation();
        if (restorePhysicalUse) {
            KeyBinding.set(mc.options.useKey.getKeyCode(), true);
        }
    }
}

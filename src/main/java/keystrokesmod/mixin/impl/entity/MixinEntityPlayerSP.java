package keystrokesmod.mixin.impl.entity;

import com.mojang.authlib.GameProfile;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PostUpdateEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreMovementPacketEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.WTap;
import keystrokesmod.module.impl.movement.NoSlow;
import keystrokesmod.module.impl.movement.Sprint;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.impl.player.Scaffold;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndAngles;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket.Action;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalClientPlayerEntity.class, priority = 999)
public abstract class MixinEntityPlayerSP extends ClientPlayerEntity {
    @Shadow
    public int sprintTimer;

    @Shadow
    protected int doubleTapSprintTime;

    @Shadow
    public float lastPortalTime;

    @Shadow
    public float portalTime;

    @Shadow
    protected Minecraft minecraft;

    @Shadow
    public Input input;

    @Shadow
    private int horseJumpTimer;

    @Shadow
    private float horseJumpSize;

    @Shadow
    private boolean sentSprinting;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    private boolean sentSneaking;

    @Shadow
    private double sentX;

    @Shadow
    private double sentY;

    @Shadow
    private double sentZ;

    @Shadow
    private float sentYaw;

    @Shadow
    private float sentPitch;

    @Shadow
    private int ticksSinceSentPosition;

    public MixinEntityPlayerSP(World p_i45074_1_, GameProfile p_i45074_2_) {
        super(p_i45074_1_, p_i45074_2_);
    }

    @Shadow
    public abstract void setSprinting(boolean var1);

    @Shadow
    public abstract void syncAbilities();

    @Shadow
    protected abstract boolean isCamera();

    @Shadow
    public abstract boolean isRidingRideableMob();

    @Shadow
    protected abstract void startRidingJump();

    @Shadow
    public abstract boolean isSneaking();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onUpdatePre(CallbackInfo c) {
        if (!Utils.isLocalPlayerSubUpdate()) {
            if (this.world.isChunkLoaded(new BlockPos(this.x, 0.0, this.z))) {
                RotationUtils.prevRenderPitch = RotationUtils.renderPitch;
                RotationUtils.prevRenderYaw = RotationUtils.renderYaw;
                ForgeEvents.MinecraftForge.EVENT_BUS.post(new PreUpdateEvent());
            }
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void onUpdatePost(CallbackInfo c) {
        if (!Utils.isLocalPlayerSubUpdate()) {
            if (this.world.isChunkLoaded(new BlockPos(this.x, 0.0, this.z))) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostUpdateEvent());
            }
        }
    }

    @Overwrite
    public void sendMovementToServer() {
        PreMotionEvent.setRotations = false;
        PreMotionEvent.setRenderYaw(false);
        RotationUtils.setFakeRotations = false;
        PreMotionEvent preMotionEvent = new PreMotionEvent(
                this.x,
                this.getShape().minY,
                this.z,
                this.yaw,
                this.pitch,
                this.onGround,
                this.isSprinting(),
                this.isSneaking());
        ForgeEvents.MinecraftForge.EVENT_BUS.post(preMotionEvent);
        RotationUtils.serverRotations = new float[] {preMotionEvent.getYaw(), preMotionEvent.getPitch()};
        boolean flag = preMotionEvent.isSprinting();
        if (flag != this.sentSprinting) {
            if (flag) {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, Action.START_SPRINTING));
            } else {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, Action.STOP_SPRINTING));
            }

            this.sentSprinting = flag;
        }

        boolean flag1 = preMotionEvent.isSneaking();
        if (flag1 != this.sentSneaking) {
            if (flag1) {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, Action.START_SNEAKING));
            } else {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, Action.STOP_SNEAKING));
            }

            this.sentSneaking = flag1;
        }

        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PreMovementPacketEvent());
        if (this.isCamera() || Freecam.freeEntity != null) {
            if (PreMotionEvent.setRenderYaw()) {
                RotationUtils.setRenderYaw(preMotionEvent.getYaw());
            }

            RotationUtils.renderPitch = preMotionEvent.getPitch();
            RotationUtils.renderYaw = preMotionEvent.getYaw();
            if (RotationUtils.setFakeRotations) {
                RotationUtils.renderPitch = RotationUtils.fakeRotations[1];
                RotationUtils.renderYaw = RotationUtils.fakeRotations[0];
                RotationUtils.setRenderYaw(RotationUtils.renderYaw);
            }

            RotationUtils.setFakeRotations = false;
            double d0 = preMotionEvent.getPosX() - this.sentX;
            double d1 = preMotionEvent.getPosY() - this.sentY;
            double d2 = preMotionEvent.getPosZ() - this.sentZ;
            double d3 = preMotionEvent.getYaw() - this.sentYaw;
            double d4 = preMotionEvent.getPitch() - this.sentPitch;
            boolean flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4 || this.ticksSinceSentPosition >= 20;
            boolean flag3 = d3 != 0.0 || d4 != 0.0;
            if (this.vehicle == null) {
                if (flag2 && flag3) {
                    this.networkHandler.sendPacket(new PositionAndAngles(
                            preMotionEvent.getPosX(),
                            preMotionEvent.getPosY(),
                            preMotionEvent.getPosZ(),
                            preMotionEvent.getYaw(),
                            preMotionEvent.getPitch(),
                            preMotionEvent.isOnGround()));
                } else if (flag2) {
                    this.networkHandler.sendPacket(new Position(
                            preMotionEvent.getPosX(),
                            preMotionEvent.getPosY(),
                            preMotionEvent.getPosZ(),
                            preMotionEvent.isOnGround()));
                } else if (flag3) {
                    this.networkHandler.sendPacket(new Angles(
                            preMotionEvent.getYaw(), preMotionEvent.getPitch(), preMotionEvent.isOnGround()));
                } else {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket(preMotionEvent.isOnGround()));
                }
            } else {
                this.networkHandler.sendPacket(new PositionAndAngles(
                        this.velocityX,
                        -999.0,
                        this.velocityZ,
                        preMotionEvent.getYaw(),
                        preMotionEvent.getPitch(),
                        preMotionEvent.isOnGround()));
                flag2 = false;
            }

            this.ticksSinceSentPosition++;
            if (flag2) {
                this.sentX = preMotionEvent.getPosX();
                this.sentY = preMotionEvent.getPosY();
                this.sentZ = preMotionEvent.getPosZ();
                this.ticksSinceSentPosition = 0;
            }

            if (flag3) {
                this.sentYaw = preMotionEvent.getYaw();
                this.sentPitch = preMotionEvent.getPitch();
            }
        }

        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostMotionEvent());
    }

    @Inject(method = "serverTickAi", at = @At("RETURN"))
    private void onUpdateEntityActionState(CallbackInfo ci) {
        if (Freecam.freeEntity != null) {
            this.forwardSpeed = 0.0F;
            this.sidewaysSpeed = 0.0F;
            this.jumping = false;
        }
    }

    @Overwrite
    public void mobTick() {
        if (this.sprintTimer > 0) {
            this.sprintTimer--;
            if (this.sprintTimer == 0) {
                this.setSprinting(false);
            }
        }

        if (this.doubleTapSprintTime > 0) {
            this.doubleTapSprintTime--;
        }

        this.lastPortalTime = this.portalTime;
        if (this.inPortal) {
            if (this.minecraft.screen != null && !this.minecraft.screen.shouldPauseGame()) {
                this.minecraft.openScreen(null);
            }

            if (this.portalTime == 0.0F) {
                this.minecraft
                        .getSoundManager()
                        .play(SimpleSoundInstance.of(
                                new Identifier("portal.trigger"), this.random.nextFloat() * 0.4F + 0.8F));
            }

            this.portalTime += 0.0125F;
            if (this.portalTime >= 1.0F) {
                this.portalTime = 1.0F;
            }

            this.inPortal = false;
        } else if (this.hasStatusEffect(StatusEffect.NAUSEA)
                && this.getEffectInstance(StatusEffect.NAUSEA).getDuration() > 60
                && (ModuleManager.antiDebuff == null
                        || !ModuleManager.antiDebuff.canRemoveNausea(StatusEffect.NAUSEA))) {
            this.portalTime += 0.006666667F;
            if (this.portalTime > 1.0F) {
                this.portalTime = 1.0F;
            }
        } else {
            if (this.portalTime > 0.0F) {
                this.portalTime -= 0.05F;
            }

            if (this.portalTime < 0.0F) {
                this.portalTime = 0.0F;
            }
        }

        if (this.portalCooldown > 0) {
            this.portalCooldown--;
        }

        boolean flag = this.input.jumping;
        boolean flag1 = this.input.sneaking;
        float f = 0.8F;
        boolean flag2 = this.input.movementForward >= f;
        this.input.tick();
        boolean usingItem = NoSlow.isUsingItemForMovement(this);
        boolean stopSprint =
                ModuleManager.noSlow == null || !ModuleManager.noSlow.isEnabled() || NoSlow.slowed.getInput() == 80.0;
        if (usingItem && !this.isRiding()) {
            Input var10000 = this.input;
            float slowed = NoSlow.getSlowed();
            var10000.movementSideways *= slowed;
            var10000 = this.input;
            var10000.movementForward *= slowed;
            if (stopSprint) {
                this.doubleTapSprintTime = 0;
            }
        }

        this.pushAwayFrom(this.x - this.width * 0.35, this.getShape().minY + 0.5, this.z + this.width * 0.35);
        this.pushAwayFrom(this.x - this.width * 0.35, this.getShape().minY + 0.5, this.z - this.width * 0.35);
        this.pushAwayFrom(this.x + this.width * 0.35, this.getShape().minY + 0.5, this.z - this.width * 0.35);
        this.pushAwayFrom(this.x + this.width * 0.35, this.getShape().minY + 0.5, this.z + this.width * 0.35);
        boolean flag3 = this.getHungerManager().getFoodLevel() > 6.0F || this.abilities.canFly;
        Scaffold scaffold = (Scaffold) ModuleManager.getModule(Scaffold.class);
        boolean scaffoldSprint = scaffold != null
                && scaffold.isAlwaysSprinting()
                && !this.input.sneaking
                && (this.input.movementForward != 0.0F || this.input.movementSideways != 0.0F);
        boolean scaffoldAllowsSprint = scaffold == null || scaffold.canSprint();
        boolean sprintDirectionAllowed = scaffoldAllowsSprint && (this.input.movementForward >= f || scaffoldSprint);
        boolean effectiveSprintKeyDown = this.minecraft.options.sprintKey.isPressed() || scaffoldSprint;
        if (scaffoldAllowsSprint
                && this.onGround
                && !flag1
                && !flag2
                && this.input.movementForward >= f
                && !this.isSprinting()
                && flag3
                && (!usingItem || !stopSprint)
                && !this.hasStatusEffect(StatusEffect.BLINDNESS)) {
            if (this.doubleTapSprintTime <= 0 && !effectiveSprintKeyDown) {
                this.doubleTapSprintTime = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting()
                && effectiveSprintKeyDown
                && (this.input.movementForward != 0.0F || this.input.movementSideways != 0.0F)
                && sprintDirectionAllowed
                && flag3
                && (!usingItem && !this.minecraft.player.isSwordBlocking() || !stopSprint)
                && !this.hasStatusEffect(StatusEffect.BLINDNESS)) {
            this.setSprinting(true);
        }

        if (this.isSprinting()
                && (!sprintDirectionAllowed
                        || !flag3
                        || this.collidingHorizontally
                        || ModuleUtils.setSlow
                        || this.input.movementForward == 0.0F && this.input.movementSideways == 0.0F
                        || this.minecraft.options.sneakKey.isPressed()
                        || ModuleManager.wTap.isEnabled() && WTap.stopSprint)) {
            this.setSprinting(false);
            WTap.stopSprint = false;
        }

        Sprint sprintMod = ModuleManager.sprint;
        if (scaffoldAllowsSprint
                && !RotationHelper.get().ownsServerRelativeMovement()
                && !this.isSprinting()
                && sprintMod != null
                && sprintMod.isEnabled()
                && !this.collidingHorizontally
                && (this.input.movementForward != 0.0F || this.input.movementSideways != 0.0F)
                && !this.minecraft.options.sneakKey.isPressed()
                && !this.hasStatusEffect(StatusEffect.BLINDNESS)) {
            boolean movingBackwards = this.input.movementForward < 0.0F;
            boolean movingSideways = this.input.movementForward == 0.0F && this.input.movementSideways != 0.0F;
            boolean directionAllowed = !movingBackwards && !movingSideways
                    || movingBackwards && sprintMod.allowWhileBackwards()
                    || movingSideways && sprintMod.allowWhileSideways();
            boolean usingItemAllowed = !usingItem || sprintMod.allowWhileUsingItem();
            boolean needsOverride = movingBackwards || movingSideways || usingItem;
            if (directionAllowed && usingItemAllowed && needsOverride) {
                this.setSprinting(true);
            }
        }

        if (this.abilities.canFly) {
            if (this.minecraft.interactionManager.isSpectator()) {
                if (!this.abilities.flying) {
                    this.abilities.flying = true;
                    this.syncAbilities();
                }
            } else if (!flag && this.input.jumping) {
                if (this.pressedJumpTwiceTimer == 0) {
                    this.pressedJumpTwiceTimer = 7;
                } else {
                    this.abilities.flying = !this.abilities.flying;
                    this.syncAbilities();
                    this.pressedJumpTwiceTimer = 0;
                }
            }
        }

        if (this.abilities.flying && this.isCamera()) {
            if (this.input.sneaking) {
                this.velocityY = this.velocityY - this.abilities.getFlySpeed() * 3.0F;
            }

            if (this.input.jumping) {
                this.velocityY = this.velocityY + this.abilities.getFlySpeed() * 3.0F;
            }
        }

        if (this.isRidingRideableMob()) {
            if (this.horseJumpTimer < 0) {
                this.horseJumpTimer++;
                if (this.horseJumpTimer == 0) {
                    this.horseJumpSize = 0.0F;
                }
            }

            if (flag && !this.input.jumping) {
                this.horseJumpTimer = -10;
                this.startRidingJump();
            } else if (!flag && this.input.jumping) {
                this.horseJumpTimer = 0;
                this.horseJumpSize = 0.0F;
            } else if (flag) {
                this.horseJumpTimer++;
                if (this.horseJumpTimer < 10) {
                    this.horseJumpSize = this.horseJumpTimer * 0.1F;
                } else {
                    this.horseJumpSize = 0.8F + 2.0F / (this.horseJumpTimer - 9) * 0.1F;
                }
            }
        } else {
            this.horseJumpSize = 0.0F;
        }

        super.mobTick();
        if (this.onGround && this.abilities.flying && !this.minecraft.interactionManager.isSpectator()) {
            this.abilities.flying = false;
            this.syncAbilities();
        }
    }
}

package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class AntiFireball extends Module {
    private final SliderSetting fov;
    private final SliderSetting range;
    private final SliderSetting targetCPS;
    private final SliderSetting rotationSpeed;
    private final ButtonSetting noRotate;
    private final ButtonSetting onlyOnGround;
    private final ButtonSetting sneakWhileActive;
    private final ButtonSetting disableInReplays;
    public ProjectileEntity fireball;
    private final HashSet<Entity> fireballs = new HashSet<>();
    private long nextClickTimeNanos;
    private ProjectileEntity scheduledFireball;
    private final Random rand = new Random();

    public AntiFireball() {
        super("Anti Fireball", Module.category.player);
        this.registerSetting(this.targetCPS = new SliderSetting("Target CPS", 12.0, 1.0, 20.0, 0.5));
        this.registerSetting(this.fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(this.range = new SliderSetting("Range", 8.0, 3.0, 15.0, 0.5));
        this.registerSetting(this.rotationSpeed = new SliderSetting("Rotation speed", 15.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.noRotate = new ButtonSetting("No rotate", false));
        this.registerSetting(this.onlyOnGround = new ButtonSetting("Only on ground", false));
        this.registerSetting(this.sneakWhileActive = new ButtonSetting("Sneak while active", false));
        this.registerSetting(this.disableInReplays = new ButtonSetting("Disable in replays", true));
    }

    @Override
    public void onDisable() {
        this.fireballs.clear();
        this.fireball = null;
        this.resetClickScheduler();
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck() && mc.screen == null && (!this.disableInReplays.isToggled() || !Utils.isReplay())) {
            ProjectileEntity selectedFireball = this.getFireball();
            if (selectedFireball == null || selectedFireball != this.fireball) {
                this.resetClickScheduler();
            }

            this.fireball = selectedFireball;
        } else {
            this.fireball = null;
            this.resetClickScheduler();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (Utils.nullCheck() && mc.screen == null && (!this.disableInReplays.isToggled() || !Utils.isReplay())) {
            if (!this.isFireballValid(this.fireball)) {
                this.resetClickScheduler();
            } else {
                if (this.sneakWhileActive.isToggled() && !mc.player.isRiding() && !mc.player.abilities.flying) {
                    e.setSneak(true);
                }
            }
        } else {
            this.fireball = null;
            this.resetClickScheduler();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (Utils.nullCheck() && mc.screen == null && (!this.disableInReplays.isToggled() || !Utils.isReplay())) {
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
                this.resetClickScheduler();
            } else if (this.onlyOnGround.isToggled() && !mc.player.onGround) {
                this.resetClickScheduler();
            } else if (!this.isFireballValid(this.fireball)) {
                this.resetClickScheduler();
            } else if (!this.noRotate.isToggled()) {
                float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                float[] target = this.computeAimRotations(baseYaw, basePitch);
                float[] smooth = RotationUtils.smoothRotation(
                        baseYaw, basePitch, target[0], target[1], (int) this.rotationSpeed.getInput());
                e.setYaw(smooth[0]);
                e.setPitch(smooth[1]);
            }
        } else {
            this.fireball = null;
            this.resetClickScheduler();
        }
    }

    private float[] computeAimRotations(float baseYaw, float basePitch) {
        Vec3d eye = mc.player.getEyePosition(1.0F);
        float borderSize = this.fireball.getPickRadius();
        Box fireballBox = this.fireball.getShape().grown(borderSize, borderSize, borderSize);
        double reach = mc.interactionManager.getReach();
        List<PlayerEntity> players = new ArrayList<>();

        for (PlayerEntity player : mc.world.players) {
            if (player != mc.player
                    && player.deathTicks == 0
                    && !Utils.isFriended(player)
                    && !Utils.isTeammate(player)
                    && !AntiBot.isBot(player)) {
                players.add(player);
            }
        }

        players.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)));

        for (PlayerEntity player : players) {
            float[] rot = RotationUtils.getRotationsToPoint(player.x, player.y, player.z, baseYaw, basePitch);
            if (this.hitsFireballBox(eye, rot[0], rot[1], fireballBox, reach)) {
                return rot;
            }
        }

        double topY = fireballBox.maxY;
        double centerX = (fireballBox.minX + fireballBox.maxX) / 2.0;
        double centerZ = (fireballBox.minZ + fireballBox.maxZ) / 2.0;
        return RotationUtils.getRotationsToPoint(centerX, topY, centerZ, baseYaw, basePitch);
    }

    private boolean hitsFireballBox(Vec3d eye, float yaw, float pitch, Box box, double range) {
        Vec3d look = Utils.getLookVec(yaw, pitch);
        Vec3d end = eye.add(look.x * range, look.y * range, look.z * range);
        HitResult intercept = box.clip(eye, end);
        return intercept != null;
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (Utils.nullCheck() && mc.screen == null && (!this.disableInReplays.isToggled() || !Utils.isReplay())) {
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.shouldOverrideMouseOver()) {
                this.resetClickScheduler();
            } else if (this.onlyOnGround.isToggled() && !mc.player.onGround) {
                this.resetClickScheduler();
            } else if (!this.isFireballValid(this.fireball)) {
                this.resetClickScheduler();
            } else {
                HitResult mop = mc.crosshairTarget;
                if (mop != null && mop.type == Type.ENTITY && mop.entity == this.fireball) {
                    if (this.scheduledFireball != this.fireball) {
                        this.resetClickScheduler();
                        this.scheduledFireball = this.fireball;
                    }

                    long now = System.nanoTime();
                    if (this.nextClickTimeNanos == 0L) {
                        this.nextClickTimeNanos = now;
                    }

                    int key = mc.options.attackKey.getKeyCode();
                    if (now - this.nextClickTimeNanos >= 0L) {
                        if (!this.isFireballValid(this.fireball)
                                || mc.crosshairTarget == null
                                || mc.crosshairTarget.type != Type.ENTITY
                                || mc.crosshairTarget.entity != this.fireball) {
                            this.resetClickScheduler();
                            return;
                        }

                        KeyBinding.click(key);
                        ReflectionUtils.setButton(0, true);
                        this.nextClickTimeNanos = now + this.nextDelayNanos();
                    }
                } else {
                    this.resetClickScheduler();
                }
            }
        } else {
            this.fireball = null;
            this.resetClickScheduler();
        }
    }

    private boolean isFireballValid(ProjectileEntity target) {
        return target != null
                && mc.world != null
                && !target.removed
                && mc.world.getEntity(target.getNetworkId()) == target
                && mc.world.entities.contains(target);
    }

    private void resetClickScheduler() {
        this.nextClickTimeNanos = 0L;
        this.scheduledFireball = null;
    }

    private ProjectileEntity getFireball() {
        double rangeSq = this.range.getInput() * this.range.getInput();
        float fovVal = (float) this.fov.getInput();
        if (this.onlyOnGround.isToggled() && !mc.player.onGround) {
            return null;
        }

        for (Entity entity : mc.world.entities) {
            if (entity instanceof ProjectileEntity
                    && this.fireballs.contains(entity)
                    && this.isFireballValid((ProjectileEntity) entity)
                    && !(mc.player.squaredDistanceTo(entity) > rangeSq)
                    && (fovVal == 360.0F || Utils.inFov(fovVal, entity))) {
                return (ProjectileEntity) entity;
            }
        }

        return null;
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (!Utils.nullCheck()) {
            this.resetClickScheduler();
        } else {
            if (e.entity == mc.player) {
                this.fireballs.clear();
                this.fireball = null;
                this.resetClickScheduler();
            } else if (e.entity instanceof ProjectileEntity && mc.player.squaredDistanceTo(e.entity) > 16.0) {
                this.fireballs.add(e.entity);
            }
        }
    }

    private long nextDelayNanos() {
        int target = Math.max(1, (int) this.targetCPS.getInput());
        int baseDelay = 1000 / target;
        int variation = this.rand.nextInt(Math.max(1, baseDelay / 3 + 1)) - baseDelay / 6;
        return TimeUnit.MILLISECONDS.toNanos(Math.max(33, baseDelay + variation));
    }
}

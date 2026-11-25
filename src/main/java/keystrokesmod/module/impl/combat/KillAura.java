package keystrokesmod.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.helper.zombies.ZombiesGameHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.BedWars;
import keystrokesmod.module.impl.minigames.SkyWars;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesMobs;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.GolemEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.MonsterEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Action;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class KillAura extends Module {
    private SliderSetting targetCPS;
    private SliderSetting fov;
    private SliderSetting attackRange;
    private SliderSetting swingRange;
    private SliderSetting aimRange;
    public SliderSetting rotationMode;
    private SliderSetting speed;
    private SliderSetting sortMode;
    private SliderSetting switchDelay;
    private SliderSetting targets;
    private ButtonSetting attackMobs;
    private ButtonSetting targetInvis;
    private ButtonSetting disableInInventory;
    private ButtonSetting disableWhileMining;
    private ButtonSetting aimThroughBlocks;
    private ButtonSetting aimThroughEntities;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting prioritizeEnemies;
    private ButtonSetting disableWhileUsing;
    private ButtonSetting disableWhileBlocking;
    private ButtonSetting silentSwingWhileBlocking;
    private ButtonSetting requireMouseDown;
    private ButtonSetting weaponOnly;
    private ButtonSetting usePackets;
    private String[] rotationModes = new String[] {"Silent", "Lock view", "None"};
    private String[] sortModes = new String[] {"Distance", "Health", "Hurt time", "Yaw"};
    public static LivingEntity target;
    public static LivingEntity attackingEntity;
    private final Map<Integer, Long> hitMap = new HashMap<>();
    private Long targetFirstAttackTime;
    private long targetSelectedTime;
    private List<Entity> hostileMobs = new ArrayList<>();
    private Map<Integer, Boolean> golems = new HashMap<>();
    private long nextClickTime;
    private boolean clickScheduled;
    private LivingEntity pendingClickTarget;
    private LivingEntity dispatchedClickTarget;
    private Random rand;
    private double targetDistance = Double.MAX_VALUE;

    public boolean isRequireMouseDown() {
        return this.requireMouseDown.isToggled();
    }

    public LivingEntity getRenderTarget() {
        return this.isEnabled() && target != null && this.targetDistance <= this.swingRange.getInput() ? target : null;
    }

    public KillAura() {
        super("KillAura", Module.category.combat);
        this.liteModule = true;
        this.registerSetting(this.targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(this.fov = new SliderSetting("FOV", "°", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(this.attackRange = new SliderSetting("Range (attack)", 3.0, 3.0, 6.0, 0.05));
        this.registerSetting(this.swingRange = new SliderSetting("Range (swing)", 4.5, 3.0, 8.0, 0.05));
        this.registerSetting(this.aimRange = new SliderSetting("Range (aim)", 4.5, 3.0, 8.0, 0.05));
        this.registerSetting(this.rotationMode = new SliderSetting("Rotation mode", 0, this.rotationModes));
        this.registerSetting(this.speed = new SliderSetting("Speed", 10.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.sortMode = new SliderSetting("Sort mode", 0, this.sortModes));
        this.registerSetting(this.switchDelay = new SliderSetting("Switch delay", "ms", 200.0, 50.0, 1000.0, 25.0));
        this.registerSetting(this.targets = new SliderSetting("Targets", 3.0, 1.0, 10.0, 1.0));
        this.registerSetting(this.targetInvis = new ButtonSetting("Target invis", true));
        this.registerSetting(this.attackMobs = new ButtonSetting("Attack mobs", false));
        this.registerSetting(this.aimThroughBlocks = new ButtonSetting("Hit through walls", false));
        this.registerSetting(this.aimThroughEntities = new ButtonSetting("Hit through entities", false));
        this.registerSetting(this.disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(this.disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(this.disableWhileUsing = new ButtonSetting("Disable while using", true, "Not using item"));
        this.registerSetting(this.disableWhileBlocking = new ButtonSetting("Disable while blocking", true));
        this.registerSetting(this.silentSwingWhileBlocking = new ButtonSetting("Silent swing while blocking", false));
        this.registerSetting(this.prioritizeEnemies = new ButtonSetting("Prioritize enemies", false));
        this.registerSetting(this.requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(this.usePackets = new ButtonSetting("Use packets", false));
    }

    @Override
    public String getInfo() {
        return this.rotationMode.getInput() == 2.0
                ? (int) this.fov.getInput() + this.fov.getSuffix()
                : this.rotationModes[(int) this.rotationMode.getInput()];
    }

    @Override
    public void onEnable() {
        this.rand = new Random();
        this.setTarget(null);
    }

    @Override
    public void onDisable() {
        this.hitMap.clear();
        this.setTarget(null);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOW)
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura != null) {
            ModuleManager.bedAura.prepareForKillAura();
        }

        if (!this.basicCondition() || !this.settingCondition()) {
            this.setTarget(null);
        } else if (this.bedAuraHasPriority()) {
            this.setTarget(null);
        } else {
            this.handleTarget();
            if (target != null) {
                this.targetDistance = RotationUtils.distanceFromEyeToClosestOnAABB(target);
                attackingEntity = this.targetDistance <= this.attackRange.getInput() ? target : null;
                if (this.rotationMode.getInput() == 0.0) {
                    float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                    float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                    float[] rot = this.getTargetRotations(baseYaw, basePitch);
                    if (rot != null) {
                        e.yaw = rot[0];
                        e.pitch = rot[1];
                    }
                } else if (this.rotationMode.getInput() == 1.0) {
                    float baseYaw = mc.player.yaw;
                    float basePitch = mc.player.pitch;
                    float[] rot = this.getTargetRotations(baseYaw, basePitch);
                    if (rot != null) {
                        rot = RotationUtils.fixRotation(rot[0], rot[1], baseYaw, basePitch);
                        mc.player.yaw = rot[0];
                        mc.player.pitch = rot[1];
                    }
                }
            }
        }
    }

    private float[] getTargetRotations(float baseYaw, float basePitch) {
        double range = this.aimRange.getInput();
        if (this.targetDistance > range) {
            return null;
        }

        boolean throughBlocks = this.aimThroughBlocks.isToggled();
        boolean throughEntities = this.aimThroughEntities.isToggled();
        float[] rot = this.usePackets.isToggled() || throughBlocks && throughEntities
                ? RotationUtils.getRotations(target, 100.0, 100.0, baseYaw, basePitch, 0.0F)
                : RotationUtils.getRotationsWithBackup(
                        target, 100.0, 100.0, baseYaw, basePitch, range, throughBlocks, throughEntities, true, 0.0F);
        return rot == null
                ? null
                : RotationUtils.smoothRotation(baseYaw, basePitch, rot[0], rot[1], (int) this.speed.getInput(), 0.0F);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        this.dispatchedClickTarget = null;
        if (!this.basicCondition()
                || !this.settingCondition()
                || !this.attackCondition()
                || target == null
                || !mc.focused
                || mc.screen != null && !mc.screen.passEvents
                || this.bedAuraHasPriority()) {
            this.resetClickState();
        } else if (!target.removed && target.deathTicks == 0 && mc.world.getEntity(target.getNetworkId()) == target) {
            this.targetDistance = RotationUtils.distanceFromEyeToClosestOnAABB(target);
            if (this.targetDistance > this.swingRange.getInput()) {
                this.resetClickState();
            } else {
                long now = System.nanoTime();
                if (!this.clickScheduled) {
                    this.nextClickTime = now;
                    this.clickScheduled = true;
                }

                if (this.pendingClickTarget != null && this.pendingClickTarget != target) {
                    this.pendingClickTarget = null;
                }

                if (now - this.nextClickTime >= 0L && this.pendingClickTarget == null) {
                    this.pendingClickTarget = target;
                }

                if (this.usePackets.isToggled() || ((IAccessorMinecraft) mc).getLeftClickCounter() <= 0) {
                    Displace displace = ModuleManager.displace;
                    if (displace != null && displace.processKillAuraAttack()) {
                        this.pendingClickTarget = null;
                    } else if (this.pendingClickTarget != null) {
                        if (!this.usePackets.isToggled()) {
                            mc.gameRenderer.pick(1.0F);
                        }

                        HitResult mouseOver = mc.crosshairTarget;
                        boolean packetAttack = this.usePackets.isToggled();
                        boolean onTarget = this.targetDistance <= this.attackRange.getInput()
                                && (packetAttack
                                        || mouseOver != null
                                                && mouseOver.type == Type.ENTITY
                                                && mouseOver.entity == this.pendingClickTarget);
                        boolean airSwing = this.targetDistance > this.attackRange.getInput()
                                && (packetAttack || mouseOver != null && mouseOver.type == Type.MISS);
                        if (onTarget || airSwing) {
                            if (onTarget
                                    && displace != null
                                    && displace.handleKillAuraAttack(this.pendingClickTarget)) {
                                this.pendingClickTarget = null;
                            } else {
                                LivingEntity attackTarget = onTarget ? this.pendingClickTarget : null;
                                this.pendingClickTarget = null;
                                this.nextClickTime = now + this.nextDelay();
                                if (this.usePackets.isToggled()) {
                                    boolean silentSwing = this.shouldSwingSilentlyWhileBlocking();
                                    if (silentSwing) {
                                        mc.player.networkHandler.sendPacket(new ArmSwingC2SPacket());
                                    } else {
                                        mc.player.swingArm();
                                    }

                                    if (attackTarget != null) {
                                        Autoblock autoblock = (Autoblock) ModuleManager.getModule(Autoblock.class);
                                        boolean blocking = mc.player.isSwordBlocking()
                                                || autoblock != null && autoblock.isBlocking();
                                        mc.interactionManager.attackEntity(mc.player, attackTarget);
                                        if (blocking) {
                                            mc.player.networkHandler.sendPacket(
                                                    new PlayerInteractEntityC2SPacket(attackTarget, Action.INTERACT));
                                        }
                                    }
                                } else {
                                    this.dispatchedClickTarget = attackTarget;

                                    try {
                                        ((IAccessorMinecraft) mc).callClickMouse();
                                    } finally {
                                        this.dispatchedClickTarget = null;
                                    }

                                    ReflectionUtils.setButton(0, true);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this.setTarget(null);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreAttack(PreAttackEvent e) {
        if (this.dispatchedClickTarget != null
                && (!this.basicCondition()
                        || !this.settingCondition()
                        || !this.attackCondition()
                        || this.bedAuraHasPriority()
                        || this.dispatchedClickTarget != target
                        || this.dispatchedClickTarget.removed
                        || this.dispatchedClickTarget.deathTicks != 0
                        || mc.world.getEntity(this.dispatchedClickTarget.getNetworkId()) != this.dispatchedClickTarget
                        || RotationUtils.distanceFromEyeToClosestOnAABB(this.dispatchedClickTarget)
                                > this.attackRange.getInput()
                        || e.objectMouseOver == null
                        || e.objectMouseOver.type != Type.ENTITY
                        || e.objectMouseOver.entity != this.dispatchedClickTarget)) {
            this.dispatchedClickTarget = null;
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onAttackDispatch(DispatchPacketEvent e) {
        if (e.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            PlayerInteractEntityC2SPacket packet = (PlayerInteractEntityC2SPacket) e.getPacket();
            if (packet.getAction() == Action.ATTACK) {
                long dispatchedAt = System.nanoTime();
                Runnable recordAttack = () -> {
                    if (this.isEnabled()
                            && Utils.nullCheck()
                            && target != null
                            && this.targetFirstAttackTime == null
                            && dispatchedAt - this.targetSelectedTime >= 0L
                            && e.getNetworkManager() == mc.player.networkHandler.getConnection()
                            && packet.getInteractTarget(mc.world) == target) {
                        this.targetFirstAttackTime = dispatchedAt;
                        this.hitMap.put(target.getNetworkId(), dispatchedAt);
                    }
                };
                if (mc.isOnSameThread()) {
                    recordAttack.run();
                } else {
                    mc.executeTask(recordAttack);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSetAttackTarget(ForgeEvents.LivingSetAttackTargetEvent e) {
        if (e.entity != null && !this.hostileMobs.contains(e.entity)) {
            if (!(e.target instanceof PlayerEntity) || !e.target.getName().equals(mc.player.getName())) {
                return;
            }

            if (Utils.getBedwarsStatus() == 2 && e.entity instanceof ZombiePigmanEntity) {
                return;
            }

            this.hostileMobs.add(e.entity);
        }

        if (e.target == null && this.hostileMobs.contains(e.entity)) {
            this.hostileMobs.remove(e.entity);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.hitMap.clear();
            this.hostileMobs.clear();
            this.golems.clear();
            this.setTarget(null);
        }
    }

    private void setTarget(Entity entity) {
        LivingEntity nextTarget = entity instanceof LivingEntity ? (LivingEntity) entity : null;
        if (target != nextTarget) {
            this.targetFirstAttackTime = null;
            this.targetSelectedTime = System.nanoTime();
            this.dispatchedClickTarget = null;
            this.pendingClickTarget = null;
            if (ModuleManager.displace != null) {
                ModuleManager.displace.cancelKillAuraAttack();
            }
        }

        target = nextTarget;
        if (target == null) {
            attackingEntity = null;
            this.targetDistance = Double.MAX_VALUE;
            this.resetClickState();
        }
    }

    private void resetClickState() {
        if (ModuleManager.displace != null) {
            ModuleManager.displace.cancelKillAuraAttack();
        }

        this.nextClickTime = 0L;
        this.clickScheduled = false;
        this.pendingClickTarget = null;
        this.dispatchedClickTarget = null;
    }

    private boolean bedAuraHasPriority() {
        return ModuleManager.bedAura != null
                && ModuleManager.bedAura.shouldOverrideMouseOver()
                && !ModuleManager.bedAura.isPrioritizingKillAura();
    }

    public boolean hasPriorityTarget() {
        if (this.isEnabled()
                && this.basicCondition()
                && this.settingCondition()
                && target != null
                && target.deathTicks == 0
                && mc.world.getEntity(target.getNetworkId()) == target) {
            double maxRange = Math.max(this.attackRange.getInput(), this.aimRange.getInput());
            KillAura.Candidate candidate = this.getCandidateTarget(target, maxRange, (float) this.fov.getInput());
            return candidate != null
                    && this.buildKillAuraTarget(candidate.entity, candidate.distance, maxRange) != null;
        } else {
            return false;
        }
    }

    public LivingEntity getClosestTarget(double maxRange) {
        if (!Utils.nullCheck()) {
            return null;
        }

        KillAura.KillAuraTarget closest = null;
        KillAura.KillAuraTarget closestEnemy = null;

        for (Entity entity : mc.world.entities) {
            KillAura.Candidate candidate = this.getCandidateTarget(entity, maxRange, (float) this.fov.getInput());
            if (candidate != null) {
                KillAura.KillAuraTarget auraTarget =
                        this.buildKillAuraTarget(candidate.entity, candidate.distance, maxRange);
                if (auraTarget != null) {
                    if (closest == null || auraTarget.distance < closest.distance) {
                        closest = auraTarget;
                    }

                    if (auraTarget.isEnemy && (closestEnemy == null || auraTarget.distance < closestEnemy.distance)) {
                        closestEnemy = auraTarget;
                    }
                }
            }
        }

        if (this.prioritizeEnemies.isToggled() && closestEnemy != null) {
            return closestEnemy.entity;
        } else {
            return closest != null ? closest.entity : null;
        }
    }

    private void handleTarget() {
        double maxRange = Math.max(this.attackRange.getInput(), this.aimRange.getInput());
        float fovValue = (float) this.fov.getInput();
        List<KillAura.KillAuraTarget> candidates = new ArrayList<>();

        for (Entity entity : mc.world.entities) {
            KillAura.Candidate candidate = this.getCandidateTarget(entity, maxRange, fovValue);
            if (candidate != null) {
                KillAura.KillAuraTarget auraTarget =
                        this.buildKillAuraTarget(candidate.entity, candidate.distance, maxRange);
                if (auraTarget != null) {
                    candidates.add(auraTarget);
                }
            }
        }

        if (this.prioritizeEnemies.isToggled()) {
            List<KillAura.KillAuraTarget> enemies = new ArrayList<>();

            for (KillAura.KillAuraTarget candidate : candidates) {
                if (candidate.isEnemy) {
                    enemies.add(candidate);
                }
            }

            if (!enemies.isEmpty()) {
                candidates = enemies;
            }
        }

        candidates.sort(this.getTargetComparator().thenComparingDouble(c -> c.distance));
        double attackRangeValue = this.attackRange.getInput();
        List<KillAura.KillAuraTarget> attackTargets = new ArrayList<>();

        for (KillAura.KillAuraTarget candidate : candidates) {
            if (candidate.distance <= attackRangeValue) {
                attackTargets.add(candidate);
            }
        }

        if (!attackTargets.isEmpty()) {
            this.setTarget(this.selectAttackTarget(attackTargets).entity);
        } else if (!candidates.isEmpty()) {
            this.setTarget(candidates.get(0).entity);
        } else {
            this.setTarget(null);
        }
    }

    private KillAura.Candidate getCandidateTarget(Entity entity, double maxRange, float fovValue) {
        if (entity instanceof LivingEntity && entity != mc.player && !entity.removed) {
            boolean targetableUnicorn = entity instanceof HorseBaseEntity && BedWars.shouldTargetUnicorn();
            if (entity instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity) entity;
                if (Utils.isFriended(player) || player.deathTicks != 0) {
                    return null;
                }

                if (AntiBot.isBot(entity) || this.ignoreTeammates.isToggled() && Utils.isTeammate(entity)) {
                    return null;
                }
            } else if (targetableUnicorn) {
                if (((HorseBaseEntity) entity).deathTicks != 0) {
                    return null;
                }
            } else if (this.attackMobs.isToggled() && isZombiesTarget(entity)) {
                if (((LivingEntity) entity).deathTicks != 0) {
                    return null;
                }
            } else {
                if (!(entity instanceof PathFinderMobEntity) || !this.attackMobs.isToggled()) {
                    return null;
                }

                PathFinderMobEntity creature = (PathFinderMobEntity) entity;
                if (creature.goalSelector == null || creature.isNoAi() || creature.deathTicks != 0) {
                    return null;
                }

                // Was a "net.minecraft.entity.monster." package check, which never matched the intermediary class
                // names the game runs with. EntityMob + EntityGolem cover that package's PathFinderMobs.
                if (!(entity instanceof MonsterEntity) && !(entity instanceof GolemEntity)) {
                    return null;
                }
            }

            if (entity.isInvisible() && !this.targetInvis.isToggled()) {
                return null;
            }

            if (fovValue != 360.0F && !RotationUtils.isEyeInsideEntityAABB(entity) && !Utils.inFov(fovValue, entity)) {
                return null;
            }

            double distance = RotationUtils.distanceFromEyeToClosestOnAABB(entity);
            return distance > maxRange ? null : new KillAura.Candidate((LivingEntity) entity, distance);
        } else {
            return null;
        }
    }

    private KillAura.KillAuraTarget buildKillAuraTarget(
            LivingEntity entity, double distanceToBoundingBox, double maxRange) {
        boolean targetableUnicorn = entity instanceof HorseBaseEntity && BedWars.shouldTargetUnicorn();
        if (entity instanceof PathFinderMobEntity
                && !targetableUnicorn
                && this.attackMobs.isToggled()
                && !isZombiesTarget(entity)
                && !this.isHostile((PathFinderMobEntity) entity)) {
            return null;
        }

        double multipointH = 100.0;
        double multipointV = 100.0;
        if (!this.usePackets.isToggled()
                && !RotationUtils.hasValidAimPoint(
                        entity,
                        multipointH,
                        multipointV,
                        maxRange,
                        this.aimThroughBlocks.isToggled(),
                        this.aimThroughEntities.isToggled(),
                        true)) {
            return null;
        }

        boolean isEnemyPlayer = entity instanceof PlayerEntity && Utils.isEnemy((PlayerEntity) entity);
        return new KillAura.KillAuraTarget(
                entity,
                distanceToBoundingBox,
                entity.getHealth(),
                entity.damagedTimer,
                RotationUtils.distanceFromYaw(entity, false),
                entity.getNetworkId(),
                isEnemyPlayer);
    }

    private Comparator<KillAura.KillAuraTarget> getTargetComparator() {
        switch ((int) this.sortMode.getInput()) {
            case 0:
            default:
                return Comparator.comparingDouble(target -> target.distance);
            case 1:
                return Comparator.comparingDouble(target -> target.health);
            case 2:
                return Comparator.comparingInt(target -> target.hurttime);
            case 3:
                return Comparator.comparingDouble(target -> target.yawDelta);
        }
    }

    private KillAura.KillAuraTarget selectAttackTarget(List<KillAura.KillAuraTarget> attackTargets) {
        long now = System.nanoTime();
        long switchDelayNanos = TimeUnit.MILLISECONDS.toNanos((long) this.switchDelay.getInput());
        int targetCount = Math.min(attackTargets.size(), (int) this.targets.getInput());

        for (KillAura.KillAuraTarget candidate : attackTargets) {
            if (candidate.entity == target
                    && (this.targetFirstAttackTime == null || now - this.targetFirstAttackTime < switchDelayNanos)) {
                return candidate;
            }
        }

        KillAura.KillAuraTarget leastRecentlyAttacked = attackTargets.get(0);
        long longestSinceAttack = Long.MIN_VALUE;

        for (KillAura.KillAuraTarget candidate : attackTargets) {
            Long firstAttackTime = this.hitMap.get(candidate.entityId);
            if (firstAttackTime == null || now - firstAttackTime >= switchDelayNanos * targetCount) {
                return candidate;
            }

            long sinceAttack = now - firstAttackTime;
            if (sinceAttack > longestSinceAttack) {
                longestSinceAttack = sinceAttack;
                leastRecentlyAttacked = candidate;
            }
        }

        return leastRecentlyAttacked;
    }

    /**
     * In a Zombies game every hostile mob is a target, like Zombies Aimbot (which also aims at these). Outside Zombies
     * Raven's rules apply: only mobs that target you, which client-side on a server comes down to silverfish and iron
     * golems (mob AI only runs on the server).
     */
    private static boolean isZombiesTarget(Entity entity) {
        if (!ZombiesGameHelper.isInZombies()) {
            return false;
        }

        if (entity instanceof WitherEntity) {
            // Skips the invisible wither Hypixel uses to draw boss bars.
            return ZombiesMobs.isWitherBoss(entity);
        }

        return entity instanceof MonsterEntity
                || entity instanceof GolemEntity
                || entity instanceof SlimeEntity
                || entity instanceof GhastEntity
                || entity instanceof WolfEntity;
    }

    private boolean isHostile(PathFinderMobEntity entityCreature) {
        if (SkyWars.onlyAuraHostiles()) {
            return entityCreature instanceof GiantEntity
                    ? false
                    : !ModuleManager.skyWars.spawnedMobs.contains(entityCreature.getNetworkId());
        }

        if (entityCreature instanceof SilverfishEntity) {
            String teamColor = Utils.getFirstColorCode(entityCreature.getCustomName());
            String teamColorSelf =
                    Utils.getFirstColorCode(mc.player.getDisplayName().getFormattedString());
            return teamColor.isEmpty() || !teamColorSelf.equals(teamColor) && !Utils.isTeammate(entityCreature);
        }

        if (!(entityCreature instanceof IronGolemEntity)) {
            return entityCreature instanceof ZombiePigmanEntity && Utils.getBedwarsStatus() != 2
                    ? false
                    : this.hostileMobs.contains(entityCreature);
        }

        if (Utils.getBedwarsStatus() != 2) {
            return true;
        }

        if (this.golems.containsKey(entityCreature.getNetworkId())) {
            return !this.golems.getOrDefault(entityCreature.getNetworkId(), false);
        }

        double nearestDistance = -1.0;
        ArmorStandEntity nearestArmorStand = null;

        for (Entity entity : mc.world.entities) {
            if (entity instanceof ArmorStandEntity) {
                String stripped = Utils.stripString(entity.getDisplayName().getFormattedString());
                if (stripped.contains("[") && stripped.endsWith("]")) {
                    double distanceSq = entity.squaredDistanceTo(entityCreature.x, entityCreature.y, entityCreature.z);
                    if (distanceSq < nearestDistance || nearestDistance == -1.0) {
                        nearestDistance = distanceSq;
                        nearestArmorStand = (ArmorStandEntity) entity;
                    }
                }
            }
        }

        if (nearestArmorStand == null) {
            return !ModuleManager.bedwars.spawnedMobs.contains(entityCreature.getNetworkId());
        }

        String teamColor =
                Utils.getFirstColorCode(nearestArmorStand.getDisplayName().getFormattedString());
        String teamColorSelf =
                Utils.getFirstColorCode(mc.player.getDisplayName().getFormattedString());
        boolean isTeam =
                !teamColor.isEmpty() && (teamColorSelf.equals(teamColor) || Utils.isTeammate(nearestArmorStand));
        this.golems.put(entityCreature.getNetworkId(), isTeam);
        return !isTeam;
    }

    private boolean basicCondition() {
        return !Utils.nullCheck() ? false : !mc.player.removed;
    }

    private boolean settingCondition() {
        if (this.requireMouseDown.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        } else if (this.weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        } else {
            return this.disableWhileMining.isToggled() && Utils.isMining()
                    ? false
                    : !this.disableInInventory.isToggled() || mc.screen == null;
        }
    }

    private boolean attackCondition() {
        return (!this.disableWhileUsing.isToggled() || !mc.player.hasItemInUse())
                && (!this.disableWhileBlocking.isToggled() || !mc.player.isSwordBlocking());
    }

    public boolean shouldSwingSilentlyWhileBlocking() {
        if (this.isEnabled() && this.silentSwingWhileBlocking.isToggled() && target != null && Utils.nullCheck()) {
            Autoblock autoblock = (Autoblock) ModuleManager.getModule(Autoblock.class);
            return mc.player.isSwordBlocking() || autoblock != null && autoblock.isBlockingForSwing();
        } else {
            return false;
        }
    }

    private long nextDelay() {
        double cps = Math.max(1.0, this.targetCPS.getInput());
        double delay = Math.max(33.0, 1000.0 / cps + this.rand.nextInt(21) - 10.0);
        return Math.round(delay * TimeUnit.MILLISECONDS.toNanos(1L));
    }

    public SliderSetting getAttackRangeSetting() {
        return this.attackRange;
    }

    public SliderSetting getSwingRangeSetting() {
        return this.swingRange;
    }

    public SliderSetting getAimRangeSetting() {
        return this.aimRange;
    }

    public boolean canDisplaceAttack(Entity entity) {
        return entity != null
                && this.isEnabled()
                && this.basicCondition()
                && this.settingCondition()
                && this.attackCondition()
                && entity == target
                && !entity.removed
                && target.deathTicks == 0
                && mc.world.getEntity(entity.getNetworkId()) == entity
                && mc.focused
                && mc.screen == null
                && !this.bedAuraHasPriority()
                && RotationUtils.distanceFromEyeToClosestOnAABB(entity) <= this.attackRange.getInput()
                && (this.usePackets.isToggled()
                        || RotationUtils.hasValidAimPoint(
                                entity,
                                100.0,
                                100.0,
                                this.attackRange.getInput(),
                                this.aimThroughBlocks.isToggled(),
                                this.aimThroughEntities.isToggled(),
                                true));
    }

    public float[] getDisplaceRotations(float baseYaw, float basePitch) {
        return this.usePackets.isToggled()
                ? RotationUtils.getRotations(target, 100.0, 100.0, baseYaw, basePitch, 0.0F)
                : RotationUtils.getRotationsWithBackup(
                        target,
                        100.0,
                        100.0,
                        baseYaw,
                        basePitch,
                        this.attackRange.getInput(),
                        this.aimThroughBlocks.isToggled(),
                        this.aimThroughEntities.isToggled(),
                        true,
                        0.0F);
    }

    public boolean shouldOverrideMouseOver() {
        return this.isEnabled()
                && !this.usePackets.isToggled()
                && Utils.nullCheck()
                && attackingEntity != null
                && target == attackingEntity
                && this.basicCondition()
                && this.settingCondition()
                && !this.bedAuraHasPriority()
                && this.targetDistance <= this.swingRange.getInput();
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (this.shouldOverrideMouseOver()) {
            Entity viewEntity = mc.getCamera();
            if (viewEntity != null) {
                Vec3d eyes = viewEntity.getEyePosition(partialTicks);
                Vec3d look = viewEntity.getRotationVector(partialTicks);
                double reach = this.attackRange.getInput();
                Vec3d rayEnd = eyes.add(look.x * reach, look.y * reach, look.z * reach);
                float border = attackingEntity.getPickRadius();
                Box bb = attackingEntity.getShape().grown(border, border, border);
                HitResult intercept = bb.clip(eyes, rayEnd);
                boolean inside = bb.contains(eyes);
                if (inside || intercept != null) {
                    Vec3d hitVec = inside ? eyes : intercept.facePos;
                    if (!inside && !this.aimThroughBlocks.isToggled()) {
                        HitResult blockHit =
                                RotationUtils.rayTraceBlocksIgnoringOpenFenceGates(eyes, hitVec, false, false, true);
                        if (blockHit != null && blockHit.type == Type.BLOCK) {
                            return;
                        }
                    }

                    if (inside
                            || this.aimThroughEntities.isToggled()
                            || !RotationUtils.isPathBlockedByEntity(eyes, hitVec, attackingEntity)) {
                        mc.crosshairTarget = new HitResult(attackingEntity, hitVec);
                        mc.targetEntity = attackingEntity;
                        GameRenderer renderer = mc.gameRenderer;
                        if (renderer instanceof IAccessorEntityRenderer) {
                            ((IAccessorEntityRenderer) renderer).setPointedEntity(attackingEntity);
                        }
                    }
                }
            }
        }
    }

    private static final class Candidate {
        final LivingEntity entity;
        final double distance;

        Candidate(LivingEntity entity, double distance) {
            this.entity = entity;
            this.distance = distance;
        }
    }

    static class KillAuraTarget {
        final LivingEntity entity;
        final double distance;
        final float health;
        final int hurttime;
        final double yawDelta;
        final int entityId;
        final boolean isEnemy;

        public KillAuraTarget(
                LivingEntity entity,
                double distance,
                float health,
                int hurttime,
                double yawDelta,
                int entityId,
                boolean isEnemy) {
            this.entity = entity;
            this.distance = distance;
            this.health = health;
            this.hurttime = hurttime;
            this.yawDelta = yawDelta;
            this.entityId = entityId;
            this.isEnemy = isEnemy;
        }
    }
}

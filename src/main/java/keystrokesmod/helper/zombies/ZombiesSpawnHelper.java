package keystrokesmod.helper.zombies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import keystrokesmod.platform.ornithe.ForgeEvents.EntityJoinWorldEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.zombies.MobType;
import keystrokesmod.utility.zombies.ZombiesMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.GolemEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;

/**
 * Tags Zombies mobs by spawn order: the first mobs of a powerup round carry the powerup, and the
 * most recently spawned mob is the "bad headshot" one. See {@link MobType}.
 */
public final class ZombiesSpawnHelper implements IMinecraftInstance {
    /** Alien Arcadium keeps a pet wolf here that never carries a powerup. */
    private static final double AA_WOLF_X = -16.5;

    private static final double AA_WOLF_Y = 72.0;
    private static final double AA_WOLF_Z = -0.5;
    private static final double AA_WOLF_RADIUS_SQ = 36.0;

    private static boolean collecting = true;
    private static Set<LivingEntity> powerupEnsured = new HashSet<>();
    private static Set<LivingEntity> powerupPredicted = new HashSet<>();
    /** Tracked mobs in spawn order; the last one is the bad-headshot mob. */
    private static List<LivingEntity> spawnOrder = new ArrayList<>();

    private static Set<LivingEntity> allMobs = new HashSet<>();

    ZombiesSpawnHelper() {}

    public static MobType getMobType(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return MobType.NONE;
        }
        if (powerupEnsured.contains(entity)) {
            return MobType.POWERUP_ENSURED;
        }
        if (powerupPredicted.contains(entity)) {
            return MobType.POWERUP_PREDICT;
        }
        return !spawnOrder.isEmpty() && spawnOrder.get(spawnOrder.size() - 1).equals(entity)
                ? MobType.BAD_HEADSHOT
                : MobType.NONE;
    }

    static void onNewRound() {
        powerupEnsured.clear();
        powerupPredicted.clear();
        spawnOrder.clear();
        allMobs.clear();
        collecting = true;
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        // In singleplayer the integrated server fires this on its own thread; only track client-side mobs.
        if (!event.world.isClient || !(event.entity instanceof LivingEntity)) {
            return;
        }

        LivingEntity mob = (LivingEntity) event.entity;
        if (mob.equals(mc.player)) {
            powerupEnsured = new HashSet<>();
            powerupPredicted = new HashSet<>();
            spawnOrder = new ArrayList<>();
            allMobs = new HashSet<>();
            collecting = true;
            return;
        }
        if (!isTrackedMob(mob)) {
            return;
        }

        if (collecting && canCarryPowerup(mob)) {
            assignPowerup(mob);
        }
        if (!spawnOrder.contains(mob)) {
            spawnOrder.add(mob);
        }
        allMobs.add(mob);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START || mc.world == null || mc.player == null) {
            return;
        }

        List<LivingEntity> tracked = new ArrayList<>(allMobs);
        tracked.addAll(spawnOrder);
        for (LivingEntity mob : tracked) {
            if (mob.removed || mob.getHealth() == 0.0F) {
                allMobs.remove(mob);
                spawnOrder.remove(mob);
                powerupEnsured.remove(mob);
                powerupPredicted.remove(mob);
            }
        }
    }

    /**
     * The first {@code incoming} mobs of the round are sure to carry a powerup; the next one or two
     * might (two when no pattern is known yet, past round 1).
     */
    private static void assignPowerup(LivingEntity mob) {
        int round = ZombiesGameHelper.getCurrentRound();
        int predict = ZombiesPowerupHelper.hasNoPatterns() && round != 1 ? 2 : 1;
        int incoming = ZombiesPowerupHelper.getIncomingPowerups();
        boolean seen = powerupEnsured.contains(mob) || powerupPredicted.contains(mob);

        if (!seen && powerupEnsured.size() == incoming && powerupPredicted.size() < predict) {
            powerupPredicted.add(mob);
        } else if (!seen && powerupEnsured.size() < incoming) {
            powerupEnsured.add(mob);
        }

        if (powerupEnsured.size() == incoming && powerupPredicted.size() == predict) {
            collecting = false;
        }
    }

    private static boolean canCarryPowerup(LivingEntity mob) {
        if (isPowerupDetectionBlocked()
                || mob instanceof SquidEntity
                || mob instanceof ChickenEntity
                || mob instanceof MooshroomEntity) {
            return false;
        }
        return !(mob instanceof WolfEntity
                && ZombiesMap.current() == ZombiesMap.ALIEN_ARCADIUM
                && mob.squaredDistanceTo(AA_WOLF_X, AA_WOLF_Y, AA_WOLF_Z) <= AA_WOLF_RADIUS_SQ);
    }

    private static boolean isTrackedMob(LivingEntity entity) {
        return entity instanceof ZombieEntity
                || entity instanceof SlimeEntity
                || entity instanceof WolfEntity
                || entity instanceof WitchEntity
                || entity instanceof EndermiteEntity
                || entity instanceof CreeperEntity
                || entity instanceof BlazeEntity
                || entity instanceof SkeletonEntity
                || entity instanceof GhastEntity
                || entity instanceof GolemEntity
                || entity instanceof SquidEntity
                || entity instanceof SilverfishEntity
                || entity instanceof GiantEntity
                || entity instanceof CaveSpiderEntity
                || entity instanceof MooshroomEntity
                || entity instanceof OcelotEntity
                || entity instanceof GuardianEntity && entity.getMaxHealth() > 30.0F
                || entity instanceof ChickenEntity && !entity.isInvisible();
    }

    /** Boss rounds don't drop powerups from regular mobs. */
    private static boolean isPowerupDetectionBlocked() {
        int round = ZombiesGameHelper.getCurrentRound();
        if (round <= 0) {
            return false;
        }

        switch (ZombiesMap.current()) {
            case DEAD_END:
                return round % 5 == 0 && round <= 30;
            case BAD_BLOOD:
                return round % 5 == 0 && round >= 10 && round <= 30;
            case PRISON:
                return round == 10 || round == 20 || round == 30;
            case ALIEN_ARCADIUM:
                return round == 25 || round == 35 || round == 56 || round == 57 || round == 101 || round == 105;
            default:
                return false;
        }
    }
}

package keystrokesmod.module.impl.other;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class HitParticles extends Module {
    private static final int PARTICLE_CRIT = 4;
    private static final int PARTICLE_CRIT_MAGIC = 13;
    private static final double ARROW_SCAN_EXPAND = 96.0;
    private static final int[] ARGS_NONE = new int[0];
    private static final Random RANDOM = new Random();
    private final ButtonSetting onMelee;
    private final ButtonSetting onRanged;
    private final SliderSetting meleeParticle;
    private final SliderSetting rangedParticle;
    private final SliderSetting meleeMultiplier;
    private final SliderSetting rangedMultiplier;
    private final Map<Integer, Integer> rangedSpawnForArrow = new HashMap<>();
    private static final String[] PARTICLE_NAMES = new String[] {
        "Angry Villager",
        "Blood",
        "Cloud",
        "Confetti",
        "Critical",
        "Crit/Magic Crit",
        "Enchantment",
        "Explosion",
        "Flame",
        "Happy Villager",
        "Heart",
        "Instant Spell",
        "Lava",
        "Magic Critical",
        "Mob Spell",
        "Music Note",
        "Portal",
        "Slime",
        "Smoke",
        "Snow",
        "Spark",
        "Spell",
        "Splash",
        "Witch"
    };
    private static final int[] PARTICLE_IDS =
            new int[] {20, 37, 32, 30, 9, -1, 25, 1, 26, 21, 34, 14, 27, 10, 15, 23, 24, 33, 11, 31, 3, 13, 5, 17};
    private static final int[] PARTICLE_COUNTS =
            new int[] {8, 32, 16, 16, 32, -1, 16, 1, 16, 16, 8, 16, 16, 32, 16, 8, 32, 16, 16, 16, 16, 16, 32, 16};
    private static final float[] PARTICLE_OFFSETS = new float[] {
        2.5F, 1.2F, 0.2F, 3.0F, 1.0F, -1.0F, 1.0F, 1.0F, 0.05F, 2.5F, 2.0F, 0.7F, 1.0F, 1.0F, 1.0F, 3.0F, 1.0F, 0.5F,
        0.08F, 1.0F, 0.1F, 1.0F, 1.0F, 0.5F
    };
    private static final boolean[] PARTICLE_IGNORE_DIST = new boolean[] {
        true, true, false, true, false, false, true, true, false, true, true, false, false, false, false, true, true,
        false, true, true, false, false, false, false
    };
    private static final int[][] PARTICLE_ARGS = new int[][] {
        ARGS_NONE,
        {Block.getId(Blocks.NETHERRACK)},
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE,
        ARGS_NONE
    };

    public HitParticles() {
        super("Hit Particles", Module.category.other);
        this.registerSetting(new DescriptionSetting("Melee"));
        this.registerSetting(this.onMelee = new ButtonSetting("Melee hits", true));
        this.registerSetting(this.meleeParticle = new SliderSetting("Melee particle", 4, PARTICLE_NAMES));
        this.registerSetting(this.meleeMultiplier = new SliderSetting("Melee multiplier", 1.0, 1.0, 8.0, 1.0));
        this.registerSetting(new DescriptionSetting("Ranged"));
        this.registerSetting(this.onRanged = new ButtonSetting("Arrow hits", true));
        this.registerSetting(this.rangedParticle = new SliderSetting("Ranged particle", 4, PARTICLE_NAMES));
        this.registerSetting(this.rangedMultiplier = new SliderSetting("Ranged multiplier", 1.0, 1.0, 8.0, 1.0));
    }

    @Override
    public void onDisable() {
        this.rangedSpawnForArrow.clear();
    }

    @ForgeEvents.SubscribeEvent
    public void onAttackEntity(ForgeEvents.AttackEntityEvent event) {
        if (this.isEnabled() && this.onMelee.isToggled() && Utils.nullCheck()) {
            if (event.entityPlayer == mc.player && event.target instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) event.target;
                if (target.damagedTimer <= 5 && target.invulnerableTimer <= 0) {
                    int idx = (int) this.meleeParticle.getInput();
                    this.spawnParticleType(idx, target, (int) this.meleeMultiplier.getInput());
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (this.isEnabled()
                && this.onRanged.isToggled()
                && event.phase == ForgeEvents.TickEvent.Phase.END
                && Utils.nullCheck()) {
            this.pruneArrowDedupeMap();
            Box scan = mc.player.getShape().grown(96.0, 96.0, 96.0);
            List<ArrowEntity> arrows = mc.world.getEntitiesOfType(ArrowEntity.class, scan);
            int i = 0;

            for (int n = arrows.size(); i < n; i++) {
                ArrowEntity arrow = arrows.get(i);
                if (arrow.shooter == mc.player) {
                    LivingEntity target = getCollisionEntity(arrow);
                    if (target != null && target.damagedTimer <= 0) {
                        int aid = arrow.getNetworkId();
                        int tid = target.getNetworkId();
                        Integer prev = this.rangedSpawnForArrow.get(aid);
                        if (prev == null || prev != tid) {
                            this.rangedSpawnForArrow.put(aid, tid);
                            int idx = (int) this.rangedParticle.getInput();
                            this.spawnParticleType(idx, target, (int) this.rangedMultiplier.getInput());
                        }
                    }
                }
            }
        }
    }

    private void pruneArrowDedupeMap() {
        Iterator<Entry<Integer, Integer>> it =
                this.rangedSpawnForArrow.entrySet().iterator();

        while (it.hasNext()) {
            Entry<Integer, Integer> entry = it.next();
            if (mc.world.getEntity(entry.getKey()) == null) {
                it.remove();
            }
        }
    }

    private void spawnParticleType(int index, Entity entity, int multiplier) {
        if (index >= 0 && index < PARTICLE_IDS.length) {
            int id = PARTICLE_IDS[index];
            if (id == -1) {
                spawnParticleRaw(
                        9,
                        entity,
                        multiplier,
                        PARTICLE_COUNTS[4],
                        PARTICLE_OFFSETS[4],
                        PARTICLE_IGNORE_DIST[4],
                        PARTICLE_ARGS[4]);
                spawnParticleRaw(
                        10,
                        entity,
                        multiplier,
                        PARTICLE_COUNTS[13],
                        PARTICLE_OFFSETS[13],
                        PARTICLE_IGNORE_DIST[13],
                        PARTICLE_ARGS[13]);
            } else {
                spawnParticleRaw(
                        id,
                        entity,
                        multiplier,
                        PARTICLE_COUNTS[index],
                        PARTICLE_OFFSETS[index],
                        PARTICLE_IGNORE_DIST[index],
                        PARTICLE_ARGS[index]);
            }
        }
    }

    private static void spawnParticleRaw(
            int id, Entity entity, int multiplier, int count, float offset, boolean ignoreDistance, int[] args) {
        for (int i = 0; i < count * multiplier; i++) {
            double xOffset = RANDOM.nextFloat() * (offset * 2.0F) - offset;
            double yOffset = RANDOM.nextFloat() * (offset * 2.0F) - offset;
            double zOffset = RANDOM.nextFloat() * (offset * 2.0F) - offset;
            if (ignoreDistance || xOffset * xOffset + yOffset * yOffset + zOffset * zOffset <= 1.0) {
                double x = entity.x + xOffset * entity.width / 4.0;
                double y = entity.getShape().minY + entity.height / 2.0F + yOffset * entity.height / 4.0;
                double z = entity.z + zOffset * entity.width / 4.0;
                mc.particleManager.addParticle(id, x, y, z, xOffset, yOffset, zOffset, args);
            }
        }
    }

    private static LivingEntity getCollisionEntity(ArrowEntity arrow) {
        World world = arrow.world;
        Vec3d pos = new Vec3d(arrow.x, arrow.y, arrow.z);
        Vec3d motionEnd = new Vec3d(arrow.x + arrow.velocityX, arrow.y + arrow.velocityY, arrow.z + arrow.velocityZ);
        HitResult rayTrace = world.rayTrace(pos, motionEnd, false, true, false);
        Vec3d traceEnd = motionEnd;
        if (rayTrace != null) {
            traceEnd = rayTrace.facePos;
        }

        LivingEntity target = null;
        double closestSq = 0.0;
        Box search = arrow.getShape()
                .expanded(arrow.velocityX, arrow.velocityY, arrow.velocityZ)
                .grown(1.0, 1.0, 1.0);

        for (Entity entity : world.getEntities(arrow, search)) {
            if (entity instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) entity;
                if (living.hasCollision() && living != arrow.shooter) {
                    Box collisionBox = entity.getShape().grown(0.3, 0.3, 0.3);
                    HitResult collision = collisionBox.clip(pos, traceEnd);
                    if (collision != null) {
                        double distSq = pos.squaredDistanceTo(collision.facePos);
                        if (!(distSq >= closestSq) || closestSq == 0.0) {
                            target = living;
                            closestSq = distSq;
                        }
                    }
                }
            }
        }

        return target;
    }
}

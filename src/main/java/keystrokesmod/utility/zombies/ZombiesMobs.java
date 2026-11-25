package keystrokesmod.utility.zombies;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import keystrokesmod.helper.zombies.ZombiesSpawnHelper;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.BossBar;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Classifies mobs in Hypixel Zombies: bosses, geared zombies, O1 babies and the
 * powerup / bad-headshot mobs detected by {@link ZombiesSpawnHelper}.
 */
public final class ZombiesMobs {
    public static final Color COLOR_SPECIAL = Color.RED;
    public static final Color COLOR_POWERUP_ENSURED = new Color(128, 0, 0);
    public static final Color COLOR_POWERUP_PREDICT = new Color(255, 92, 92);
    public static final Color COLOR_BAD_HEADSHOT = new Color(0, 255, 0);
    /** Mobs the Zombies Aimbot can only shoot through the bad headshot mob (no headshot counts). */
    public static final Color COLOR_BAD_HEADSHOT_LINE = Color.YELLOW;

    private static Set<Integer> badHsLine = Collections.emptySet();

    /** Set by the Zombies Aimbot on each scan; {@code null} clears it. */
    public static void setBadHsLine(Set<Integer> ids) {
        badHsLine = ids == null ? Collections.<Integer>emptySet() : ids;
    }

    public static final Color COLOR_INVISIBLE = new Color(0, 0, 170);
    public static final Color COLOR_NORMAL = Color.WHITE;

    private static final float ADULT_ZOMBIE_WIDTH = 0.6F;

    /** Skull item damage value of a player head. */
    private static final int PLAYER_HEAD_META = 3;

    /** Boss name-tag armor stand id -> boss entity id. */
    private static final Map<Integer, Integer> BOSS_MAP = new HashMap<>();

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == Phase.END && mc.world != null) {
            fixBabyZombieSizes(mc.world.entities);
            updateBossMap(mc.world.entities);
        } else if (mc.world == null) {
            BOSS_MAP.clear();
        }
    }

    private static void updateBossMap(List<Entity> entities) {
        BOSS_MAP.clear();
        List<ArmorStandEntity> armorStands = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity instanceof ArmorStandEntity) {
                armorStands.add((ArmorStandEntity) entity);
            }
        }

        for (ArmorStandEntity stand : armorStands) {
            if (!stand.hasCustomName()) {
                continue;
            }

            String name = stand.getCustomName();
            if (!name.contains("§8[") && !name.contains("§8]")) {
                continue;
            }

            LivingEntity closest = null;
            double closestDistSq = Double.MAX_VALUE;
            for (Entity other : entities) {
                if (other instanceof LivingEntity && !(other instanceof ArmorStandEntity)) {
                    double dx = stand.x - other.x;
                    double dz = stand.z - other.z;
                    double dy = stand.y - other.y;
                    if (dy > 0.0 && dy < 3.0) {
                        double distSq = dx * dx + dz * dz;
                        if (distSq < 2.0 && distSq < closestDistSq) {
                            closest = (LivingEntity) other;
                            closestDistSq = distSq;
                        }
                    }
                }
            }

            if (closest != null) {
                BOSS_MAP.put(stand.getNetworkId(), closest.getNetworkId());
            }
        }
    }

    public static Map<Integer, Integer> getBossMap() {
        return BOSS_MAP;
    }

    public static boolean isBoss(Entity entity) {
        return BOSS_MAP.containsValue(entity.getNetworkId());
    }

    /**
     * Bosses (nametagged, wither, Herobrine), geared zombies and O1s: the mobs drawn in red and
     * targeted first by Aimbot's "Bosses first".
     */
    public static boolean isSpecialRedEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        // Withers and Herobrine are checked separately: the boss map misses them.
        return isWitherBoss(entity) || isHerobrine(entity) || isO1(entity) || isBoss(entity) || isGearedZombie(entity);
    }

    /** The boss bar currently on screen mentions {@code name} (case-insensitive, colours stripped). */
    public static boolean isBossBarShowing(String name) {
        return BossBar.timer > 0
                && BossBar.name != null
                && Utils.stripColor(BossBar.name).toLowerCase(Locale.ROOT).contains(name);
    }

    /**
     * Herobrine: a visible zombie wearing a player head while its boss bar shows. Sentinels also
     * wear player heads but are invisible.
     */
    public static boolean isHerobrine(Entity entity) {
        if (!(entity instanceof ZombieEntity) || entity.isInvisible() || !isBossBarShowing("herobrine")) {
            return false;
        }
        ItemStack helmet = ((ZombieEntity) entity).getEquipment(4);
        return helmet != null && helmet.getItem() == Items.SKULL && helmet.getMetadata() == PLAYER_HEAD_META;
    }

    /**
     * The Zombies wither boss. Hypixel draws 1.8 boss bars (e.g. Herobrine's) with a hidden, invisible
     * wither; that one is not a mob and is excluded.
     */
    public static boolean isWitherBoss(Entity entity) {
        return entity instanceof WitherEntity && !entity.isInvisible();
    }

    public static boolean isO1(Entity entity) {
        if (!(entity instanceof ZombieEntity) || !((ZombieEntity) entity).isBaby()) {
            return false;
        }
        ItemStack held = heldItem(entity);
        return held != null && (held.getItem() == Items.DIAMOND_SWORD || held.getItem() == Items.PAPER);
    }

    public static boolean isGearedZombie(Entity entity) {
        if (!(entity instanceof ZombieEntity)) {
            return false;
        }

        ItemStack[] inventory = entity.getEquipment();
        if (inventory == null || inventory.length < 4) {
            return false;
        }

        boolean chestplate = isItem(inventory[3], Items.CHAINMAIL_CHESTPLATE);
        boolean leggings = isItem(inventory[2], Items.CHAINMAIL_LEGGINGS);
        boolean boots = isItem(inventory[1], Items.CHAINMAIL_BOOTS);
        boolean sword = isItem(inventory[0], Items.STONE_SWORD) || isItem(inventory[0], Items.IRON_SWORD);
        return chestplate && leggings && boots && sword;
    }

    /**
     * The Zombies highlight colour for this mob: red for bosses, blue for invisible mobs
     * (Sentinels), the powerup / bad-headshot colours, otherwise white. {@code null} for
     * non-living entities.
     */
    public static Color colorFor(Entity entity) {
        return colorFor(entity, true);
    }

    /**
     * Same as {@link #colorFor(Entity)}, but with {@code whiteNormal} off a plain mob (nothing
     * special about it) gets {@code null}, so the caller can fall back to its own colour.
     */
    public static Color colorFor(Entity entity, boolean whiteNormal) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }
        if (isSpecialRedEntity(entity)) {
            return COLOR_SPECIAL;
        }
        if (((LivingEntity) entity).hasStatusEffect(StatusEffect.INVISIBILITY)) {
            return COLOR_INVISIBLE;
        }

        MobType mobType = ZombiesSpawnHelper.getMobType(entity);
        Color normal = badHsLine.contains(entity.getNetworkId())
                ? COLOR_BAD_HEADSHOT_LINE
                : (whiteNormal ? COLOR_NORMAL : null);
        if (mobType == null) {
            return normal;
        }
        switch (mobType) {
            case BAD_HEADSHOT:
                return COLOR_BAD_HEADSHOT;
            case POWERUP_PREDICT:
                return COLOR_POWERUP_PREDICT;
            case POWERUP_ENSURED:
                return COLOR_POWERUP_ENSURED;
            default:
                return normal;
        }
    }

    /**
     * Gives baby zombies their real (half-size) hitbox. Vanilla 1.8.9 only resizes a zombie in
     * {@code setChild()}, which a client never calls for server-spawned mobs, so babies otherwise
     * keep an adult-sized box client side (ESP boxes, crosshair targeting).
     */
    private static void fixBabyZombieSizes(List<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof ZombieEntity) {
                ZombieEntity zombie = (ZombieEntity) entity;
                boolean child = zombie.isBaby();
                boolean childSized = zombie.width < ADULT_ZOMBIE_WIDTH * 0.75F;
                if (child != childSized) {
                    zombie.updateDimensions(child);
                }
            }
        }
    }

    private static ItemStack heldItem(Entity entity) {
        ItemStack[] inventory = entity.getEquipment();
        return inventory != null && inventory.length > 0 ? inventory[0] : null;
    }

    private static boolean isItem(ItemStack stack, net.minecraft.item.Item item) {
        return stack != null && stack.getItem() == item;
    }

    private ZombiesMobs() {}

    public static ZombiesMobs createTickHandler() {
        return new ZombiesMobs();
    }
}

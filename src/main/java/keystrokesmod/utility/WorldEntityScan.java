package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.world.World;

/** One ordered, type-only pass over the loaded entities. Entity properties are never cached here. */
public final class WorldEntityScan {
    public enum Kind {
        ITEMS,
        ARMOR_STANDS,
        LIVING,
        PRIMED_TNT;

        public int bit() {
            return 1 << this.ordinal();
        }
    }

    public interface MembershipVersion {
        long feral$getEntityMembershipVersion();
    }

    private final World world;
    private final long version;
    private final int sourceSize;
    private final int kinds;
    private final List<Entity> items;
    private final List<Entity> armorStands;
    private final List<Entity> living;
    private final List<Entity> primedTnt;

    private WorldEntityScan(World world, long version, int kinds) {
        this.world = world;
        this.version = version;
        this.sourceSize = world.entities.size();
        this.kinds = kinds;

        List<Entity> items = bucket(kinds, Kind.ITEMS);
        List<Entity> armorStands = bucket(kinds, Kind.ARMOR_STANDS);
        List<Entity> living = bucket(kinds, Kind.LIVING);
        List<Entity> primedTnt = bucket(kinds, Kind.PRIMED_TNT);
        for (Entity entity : world.entities) {
            if (items != null && entity instanceof ItemEntity) {
                items.add(entity);
            }
            if (armorStands != null && entity instanceof ArmorStandEntity) {
                armorStands.add(entity);
            }
            if (living != null && entity instanceof LivingEntity) {
                living.add(entity);
            }
            if (primedTnt != null && entity instanceof PrimedTntEntity) {
                primedTnt.add(entity);
            }
        }
        this.items = readOnly(items);
        this.armorStands = readOnly(armorStands);
        this.living = readOnly(living);
        this.primedTnt = readOnly(primedTnt);
    }

    private static List<Entity> bucket(int kinds, Kind kind) {
        return (kinds & kind.bit()) != 0 ? new ArrayList<>() : null;
    }

    private static List<Entity> readOnly(List<Entity> bucket) {
        return bucket == null ? Collections.emptyList() : Collections.unmodifiableList(bucket);
    }

    /** Owned by a single event; rebuild only if the world, membership or requested kinds changed. */
    public static WorldEntityScan refresh(WorldEntityScan existing, World world, int kinds) {
        if (!(world instanceof MembershipVersion)) {
            return null;
        }
        long version = ((MembershipVersion) world).feral$getEntityMembershipVersion();
        if (existing != null && existing.matches(world, version, kinds)) {
            return existing;
        }
        return new WorldEntityScan(world, version, kinds);
    }

    private boolean matches(World world, long version, int kinds) {
        return this.world == world
                && this.version == version
                // Also catch direct list growth/shrinkage; standard world APIs are versioned.
                && this.sourceSize == world.entities.size()
                && (kinds & ~this.kinds) == 0;
    }

    /** Optional reader: don't build a snapshot just for this lookup. */
    public List<Entity> candidatesIfFresh(World world, Kind kind) {
        return world instanceof MembershipVersion
                        && this.matches(
                                world, ((MembershipVersion) world).feral$getEntityMembershipVersion(), kind.bit())
                ? this.candidates(kind)
                : world.entities;
    }

    public List<Entity> candidates(Kind kind) {
        switch (kind) {
            case ITEMS:
                return this.items;
            case ARMOR_STANDS:
                return this.armorStands;
            case LIVING:
                return this.living;
            case PRIMED_TNT:
                return this.primedTnt;
            default:
                throw new AssertionError(kind);
        }
    }
}

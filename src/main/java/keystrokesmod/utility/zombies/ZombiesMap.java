package keystrokesmod.utility.zombies;

import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

/**
 * Hypixel Zombies maps: which one we're on, its wave spawn times (seconds into each round) and
 * its powerup round patterns.
 */
public enum ZombiesMap {
    NONE(new int[0][]),
    PRISON(new int[][] {
        {10, 20},
        {10, 20, 30},
        {10, 17, 24, 31},
        {10, 17, 24, 31},
        {10, 20, 30},
        {10, 20, 30},
        {10, 20, 30},
        {10, 25, 40},
        {10, 25, 35},
        {10, 25, 45},
        {10, 25, 40},
        {10, 25, 37},
        {10, 22, 34},
        {10, 25, 37},
        {10, 25, 40},
        {10, 22, 37},
        {10, 22, 42},
        {10, 25, 45},
        {10, 25, 45},
        {10, 25, 40},
        {10, 20, 35, 55, 75},
        {10, 25, 40},
        {10, 30, 50},
        {10, 30, 50},
        {10, 25, 45},
        {10, 30, 50},
        {10, 25, 45},
        {10, 30, 50},
        {10, 30, 55},
        {10}
    }),
    THE_LAB(new int[][] {
        {10, 22},
        {10, 22},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34}
    }),
    DEAD_END(new int[][] {
        {10, 20},
        {10, 20},
        {10, 20, 35},
        {10, 20, 35},
        {10, 22, 37},
        {10, 22, 44},
        {10, 25, 47},
        {10, 25, 50},
        {10, 22, 38},
        {10, 24, 45},
        {10, 25, 48},
        {10, 25, 50},
        {10, 25, 50},
        {10, 25, 45},
        {10, 25, 46},
        {10, 24, 47},
        {10, 24, 47},
        {10, 24, 47},
        {10, 24, 47},
        {10, 24, 49},
        {10, 23, 44},
        {10, 23, 45},
        {10, 23, 42},
        {10, 23, 43},
        {10, 23, 43},
        {10, 23, 36},
        {10, 24, 44},
        {10, 24, 42},
        {10, 24, 42},
        {10, 24, 45}
    }),
    BAD_BLOOD(new int[][] {
        {10, 22},
        {10, 22},
        {10, 22},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34},
        {10, 24, 38},
        {10, 24, 38},
        {10, 22, 34},
        {10, 24, 38},
        {10, 22, 34}
    }),
    ALIEN_ARCADIUM(new int[][] {
        {10, 13, 16, 19},
        {10, 14, 18, 22},
        {10, 13, 16, 19},
        {10, 14, 17, 21, 25, 28},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 19, 23, 28, 32},
        {10, 15, 19, 23, 27, 31},
        {10, 15, 20, 25, 30, 35},
        {10, 14, 19, 23, 28, 32},
        {10, 16, 22, 27, 33, 38},
        {10, 16, 21, 27, 32, 38},
        {10, 16, 22, 28, 34, 40},
        {10, 16, 22, 28, 34, 40},
        {10, 16, 21, 26, 31, 36},
        {10, 17, 24, 31, 38, 46},
        {10, 16, 22, 27, 33, 38},
        {10, 14, 19, 23, 28, 32},
        {10, 14, 19, 23, 28, 32},
        {10, 14, 18, 22, 26, 30},
        {10, 15, 21, 26, 31, 36},
        {10, 14, 19, 23, 28, 32},
        {10, 14, 19, 23, 28, 34},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 19, 23, 28, 32},
        {10},
        {10, 23, 36},
        {10, 22, 34},
        {10, 20, 30},
        {10, 24, 38},
        {10, 22, 34},
        {10, 22, 34},
        {10, 21, 32},
        {10, 22, 34},
        {10, 22, 34},
        {10},
        {10, 22, 34},
        {10, 20, 31},
        {10, 22, 34},
        {10, 22, 34},
        {10, 22, 34, 37, 45},
        {10, 21, 32},
        {10, 22, 34},
        {10, 13, 22, 25, 34, 37},
        {10, 22, 34},
        {10, 22, 34, 35},
        {10, 21, 32, 35},
        {10, 20, 30},
        {10, 20, 30, 33},
        {10, 21, 32},
        {10, 22, 34, 37},
        {10, 20, 30, 33},
        {10, 22, 34, 37},
        {10, 22, 34, 37},
        {10, 20, 32, 35, 39},
        {10, 16, 22, 28, 34, 40},
        {10, 14, 18},
        {10, 14, 18},
        {10, 22, 34, 37, 38},
        {10, 14, 18, 22, 26, 30},
        {10, 20, 30, 33},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 27, 32},
        {10, 14, 18, 22, 27, 32},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {10, 14, 18, 22, 26, 30},
        {5},
        {5},
        {5},
        {5},
        {5}
    });

    /** Block whose type differs on every map, used to tell them apart. */
    private static final BlockPos MAP_PROBE = new BlockPos(0, 72, 12);

    private final int[][] waveTimes;

    ZombiesMap(int[][] waveTimes) {
        this.waveTimes = waveTimes;
    }

    /** The map the player is on, or {@link #NONE}. */
    public static ZombiesMap current() {
        if (!Utils.nullCheck()) {
            return NONE;
        }

        Block block = BlockUtils.getBlock(MAP_PROBE);
        switch (block.getTranslationKey()) {
            case "tile.air":
                return THE_LAB;
            case "tile.woolCarpet":
                return ALIEN_ARCADIUM;
            case "tile.stonebricksmooth":
                return BAD_BLOOD;
            case "tile.cloth":
                return DEAD_END;
            case "tile.clayHardenedStained":
                return PRISON;
            default:
                return NONE;
        }
    }

    /** Wave spawn times in seconds for {@code round}, or an empty array if unknown. */
    public int[] waveTimes(int round) {
        return round >= 1 && round <= this.waveTimes.length ? this.waveTimes[round - 1].clone() : new int[0];
    }

    /**
     * Rounds with an Insta Kill for powerup pattern 2 or 3 (named after the first round it can
     * appear on), or {@code null} for any other pattern or a map without one.
     */
    public Integer[] instaKillRounds(int pattern) {
        if (pattern != 2 && pattern != 3) {
            return null;
        }
        switch (this) {
            case DEAD_END:
                return pattern == 2 ? new Integer[] {2, 8, 11, 14, 17, 23} : R3_INSTA_DE_BB;
            case BAD_BLOOD:
                return pattern == 2 ? new Integer[] {2, 5, 8, 11, 14, 17, 23} : R3_INSTA_DE_BB;
            case THE_LAB:
                return pattern == 2 ? new Integer[] {2, 8, 11, 14, 17, 23} : new Integer[] {3, 6, 9, 12, 18, 21, 24};
            case PRISON:
                return pattern == 2
                        ? new Integer[] {2, 5, 8, 11, 14, 17, 23}
                        : new Integer[] {3, 6, 9, 12, 15, 18, 21, 24};
            case ALIEN_ARCADIUM:
                return pattern == 2 ? R2_INSTA_AA : R3_INSTA_AA;
            default:
                return null;
        }
    }

    /** Like {@link #instaKillRounds} for Max Ammo. */
    public Integer[] maxAmmoRounds(int pattern) {
        if (pattern != 2 && pattern != 3) {
            return null;
        }
        switch (this) {
            case DEAD_END:
                return pattern == 2 ? new Integer[] {2, 8, 12, 16, 21, 26} : R3_MAX_DE_BB;
            case BAD_BLOOD:
                return pattern == 2 ? new Integer[] {2, 5, 8, 12, 16, 21, 26} : R3_MAX_DE_BB;
            case THE_LAB:
                return pattern == 2
                        ? new Integer[] {2, 8, 12, 16, 21, 26, 31, 36}
                        : new Integer[] {3, 6, 9, 13, 17, 22, 27, 32, 37};
            case PRISON:
                return pattern == 2 ? new Integer[] {2, 5, 8, 12, 16, 21, 26} : new Integer[] {3, 6, 9, 13, 17, 22, 27};
            case ALIEN_ARCADIUM:
                return pattern == 2 ? R2_MAX_AA : R3_MAX_AA;
            default:
                return null;
        }
    }

    /** Shopping Spree rounds for pattern 5, 6 or 7 (Alien Arcadium only), or {@code null}. */
    public static Integer[] shoppingSpreeRounds(int pattern) {
        switch (pattern) {
            case 5:
                return R5_SS_AA;
            case 6:
                return R6_SS_AA;
            case 7:
                return R7_SS_AA;
            default:
                return null;
        }
    }

    // Tables shared between maps or too long to inline.
    private static final Integer[] R3_INSTA_DE_BB = {3, 6, 9, 12, 18, 21, 24};
    private static final Integer[] R3_MAX_DE_BB = {3, 6, 9, 13, 17, 22, 27};
    private static final Integer[] R2_INSTA_AA = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final Integer[] R3_INSTA_AA = {3, 6, 9, 12, 15, 18, 21};
    private static final Integer[] R2_MAX_AA = {
        2, 5, 8, 12, 16, 21, 26, 31, 36, 41, 46, 51, 61, 66, 71, 76, 81, 86, 91, 96
    };
    private static final Integer[] R3_MAX_AA = {
        3, 6, 9, 13, 17, 22, 27, 32, 37, 42, 47, 52, 62, 67, 72, 77, 82, 87, 92, 97
    };
    private static final Integer[] R5_SS_AA = {5, 15, 45, 55, 65, 75, 85, 95, 105};
    private static final Integer[] R6_SS_AA = {6, 16, 26, 36, 46, 66, 76, 86, 96};
    private static final Integer[] R7_SS_AA = {7, 17, 27, 37, 47, 67, 77, 87, 97};
}

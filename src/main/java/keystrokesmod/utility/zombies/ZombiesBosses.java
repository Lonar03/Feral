// Boss tags ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: hypixel/game/zombies/round/ZombiesRoundData.java (tag(...) calls). Adjust's table holds at most one boss per
// wave and is incomplete, so extra entries can be added in keystrokes/zombiesbosses.json.
package keystrokesmod.utility.zombies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

public final class ZombiesBosses {
    public static final int NORMAL = 0;
    public static final int HARD = 1;
    public static final int RIP = 2;
    public static final String[] DIFFICULTIES = {"Normal", "Hard", "RIP"};

    /** Regular mobs Adjust also tags; only shown with "minor" on. */
    private static final Set<String> MINOR = new java.util.HashSet<>(java.util.Arrays.asList("Blaze", "Slime"));

    // map -> round -> wave (1-based) -> difficulty -> bosses
    private static final Map<ZombiesMap, Map<Integer, Map<Integer, List<Set<String>>>>> TABLE =
            new EnumMap<>(ZombiesMap.class);

    static {
        load();
    }

    private ZombiesBosses() {}

    public static synchronized void load() {
        TABLE.clear();
        ZombiesMap de = ZombiesMap.DEAD_END;
        tag(de, 5, 3, null, null, "Bombie");
        tag(de, 10, 3, "Bombie", "Bombie", "Bombie");
        tag(de, 15, 1, "Blaze", "Blaze", "Blaze");
        tag(de, 15, 3, null, "Bombie", "Inferno");
        tag(de, 18, 1, "Blaze", "Blaze", null);
        tag(de, 20, 3, "Inferno", "Inferno", "Inferno");
        tag(de, 25, 3, null, "Bombie", "Broodmother");
        tag(de, 30, 3, "Broodmother", "Broodmother", "Broodmother");
        ZombiesMap bb = ZombiesMap.BAD_BLOOD;
        tag(bb, 1, 1, null, null, "Slime");
        tag(bb, 2, 1, null, "Slime", "Slime");
        tag(bb, 3, 1, "Slime", "Slime", "Slime");
        tag(bb, 3, 2, "Slime", "Slime", "Slime");
        tag(bb, 4, 2, null, null, "Slime");
        tag(bb, 4, 3, "Slime", "Slime", "Slime");
        tag(bb, 6, 2, null, "Slime", "Slime");
        tag(bb, 7, 1, null, "Slime", "Slime");
        tag(bb, 7, 2, null, "Slime", "Slime");
        tag(bb, 7, 3, null, "Slime", "Slime");
        tag(bb, 9, 1, "Slime", "Slime", "Slime");
        tag(bb, 9, 2, "Slime", "Slime", "Slime");
        tag(bb, 9, 3, "Slime", "Slime", "Slime");
        tag(bb, 10, 3, "King Slime", "King Slime", "King Slime");
        tag(bb, 20, 3, "Wither", "Wither", "Wither");
        tag(bb, 30, 3, "Herobrine", "Herobrine", "Herobrine");
        ZombiesMap aa = ZombiesMap.ALIEN_ARCADIUM; // no difficulties, everything counts as Normal
        tag(aa, 25, 1, "Mega Blob", "Mega Blob", "Mega Blob");
        tag(aa, 35, 1, "Mega Magma", "Mega Magma", "Mega Magma");
        tag(aa, 56, 1, "Mega Blob", "Mega Blob", "Mega Blob");
        tag(aa, 57, 1, "Mega Magma", "Mega Magma", "Mega Magma");
        tag(aa, 101, 1, "World Ender", "World Ender", "World Ender");
        ZombiesMap prison = ZombiesMap.PRISON;
        tag(prison, 10, 3, "Angry Prisoner", "Angry Prisoner", "Angry Prisoner");
        tag(prison, 20, 3, "Corrupted Pigman", "Corrupted Pigman", "Corrupted Pigman");
        tag(prison, 30, 1, "Warden", "Warden", "Warden");
        loadFile();
    }

    private static void tag(ZombiesMap map, int round, int wave, String normal, String hard, String rip) {
        String[] bosses = {normal, hard, rip};
        for (int d = 0; d < 3; d++) {
            if (bosses[d] != null) {
                add(map, round, wave, d, bosses[d]);
            }
        }
    }

    private static void add(ZombiesMap map, int round, int wave, int difficulty, String boss) {
        Map<Integer, List<Set<String>>> waves =
                TABLE.computeIfAbsent(map, m -> new HashMap<>()).computeIfAbsent(round, r -> new HashMap<>());
        List<Set<String>> perDifficulty = waves.computeIfAbsent(wave, w -> {
            List<Set<String>> list = new ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                list.add(new LinkedHashSet<>());
            }
            return list;
        });
        perDifficulty.get(difficulty).add(boss);
    }

    /** Bosses per wave (index 0 = wave 1) of {@code round}; empty sets for waves without one. */
    public static synchronized List<Set<String>> bosses(
            ZombiesMap map, int round, int difficulty, int waves, boolean minor) {
        List<Set<String>> result = new ArrayList<>(waves);
        Map<Integer, List<Set<String>>> table =
                TABLE.getOrDefault(map, new HashMap<>()).getOrDefault(round, new HashMap<>());
        for (int wave = 1; wave <= waves; wave++) {
            Set<String> set = new LinkedHashSet<>();
            List<Set<String>> perDifficulty = table.get(wave);
            if (perDifficulty != null) {
                for (String boss : perDifficulty.get(Math.max(0, Math.min(2, difficulty)))) {
                    if (minor || !MINOR.contains(boss)) {
                        set.add(boss);
                    }
                }
            }
            result.add(set);
        }
        return result;
    }

    public static File file() {
        return new File(Minecraft.getInstance().gameDir + File.separator + "keystrokes", "zombiesbosses.json");
    }

    /**
     * Extra bosses, added on top of Adjust's table:
     * [["DEAD_END", 25, 1, "rip", "Bombie"], ["DEAD_END", 25, 2, "rip", "Bombie"]]
     * map = DEAD_END, BAD_BLOOD, ALIEN_ARCADIUM, PRISON; wave is 1-based; difficulty = normal, hard, rip or all.
     */
    private static void loadFile() {
        File file = file();
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("[\n  [\"DEAD_END\", 25, 1, \"rip\", \"Bombie\"],\n"
                            + "  [\"DEAD_END\", 25, 2, \"rip\", \"Bombie\"]\n]\n");
                }
            }
            try (FileReader reader = new FileReader(file)) {
                JsonArray array = new JsonParser().parse(reader).getAsJsonArray();
                for (JsonElement element : array) {
                    JsonArray entry = element.getAsJsonArray();
                    ZombiesMap map =
                            ZombiesMap.valueOf(entry.get(0).getAsString().toUpperCase());
                    int round = entry.get(1).getAsInt();
                    int wave = entry.get(2).getAsInt();
                    String difficulty = entry.get(3).getAsString().toLowerCase();
                    String boss = entry.get(4).getAsString();
                    for (int d = 0; d < 3; d++) {
                        if (difficulty.equals("all") || difficulty.equals(DIFFICULTIES[d].toLowerCase())) {
                            add(map, round, wave, d, boss);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Boss Tracker] could not load " + file + ": " + e);
        }
    }
}

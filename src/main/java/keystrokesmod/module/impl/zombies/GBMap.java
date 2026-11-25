package keystrokesmod.module.impl.zombies;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/**
 * Turns a fixed list of blocks per Zombies map into client-side ghost blocks. The list lives in
 * {@code .minecraft/keystrokes/zombies/gbmap.txt} (seeded from the bundled default) and is reloaded
 * automatically when the file changes, so it can be edited mid game.
 */
public class GBMap extends Module {
    private static final File DIRECTORY = new File(new File(Minecraft.getInstance().gameDir, "keystrokes"), "zombies");
    private static final File FILE = new File(DIRECTORY, "gbmap.txt");
    private static final String HEADER = "# GB Map: blocks turned into ghost blocks on each Zombies map.\n"
            + "# One block per line: x y z map. Maps: DE (Dead End), BB (Bad Blood), PR (Prison).\n"
            + "# Ghost Block with Debug on prints the coordinates of the block you remove.\n"
            + "# Saved changes are picked up automatically.\n";
    private static final int FILE_CHECK_INTERVAL_TICKS = 20;

    /** Map code -> blocks to remove on that map. */
    private final Map<String, List<BlockPos>> blocksByMap = new HashMap<>();

    private long loadedLastModified = -1L;
    private int ticksUntilFileCheck = 0;

    public GBMap() {
        super("GB Map", Module.category.zombies);
        this.registerSetting(new DescriptionSetting("Edit gbmap.txt, changes load automatically."));
        this.registerSetting(new ButtonSetting("Open folder", () -> {
            ensureFileExists();
            if (!Utils.openFolder(DIRECTORY)) {
                Utils.sendMessage("&cCouldn't open the folder: &7" + DIRECTORY.getAbsolutePath());
            }
        }));
        this.registerSetting(new ButtonSetting("Reload", () -> {
            this.load();
            Utils.sendMessage("&7GB Map: loaded &b" + this.countBlocks() + "&7 blocks.");
        }));
    }

    @Override
    public void onEnable() {
        this.load();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !this.isEnabled() || mc.world == null) {
            return;
        }

        if (--this.ticksUntilFileCheck <= 0) {
            this.ticksUntilFileCheck = FILE_CHECK_INTERVAL_TICKS;
            if (FILE.lastModified() != this.loadedLastModified) {
                this.load();
            }
        }

        List<BlockPos> blocks = this.blocksByMap.get(getMapCode(ZombiesMap.current()));
        if (blocks == null) {
            return;
        }

        // Re-applied every tick so blocks stay gone when the server resends them.
        for (BlockPos pos : blocks) {
            if (mc.world.isChunkLoaded(pos) && !mc.world.isAir(pos)) {
                mc.world.removeBlock(pos);
            }
        }
    }

    private void load() {
        ensureFileExists();
        this.blocksByMap.clear();
        this.loadedLastModified = FILE.lastModified();

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length < 4) {
                    continue;
                }

                try {
                    BlockPos pos = new BlockPos(
                            Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                    this.blocksByMap
                            .computeIfAbsent(parts[3].toUpperCase(), k -> new ArrayList<>())
                            .add(pos);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            System.err.println("[GBMap] Could not read " + FILE.getAbsolutePath());
        }
    }

    /** Copies the bundled default list to the config folder on first use. */
    private static void ensureFileExists() {
        if (FILE.exists()) {
            return;
        }

        DIRECTORY.mkdirs();
        try (InputStream defaults = GBMap.class.getResourceAsStream("/assets/keystrokesmod/zombies/gbmap.txt")) {
            String body = defaults == null ? "" : Utils.readInputStream(defaults);
            Files.write(FILE.toPath(), (HEADER + body).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[GBMap] Could not create " + FILE.getAbsolutePath());
        }
    }

    private int countBlocks() {
        int count = 0;
        for (List<BlockPos> blocks : this.blocksByMap.values()) {
            count += blocks.size();
        }
        return count;
    }

    private static String getMapCode(ZombiesMap map) {
        switch (map) {
            case DEAD_END:
                return "DE";
            case BAD_BLOOD:
                return "BB";
            case PRISON:
                return "PR";
            default:
                return "";
        }
    }
}

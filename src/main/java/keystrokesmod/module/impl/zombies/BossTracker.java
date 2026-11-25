package keystrokesmod.module.impl.zombies;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import keystrokesmod.helper.zombies.ZombiesGameHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.RenderTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.WorldEvent.Unload;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesBosses;
import keystrokesmod.utility.zombies.ZombiesMap;
import keystrokesmod.utility.zombies.ZombiesMobs;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

public class BossTracker extends Module {
    private static final int MESSAGE_DURATION_TICKS = 50;
    private static final String GREAT_HALL = "Great Hall";
    private final Map<BlockPos, String> trackedCoords = new HashMap<>();
    private final Set<Integer> seenBossIds = new HashSet<>();
    private final Set<Integer> seenO1Ids = new HashSet<>();
    private final Set<Integer> seenWitherIds = new HashSet<>();
    private boolean herobrineAnnounced = false;
    private final Map<String, Integer> messagesToDisplay = new LinkedHashMap<>();
    /** Bosses to draw a tracer to, refreshed every tick. */
    private final List<Entity> tracerTargets = new ArrayList<>();

    private final ButtonSetting tracers;
    private final ColorSetting tracerColor;
    private final SliderSetting tracerWidth;
    // boss-per-wave alerts (boss table from Adjust, see ZombiesBosses)
    private final ButtonSetting waveAlerts;
    private final SliderSetting bossWarning;
    private final SliderSetting difficulty;
    private final ButtonSetting minorMobs;
    private int alertedRound = -1;
    private int alertedDifficulty = -1;
    private final Set<Integer> warnedWaves = new HashSet<>();

    public BossTracker() {
        super("Boss Tracker", Module.category.zombies);
        // Tracer lines from your crosshair to every boss, like the old Yogurt (ZombieCat) tracers.
        this.registerSetting(this.tracers = new ButtonSetting("Tracers", true));
        this.registerSetting(this.tracerColor = new ColorSetting("Tracer color", 255, 0, 0, 150));
        this.registerSetting(this.tracerWidth = new SliderSetting("Tracer width", 2.0, 1.0, 5.0, 0.5));
        this.registerSetting(this.waveAlerts = new ButtonSetting("Boss wave alerts", true));
        this.registerSetting(
                this.bossWarning = new SliderSetting("Boss wave warning", "s", "§cOff", 5.0, 0.0, 15.0, 1.0));
        this.registerSetting(
                this.difficulty = new SliderSetting("Difficulty", 0, new String[] {"Auto", "Normal", "Hard", "RIP"}));
        this.registerSetting(this.minorMobs = new ButtonSetting("Show slimes/blazes", false));
        this.registerSetting(new ButtonSetting("Reload boss list", () -> {
            ZombiesBosses.load();
            Utils.sendMessage("&7Reloaded &b" + ZombiesBosses.file().getName());
        }));
        this.loadCoordinates();
    }

    @Override
    public void guiUpdate() {
        this.tracerColor.setVisible(this.tracers.isToggled(), this);
        this.tracerWidth.setVisible(this.tracers.isToggled(), this);
        boolean alerts = this.waveAlerts.isToggled();
        this.bossWarning.setVisible(alerts, this);
        this.difficulty.setVisible(alerts, this);
        this.minorMobs.setVisible(alerts, this);
    }

    @Override
    public void onDisable() {
        this.tracerTargets.clear();
    }

    private void loadCoordinates() {
        String line;
        try (InputStream inputStream =
                        BossTracker.class.getResourceAsStream("/assets/keystrokesmod/zombies/windowcoordinates.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream)); ) {
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 4) {
                        try {
                            int x = Integer.parseInt(parts[0]);
                            int y = Integer.parseInt(parts[1]);
                            int z = Integer.parseInt(parts[2]);
                            StringBuilder nameBuilder = new StringBuilder();

                            for (int i = 3; i < parts.length; i++) {
                                nameBuilder.append(parts[i]);
                                if (i != parts.length - 1) {
                                    nameBuilder.append(" ");
                                }
                            }

                            String name = nameBuilder.toString();
                            this.trackedCoords.put(new BlockPos(x, y, z), name);
                        } catch (NumberFormatException var37) {
                        }
                    }
                }
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent event) {
        if (event.phase != Phase.END) {
            return;
        }
        this.tracerTargets.clear();
        if (!Utils.nullCheck()) {
            return;
        }

        for (Entity entity : mc.world.entities) {
            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            if (this.tracers.isToggled()
                    && entity != mc.player
                    && !(entity instanceof PlayerEntity)
                    && entity.isAlive()
                    && ZombiesMobs.isSpecialRedEntity(entity)) {
                this.tracerTargets.add(entity);
            }

            if (ZombiesMobs.isBoss(entity) || ZombiesMobs.isGearedZombie(entity)) {
                this.announceSpawn(entity, this.seenBossIds, "Boss");
            }
            if (ZombiesMobs.isO1(entity)) {
                this.announceSpawn(entity, this.seenO1Ids, "O1");
            }
            if (ZombiesMobs.isWitherBoss(entity) && entity.isAlive()) {
                this.announceWither(entity);
            }
        }

        this.checkHerobrineBossBar();
        this.updateWaveAlerts();
        this.messagesToDisplay.replaceAll((k, v) -> v - 1);
        this.messagesToDisplay.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    // ---------------------------------------------------------------- boss wave alerts

    private int currentDifficulty() {
        int setting = (int) this.difficulty.getInput();
        if (setting > 0) {
            return setting - 1;
        }
        // Adjust read the difficulty from the color of a sidebar line ("§a" Normal, "§c" Hard, "§4" RIP);
        // we look at the "Map:" line. Not checked on a live sidebar yet.
        for (String raw : Utils.getSidebarLines()) {
            String clean = Utils.stripColor(raw);
            if (!clean.contains(": ")
                    || clean.contains("/")
                    || !clean.toLowerCase().startsWith("map")) {
                continue;
            }
            if (raw.contains("§4")) {
                return ZombiesBosses.RIP;
            }
            if (raw.contains("§c")) {
                return ZombiesBosses.HARD;
            }
            return ZombiesBosses.NORMAL;
        }
        return ZombiesBosses.NORMAL;
    }

    private void updateWaveAlerts() {
        if (!this.waveAlerts.isToggled()) {
            return;
        }
        int round = ZombiesGameHelper.getCurrentRound();
        if (round <= 0) {
            this.alertedRound = -1;
            return;
        }
        ZombiesMap map = ZombiesMap.current();
        int[] waveTimes = ZombiesGameHelper.getWaveTimes();
        if (map == ZombiesMap.NONE || waveTimes.length == 0) {
            return;
        }
        int difficulty = this.currentDifficulty();
        List<Set<String>> bosses =
                ZombiesBosses.bosses(map, round, difficulty, waveTimes.length, this.minorMobs.isToggled());
        // reprint if the difficulty only got known after the round started
        if (round != this.alertedRound || difficulty != this.alertedDifficulty) {
            if (round != this.alertedRound) {
                this.warnedWaves.clear();
            }
            this.alertedRound = round;
            this.alertedDifficulty = difficulty;
            StringBuilder text = new StringBuilder();
            for (int wave = 0; wave < bosses.size(); wave++) {
                for (String boss : bosses.get(wave)) {
                    if (text.length() > 0) {
                        text.append(" §7· ");
                    }
                    text.append("§c").append(boss).append(" §7w").append(wave + 1);
                }
            }
            if (text.length() > 0) {
                Utils.sendRawMessage("§7[§cBosses§7] §fR" + round + ": " + text);
            }
        }
        int warnSeconds = (int) this.bossWarning.getInput();
        if (warnSeconds <= 0) {
            return;
        }
        int elapsed = ZombiesGameHelper.getRoundTimeMs();
        for (int wave = 0; wave < bosses.size() && wave < waveTimes.length; wave++) {
            if (bosses.get(wave).isEmpty() || this.warnedWaves.contains(wave)) {
                continue;
            }
            int untilMs = waveTimes[wave] * 1000 - elapsed;
            if (untilMs <= warnSeconds * 1000 && untilMs > -1000) {
                this.warnedWaves.add(wave);
                String names = String.join(", ", bosses.get(wave));
                this.announce("§c" + names + " §fin §c" + Math.max(0, (untilMs + 999) / 1000) + "s");
                mc.player.playSound("note.pling", 1.0F, 1.0F);
            }
        }
    }

    /** Announces {@code entity} once if it stands on one of the tracked window coordinates. */
    private void announceSpawn(Entity entity, Set<Integer> seenIds, String label) {
        if (seenIds.contains(entity.getNetworkId())) {
            return;
        }

        String window = this.trackedCoords.get(new BlockPos(entity));
        if (window != null) {
            String msg = "§aThe " + label + " Spawned In: §c" + window;
            this.announce(msg);
            seenIds.add(entity.getNetworkId());
        }
    }

    /**
     * Withers don't spawn at a window but always in the Great Hall, so they're announced on sight
     * with that room instead of being looked up in windowcoordinates.txt.
     */
    private void announceWither(Entity wither) {
        if (this.seenWitherIds.add(wither.getNetworkId())) {
            this.announce("§aThe Wither Spawned In: §c" + GREAT_HALL);
        }
    }

    /**
     * Herobrine is an unarmoured zombie with a player head, which Sentinels also are, so it's
     * recognised by its boss bar instead of its entity. Also always spawns in the Great Hall.
     */
    private void checkHerobrineBossBar() {
        if (!this.herobrineAnnounced && ZombiesMobs.isBossBarShowing("herobrine")) {
            this.herobrineAnnounced = true;
            this.announce("§aHerobrine Spawned In: §c" + GREAT_HALL);
        }
    }

    private void announce(String msg) {
        Utils.sendRawMessage(msg);
        this.messagesToDisplay.put(msg, MESSAGE_DURATION_TICKS);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!this.tracers.isToggled() || this.tracerTargets.isEmpty() || !Utils.nullCheck()) {
            return;
        }
        int color = this.tracerColor.getColor();
        float width = (float) this.tracerWidth.getInput();
        for (Entity boss : this.tracerTargets) {
            if (!boss.removed) {
                RenderUtils.drawTracerLine(boss, color, width, event.partialTicks);
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderTickEvent event) {
        // Feral draws HUD text at the end of the render tick (like the HUD module); Meower used Forge's overlay event
        if (event.phase != Phase.END || !Utils.nullCheck()) {
            return;
        }
        TextRenderer fr = mc.textRenderer;
        Window sr = new Window(mc);
        int screenWidth = sr.getWidth();
        int screenHeight = sr.getHeight();
        int yPos = screenHeight / 6;

        for (String msg : this.messagesToDisplay.keySet()) {
            int msgWidth = fr.getWidth(msg);
            GL11.glPushMatrix();
            GL11.glTranslatef(screenWidth / 2.0F, yPos, 0.0F);
            GL11.glScalef(2.0F, 2.0F, 1.0F);
            fr.drawWithShadow(msg, -msgWidth / 2, 0.0F, 16777215);
            GL11.glPopMatrix();
            yPos += fr.fontHeight * 2 + 4;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(Unload event) {
        this.seenBossIds.clear();
        this.seenO1Ids.clear();
        this.seenWitherIds.clear();
        this.herobrineAnnounced = false;
        this.messagesToDisplay.clear();
        this.tracerTargets.clear();
    }
}

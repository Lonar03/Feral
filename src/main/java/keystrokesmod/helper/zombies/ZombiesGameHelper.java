package keystrokesmod.helper.zombies;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.platform.ornithe.ForgeEvents.EntityJoinWorldEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.MinecraftForge;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesLanguage;
import keystrokesmod.utility.zombies.ZombiesMap;
import keystrokesmod.utility.zombies.ZombiesMobs;
import net.minecraft.client.Minecraft;

/**
 * Tracks the state of a Hypixel Zombies game: the sidebar, the current round, when it started and
 * its wave spawn times. Round changes come from the "Round N" title (see {@code MixinGuiIngame}).
 * {@link #init()} starts every Zombies tracker.
 */
public final class ZombiesGameHelper implements IMinecraftInstance {
    private static final int SCOREBOARD_REFRESH_TICKS = 5;
    /** Sidebar row (1-based, below the title) that shows "Zombies Left: N". */
    private static final int ZOMBIES_LEFT_ROW = 4;

    private static boolean initialised;
    private static String scoreboardTitle = "";
    /** Cleaned sidebar rows below the title, top to bottom. Kept from the last Zombies sidebar. */
    private static List<String> scoreboardRows = new ArrayList<>();

    private static int ticksUntilRefresh = SCOREBOARD_REFRESH_TICKS;
    private static int currentRound;
    private static int[] waveTimes = new int[0];
    /** When the current round's title showed, or 0 when no round is running. */
    private static long roundStartMs;

    private ZombiesGameHelper() {}

    public static void init() {
        if (initialised) {
            return;
        }

        ZombiesLanguage.load();
        MinecraftForge.EVENT_BUS.register(new ZombiesGameHelper());
        MinecraftForge.EVENT_BUS.register(new ZombiesPowerupHelper());
        MinecraftForge.EVENT_BUS.register(new ZombiesSpawnHelper());
        MinecraftForge.EVENT_BUS.register(ZombiesMobs.createTickHandler());
        initialised = true;
    }

    /** Current round, or 0 outside a game. */
    public static int getCurrentRound() {
        return currentRound;
    }

    /** Wave spawn times of the current round, in seconds from the round start. */
    public static int[] getWaveTimes() {
        return waveTimes.clone();
    }

    /** Milliseconds since the current round started, or 0 when no round is running. */
    public static int getRoundTimeMs() {
        return roundStartMs == 0L ? 0 : (int) (Minecraft.getTime() - roundStartMs);
    }

    /** The sidebar shows a Zombies game (title only; also true in the pre-game lobby). */
    public static boolean isZombiesScoreboard() {
        return ZombiesLanguage.isZombiesScoreboardTitle(scoreboardTitle);
    }

    /** In a running Zombies game: Zombies sidebar with a "Zombies Left" row. */
    public static boolean isInZombies() {
        return isZombiesScoreboard() && ZombiesLanguage.isZombiesLeftLine(getScoreboardRow(ZOMBIES_LEFT_ROW));
    }

    /** Cleaned sidebar row {@code row} (1-based, below the title), or "" if there is none. */
    public static String getScoreboardRow(int row) {
        return row >= 1 && row <= scoreboardRows.size() ? scoreboardRows.get(row - 1) : "";
    }

    /** Called for every title shown on screen. Starts a new round on "Round N". */
    public static void onTitle(String rawTitle) {
        String title = ZombiesLanguage.clean(rawTitle);
        ZombiesLanguage.detectLanguage(getScoreboardRow(ZOMBIES_LEFT_ROW));
        boolean gameEnded = ZombiesLanguage.contains(title, "zombies.game.youwin")
                || ZombiesLanguage.contains(title, "zombies.game.gameover");
        if (!ZombiesLanguage.isRoundTitle(title) && !gameEnded) {
            return;
        }

        roundStartMs = Minecraft.getTime();
        if (!isZombiesScoreboard()) {
            return;
        }

        setRound(ZombiesLanguage.roundNumber(title));
        ZombiesSpawnHelper.onNewRound();
        ZombiesPowerupHelper.onNewRound();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START || !Utils.nullCheck() || mc.isSingleplayer()) {
            return;
        }
        if (--ticksUntilRefresh <= 0) {
            ticksUntilRefresh = SCOREBOARD_REFRESH_TICKS;
            refreshScoreboard();
        }
    }

    /** Joining a world (every server switch) ends whatever game was being tracked. */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isClient && !mc.isSingleplayer() && event.entity == mc.player) {
            scoreboardTitle = "";
            scoreboardRows = new ArrayList<>();
            roundStartMs = 0L;
            setRound(0);
        }
    }

    private static void setRound(int round) {
        currentRound = round;
        waveTimes = ZombiesMap.current().waveTimes(round);
    }

    private static void refreshScoreboard() {
        // Title first, then rows top to bottom.
        List<String> lines = Utils.getSidebarLines();
        if (lines.isEmpty()) {
            return;
        }

        scoreboardTitle = ZombiesLanguage.clean(lines.get(0));
        if (!isZombiesScoreboard() || lines.size() < 2) {
            return;
        }

        List<String> rows = new ArrayList<>(lines.size() - 1);
        for (String line : lines.subList(1, lines.size())) {
            rows.add(ZombiesLanguage.clean(line));
        }
        scoreboardRows = rows;
    }
}

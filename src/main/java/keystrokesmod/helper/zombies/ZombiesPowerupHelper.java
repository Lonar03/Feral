package keystrokesmod.helper.zombies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.EntityJoinWorldEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesLanguage;
import keystrokesmod.utility.zombies.ZombiesMap;

/**
 * Works out which rounds drop Insta Kill, Max Ammo and Shopping Spree. Each map has two possible
 * patterns per powerup (Shopping Spree: three, Alien Arcadium only); the pattern is read from the
 * first "activated" chat message or powerup armor stand, or assumed once round 3 / 7 is reached.
 */
public final class ZombiesPowerupHelper implements IMinecraftInstance {
    /** A powerup seen this early into a round still belongs to the previous round's pattern. */
    private static final int CHAT_GRACE_MS = 500;

    private static final int ARMOR_STAND_GRACE_MS = 1000;

    private static List<Integer> instaKillRounds = new ArrayList<>();
    private static List<Integer> maxAmmoRounds = new ArrayList<>();
    private static List<Integer> shoppingSpreeRounds = new ArrayList<>();
    private static final Set<Integer> processedArmorStands = new HashSet<>();
    private static int incomingPowerups;

    ZombiesPowerupHelper() {}

    /** How many powerups drop this round, according to the known patterns. */
    public static int getIncomingPowerups() {
        return incomingPowerups;
    }

    /** No pattern is known yet for any powerup. */
    public static boolean hasNoPatterns() {
        return instaKillRounds.isEmpty() && maxAmmoRounds.isEmpty() && shoppingSpreeRounds.isEmpty();
    }

    static void onNewRound() {
        incomingPowerups = 0;
        applyDefaultPatterns();
        updateIncomingPowerups();
    }

    /** Called with each armor stand name received from the server. */
    public static void onArmorStandName(String name, int networkId) {
        int round = ZombiesGameHelper.getCurrentRound();
        if (!processedArmorStands.add(networkId) || round == 0) {
            return;
        }

        int ms = ZombiesGameHelper.getRoundTimeMs();
        boolean early = ms <= ARMOR_STAND_GRACE_MS;
        ZombiesMap aa = ZombiesMap.ALIEN_ARCADIUM;
        if (ZombiesLanguage.equals(name, "zombies.game.maxammo.upper")) {
            int pattern = patternFromArmorStand(aa.maxAmmoRounds(2), aa.maxAmmoRounds(3), round, early);
            if (pattern != 0) {
                maxAmmoRounds = rounds(ZombiesMap.current().maxAmmoRounds(pattern));
            }
        } else if (ZombiesLanguage.equals(name, "zombies.game.instakill.upper")) {
            int pattern = patternFromArmorStand(aa.instaKillRounds(2), aa.instaKillRounds(3), round, early);
            if (pattern != 0) {
                instaKillRounds = rounds(ZombiesMap.current().instaKillRounds(pattern));
            }
        } else if (ZombiesLanguage.equals(name, "zombies.game.shoppingspree.upper")) {
            int pattern = shoppingSpreePatternFromArmorStand(round, early);
            if (pattern != 0) {
                shoppingSpreeRounds = rounds(ZombiesMap.shoppingSpreeRounds(pattern));
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.type != 0 && event.type != 1) {
            return;
        }

        String message = ZombiesLanguage.clean(event.message.getString());
        // Player chat has a colon; powerup announcements don't.
        if (message.contains(":") || !ZombiesGameHelper.isInZombies()) {
            return;
        }
        if (!ZombiesLanguage.contains(message, "zombies.game.activated")
                && !ZombiesLanguage.contains(message, "zombies.game.activated.2")) {
            return;
        }

        int round = ZombiesGameHelper.getCurrentRound();
        if (round == 0) {
            return;
        }

        boolean early = ZombiesGameHelper.getRoundTimeMs() <= CHAT_GRACE_MS;
        ZombiesMap map = ZombiesMap.current();
        if (ZombiesLanguage.contains(message, "zombies.game.instakill.lower")) {
            if (instaKillRounds.isEmpty()) {
                instaKillRounds = rounds(map.instaKillRounds(patternFromChat(round, early)));
            }
        } else if (ZombiesLanguage.contains(message, "zombies.game.maxammo.lower")) {
            if (maxAmmoRounds.isEmpty()) {
                maxAmmoRounds = rounds(map.maxAmmoRounds(patternFromChat(round, early)));
            }
        } else if (ZombiesLanguage.contains(message, "zombies.game.shoppingspree.lower")
                && shoppingSpreeRounds.isEmpty()) {
            shoppingSpreeRounds = rounds(ZombiesMap.shoppingSpreeRounds(shoppingSpreePatternFromChat(round, early)));
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.START && Utils.nullCheck() && ZombiesGameHelper.getCurrentRound() > 0) {
            applyDefaultPatterns();
            updateIncomingPowerups();
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isClient && event.entity == mc.player) {
            instaKillRounds = new ArrayList<>();
            maxAmmoRounds = new ArrayList<>();
            shoppingSpreeRounds = new ArrayList<>();
            processedArmorStands.clear();
        }
    }

    /** No Insta Kill / Max Ammo by round 3 means pattern 3; no Shopping Spree by round 7 means 7. */
    private static void applyDefaultPatterns() {
        int round = ZombiesGameHelper.getCurrentRound();
        if (!ZombiesGameHelper.isInZombies() || round == 0) {
            return;
        }

        ZombiesMap map = ZombiesMap.current();
        if (round == 3) {
            if (instaKillRounds.isEmpty()) {
                instaKillRounds = rounds(map.instaKillRounds(3));
            }
            if (maxAmmoRounds.isEmpty()) {
                maxAmmoRounds = rounds(map.maxAmmoRounds(3));
            }
        }
        if (round == 7 && map == ZombiesMap.ALIEN_ARCADIUM && shoppingSpreeRounds.isEmpty()) {
            shoppingSpreeRounds = rounds(ZombiesMap.shoppingSpreeRounds(7));
        }
    }

    private static void updateIncomingPowerups() {
        int round = ZombiesGameHelper.getCurrentRound();
        if (!ZombiesGameHelper.isInZombies() || round == 0) {
            return;
        }

        int count = 0;
        for (List<Integer> pattern : Arrays.asList(instaKillRounds, maxAmmoRounds, shoppingSpreeRounds)) {
            for (int r : pattern) {
                if (r == round) {
                    count++;
                }
            }
        }
        incomingPowerups = count;
    }

    /** Insta Kill / Max Ammo pattern from the round it was announced in; 0 if it can't be told. */
    private static int patternFromChat(int round, boolean early) {
        if (round == 2 || (round == 3 && early)) {
            return 2;
        }
        return round == 3 || round == 4 ? 3 : 0;
    }

    private static int shoppingSpreePatternFromChat(int round, boolean early) {
        if (round == 5 || (round == 6 && early)) {
            return 5;
        }
        if (round == 6 || (round == 7 && early)) {
            return 6;
        }
        return round == 7 || round == 8 ? 7 : 0;
    }

    /** Insta Kill / Max Ammo pattern from a powerup armor stand, using Alien Arcadium's rounds. */
    private static int patternFromArmorStand(Integer[] pattern2, Integer[] pattern3, int round, boolean early) {
        List<Integer> r2 = Arrays.asList(pattern2);
        List<Integer> r3 = Arrays.asList(pattern3);
        if (r2.contains(round) || (r3.contains(round) && early)) {
            return 2;
        }
        return r3.contains(round) || (r3.contains(round - 1) && early) ? 3 : 0;
    }

    private static int shoppingSpreePatternFromArmorStand(int round, boolean early) {
        List<Integer> r5 = Arrays.asList(ZombiesMap.shoppingSpreeRounds(5));
        List<Integer> r6 = Arrays.asList(ZombiesMap.shoppingSpreeRounds(6));
        List<Integer> r7 = Arrays.asList(ZombiesMap.shoppingSpreeRounds(7));
        if (r5.contains(round) || (r6.contains(round) && early)) {
            return 5;
        }
        if (r6.contains(round) || (r7.contains(round) && early)) {
            return 6;
        }
        return r7.contains(round) || (r7.contains(round - 1) && early) ? 7 : 0;
    }

    private static List<Integer> rounds(Integer[] pattern) {
        return pattern == null ? new ArrayList<>() : Arrays.asList(pattern);
    }
}

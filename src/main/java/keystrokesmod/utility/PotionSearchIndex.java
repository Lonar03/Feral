package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import keystrokesmod.module.setting.impl.PotionListSetting;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.locale.I18n;

public final class PotionSearchIndex {
    private static final int MAX_SEARCH_RESULTS = 100;
    private static final int MAX_POTION_META = 16384;
    private static List<PotionSearchIndex.PotionEntry> allPotionEntries;
    private static Map<String, PotionSearchIndex.PotionEntry> entriesByKey;
    private static final Map<Integer, ItemStack> itemStacksByPotionId = new HashMap<>();

    private PotionSearchIndex() {}

    public static List<PotionSearchIndex.PotionEntry> search(String query, PotionListSetting setting) {
        ensurePotionList();
        String trimmedQuery = query == null ? "" : query.trim();
        String lowerQuery = trimmedQuery.toLowerCase(Locale.ROOT);
        List<PotionSearchIndex.ScoredPotionEntry> scoredEntries = new ArrayList<>();

        for (PotionSearchIndex.PotionEntry entry : allPotionEntries) {
            if (!setting.containsPotion(entry.key)) {
                int score = getScore(entry, lowerQuery);
                if (score > 0) {
                    scoredEntries.add(new PotionSearchIndex.ScoredPotionEntry(entry, score));
                }
            }
        }

        scoredEntries.sort(
                Comparator.<PotionSearchIndex.ScoredPotionEntry>comparingInt(scoredEntry -> -scoredEntry.score)
                        .thenComparing(scoredEntry -> scoredEntry.entry.displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(scoredEntry -> scoredEntry.entry.key));
        List<PotionSearchIndex.PotionEntry> results = new ArrayList<>(Math.min(100, scoredEntries.size()));

        for (int i = 0; i < Math.min(100, scoredEntries.size()); i++) {
            results.add(scoredEntries.get(i).entry);
        }

        return results;
    }

    public static String getDisplayName(String potionKey) {
        ensurePotionList();
        PotionSearchIndex.PotionEntry entry = entriesByKey.get(potionKey);
        return entry != null ? entry.displayName : potionKey;
    }

    public static ItemStack getItemStack(String potionKey) {
        ensurePotionList();
        PotionSearchIndex.PotionEntry entry = entriesByKey.get(potionKey);
        if (entry == null) {
            return null;
        } else if (itemStacksByPotionId.containsKey(entry.potionId)) {
            ItemStack cachedStack = itemStacksByPotionId.get(entry.potionId);
            return cachedStack != null ? cachedStack.copy() : null;
        } else {
            ItemStack resolvedStack = resolvePotionItemStack(entry.potionId);
            itemStacksByPotionId.put(entry.potionId, resolvedStack);
            return resolvedStack != null ? resolvedStack.copy() : null;
        }
    }

    private static int getScore(PotionSearchIndex.PotionEntry entry, String lowerQuery) {
        if (lowerQuery.isEmpty()) {
            return 1;
        }

        String displayName = entry.displayName.toLowerCase(Locale.ROOT);
        String key = entry.key.toLowerCase(Locale.ROOT);
        String simpleKey = getSimpleKey(entry.key).toLowerCase(Locale.ROOT);
        if (displayName.equals(lowerQuery)) {
            return 1000;
        }

        if (simpleKey.equals(lowerQuery)) {
            return 900;
        }

        if (displayName.startsWith(lowerQuery)) {
            return 800;
        }

        if (simpleKey.startsWith(lowerQuery)) {
            return 700;
        }

        for (String token : displayName.split("\\s+")) {
            if (token.startsWith(lowerQuery)) {
                return 600;
            }
        }

        if (displayName.contains(lowerQuery)) {
            return 500;
        } else {
            return !simpleKey.contains(lowerQuery) && !key.contains(lowerQuery) ? 0 : 400;
        }
    }

    private static String getSimpleKey(String potionKey) {
        if (potionKey != null && !potionKey.isEmpty()) {
            int lastDotIndex = potionKey.lastIndexOf(46);
            return lastDotIndex >= 0 && lastDotIndex < potionKey.length() - 1
                    ? potionKey.substring(lastDotIndex + 1)
                    : potionKey;
        } else {
            return "";
        }
    }

    private static void ensurePotionList() {
        if (allPotionEntries == null || entriesByKey == null) {
            Map<String, PotionSearchIndex.PotionEntry> keyedEntries = new LinkedHashMap<>();

            for (int potionId = 0; potionId < StatusEffect.BY_ID.length; potionId++) {
                StatusEffect potion = StatusEffect.BY_ID[potionId];
                if (potion != null
                        && potion.getTranslationKey() != null
                        && !potion.getTranslationKey().isEmpty()) {
                    String potionKey = potion.getTranslationKey();
                    if (!keyedEntries.containsKey(potionKey)) {
                        String displayName = I18n.translate(potionKey);
                        if (displayName == null || displayName.isEmpty() || displayName.equals(potionKey)) {
                            displayName = getSimpleKey(potionKey);
                        }

                        keyedEntries.put(
                                potionKey, new PotionSearchIndex.PotionEntry(potionId, potionKey, displayName));
                    }
                }
            }

            ArrayList<PotionSearchIndex.PotionEntry> sortedEntries = new ArrayList<>(keyedEntries.values());
            sortedEntries.sort(Comparator.<PotionSearchIndex.PotionEntry, String>comparing(
                            entry -> entry.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.key));
            allPotionEntries = Collections.unmodifiableList(sortedEntries);
            entriesByKey = keyedEntries;
        }
    }

    private static ItemStack resolvePotionItemStack(int potionId) {
        if (!(Items.POTION instanceof PotionItem)) {
            return null;
        }

        PotionItem potionItem = Items.POTION;
        ItemStack bestStack = null;
        int bestScore = Integer.MIN_VALUE;

        for (int metadata = 0; metadata <= 16384; metadata++) {
            List<StatusEffectInstance> effects = potionItem.getPotionEffects(metadata);
            if (effects != null && !effects.isEmpty()) {
                int score = 0;
                boolean matchesPotion = false;

                for (StatusEffectInstance listedEffect : effects) {
                    if (listedEffect.getId() == potionId) {
                        matchesPotion = true;
                        score += 100;
                        if (listedEffect.getAmplifier() == 0) {
                            score += 10;
                        }
                    } else {
                        score -= 20;
                    }
                }

                if (matchesPotion) {
                    if (effects.size() == 1) {
                        score += 50;
                    }

                    if (!PotionItem.isSplashPotion(metadata)) {
                        score += 25;
                    }

                    if (score > bestScore) {
                        bestScore = score;
                        bestStack = new ItemStack(Items.POTION, 1, metadata);
                    }
                }
            }
        }

        return bestStack != null ? bestStack : new ItemStack(Items.POTION);
    }

    public static final class PotionEntry {
        public final int potionId;
        public final String key;
        public final String displayName;

        private PotionEntry(int potionId, String key, String displayName) {
            this.potionId = potionId;
            this.key = key;
            this.displayName = displayName != null ? displayName : "";
        }
    }

    private static final class ScoredPotionEntry {
        private final PotionSearchIndex.PotionEntry entry;
        private final int score;

        private ScoredPotionEntry(PotionSearchIndex.PotionEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }
}

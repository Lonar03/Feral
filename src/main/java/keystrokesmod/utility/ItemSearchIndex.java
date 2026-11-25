package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import keystrokesmod.module.setting.impl.ItemListSetting;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.resource.Identifier;

public final class ItemSearchIndex {
    private static final int MAX_SEARCH_RESULTS = 100;
    private static List<ItemSearchIndex.ItemEntry> allItemEntries;
    private static Map<String, List<ItemSearchIndex.ItemEntry>> variantsByRegistryId;

    private ItemSearchIndex() {}

    public static List<ItemSearchIndex.GroupedItemResult> searchGrouped(String query, ItemListSetting setting) {
        ensureItemList();
        if (query != null && !(query = query.trim()).isEmpty()) {
            String lowerQuery = query.toLowerCase();
            List<ItemSearchIndex.ScoredItem> scored = new ArrayList<>();

            for (ItemSearchIndex.ItemEntry entry : allItemEntries) {
                if (!isCoveredBySetting(entry, setting)) {
                    String displayNameLower = entry.displayName.toLowerCase();
                    String searchId = getSearchId(entry.storageId).toLowerCase();
                    int score = 0;
                    if (displayNameLower.equals(lowerQuery)) {
                        score = 1000;
                    } else if (searchId.equals(lowerQuery)) {
                        score = 900;
                    } else if (displayNameLower.startsWith(lowerQuery)) {
                        score = 800;
                    } else if (searchId.startsWith(lowerQuery)) {
                        score = 700;
                    } else {
                        String[] tokens = displayNameLower.split("\\s+");

                        for (String token : tokens) {
                            if (token.startsWith(lowerQuery)) {
                                score = 600;
                                break;
                            }
                        }

                        if (score == 0 && displayNameLower.contains(lowerQuery)) {
                            score = 500;
                        } else if (score == 0 && searchId.contains(lowerQuery)) {
                            score = 400;
                        }
                    }

                    if (score > 0) {
                        scored.add(new ItemSearchIndex.ScoredItem(entry, score));
                    }
                }
            }

            scored.sort(Comparator.<ItemSearchIndex.ScoredItem>comparingInt(scoredItemx -> -scoredItemx.score)
                    .thenComparing(scoredItemx -> scoredItemx.entry.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(scoredItemx -> scoredItemx.entry.storageId));
            Map<String, Integer> bestScoreByRegistry = new HashMap<>();

            for (ItemSearchIndex.ScoredItem scoredItem : scored) {
                String registryId = getRegistryId(scoredItem.entry.storageId);
                if (registryId != null) {
                    Integer currentBest = bestScoreByRegistry.get(registryId);
                    if (currentBest == null || currentBest < scoredItem.score) {
                        bestScoreByRegistry.put(registryId, scoredItem.score);
                    }
                }
            }

            List<ItemSearchIndex.GroupedItemResult> results = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (ItemSearchIndex.ScoredItem scoredItem : scored) {
                String registryId = getRegistryId(scoredItem.entry.storageId);
                if (registryId != null && seen.add(registryId)) {
                    List<ItemSearchIndex.ItemEntry> variants = getVariants(registryId);
                    if (!variants.isEmpty()) {
                        int groupScore = bestScoreByRegistry.containsKey(registryId)
                                ? bestScoreByRegistry.get(registryId)
                                : scoredItem.score;
                        results.add(new ItemSearchIndex.GroupedItemResult(
                                registryId,
                                variants,
                                groupScore,
                                getGroupDisplayName(registryId, variants),
                                getAllSelectionStorageId(registryId)));
                        if (results.size() >= 100) {
                            break;
                        }
                    }
                }
            }

            results.sort(Comparator.<ItemSearchIndex.GroupedItemResult>comparingInt(
                            groupedItemResult -> -groupedItemResult.score)
                    .thenComparing(
                            groupedItemResult -> groupedItemResult.getGroupDisplayName(),
                            String.CASE_INSENSITIVE_ORDER));
            return results;
        } else {
            return Collections.emptyList();
        }
    }

    public static List<ItemSearchIndex.ItemEntry> getVariants(String registryId) {
        ensureItemList();
        if (variantsByRegistryId == null) {
            return Collections.emptyList();
        }

        List<ItemSearchIndex.ItemEntry> variants = variantsByRegistryId.get(registryId);
        return variants != null ? variants : Collections.emptyList();
    }

    public static String getStorageId(ItemStack stack) {
        if (stack != null && stack.getItem() != null) {
            String registryId = getRegistryId(stack.getItem());
            if (registryId == null) {
                return null;
            } else if (shouldStoreMeta(stack.getItem())) {
                int meta = stack.getMetadata();
                return meta != 0 ? registryId + ":" + meta : registryId;
            } else {
                return registryId;
            }
        } else {
            return null;
        }
    }

    public static String getRegistryId(Item item) {
        return item != null && Item.REGISTRY.getKey(item) != null
                ? ((Identifier) Item.REGISTRY.getKey(item)).toString()
                : null;
    }

    public static boolean hasMultipleVariants(Item item) {
        String registryId = getRegistryId(item);
        return registryId != null && getVariants(registryId).size() > 1;
    }

    public static boolean isWildcard(String storageId) {
        return storageId != null && storageId.endsWith(":*");
    }

    public static boolean isGroupedSelection(String storageId) {
        return ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId) != null || isWildcard(storageId);
    }

    public static String getAllSelectionStorageId(String groupId) {
        return ItemSearchIndex.SyntheticItemCategory.fromStorageId(groupId) != null ? groupId : groupId + ":*";
    }

    public static List<ItemSearchIndex.ItemEntry> getSelectionVariants(String storageId) {
        if (storageId != null && !storageId.isEmpty()) {
            ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                    ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
            if (syntheticCategory != null) {
                return getVariants(storageId);
            } else {
                return isWildcard(storageId) ? getVariants(getRegistryId(storageId)) : Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    public static boolean matches(List<String> storageIds, ItemStack stack) {
        if (storageIds != null && !storageIds.isEmpty()) {
            for (String storageId : storageIds) {
                if (matches(storageId, stack)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static boolean matches(String storageId, ItemStack stack) {
        if (storageId != null && !storageId.isEmpty()) {
            ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                    ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
            if (syntheticCategory != null) {
                return syntheticCategory.matches(stack);
            }

            if (stack != null && stack.getItem() != null) {
                String stackStorageId = getStorageId(stack);
                if (stackStorageId == null) {
                    return false;
                }

                if (storageId.equals(stackStorageId)) {
                    return true;
                }

                String registryId = getRegistryId(stackStorageId);
                return registryId != null && storageId.equals(registryId + ":*");
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static double getMatchQuality(String storageId, ItemStack stack) {
        if (!matches(storageId, stack)) {
            return Double.NEGATIVE_INFINITY;
        }

        ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory == null) {
            return 0.0;
        }

        switch (syntheticCategory) {
            case SWORD:
                return ItemSortScoring.getMeleeSelectionScore(stack);
            case BOW:
                return ItemSortScoring.getBowSelectionScore(stack);
            case TOOL:
                return ItemSortScoring.getToolSelectionScore(stack);
            case AXE:
                return ItemSortScoring.getAxeScore(stack);
            case PICKAXE:
                return ItemSortScoring.getPickaxeScore(stack);
            case SHOVEL:
                return ItemSortScoring.getShovelScore(stack);
            case HOE:
                return ItemSortScoring.getHoeScore(stack);
            case SHEARS:
                return ItemSortScoring.getShearsScore(stack);
            default:
                return 0.0;
        }
    }

    public static String getRegistryId(String storageId) {
        if (storageId == null || storageId.isEmpty()) {
            return null;
        } else if (ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId) != null) {
            return storageId;
        } else if (storageId.endsWith(":*")) {
            return storageId.substring(0, storageId.length() - 2);
        } else {
            String[] parts = storageId.split(":");
            if (parts.length >= 3) {
                return parts[0] + ":" + parts[1];
            } else {
                return parts.length == 2 ? storageId : null;
            }
        }
    }

    public static ItemStack getItemStack(String storageId) {
        ensureItemList();
        if (storageId != null && !storageId.isEmpty()) {
            ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                    ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
            if (syntheticCategory != null) {
                return syntheticCategory.previewStack != null ? syntheticCategory.previewStack.copy() : null;
            }

            if (isWildcard(storageId)) {
                List<ItemSearchIndex.ItemEntry> variants = getVariants(getRegistryId(storageId));
                return variants.isEmpty() ? null : variants.get(0).toItemStack();
            }

            String registryId = getRegistryId(storageId);
            if (registryId == null) {
                return null;
            }

            for (ItemSearchIndex.ItemEntry variant : getVariants(registryId)) {
                if (storageId.equals(variant.storageId)) {
                    return variant.toItemStack();
                }
            }

            Item item = getItemForName(registryId);
            return item == null ? null : new ItemStack(item, 1, getMetaFromStorageId(storageId));
        } else {
            return null;
        }
    }

    public static String getDisplayName(String storageId) {
        ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            List<ItemSearchIndex.ItemEntry> variants = getVariants(storageId);
            return variants.size() > 1 ? syntheticCategory.displayName + " (All)" : syntheticCategory.displayName;
        } else if (isWildcard(storageId)) {
            List<ItemSearchIndex.ItemEntry> variants = getVariants(getRegistryId(storageId));
            return !variants.isEmpty() ? getGroupDisplayName(getRegistryId(storageId), variants) + " (All)" : storageId;
        } else {
            ItemStack stack = getItemStack(storageId);
            return stack != null ? stack.getHoverName() : storageId;
        }
    }

    private static void ensureItemList() {
        if (allItemEntries == null) {
            List<ItemSearchIndex.ItemEntry> regularEntries = new ArrayList<>();
            Map<String, List<ItemSearchIndex.ItemEntry>> variantMap = new HashMap<>();

            for (Object obj : Item.REGISTRY) {
                Item item = (Item) obj;
                String registryId = getRegistryId(item);
                if (registryId != null) {
                    List<ItemStack> subItems = new ArrayList<>();
                    collectSubItems(item, subItems);
                    if (subItems.isEmpty()) {
                        subItems.add(new ItemStack(item, 1, 0));
                    }

                    Map<String, ItemSearchIndex.ItemEntry> dedupedEntries = new LinkedHashMap<>();

                    for (ItemStack stack : subItems) {
                        if (stack != null && stack.getItem() != null) {
                            String storageId = getStorageId(stack);
                            if (storageId != null && !dedupedEntries.containsKey(storageId)) {
                                String displayName = stack.getHoverName();
                                if (displayName != null && !displayName.isEmpty()) {
                                    int meta = shouldStoreMeta(item) ? stack.getMetadata() : 0;
                                    dedupedEntries.put(
                                            storageId,
                                            new ItemSearchIndex.ItemEntry(item, meta, displayName, storageId, stack));
                                }
                            }
                        }
                    }

                    if (!dedupedEntries.isEmpty()) {
                        List<ItemSearchIndex.ItemEntry> group = new ArrayList<>(dedupedEntries.values());
                        group.sort(Comparator.comparingInt(itemEntry -> itemEntry.meta));
                        regularEntries.addAll(group);
                        variantMap.put(registryId, group);
                    }
                }
            }

            allItemEntries = new ArrayList<>(regularEntries);

            for (ItemSearchIndex.SyntheticItemCategory category : ItemSearchIndex.SyntheticItemCategory.values()) {
                ItemSearchIndex.ItemEntry searchEntry = category.toSearchEntry();
                allItemEntries.add(searchEntry);
                List<ItemSearchIndex.ItemEntry> syntheticVariants = new ArrayList<>();

                for (ItemSearchIndex.ItemEntry entry : regularEntries) {
                    if (category.matches(entry.toItemStack())) {
                        syntheticVariants.add(entry);
                    }
                }

                syntheticVariants.sort(
                        Comparator.comparing(itemEntry -> itemEntry.displayName, String.CASE_INSENSITIVE_ORDER));
                if (syntheticVariants.isEmpty()) {
                    syntheticVariants.add(searchEntry);
                }

                variantMap.put(category.storageId, syntheticVariants);
            }

            allItemEntries.sort(Comparator.<ItemSearchIndex.ItemEntry, String>comparing(
                            itemEntry -> itemEntry.displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(itemEntry -> itemEntry.storageId));
            variantsByRegistryId = variantMap;
        }
    }

    private static boolean isCoveredBySetting(ItemSearchIndex.ItemEntry entry, ItemListSetting setting) {
        if (setting == null) {
            return false;
        }

        ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                ItemSearchIndex.SyntheticItemCategory.fromStorageId(entry.storageId);
        return syntheticCategory != null
                ? syntheticCategory.isCoveredBy(setting.getItems())
                : setting.matches(entry.toItemStack());
    }

    private static String getSearchId(String storageId) {
        ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                ItemSearchIndex.SyntheticItemCategory.fromStorageId(storageId);
        if (syntheticCategory != null) {
            return syntheticCategory.displayName;
        }

        String registryId = getRegistryId(storageId);
        if (registryId == null) {
            return storageId;
        }

        int separator = registryId.indexOf(58);
        return separator >= 0 ? registryId.substring(separator + 1) : registryId;
    }

    private static String getGroupDisplayName(String registryId, List<ItemSearchIndex.ItemEntry> variants) {
        ItemSearchIndex.SyntheticItemCategory syntheticCategory =
                ItemSearchIndex.SyntheticItemCategory.fromStorageId(registryId);
        if (syntheticCategory != null) {
            return syntheticCategory.displayName;
        } else {
            return !variants.isEmpty() ? variants.get(0).displayName : registryId;
        }
    }

    private static void collectSubItems(Item item, List<ItemStack> subItems) {
        try {
            item.addToCreativeMenu(item, CreativeModeTab.SEARCH, subItems);
        } catch (Exception var5) {
        }

        if (subItems.isEmpty()) {
            CreativeModeTab creativeTab = item.getCreativeModeTab();
            if (creativeTab != null) {
                try {
                    item.addToCreativeMenu(item, creativeTab, subItems);
                } catch (Exception var4) {
                }
            }
        }
    }

    private static int getMetaFromStorageId(String storageId) {
        if (storageId == null) {
            return 0;
        }

        String[] parts = storageId.split(":");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    private static Item getItemForName(String registryId) {
        try {
            return (Item) Item.REGISTRY.get(new Identifier(registryId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldStoreMeta(Item item) {
        return item != null && item.hasCustomData() && !item.isDamageable();
    }

    private static boolean isToolLike(Item item) {
        return item instanceof ToolItem || item instanceof HoeItem || item instanceof ShearsItem;
    }

    public static final class GroupedItemResult {
        public final String registryId;
        public final List<ItemSearchIndex.ItemEntry> variants;
        public final int score;
        private final String groupDisplayName;
        private final String allSelectionStorageId;

        public GroupedItemResult(
                String registryId,
                List<ItemSearchIndex.ItemEntry> variants,
                int score,
                String groupDisplayName,
                String allSelectionStorageId) {
            this.registryId = registryId;
            this.variants = variants;
            this.score = score;
            this.groupDisplayName = groupDisplayName;
            this.allSelectionStorageId = allSelectionStorageId;
        }

        public boolean isSingleVariant() {
            return this.variants.size() <= 1;
        }

        public String getGroupLabel() {
            return this.groupDisplayName + " (" + this.variants.size() + ")";
        }

        public String getGroupDisplayName() {
            return this.groupDisplayName;
        }

        public String getAllSelectionStorageId() {
            return this.allSelectionStorageId;
        }

        public ItemStack getCyclingIcon() {
            int idx = (int) (System.currentTimeMillis() / 1000L % this.variants.size());
            return this.variants.get(idx).toItemStack();
        }
    }

    public static final class ItemEntry {
        public final Item item;
        public final int meta;
        public final String displayName;
        public final String storageId;
        private final ItemStack previewStack;

        ItemEntry(Item item, int meta, String displayName, String storageId) {
            this(item, meta, displayName, storageId, item != null ? new ItemStack(item, 1, meta) : null);
        }

        ItemEntry(Item item, int meta, String displayName, String storageId, ItemStack previewStack) {
            this.item = item;
            this.meta = meta;
            this.displayName = displayName != null ? displayName : "";
            this.storageId = storageId;
            this.previewStack = previewStack != null ? previewStack.copy() : null;
        }

        public ItemStack toItemStack() {
            return this.previewStack != null ? this.previewStack.copy() : null;
        }
    }

    private static final class ScoredItem {
        final ItemSearchIndex.ItemEntry entry;
        final int score;

        ScoredItem(ItemSearchIndex.ItemEntry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private enum SyntheticItemCategory {
        FIST("@fist", "Fist", null) {
            @Override
            boolean matches(ItemStack stack) {
                return stack == null;
            }
        },
        SWORD("@category:sword", "Sword", new ItemStack(Items.DIAMOND_SWORD)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof SwordItem;
            }
        },
        BOW("@category:bow", "Bow", new ItemStack(Items.BOW)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof BowItem;
            }
        },
        TOOL("@category:tool", "Tool", new ItemStack(Items.DIAMOND_PICKAXE)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && ItemSearchIndex.isToolLike(stack.getItem());
            }
        },
        AXE("@category:axe", "Axe", new ItemStack(Items.DIAMOND_AXE)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof AxeItem;
            }
        },
        PICKAXE("@category:pickaxe", "Pickaxe", new ItemStack(Items.DIAMOND_PICKAXE)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof PickaxeItem;
            }
        },
        SHOVEL("@category:shovel", "Shovel", new ItemStack(Items.DIAMOND_SHOVEL)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ShovelItem;
            }
        },
        HOE("@category:hoe", "Hoe", new ItemStack(Items.DIAMOND_HOE)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof HoeItem;
            }
        },
        SHEARS("@category:shears", "Shears", new ItemStack(Items.SHEARS)) {
            @Override
            boolean matches(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ShearsItem;
            }
        };

        private final String storageId;
        private final String displayName;
        private final ItemStack previewStack;

        SyntheticItemCategory(String storageId, String displayName, ItemStack previewStack) {
            this.storageId = storageId;
            this.displayName = displayName;
            this.previewStack = previewStack;
        }

        abstract boolean matches(ItemStack var1);

        ItemSearchIndex.ItemEntry toSearchEntry() {
            return new ItemSearchIndex.ItemEntry(null, 0, this.displayName, this.storageId, this.previewStack);
        }

        boolean isCoveredBy(List<String> selectedItems) {
            if (selectedItems == null || selectedItems.isEmpty()) {
                return false;
            } else {
                return selectedItems.contains(this.storageId)
                        ? true
                        : this != TOOL && this.isToolSubtype() && selectedItems.contains(TOOL.storageId);
            }
        }

        private boolean isToolSubtype() {
            return this == AXE || this == PICKAXE || this == SHOVEL || this == HOE || this == SHEARS;
        }

        static ItemSearchIndex.SyntheticItemCategory fromStorageId(String storageId) {
            if (storageId == null) {
                return null;
            }

            for (ItemSearchIndex.SyntheticItemCategory category : values()) {
                if (category.storageId.equals(storageId)) {
                    return category;
                }
            }

            return null;
        }
    }
}

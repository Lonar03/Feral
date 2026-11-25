// Custom skin and "Hide name" ported from Meowtils' AccountHider (wtf.tatp.meowtils) via Meower, modified for Feral
package keystrokesmod.module.impl.other;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.SkinManager;
import keystrokesmod.utility.TextUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public class AccountHider extends Module {
    private static final long PROFILE_CACHE_REFRESH_MS = 250L;
    private static final int MIN_ALIAS_NUMBER = 1;
    private static final String DEFAULT_FAKE_NAME = "You";
    private static final String DEFAULT_HIDE_ALL_PREFIX = "Player";
    public static String fakeName = "You";
    public static ButtonSetting hideAllNames;
    public static ButtonSetting hideName;
    public static ButtonSetting customSkin;
    public static SliderSetting skin;
    public static SliderSetting arms;
    public static TextSetting fakeNameSetting;
    public static TextSetting hideAllPrefixSetting;
    private static final LinkedHashSet<String> detectedSelfNames = new LinkedHashSet<>();
    private static String formattedFakeName = "You";
    private static String formattedHideAllPrefix = "Player";
    private static String cachedSelfKey;
    private static boolean cachedHideAllState;
    private static boolean cachedHideNameState = true;
    private static List<String> cachedSelfNames = Collections.emptyList();
    private static Map<String, List<String>> cachedVisibleNamesByKey = Collections.emptyMap();
    private static List<Entry<String, String>> cachedOtherNameReplacements = Collections.emptyList();
    private static Map<String, Integer> cachedAliasNumbers = Collections.emptyMap();
    private static long lastProfileCacheRefresh;
    private static int transformVersion;
    private static int lastTransformVersion = -1;
    private static String lastInput;
    private static String lastOutput;

    public AccountHider() {
        super("Account Hider", Module.category.other);
        this.liteModule = true;
        this.registerSetting(hideName = new ButtonSetting("Hide name", true));
        this.registerSetting(
                fakeNameSetting = new TextSetting("Fake name", fakeName, "Type a fake name...", 48) {
                    @Override
                    public void setText(String text) {
                        super.setText(AccountHider.normalizeFakeName(text));
                        AccountHider.applyFakeName(this.getText());
                    }

                    @Override
                    public void loadProfile(JsonObject data) {
                        if (data != null) {
                            String profileKey = this.getProfileKey();
                            if (data.has(profileKey) && data.get(profileKey).isJsonPrimitive()) {
                                this.setText(data.getAsJsonPrimitive(profileKey).getAsString());
                            }
                        }
                    }
                });
        this.registerSetting(hideAllNames = new ButtonSetting("Hide all names", false));
        this.registerSetting(
                hideAllPrefixSetting = new TextSetting("Hide-all prefix", "Player", "Type a prefix...", 24) {
                    @Override
                    public void setText(String text) {
                        super.setText(AccountHider.normalizeHideAllPrefix(text));
                        AccountHider.applyHideAllPrefix(this.getText());
                    }

                    @Override
                    public void loadProfile(JsonObject data) {
                        if (data != null) {
                            String profileKey = this.getProfileKey();
                            if (data.has(profileKey) && data.get(profileKey).isJsonPrimitive()) {
                                this.setText(data.getAsJsonPrimitive(profileKey).getAsString());
                            }
                        }
                    }
                });
        hideAllPrefixSetting.visible = false;

        SkinManager.reloadSkins();
        GroupSetting skinGroup = new GroupSetting("Custom skin");
        this.registerSetting(skinGroup);
        this.registerSetting(customSkin = new ButtonSetting(skinGroup, "Enabled", false));
        this.registerSetting(skin = new SliderSetting(skinGroup, "Skin", 0, SkinManager.getSkinOptions()));
        this.registerSetting(arms = new SliderSetting(skinGroup, "Arms", 0, new String[] {"Default", "Slim"}));
        this.registerSetting(new ButtonSetting("Open skins folder", AccountHider::openSkinsFolder));
        this.registerSetting(new ButtonSetting("Load skins", AccountHider::loadSkins));
    }

    private static void openSkinsFolder() {
        if (!SkinManager.openSkinDirectory()) {
            Utils.sendMessage("&cCould not open skins folder: &7"
                    + SkinManager.getSkinDirectory().getAbsolutePath());
        }
    }

    private static void loadSkins() {
        SkinManager.ReloadResult result = SkinManager.reloadSkins();
        skin.setOptions(SkinManager.getSkinOptions());
        String message =
                "&7Loaded &b" + result.getLoaded() + " &7custom skin" + (result.getLoaded() == 1 ? "" : "s") + ".";
        Utils.sendMessage(result.getFailed() > 0 ? message + " &c" + result.getFailed() + " failed." : message);
    }

    /** The custom skin for the local player, or null to keep the real one. */
    public static Identifier getCustomSkin() {
        return ModuleManager.accountHider != null && ModuleManager.accountHider.isEnabled() && customSkin.isToggled()
                ? SkinManager.getSkin((int) skin.getInput())
                : null;
    }

    public static String getCustomSkinType() {
        return arms.getInput() == 1.0 ? "slim" : "default";
    }

    private static boolean hidesOwnName() {
        return hideName == null || hideName.isToggled();
    }

    public static String getFakeName(String input) {
        if (!shouldProcessText(input)) {
            return input;
        }

        refreshCaches();
        if (lastTransformVersion == transformVersion && input.equals(lastInput)) {
            return lastOutput;
        }

        String replaced = hidesOwnName() ? replaceNames(input, cachedSelfNames, formattedFakeName) : input;
        if (cachedHideAllState) {
            for (Entry<String, String> entry : cachedOtherNameReplacements) {
                replaced = TextUtils.replaceKeepingFormatting(replaced, entry.getKey(), entry.getValue());
            }
        }

        lastInput = input;
        lastOutput = replaced;
        lastTransformVersion = transformVersion;
        return replaced;
    }

    public static Text getPlayerDisplayName(PlayerEntity player, Text original) {
        if (original != null && shouldProcessText(original.getFormattedString())) {
            refreshCaches();
            String originalText = original.getFormattedString();
            String replaced = getDisplayTextForPlayer(player, originalText);
            if (Objects.equals(originalText, replaced)) {
                return original;
            }

            LiteralText component = new LiteralText(replaced);
            component.setStyle(original.getStyle().deepCopy());
            return component;
        } else {
            return original;
        }
    }

    public static String getTabName(PlayerInfo playerInfo, String original) {
        if (!shouldProcessText(original)) {
            return original;
        }

        refreshCaches();
        String key = getIdentityKey(playerInfo);
        if (key == null) {
            return original;
        }

        if (isSelfKey(key)) {
            return hidesOwnName()
                    ? replaceNames(original, getTabVisibleNames(playerInfo, key), formattedFakeName)
                    : original;
        }

        if (!cachedHideAllState) {
            return original;
        }

        String replacement = getFormattedAlias(key);
        return replacement == null
                ? original
                : replaceNames(original, getTabVisibleNames(playerInfo, key), replacement);
    }

    public static void setFakeName(String name) {
        String normalized = normalizeFakeName(name);
        if (fakeNameSetting != null && !normalized.equals(fakeNameSetting.getText())) {
            fakeNameSetting.setText(normalized);
        } else {
            applyFakeName(normalized);
        }
    }

    private static String getDisplayTextForPlayer(PlayerEntity player, String original) {
        if (player == null) {
            return original;
        }

        String key = getIdentityKey(player);
        if (isSelfPlayer(player, key)) {
            return hidesOwnName()
                    ? replaceNames(original, getEntityVisibleNames(player, key), formattedFakeName)
                    : original;
        }

        if (!cachedHideAllState) {
            return original;
        }

        String replacement = getFormattedAlias(key);
        return replacement == null ? original : replaceNames(original, getEntityVisibleNames(player, key), replacement);
    }

    private static void refreshCaches() {
        if (mc.player != null) {
            if (cachedHideNameState != hidesOwnName()) {
                cachedHideNameState = hidesOwnName();
                markCacheDirty();
            }

            boolean hideEveryone = hideAllNames != null && hideAllNames.isToggled();
            if (cachedHideAllState != hideEveryone) {
                cachedHideAllState = hideEveryone;
                clearHideAllCaches();
                markCacheDirty();
            }

            String selfKey = getSelfKey();
            if (!Objects.equals(cachedSelfKey, selfKey)) {
                cachedSelfKey = selfKey;
                markCacheDirty();
            }

            List<String> selfNames = buildSelfNames();
            if (!selfNames.equals(cachedSelfNames)) {
                cachedSelfNames = selfNames;
                markCacheDirty();
            }

            if (hideEveryone) {
                long now = System.currentTimeMillis();
                if (now - lastProfileCacheRefresh >= 250L) {
                    lastProfileCacheRefresh = now;
                    AccountHider.HideAllCache cache = buildHideAllCache(selfNames);
                    if (!cache.visibleNamesByKey.equals(cachedVisibleNamesByKey)
                            || !cache.aliasNumbers.equals(cachedAliasNumbers)
                            || !cache.replacements.equals(cachedOtherNameReplacements)) {
                        cachedVisibleNamesByKey = cache.visibleNamesByKey;
                        cachedAliasNumbers = cache.aliasNumbers;
                        cachedOtherNameReplacements = cache.replacements;
                        markCacheDirty();
                    }
                }
            }
        }
    }

    private static AccountHider.HideAllCache buildHideAllCache(List<String> selfNames) {
        LinkedHashMap<String, LinkedHashSet<String>> targets = new LinkedHashMap<>();
        HashSet<String> protectedNames = new HashSet<>();

        for (String selfName : selfNames) {
            protectedNames.add(normalizeName(selfName));
        }

        for (Entry<String, List<String>> entry : cachedVisibleNamesByKey.entrySet()) {
            for (String visibleName : entry.getValue()) {
                addTargetName(targets, protectedNames, entry.getKey(), visibleName);
            }
        }

        collectTargetsFromTab(targets, protectedNames);
        collectTargetsFromWorld(targets, protectedNames);
        if (targets.isEmpty()) {
            return AccountHider.HideAllCache.EMPTY;
        }

        LinkedHashMap<String, List<String>> visibleNamesByKey = new LinkedHashMap<>();

        for (Entry<String, LinkedHashSet<String>> entry : targets.entrySet()) {
            visibleNamesByKey.put(entry.getKey(), sortNames(entry.getValue()));
        }

        LinkedHashMap<String, Integer> aliasNumbers = assignAliasNumbers(visibleNamesByKey.keySet());
        List<Entry<String, String>> replacements = new ArrayList<>();

        for (Entry<String, List<String>> entry : visibleNamesByKey.entrySet()) {
            Integer aliasNumber = aliasNumbers.get(entry.getKey());
            if (aliasNumber != null) {
                String replacement = formattedHideAllPrefix + aliasNumber;

                for (String visibleName : entry.getValue()) {
                    replacements.add(new SimpleEntry<>(visibleName, replacement));
                }
            }
        }

        replacements.sort((first, second) -> {
            int lengthCompare =
                    Integer.compare(second.getKey().length(), first.getKey().length());
            return lengthCompare != 0 ? lengthCompare : first.getKey().compareToIgnoreCase(second.getKey());
        });
        return new AccountHider.HideAllCache(visibleNamesByKey, aliasNumbers, replacements);
    }

    private static void collectTargetsFromTab(Map<String, LinkedHashSet<String>> targets, Set<String> protectedNames) {
        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getOnlinePlayers() != null) {
            PlayerInfo self = getSelfPlayerInfo();

            for (PlayerInfo playerInfo : mc.getNetworkHandler().getOnlinePlayers()) {
                if (playerInfo != null && playerInfo != self) {
                    addTargetName(targets, protectedNames, getIdentityKey(playerInfo), getProfileName(playerInfo));
                }
            }
        }
    }

    private static void collectTargetsFromWorld(
            Map<String, LinkedHashSet<String>> targets, Set<String> protectedNames) {
        if (mc.world != null && mc.world.players != null) {
            for (PlayerEntity player : mc.world.players) {
                if (player != null && player != mc.player) {
                    PlayerInfo playerInfo = getPlayerInfo(player);
                    String key = playerInfo != null
                            ? getIdentityKey(playerInfo)
                            : getIdentityKey(player.getUuid(), player.getName());
                    addTargetName(targets, protectedNames, key, player.getName());
                    if (playerInfo != null) {
                        addTargetName(targets, protectedNames, key, getProfileName(playerInfo));
                    }
                }
            }
        }
    }

    private static void addTargetName(
            Map<String, LinkedHashSet<String>> targets, Set<String> protectedNames, String key, String candidate) {
        String normalized = sanitizeName(candidate);
        if (key != null && !normalized.isEmpty() && !protectedNames.contains(normalizeName(normalized))) {
            LinkedHashSet<String> names = targets.computeIfAbsent(key, k -> new LinkedHashSet<>());
            names.add(normalized);
        }
    }

    private static LinkedHashMap<String, Integer> assignAliasNumbers(Set<String> keys) {
        LinkedHashMap<String, Integer> aliasNumbers = new LinkedHashMap<>();
        HashSet<Integer> usedAliasNumbers = new HashSet<>();

        for (String key : keys) {
            Integer existing = cachedAliasNumbers.get(key);
            if (existing != null && usedAliasNumbers.add(existing)) {
                aliasNumbers.put(key, existing);
            }
        }

        for (String key : keys) {
            if (!aliasNumbers.containsKey(key)) {
                int aliasNumber = 1;

                while (usedAliasNumbers.contains(aliasNumber)) {
                    aliasNumber++;
                }

                aliasNumbers.put(key, aliasNumber);
                usedAliasNumbers.add(aliasNumber);
            }
        }

        return aliasNumbers;
    }

    /** Every name the local player has shown up as this session (account name, tab profile name / nick). */
    public static List<String> getSelfNames() {
        return mc.player == null ? Collections.emptyList() : buildSelfNames();
    }

    private static List<String> buildSelfNames() {
        rememberSelfName(mc.player.getName());
        PlayerInfo selfPlayerInfo = getSelfPlayerInfo();
        if (selfPlayerInfo != null) {
            rememberSelfName(getProfileName(selfPlayerInfo));
        }

        return sortNames(detectedSelfNames);
    }

    private static boolean rememberSelfName(String candidate) {
        String normalized = sanitizeName(candidate);
        return !normalized.isEmpty() && detectedSelfNames.add(normalized);
    }

    private static List<String> getEntityVisibleNames(PlayerEntity player, String key) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addNames(names, cachedVisibleNamesByKey.get(key));
        addName(names, player.getName());
        PlayerInfo playerInfo = getPlayerInfo(player);
        if (playerInfo != null) {
            addName(names, getProfileName(playerInfo));
        }

        if (isSelfPlayer(player, key)) {
            addNames(names, cachedSelfNames);
        }

        return sortNames(names);
    }

    private static List<String> getTabVisibleNames(PlayerInfo playerInfo, String key) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addNames(names, cachedVisibleNamesByKey.get(key));
        addName(names, getProfileName(playerInfo));
        if (isSelfKey(key)) {
            addNames(names, cachedSelfNames);
        }

        return sortNames(names);
    }

    private static void addNames(Set<String> target, Iterable<String> names) {
        if (names != null) {
            for (String name : names) {
                addName(target, name);
            }
        }
    }

    private static void addName(Set<String> target, String name) {
        String normalized = sanitizeName(name);
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    private static List<String> sortNames(Iterable<String> names) {
        ArrayList<String> sorted = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                String normalized = sanitizeName(name);
                if (!normalized.isEmpty() && !sorted.contains(normalized)) {
                    sorted.add(normalized);
                }
            }
        }

        Collections.sort(sorted, (first, second) -> {
            int lengthCompare = Integer.compare(second.length(), first.length());
            return lengthCompare != 0 ? lengthCompare : first.compareToIgnoreCase(second);
        });
        return sorted;
    }

    private static String replaceNames(String input, Iterable<String> names, String replacement) {
        String replaced = TextUtils.replaceAllKeepingFormatting(input, names, replacement);
        return replaced == null ? input : replaced;
    }

    private static PlayerInfo getSelfPlayerInfo() {
        return mc.getNetworkHandler() != null && mc.player != null
                ? mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid())
                : null;
    }

    private static PlayerInfo getPlayerInfo(PlayerEntity player) {
        return mc.getNetworkHandler() != null && player != null
                ? mc.getNetworkHandler().getOnlinePlayer(player.getUuid())
                : null;
    }

    private static String getSelfKey() {
        PlayerInfo selfPlayerInfo = getSelfPlayerInfo();
        if (selfPlayerInfo != null) {
            return getIdentityKey(selfPlayerInfo);
        } else {
            return mc.player == null ? null : getIdentityKey(mc.player.getUuid(), mc.player.getName());
        }
    }

    private static boolean isSelfPlayer(PlayerEntity player, String key) {
        return player == mc.player || isSelfKey(key);
    }

    private static boolean isSelfKey(String key) {
        return key != null && key.equals(cachedSelfKey);
    }

    private static String getFormattedAlias(String key) {
        Integer aliasNumber = cachedAliasNumbers.get(key);
        return aliasNumber == null ? null : formattedHideAllPrefix + aliasNumber;
    }

    private static String getProfileName(PlayerInfo playerInfo) {
        GameProfile profile = playerInfo == null ? null : playerInfo.getProfile();
        return profile == null ? "" : profile.getName();
    }

    private static String getIdentityKey(PlayerInfo playerInfo) {
        GameProfile profile = playerInfo == null ? null : playerInfo.getProfile();
        return profile == null ? null : getIdentityKey(profile.getId(), profile.getName());
    }

    private static String getIdentityKey(PlayerEntity player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        if (playerInfo != null) {
            return getIdentityKey(playerInfo);
        } else {
            return player == null ? null : getIdentityKey(player.getUuid(), player.getName());
        }
    }

    private static String getIdentityKey(UUID uniqueId, String fallbackName) {
        if (uniqueId != null) {
            return uniqueId.toString();
        }

        String normalized = normalizeName(fallbackName);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeFakeName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.isEmpty() ? "You" : normalized;
    }

    private static String normalizeHideAllPrefix(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "Player" : normalized;
    }

    private static String sanitizeName(String value) {
        return Utils.stripColor(value == null ? "" : value).trim();
    }

    private static String normalizeName(String value) {
        return sanitizeName(value).toLowerCase(Locale.ROOT);
    }

    private static boolean shouldProcessText(String input) {
        return input != null && !input.isEmpty() && mc.player != null;
    }

    private static void applyFakeName(String name) {
        String normalized = normalizeFakeName(name);
        String nextFormattedFakeName = Utils.formatColor(normalized);
        if (!Objects.equals(fakeName, normalized) || !Objects.equals(formattedFakeName, nextFormattedFakeName)) {
            fakeName = normalized;
            formattedFakeName = nextFormattedFakeName;
            markCacheDirty();
        }
    }

    private static void applyHideAllPrefix(String value) {
        String normalized = normalizeHideAllPrefix(value);
        String nextFormattedHideAllPrefix = Utils.formatColor(normalized);
        if (!Objects.equals(formattedHideAllPrefix, nextFormattedHideAllPrefix)) {
            formattedHideAllPrefix = nextFormattedHideAllPrefix;
            markCacheDirty();
        }
    }

    private static void clearHideAllCaches() {
        cachedVisibleNamesByKey = Collections.emptyMap();
        cachedOtherNameReplacements = Collections.emptyList();
        cachedAliasNumbers = Collections.emptyMap();
        lastProfileCacheRefresh = 0L;
    }

    private static void markCacheDirty() {
        transformVersion++;
        lastTransformVersion = -1;
        lastInput = null;
        lastOutput = null;
        lastProfileCacheRefresh = 0L;
    }

    @Override
    public void guiUpdate() {
        hideAllPrefixSetting.setVisible(hideAllNames.isToggled(), this);
        skin.setVisible(customSkin.isToggled(), this);
        arms.setVisible(customSkin.isToggled(), this);
    }

    private static final class HideAllCache {
        private static final AccountHider.HideAllCache EMPTY =
                new AccountHider.HideAllCache(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        private final Map<String, List<String>> visibleNamesByKey;
        private final Map<String, Integer> aliasNumbers;
        private final List<Entry<String, String>> replacements;

        private HideAllCache(
                Map<String, List<String>> visibleNamesByKey,
                Map<String, Integer> aliasNumbers,
                List<Entry<String, String>> replacements) {
            this.visibleNamesByKey = visibleNamesByKey;
            this.aliasNumbers = aliasNumbers;
            this.replacements = replacements;
        }
    }
}

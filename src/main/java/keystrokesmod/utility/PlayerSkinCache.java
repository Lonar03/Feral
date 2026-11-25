package keystrokesmod.utility;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.net.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import keystrokesmod.Feral;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;

public class PlayerSkinCache {
    private static final long PROFILE_CACHE_TTL_MS = 1800000L;
    private static final Map<String, Identifier> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, UUID> UUIDS = new ConcurrentHashMap<>();
    private static final Map<String, PlayerSkinCache.CachedProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Set<String> LOOKUP_IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOOKUP_FAILED = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final GameProfileRepository PROFILE_REPOSITORY =
            new YggdrasilAuthenticationService(Proxy.NO_PROXY, UUID.randomUUID().toString()).createProfileRepository();
    private static final ExecutorService LOOKUP_EXECUTOR = Executors.newFixedThreadPool(3);

    private PlayerSkinCache() {}

    public static Identifier getSkin(String username, PlayerInfo playerInfo) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return DefaultSkinUtils.getDefaultSkin(PlayerEntity.getUuidForOffline("Steve"));
        }

        if (playerInfo != null && playerInfo.getProfile() != null) {
            GameProfile profile = playerInfo.getProfile();
            UUID uuid = profile.getId();
            if (uuid != null) {
                UUIDS.put(normalized, uuid);
            }

            if (profile.getName() != null && Feral.playerRelationsManager != null) {
                Feral.playerRelationsManager.refreshDisplayName(profile.getName());
            }

            Identifier location = playerInfo.getSkinTexture();
            if (location != null) {
                SKINS.put(normalized, location);
                return location;
            }
        }

        Identifier cached = SKINS.get(normalized);
        if (cached != null) {
            return cached;
        }

        if (!LOOKUP_FAILED.contains(normalized)) {
            requestSkin(normalized, username);
        }

        UUID fallbackUuid = UUIDS.get(normalized);
        if (fallbackUuid == null) {
            fallbackUuid = PlayerEntity.getUuidForOffline(username);
            UUIDS.put(normalized, fallbackUuid);
        }

        return DefaultSkinUtils.getDefaultSkin(fallbackUuid);
    }

    private static void requestSkin(String normalized, String username) {
        long now = System.currentTimeMillis();
        PlayerSkinCache.CachedProfile cachedProfile = PROFILES.get(normalized);
        if (cachedProfile != null && !cachedProfile.isExpired(now)) {
            if (cachedProfile.profile == null) {
                LOOKUP_FAILED.add(normalized);
            } else {
                resolveSkinFromProfile(normalized, cachedProfile.profile);
            }
        } else if (LOOKUP_IN_FLIGHT.add(normalized)) {
            LOOKUP_EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final GameProfile[] holder = new GameProfile[1];
                        PlayerSkinCache.PROFILE_REPOSITORY.findProfilesByNames(
                                new String[] {username}, Agent.MINECRAFT, new ProfileLookupCallback() {
                                    public void onProfileLookupSucceeded(GameProfile profile) {
                                        holder[0] = profile;
                                    }

                                    public void onProfileLookupFailed(GameProfile profile, Exception exception) {}
                                });
                        GameProfile profile = holder[0];
                        if (profile == null) {
                            PlayerSkinCache.cacheProfile(normalized, null);
                            PlayerSkinCache.LOOKUP_FAILED.add(normalized);
                            return;
                        }

                        Minecraft minecraft = Minecraft.getInstance();
                        GameProfile filledProfile =
                                minecraft.getSessionService().fillProfileProperties(profile, false);
                        if (filledProfile != null) {
                            PlayerSkinCache.cacheProfile(normalized, filledProfile);
                            PlayerSkinCache.resolveSkinFromProfile(normalized, filledProfile);
                            return;
                        }

                        PlayerSkinCache.cacheProfile(normalized, null);
                        PlayerSkinCache.LOOKUP_FAILED.add(normalized);
                    } catch (Exception ignored) {
                        PlayerSkinCache.cacheProfile(normalized, null);
                        PlayerSkinCache.LOOKUP_FAILED.add(normalized);
                        return;
                    } finally {
                        PlayerSkinCache.LOOKUP_IN_FLIGHT.remove(normalized);
                    }
                }
            });
        }
    }

    private static void resolveSkinFromProfile(String normalized, GameProfile profile) {
        if (profile == null) {
            LOOKUP_FAILED.add(normalized);
        } else {
            if (profile.getId() != null) {
                UUIDS.put(normalized, profile.getId());
            }

            if (profile.getName() != null && Feral.playerRelationsManager != null) {
                Feral.playerRelationsManager.refreshDisplayName(profile.getName());
            }

            Minecraft minecraft = Minecraft.getInstance();
            Map<Type, MinecraftProfileTexture> textures =
                    minecraft.getSkinManager().getTextures(profile);
            final MinecraftProfileTexture skinTexture = textures == null ? null : textures.get(Type.SKIN);
            if (skinTexture == null) {
                LOOKUP_FAILED.add(normalized);
            } else {
                minecraft.executeTask(new Runnable() {
                    @Override
                    public void run() {
                        Identifier location =
                                Minecraft.getInstance().getSkinManager().register(skinTexture, Type.SKIN);
                        if (location != null) {
                            PlayerSkinCache.SKINS.put(normalized, location);
                            PlayerSkinCache.LOOKUP_FAILED.remove(normalized);
                        } else {
                            PlayerSkinCache.LOOKUP_FAILED.add(normalized);
                        }
                    }
                });
            }
        }
    }

    private static void cacheProfile(String normalized, GameProfile profile) {
        PROFILES.put(normalized, new PlayerSkinCache.CachedProfile(profile, System.currentTimeMillis() + 1800000L));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static final class CachedProfile {
        private final GameProfile profile;
        private final long expiresAtMs;

        private CachedProfile(GameProfile profile, long expiresAtMs) {
            this.profile = profile;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired(long now) {
            return now >= this.expiresAtMs;
        }
    }
}

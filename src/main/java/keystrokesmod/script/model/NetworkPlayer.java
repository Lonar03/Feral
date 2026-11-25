package keystrokesmod.script.model;

import com.google.common.collect.Iterables;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.scoreboard.team.Team;

public class NetworkPlayer {
    private PlayerInfo playerInfo;
    private static HashMap<String, NetworkPlayer> cache = new HashMap<>();

    public NetworkPlayer(PlayerInfo playerInfo) {
        this.playerInfo = playerInfo;
    }

    public String getCape() {
        return this.playerInfo.getCapeTexture().getPath();
    }

    public String getDisplayName() {
        return this.playerInfo.getProfile() == Minecraft.getInstance().player.getGameProfile()
                ? Minecraft.getInstance().player.getDisplayName().getString()
                : Team.getMemberDisplayName(this.playerInfo.getTeam(), this.getName());
    }

    public String getName() {
        return this.playerInfo == null ? "" : this.playerInfo.getProfile().getName();
    }

    public int getPing() {
        return this.playerInfo == null ? 0 : this.playerInfo.getPing();
    }

    public String getSkinData() {
        Property texture = (Property)
                Iterables.getFirst(this.playerInfo.getProfile().getProperties().get("textures"), null);
        return texture == null
                ? null
                : new String(Base64.getDecoder().decode(texture.getValue().getBytes(StandardCharsets.UTF_8)));
    }

    public String getUUID() {
        return this.playerInfo == null
                ? ""
                : this.playerInfo.getProfile().getId().toString();
    }

    public static NetworkPlayer convert(PlayerInfo networkPlayerInfo) {
        if (networkPlayerInfo == null) {
            return null;
        }

        String id = networkPlayerInfo.getProfile().getId().toString();
        NetworkPlayer cachedEntity = cache.get(id);
        if (cachedEntity == null) {
            cachedEntity = new NetworkPlayer(networkPlayerInfo);
            cache.put(id, cachedEntity);
        }

        return cachedEntity;
    }

    public static void clearCache() {
        cache.clear();
    }
}

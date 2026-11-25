package keystrokesmod.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import keystrokesmod.script.model.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entities;
import net.minecraft.entity.living.LivingEntity;

public final class ClientEntityManager {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<net.minecraft.entity.Entity, ClientEntityManager.ClientEntity> clientEntities =
            new IdentityHashMap<>();
    private static final Map<String, String> aliases = new HashMap<>();
    private static int nextEntityId = -1;

    private ClientEntityManager() {}

    public static Entity spawnMob(String type, double x, double y, double z) {
        return callOnClientThread(() -> spawnMobNow(type, x, y, z), null);
    }

    public static boolean removeClientEntity(Entity entity) {
        return callOnClientThread(() -> removeClientEntityNow(entity), false);
    }

    public static void clearClientEntities() {
        callOnClientThread(
                () -> {
                    clearClientEntitiesNow();
                    return true;
                },
                false);
    }

    private static Entity spawnMobNow(String type, double x, double y, double z) {
        ClientWorld world = mc.world;
        String entityName = resolveEntityName(type);
        if (world != null && entityName != null) {
            net.minecraft.entity.Entity entity = Entities.createSilently(entityName, world);
            if (!(entity instanceof LivingEntity)) {
                return null;
            }

            int entityId = allocateEntityId(world);
            entity.updatePositionAndAngles(x, y, z, 0.0F, 0.0F);
            world.forceEntity(entityId, entity);
            clientEntities.put(entity, new ClientEntityManager.ClientEntity(world, entity, entityId));
            return Entity.convert(entity);
        } else {
            return null;
        }
    }

    private static boolean removeClientEntityNow(Entity entity) {
        if (entity != null && entity.entity != null) {
            ClientEntityManager.ClientEntity clientEntity = clientEntities.remove(entity.entity);
            if (clientEntity == null) {
                return false;
            }

            removeFromWorld(clientEntity);
            return true;
        } else {
            return false;
        }
    }

    private static void clearClientEntitiesNow() {
        List<ClientEntityManager.ClientEntity> entities = new ArrayList<>(clientEntities.values());
        clientEntities.clear();

        for (ClientEntityManager.ClientEntity entity : entities) {
            removeFromWorld(entity);
        }

        nextEntityId = -1;
    }

    private static void removeFromWorld(ClientEntityManager.ClientEntity clientEntity) {
        if (clientEntity.world.getEntity(clientEntity.entityId) == clientEntity.entity) {
            clientEntity.world.removeEntity(clientEntity.entityId);
        } else if (clientEntity.world.entities.contains(clientEntity.entity)) {
            clientEntity.world.removeEntity(clientEntity.entity);
        }
    }

    private static int allocateEntityId(ClientWorld world) {
        while (world.getEntity(nextEntityId) != null) {
            decrementEntityId();
        }

        int entityId = nextEntityId;
        decrementEntityId();
        return entityId;
    }

    private static void decrementEntityId() {
        nextEntityId = nextEntityId == Integer.MIN_VALUE ? -1 : nextEntityId - 1;
    }

    private static String resolveEntityName(String type) {
        if (type != null && !type.trim().isEmpty()) {
            String normalizedType = normalize(type);
            String alias = aliases.get(normalizedType);
            if (alias != null) {
                return alias;
            }

            for (String entityName : Entities.KEY_TO_TYPE.keySet()) {
                if (normalize(entityName).equals(normalizedType)) {
                    return entityName;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static String normalize(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }

        return normalized.replace("_", "").replace("-", "").replace(" ", "");
    }

    private static <T> T callOnClientThread(Callable<T> callable, T fallback) {
        try {
            return (T)
                    (mc.isOnSameThread()
                            ? callable.call()
                            : mc.executeTask(callable).get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    static {
        aliases.put("wither", "WitherBoss");
        aliases.put("irongolem", "VillagerGolem");
        aliases.put("snowgolem", "SnowMan");
        aliases.put("mooshroom", "MushroomCow");
        aliases.put("zombiepigman", "PigZombie");
        aliases.put("magmacube", "LavaSlime");
        aliases.put("ocelot", "Ozelot");
        aliases.put("horse", "EntityHorse");
    }

    private static final class ClientEntity {
        private final ClientWorld world;
        private final net.minecraft.entity.Entity entity;
        private final int entityId;

        private ClientEntity(ClientWorld world, net.minecraft.entity.Entity entity, int entityId) {
            this.world = world;
            this.entity = entity;
            this.entityId = entityId;
        }
    }
}

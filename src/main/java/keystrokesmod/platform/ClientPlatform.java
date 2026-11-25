package keystrokesmod.platform;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import keystrokesmod.Feral;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ClientPlatform {
    private static final MappingResolver MAPPINGS = FabricLoader.getInstance().getMappingResolver();
    private static final Properties CLASS_NAMES = loadNames("feral-mcp-class-names.properties");
    public static float lastBrightnessX;
    public static float lastBrightnessY;
    public static final int PLAYER_MIXIN_PRIORITY = 999;

    public static void handleInput(Screen screen) {
        screen.handleInputs();
    }

    private ClientPlatform() {}

    public static String className(Class<?> type) {
        String name = CLASS_NAMES.getProperty(MAPPINGS.unmapClassName("intermediary", type.getName()));
        return name == null ? type.getSimpleName() : name;
    }

    private static Properties loadNames(String resource) {
        Properties properties = new Properties();

        try {
            InputStream stream = ClientPlatform.class.getResourceAsStream("/" + resource);

            Properties var3;
            try {
                if (stream == null) {
                    throw new IllegalStateException("Missing " + resource);
                }

                properties.load(stream);
                var3 = properties;
            } catch (Throwable var6) {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable var5) {
                        var6.addSuppressed(var5);
                    }
                }

                throw var6;
            }

            if (stream != null) {
                stream.close();
            }

            return var3;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load " + resource, exception);
        }
    }

    public static String registryName(Block block) {
        return String.valueOf(Block.REGISTRY.getKey(block));
    }

    public static String registryName(Item item) {
        return String.valueOf(Item.REGISTRY.getKey(item));
    }

    public static String spawnEggEntityName(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains("entity_name", 8)
                ? stack.getNbt().getString("entity_name")
                : Entities.getKey(stack.getMetadata());
    }

    public static boolean handleChat(String message) {
        return ClientPlatform.ClientCommandHandler.instance.handle(message)
                || Feral.commandManager != null && Feral.commandManager.handleChatMessage(message);
    }

    public static boolean canRiderInteract(Entity entity) {
        return false;
    }

    public static InventorySlot slotUnderMouse(InventoryMenuScreen screen) {
        return screen.hoveredSlot;
    }

    public static int enchantability(Item item, ItemStack stack) {
        return item.getEnchantability();
    }

    public static void onPlayerDestroyItem(PlayerEntity player, ItemStack stack) {}

    public static boolean shouldSpreadItems(ItemEntityRenderer renderer) {
        return true;
    }

    public static float lastBrightnessX() {
        return lastBrightnessX;
    }

    public static float lastBrightnessY() {
        return lastBrightnessY;
    }

    public static final class ClientCommandHandler {
        public static final ClientPlatform.ClientCommandHandler instance = new ClientPlatform.ClientCommandHandler();
        private net.minecraft.server.command.AbstractCommand command;

        public void registerCommand(net.minecraft.server.command.AbstractCommand command) {
            this.command = command;
        }

        public boolean handle(String message) {
            if (this.command != null && message.startsWith("/")) {
                String[] words = message.substring(1).trim().split("\\s+");
                if (!this.command.getName().equals(words[0].toLowerCase(Locale.ROOT))) {
                    return false;
                }

                try {
                    this.command.run(Minecraft.getInstance().player, Arrays.copyOfRange(words, 1, words.length));
                } catch (Exception ignored) {
                }
                return true;
            } else {
                return false;
            }
        }
    }

    public static final class ForgeHooks {
        public static boolean onPlayerAttackTarget(PlayerEntity player, Entity target) {
            return !ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.AttackEntityEvent(player, target));
        }

        public static void onLivingJump(LivingEntity entity) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.LivingEvent.LivingJumpEvent(entity));
        }
    }

    public static final class ForgeHooksClient {
        public static BakedModel handleCameraTransforms(BakedModel model, Type type) {
            model.getTransformations().apply(type);
            return model;
        }
    }

    public static final class ReflectionHelper {
        private static final Properties names = ClientPlatform.loadNames("feral-mcp-fields.properties");

        public static Field findField(Class<?> type, String... candidates) {
            for (String candidate : candidates) {
                try {
                    Field field = type.getDeclaredField(runtimeFieldName(type, candidate));
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException var7) {
                }
            }

            throw new IllegalArgumentException(
                    "Unknown field on " + type.getName() + ": " + Arrays.toString(candidates));
        }

        public static String runtimeFieldName(Class<?> type, String name) {
            String owner = ClientPlatform.MAPPINGS.unmapClassName("intermediary", type.getName());
            String mapping = names.getProperty(owner + "." + name);
            if (mapping == null) {
                return name;
            }

            int separator = mapping.indexOf(124);
            return ClientPlatform.MAPPINGS.mapFieldName(
                    "intermediary", owner, mapping.substring(0, separator), mapping.substring(separator + 1));
        }

        public static <T> T getPrivateValue(Class<?> type, Object target, String... candidates) {
            try {
                return (T) findField(type, candidates).get(target);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to read field on " + type.getName(), exception);
            }
        }
    }
}

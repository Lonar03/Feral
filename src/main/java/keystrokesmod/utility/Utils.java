package keystrokesmod.utility;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import keystrokesmod.Feral;
import keystrokesmod.helper.MouseHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiIngame;
import keystrokesmod.mixin.impl.accessor.IAccessorItemFood;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.minigames.DuelsStats;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.impl.player.Scaffold;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.utility.color.ColorConstants;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ClayBlock;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.GravelBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.block.SoulSandBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.SyncedData.Entry;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.PlayerMenu;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.FoodItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringUtils;
import net.minecraft.text.Text;
import net.minecraft.text.Text.Serializer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class Utils implements IMinecraftInstance {
    private static final Random rand = new Random();
    private static final ThreadLocal<Integer> LOCAL_PLAYER_SUB_UPDATE_DEPTH = ThreadLocal.withInitial(() -> 0);
    public static HashSet<String> friends = new HashSet<>();
    public static HashSet<String> enemies = new HashSet<>();
    public static final Logger log = LogManager.getLogger();

    /** Returns the first matching hotbar slot, or -1. The predicate also receives empty slots. */
    public static int findHotbarSlot(Predicate<ItemStack> matches) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (matches.test(mc.player.inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public static PlayerInfo info(PlayerEntity p) {
        return p != null && mc.getNetworkHandler() != null
                ? mc.getNetworkHandler().getOnlinePlayer(p.getUuid())
                : null;
    }

    public static boolean addEnemy(String name) {
        if (Feral.playerRelationsManager != null) {
            return Feral.playerRelationsManager.addEnemy(name);
        } else if (enemies.add(name.toLowerCase())) {
            sendMessage("&7Added enemy&7: &b" + name);
            return true;
        } else {
            return false;
        }
    }

    public static boolean removeEnemy(String name) {
        if (Feral.playerRelationsManager != null) {
            return Feral.playerRelationsManager.removeEnemy(name);
        } else if (enemies.remove(name.toLowerCase())) {
            sendMessage("&7Removed enemy&7: &b" + name);
            return true;
        } else {
            return false;
        }
    }

    public static float getCameraYaw() {
        return (float) Math.toDegrees(Math.atan2(Camera.dz(), Camera.dx()));
    }

    public static float getCameraPitch() {
        return (float) Math.toDegrees(Math.acos(Camera.dy()));
    }

    public static Vec3d getCameraPos(double renderPartialTicks) {
        if (mc.options.perspective == 0) {
            return new Vec3d(mc.player.x, mc.player.y + mc.player.getEyeHeight(), mc.player.z);
        }

        float cameraDistance = 4.0F;
        if (ModuleManager.extendCamera != null && ModuleManager.extendCamera.isEnabled()) {
            cameraDistance = (float) ModuleManager.extendCamera.distance.getInput();
        }

        Entity renderEntity = mc.getCamera();
        float entityEyeHeight = renderEntity.getEyeHeight();
        double interpolatedX = renderEntity.lastX + (renderEntity.x - renderEntity.lastX) * renderPartialTicks;
        double interpolatedY =
                renderEntity.lastY + (renderEntity.y - renderEntity.lastY) * renderPartialTicks + entityEyeHeight;
        double interpolatedZ = renderEntity.lastZ + (renderEntity.z - renderEntity.lastZ) * renderPartialTicks;
        double adjustedDistance = cameraDistance;
        float cameraYaw = getCameraYaw();
        float cameraPitch = getCameraPitch();
        double offsetX = -MathHelper.sin(cameraYaw / 180.0F * (float) Math.PI)
                * MathHelper.cos(cameraPitch / 180.0F * (float) Math.PI)
                * adjustedDistance;
        double offsetZ = MathHelper.cos(cameraYaw / 180.0F * (float) Math.PI)
                * MathHelper.cos(cameraPitch / 180.0F * (float) Math.PI)
                * adjustedDistance;
        double offsetY = -MathHelper.sin(cameraPitch / 180.0F * (float) Math.PI) * adjustedDistance;
        if (ModuleManager.noCameraClip == null || !ModuleManager.noCameraClip.isEnabled()) {
            for (int i = 0; i < 8; i++) {
                float cornerOffsetX = ((i & 1) * 2 - 1) * 0.1F;
                float cornerOffsetY = ((i >> 1 & 1) * 2 - 1) * 0.1F;
                float cornerOffsetZ = ((i >> 2 & 1) * 2 - 1) * 0.1F;
                HitResult rayTraceResult = mc.world.rayTrace(
                        new Vec3d(
                                interpolatedX + cornerOffsetX,
                                interpolatedY + cornerOffsetY,
                                interpolatedZ + cornerOffsetZ),
                        new Vec3d(
                                interpolatedX - offsetX + cornerOffsetX + cornerOffsetZ,
                                interpolatedY - offsetY + cornerOffsetY,
                                interpolatedZ - offsetZ + cornerOffsetZ));
                if (rayTraceResult != null) {
                    double blockHitDistance =
                            rayTraceResult.facePos.distanceTo(new Vec3d(interpolatedX, interpolatedY, interpolatedZ));
                    if (blockHitDistance < adjustedDistance) {
                        adjustedDistance = blockHitDistance;
                    }
                }
            }
        }

        double finalCameraX = interpolatedX - offsetX * (adjustedDistance / cameraDistance);
        double finalCameraY = interpolatedY - offsetY * (adjustedDistance / cameraDistance);
        double finalCameraZ = interpolatedZ - offsetZ * (adjustedDistance / cameraDistance);
        return new Vec3d(finalCameraX, finalCameraY, finalCameraZ);
    }

    public static void printInfo(Entity ent) {
        if (ent != null && nullCheck()) {
            StringBuilder report = new StringBuilder();
            sendMessage("&7&m-------------------------");
            sendEntityDebugLine(report, "Attacking", ent.getName());
            sendEntityDebugLine(report, "Class", ent.getClass().getName());
            sendEntityDebugLine(report, "Entity ID", ent.getNetworkId());
            sendEntityDebugLine(report, "Entity type", ent instanceof PlayerEntity ? "Player" : Entities.getKey(ent));
            sendEntityDebugLine(report, "Entity type ID", Entities.getId(ent));
            UUID uuid = ent.getUuid();
            sendEntityDebugLine(
                    report, "UUID", uuid + " (variant " + uuid.variant() + ", version " + uuid.version() + ")");
            boolean isPlayer = ent instanceof PlayerEntity;
            sendEntityDebugLine(report, "Player", isPlayer);
            sendEntityDebugLine(report, "Eye distance", getDistanceToEye(ent));
            double distanceSq = raycastDistanceSq(ent, 12.0, false);
            sendEntityDebugLine(
                    report, "Raycast distance", distanceSq < 0.0 ? "No intersection" : Math.sqrt(distanceSq));
            Text displayName = ent.getDisplayName();
            sendEntityDebugLine(report, "Display name", displayName == null ? null : displayName.getString());
            sendEntityDebugLine(
                    report, "Display component", displayName == null ? null : Serializer.toJson(displayName));
            sendEntityDebugLine(report, "Custom name", ent.getCustomName());
            sendEntityDebugLine(report, "Always render name", ent.isCustomNameVisible());
            sendEntityDebugLine(report, "Position", ent.x + ", " + ent.y + ", " + ent.z);
            sendEntityDebugLine(report, "Previous position", ent.lastX + ", " + ent.lastY + ", " + ent.lastZ);
            sendEntityDebugLine(report, "Last tick position", ent.prevX + ", " + ent.prevY + ", " + ent.prevZ);
            sendEntityDebugLine(
                    report, "Server position", ent.lastKnownX + ", " + ent.lastKnownY + ", " + ent.lastKnownZ);
            sendEntityDebugLine(report, "Motion", ent.velocityX + ", " + ent.velocityY + ", " + ent.velocityZ);
            sendEntityDebugLine(report, "Rotation", ent.yaw + ", " + ent.pitch);
            sendEntityDebugLine(report, "Previous rotation", ent.lastYaw + ", " + ent.lastPitch);
            sendEntityDebugLine(report, "Bounding box", ent.getShape());
            sendEntityDebugLine(report, "Size", ent.width + " x " + ent.height);
            sendEntityDebugLine(report, "Eye height", ent.getEyeHeight());
            sendEntityDebugLine(report, "Dimension", ent.dimension);
            sendEntityDebugLine(report, "Chunk", ent.chunkX + ", " + ent.chunkY + ", " + ent.chunkZ);
            sendEntityDebugLine(report, "Ticks existed", ent.ticks);
            sendEntityDebugLine(
                    report,
                    "State",
                    "alive="
                            + ent.isAlive()
                            + ", dead="
                            + ent.removed
                            + ", invisible="
                            + ent.isInvisible()
                            + ", sneaking="
                            + ent.isSneaking()
                            + ", sprinting="
                            + ent.isSprinting()
                            + ", burning="
                            + ent.isOnFire()
                            + ", inWater="
                            + ent.isInWater()
                            + ", inLava="
                            + ent.isInLava()
                            + ", wet="
                            + ent.isInWaterOrRain()
                            + ", silent="
                            + ent.isSilent());
            sendEntityDebugLine(
                    report,
                    "Movement state",
                    "onGround="
                            + ent.onGround
                            + ", airborne="
                            + ent.velocityDirty
                            + ", noClip="
                            + ent.noClip
                            + ", collided="
                            + ent.colliding
                            + ", horizontalCollision="
                            + ent.collidingHorizontally
                            + ", verticalCollision="
                            + ent.collidingVertically
                            + ", velocityChanged="
                            + ent.damaged
                            + ", fallDistance="
                            + ent.fallDistance
                            + ", stepHeight="
                            + ent.stepHeight);
            sendEntityDebugLine(report, "Riding entity", ent.vehicle);
            sendEntityDebugLine(report, "Passenger", ent.rider);
            if (isPlayer) {
                PlayerEntity p = (PlayerEntity) ent;
                PlayerInfo clientPlayer = info(p);
                sendEntityDebugLine(report, "Ping", clientPlayer == null ? "Not found" : clientPlayer.getPing());
                sendEntityDebugLine(
                        report, "Game mode", clientPlayer == null ? "Not found" : clientPlayer.getGameMode());
                sendEntityDebugLine(report, "Teammate", isTeammate(p));
                sendEntityDebugLine(report, "Tab list", clientPlayer != null);
                if (p.getScoreboardTeam() instanceof Team) {
                    Team scoreTeam = (Team) p.getScoreboardTeam();
                    sendEntityDebugLine(report, "Team name", scoreTeam.getDisplayName());
                    sendEntityDebugLine(report, "Team prefix", scoreTeam.getPrefix());
                    sendEntityDebugLine(report, "Team suffix", scoreTeam.getSuffix());
                }
            }

            if (ent instanceof LivingEntity) {
                LivingEntity living = (LivingEntity) ent;
                sendEntityDebugLine(
                        report,
                        "Bot",
                        ModuleManager.antiBot != null && ModuleManager.antiBot.isEnabled()
                                ? AntiBot.isBot(living)
                                : "AntiBot disabled");
                sendEntityDebugLine(report, "Health", living.getHealth() + "/" + living.getMaxHealth());
                sendEntityDebugLine(report, "Absorption", living.getAbsorption());
                sendEntityDebugLine(report, "Armor", living.getArmorProtection());
                sendEntityDebugLine(report, "Hurt time", living.damagedTimer + "/" + living.damagedTime);
                sendEntityDebugLine(
                        report, "Hurt resistance", living.invulnerableTimer + "/" + living.invulnerableTicks);
                sendEntityDebugLine(report, "Death time", living.deathTicks);
                sendEntityDebugLine(report, "Head rotation", living.headYaw + ", body=" + living.bodyYaw);
                sendEntityDebugLine(
                        report,
                        "Movement input",
                        "forward=" + living.forwardSpeed + ", strafe=" + living.sidewaysSpeed);
                sendEntityDebugLine(
                        report,
                        "Swing",
                        "inProgress=" + living.armSwinging + ", progress=" + living.attackAnimationProgress);
                sendEntityDebugLine(
                        report, "Potion effects", living.getStatusEffects().size());

                for (StatusEffectInstance effect : living.getStatusEffects()) {
                    sendEntityDebugLine(report, "Potion " + effect.getId(), effect.toNbt(new NbtCompound()));
                }

                String[] equipmentSlots = new String[] {"Held item", "Boots", "Leggings", "Chestplate", "Helmet"};

                for (int slot = 0; slot < equipmentSlots.length; slot++) {
                    sendEntityDebugLine(report, equipmentSlots[slot], living.getEquipment(slot));
                }

                List<EntityAttributeInstance> attributes =
                        new ArrayList<>(living.getAttributes().getAll());
                attributes.sort(Comparator.comparing(
                        attributex -> attributex.getAttribute().getName()));

                for (EntityAttributeInstance attribute : attributes) {
                    sendEntityDebugLine(
                            report,
                            "Attribute " + attribute.getAttribute().getName(),
                            "base=" + attribute.getBase() + ", value=" + attribute.get() + ", modifiers="
                                    + attribute.getModifiers());
                }
            }

            List<Entry> metadata = ent.getSyncedData().getAll();
            if (metadata != null) {
                metadata.sort(Comparator.comparingInt(Entry::getId));

                for (Entry entry : metadata) {
                    sendEntityDebugLine(
                            report, "Metadata " + entry.getId() + " (type " + entry.getType() + ")", entry.getValue());
                }
            }

            try {
                NbtCompound nbt = new NbtCompound();
                ent.writeNbtWithoutId(nbt);
                sendEntityDebugLine(report, "Full entity NBT", nbt);
            } catch (RuntimeException ex) {
                sendEntityDebugLine(report, "Entity NBT error", ex.toString());
                log.warn("Unable to serialize entity debug NBT for " + ent.getNetworkId(), ex);
            }

            if (ModuleManager.debug != null) {
                ModuleManager.debug.showEntityDebugReport(report.toString());
            }
        }
    }

    private static void sendEntityDebugLine(StringBuilder report, String label, Object value) {
        String text = formatEntityDebugValue(value).replace("§", "\\u00a7");
        report.append(label).append(": ").append(text).append('\n');
        mc.player.sendMessage(new LiteralText("§7[§dF§7] " + label + ": §b" + text));
    }

    private static String formatEntityDebugValue(Object value) {
        if (value instanceof ItemStack) {
            return formatEntityDebugValue(((ItemStack) value).writeNbt(new NbtCompound()));
        }

        if (value instanceof Rotation) {
            return formatEntityDebugValue(((Rotation) value).toNbt());
        }

        if (value instanceof NbtCompound) {
            NbtCompound compound = (NbtCompound) value;
            List<String> entries = new ArrayList<>();

            for (String key : new TreeSet<String>(compound.getKeys())) {
                entries.add(new JsonPrimitive(key) + ":" + formatEntityDebugValue(compound.get(key)));
            }

            return "{" + String.join(",", entries) + "}";
        } else if (!(value instanceof NbtList)) {
            if (value instanceof NbtByteArray) {
                return "byte" + Arrays.toString(((NbtByteArray) value).getByteArray());
            } else if (value instanceof NbtIntArray) {
                return "int" + Arrays.toString(((NbtIntArray) value).getIntArray());
            } else {
                return value instanceof NbtString
                        ? new JsonPrimitive(((NbtString) value).asString()).toString()
                        : String.valueOf(value);
            }
        } else {
            NbtList list = (NbtList) value;
            List<String> entries = new ArrayList<>();

            for (int i = 0; i < list.size(); i++) {
                entries.add(formatEntityDebugValue(list.getElement(i)));
            }

            return "[" + String.join(",", entries) + "]";
        }
    }

    public static double raycastDistanceSq(Entity en, double max_reach, boolean calc_rot) {
        Vec3d eyeVec = mc.player.getEyePosition(1.0F);
        float yaw;
        float pitch;
        if (calc_rot) {
            float[] rot = RotationUtils.getRotations(en);
            yaw = rot[0];
            pitch = rot[1];
        } else {
            yaw = mc.player.yaw;
            pitch = mc.player.pitch;
        }

        float ff = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float ff2 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float ff3 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
        float ff4 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
        Vec3d lookVec = new Vec3d(ff2 * ff3, ff4, ff * ff3);
        double lookVecX = lookVec.x * max_reach;
        double lookVecY = lookVec.y * max_reach;
        double lookVecZ = lookVec.z * max_reach;
        Vec3d sumVec = eyeVec.add(lookVecX, lookVecY, lookVecZ);
        List list = mc.world.getEntities(
                mc.getCamera(),
                mc.getCamera().getShape().expanded(lookVecX, lookVecY, lookVecZ).grown(1.0, 1.0, 1.0));

        for (int i = 0; i < list.size(); i++) {
            Entity entity = (Entity) list.get(i);
            if (entity == en && entity.hasCollision()) {
                float cbs = entity.getPickRadius();
                Box axis = entity.getShape().grown(cbs, cbs, cbs);
                HitResult mop = axis.clip(eyeVec, sumVec);
                if (mop != null) {
                    return eyeVec.squaredDistanceTo(mop.facePos);
                }
            }
        }

        return -1.0;
    }

    public static double getDistanceToEye(Entity en) {
        return mc.player.getEyePosition(1.0F).distanceTo(en.getEyePosition(1.0F));
    }

    public static boolean isInTabList(PlayerEntity p) {
        for (PlayerInfo playerInfo : mc.getNetworkHandler().getOnlinePlayers()) {
            if (playerInfo.getProfile().equals(p.getGameProfile())) {
                return true;
            }
        }

        return false;
    }

    public static String getServerName() {
        return DuelsStats.nick.isEmpty() ? mc.player.getName() : DuelsStats.nick;
    }

    public static boolean tabbedIn() {
        return mc.screen == null && mc.focused;
    }

    public static boolean isConsuming(Entity entity) {
        return !(entity instanceof PlayerEntity)
                ? false
                : ((PlayerEntity) entity).hasItemInUse() && holdingFood((PlayerEntity) entity);
    }

    public static boolean holdingFood(LivingEntity entity) {
        return entity.getDisplayItemInHand() != null
                && entity.getDisplayItemInHand().getItem() instanceof FoodItem;
    }

    public static int getColorFromEntity(Entity entity) {
        if (entity == null) {
            return -1;
        }

        if (entity instanceof PlayerEntity) {
            Team scoreplayerteam = (Team) ((LivingEntity) entity).getScoreboardTeam();
            if (scoreplayerteam != null) {
                String s = TextRenderer.isolateFormatting(scoreplayerteam.getPrefix());
                if (s.length() >= 2) {
                    int teamColor = getColorFromFormattingCode(s.charAt(1));
                    if (teamColor != -1) {
                        return teamColor;
                    }
                }
            }
        }

        Text displayNameComponent = entity.getDisplayName();
        if (displayNameComponent == null) {
            return -1;
        }

        String displayName = removeFormatCodes(displayNameComponent.getFormattedString());
        return displayName.length() >= 2
                        && displayName.startsWith("§")
                        && Character.toLowerCase(displayName.charAt(1)) != 102
                ? getColorFromFormattingCode(displayName.charAt(1))
                : -1;
    }

    private static int getColorFromFormattingCode(char formatCode) {
        switch (Character.toLowerCase(formatCode)) {
            case '0':
                return ColorConstants.BLACK;
            case '1':
                return ColorConstants.DARK_BLUE;
            case '2':
                return ColorConstants.DARK_GREEN;
            case '3':
                return ColorConstants.DARK_AQUA;
            case '4':
                return ColorConstants.DARK_RED;
            case '5':
                return ColorConstants.DARK_PURPLE;
            case '6':
                return ColorConstants.GOLD;
            case '7':
                return ColorConstants.GRAY;
            case '8':
                return ColorConstants.DARK_GRAY;
            case '9':
                return ColorConstants.BLUE;
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'H':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            default:
                return -1;
            case 'a':
                return ColorConstants.GREEN;
            case 'b':
                return ColorConstants.AQUA;
            case 'c':
                return ColorConstants.RED;
            case 'd':
                return ColorConstants.LIGHT_PURPLE;
            case 'e':
                return ColorConstants.YELLOW;
            case 'f':
                return -1;
        }
    }

    public static boolean overVoid(double posX, double posY, double posZ) {
        for (int i = (int) posY; i > -1; i--) {
            if (!(mc.world.getBlockState(new BlockPos(posX, i, posZ)).getBlock() instanceof AirBlock)) {
                return false;
            }
        }

        return true;
    }

    public static Block getBlockFromName(String name) {
        return (Block) Block.REGISTRY.get(new Identifier("minecraft:" + name));
    }

    public static boolean canPlayerBeSeen(LivingEntity player) {
        double x = player.x;
        double y = player.y;
        double z = player.z;
        Vec3d vecPlayer = mc.player.getEyePosition(1.0F);
        double shoulderHeight = player.getEyeHeight() - 0.2;
        if (canSeeVec(vecPlayer, new Vec3d(x + 0.3, shoulderHeight, z))) {
            return true;
        }

        if (canSeeVec(vecPlayer, new Vec3d(x - 0.3, shoulderHeight, z))) {
            return true;
        }

        if (canSeeVec(vecPlayer, new Vec3d(x, shoulderHeight, z + 0.3))) {
            return true;
        }

        if (canSeeVec(vecPlayer, new Vec3d(x, shoulderHeight, z - 0.3))) {
            return true;
        }

        for (double d = player.getEyeHeight() + 0.2; d > 0.0; d -= 0.2) {
            Vec3d vecPoint = new Vec3d(x, y + d, z);
            if (canSeeVec(vecPlayer, vecPoint)) {
                return true;
            }
        }

        return false;
    }

    public static boolean holdingFireball() {
        return mc.player.getDisplayItemInHand() == null
                ? false
                : mc.player.getDisplayItemInHand().getItem() instanceof FireChargeItem;
    }

    public static boolean canSeeVec(Vec3d vecPlayer, Vec3d vecTarget) {
        HitResult mop = mc.world.rayTrace(vecPlayer, vecTarget, false, false, false);
        return mop == null || mop.type != Type.BLOCK;
    }

    public static List<PlayerInfo> getTablist(boolean removeSelf) {
        ArrayList<PlayerInfo> list = new ArrayList<>(mc.getNetworkHandler().getOnlinePlayers());
        removeDuplicates(list);
        if (removeSelf) {
            list.remove(mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid()));
        }

        return list;
    }

    public static void removeDuplicates(ArrayList list) {
        HashSet set = new HashSet(list);
        list.clear();
        list.addAll(set);
    }

    public static boolean removeFriend(String name) {
        if (Feral.playerRelationsManager != null) {
            return Feral.playerRelationsManager.removeFriend(name);
        } else if (friends.remove(name.toLowerCase())) {
            sendMessage("&7Removed &afriend&7: &b" + name);
            return true;
        } else {
            return false;
        }
    }

    public static String getCompilerDirectory() {
        String tempDirStr = System.getProperty("java.io.tmpdir") + "cmF2ZW5fc2NyaXB0cw";
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            File tempDir =
                    new File(mc.gameDir + File.separator + "keystrokes" + File.separator + "scripts", "compiler_temp");
            return !tempDir.exists() && !tempDir.mkdirs() ? tempDirStr : tempDir.getAbsolutePath();
        } else {
            return tempDirStr;
        }
    }

    public static boolean addFriend(String name) {
        if (Feral.playerRelationsManager != null) {
            return Feral.playerRelationsManager.addFriend(name);
        }

        if (friends.add(name.toLowerCase())) {
            sendMessage("&7Added &afriend&7: &b" + name);
            if (enemies.contains(name.toLowerCase())) {
                enemies.remove(name.toLowerCase());
            }

            return true;
        } else {
            return false;
        }
    }

    public static String getDistanceColor(double distance) {
        if (distance < 10.0) {
            return "&c";
        } else if (distance < 25.0) {
            return "&6";
        } else if (distance < 50.0) {
            return "&e";
        } else {
            return distance > 100.0 ? "&2" : "&a";
        }
    }

    public static boolean isWholeNumber(double num) {
        return num == Math.floor(num);
    }

    public static String asWholeNum(double input) {
        return isWholeNumber(input) ? (int) input + "" : String.valueOf(input);
    }

    public static int asWholeNumInt(double input) {
        return isWholeNumber(input) ? (int) input : (int) input;
    }

    public static int randomizeInt(int min, int max) {
        return rand.nextInt(max - min + 1) + min;
    }

    public static double randomizeDouble(double min, double max) {
        return min + (max - min) * rand.nextDouble();
    }

    public static boolean inFov(float fov, BlockPos blockPos) {
        return inFov(fov, blockPos.getX(), blockPos.getZ());
    }

    public static boolean inFov(float fov, Entity entity) {
        return inFov(fov, entity.x, entity.z);
    }

    public static boolean inFov(float fov, double posX, double posZ) {
        return inFov(mc.player, fov, posX, posZ);
    }

    public static boolean inFov(Entity viewPoint, float fov, double posX, double posZ) {
        fov = (float) (fov * 0.5);
        double wrapAngleTo180_double =
                MathHelper.wrapDegrees((viewPoint.yaw - RotationUtils.angle(posX, posZ)) % 360.0F);
        if (wrapAngleTo180_double > 0.0) {
            if (wrapAngleTo180_double < fov) {
                return true;
            }
        } else if (wrapAngleTo180_double > -fov) {
            return true;
        }

        return false;
    }

    public static boolean inFov(float origin, float fov, float targetYaw) {
        fov *= 0.5F;
        double wrapAngleTo180_double = MathHelper.wrapDegrees((origin - targetYaw) % 360.0F);
        return wrapAngleTo180_double > 0.0 ? wrapAngleTo180_double < fov : wrapAngleTo180_double > -fov;
    }

    public static Vec3d getLookVec(float yaw, float pitch) {
        float f = MathHelper.cos(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * (float) (Math.PI / 180.0));
        float f3 = MathHelper.sin(-pitch * (float) (Math.PI / 180.0));
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    public static boolean holdingBow() {
        return mc.player.getDisplayItemInHand() == null
                ? false
                : mc.player.getDisplayItemInHand().getItem() instanceof BowItem;
    }

    public static boolean bowBackwards() {
        return holdingBow() && mc.player.sidewaysSpeed == 0.0F && mc.player.forwardSpeed <= 0.0F && isMoving();
    }

    public static boolean noSlowingBackWithBow() {
        return ModuleManager.noSlow.noSlowing && bowBackwards();
    }

    public static void sendMessage(String txt) {
        if (nullCheck()) {
            String m = formatColor("&7[&dF&7]&r " + txt);
            mc.player.sendMessage(new LiteralText(m));
        }
    }

    public static void sendMessageStr(String txt) {
        if (nullCheck()) {
            String m = formatColor("&7[&dF&7]&r " + txt);
            mc.player.sendMessage(new LiteralText(m));
        }
    }

    public static void sendMessage(Object object) {
        String toString = String.valueOf(object);
        sendMessage(toString);
    }

    public static void sendDebugMessage(String message) {
        if (nullCheck()) {
            mc.player.sendMessage(new LiteralText("§7[§dF§7]§r " + message));
        }
    }

    public static void attackEntity(Entity e, boolean clientSwing, boolean silentSwing) {
        if (clientSwing) {
            mc.player.swingArm();
        } else if (silentSwing || !silentSwing && !clientSwing) {
            mc.player.networkHandler.sendPacket(new ArmSwingC2SPacket());
        }

        mc.interactionManager.attackEntity(mc.player, e);
    }

    public static void sendRawMessage(String txt) {
        if (nullCheck()) {
            mc.player.sendMessage(new LiteralText(formatColor(txt)));
        }
    }

    public static float getTotalHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorption();
    }

    public static String getHealthStr(LivingEntity entity, boolean accountDead) {
        float totalHealth = getTotalHealth(entity);
        if (accountDead && entity.removed) {
            totalHealth = 0.0F;
        }

        return getColorForHealth(entity.getHealth() / entity.getMaxHealth(), totalHealth);
    }

    public static boolean isBindDown(KeyBinding keyBinding) {
        int keyCode = keyBinding.getKeyCode();
        return keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
    }

    public static int getTool(Block block) {
        double bestScore = 1.0;
        int bestSlot = -1;

        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
            ItemStack getStackInSlot = mc.player.inventory.getItem(i);
            if (getStackInSlot != null) {
                double score = ItemSortScoring.getBlockBreakingScore(getStackInSlot, block);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    public static boolean onLadder(Entity entity) {
        int posX = MathHelper.floor(entity.x);
        int posY = MathHelper.floor(entity.y - 0.2F);
        int posZ = MathHelper.floor(entity.z);
        BlockPos blockpos = new BlockPos(posX, posY, posZ);
        Block block1 = mc.world.getBlockState(blockpos).getBlock();
        return block1 instanceof LadderBlock && !entity.onGround;
    }

    public static float getEfficiency(ItemStack itemStack, Block block) {
        float getStrVsBlock = itemStack.getMiningSpeed(block);
        if (getStrVsBlock > 1.0F) {
            int getEnchantmentLevel = EnchantmentHelper.getLevel(Enchantment.EFFICIENCY.id, itemStack);
            if (getEnchantmentLevel > 0) {
                getStrVsBlock += getEnchantmentLevel * getEnchantmentLevel + 1;
            }
        }

        return getStrVsBlock;
    }

    public static boolean isEnemy(PlayerEntity entityPlayer) {
        return entityPlayer != null && isEnemy(entityPlayer.getName());
    }

    public static boolean isEnemy(String name) {
        if (ModuleManager.relationships != null && !ModuleManager.relationships.isEnabled()) {
            return false;
        } else {
            return Feral.playerRelationsManager != null
                    ? Feral.playerRelationsManager.isEnemy(name)
                    : name != null && !enemies.isEmpty() && enemies.contains(name.toLowerCase());
        }
    }

    public static String getColorForHealth(double percent, double totalHealth) {
        if (Settings.showHealthAsHearts.isToggled()) {
            totalHealth /= 2.0;
        }

        double health = round(totalHealth, 1);
        String healthStr =
                (percent < 0.3 ? "§c" : (percent < 0.5 ? "§6" : (percent < 0.7 ? "§e" : "§a"))) + asWholeNum(health);
        if (Settings.showHeartSymbol.isToggled()) {
            healthStr = healthStr + "§c❤§r";
        }

        return healthStr;
    }

    public static int getColorForHealth(double health) {
        return health < 0.3 ? -43691 : (health < 0.5 ? -22016 : (health < 0.7 ? -171 : -11141291));
    }

    public static String formatColor(String txt) {
        return txt.replace('&', '§');
    }

    public static String getFirstColorCode(String input) {
        if (input != null && input.length() >= 2) {
            for (int i = 0; i < input.length() - 1; i++) {
                if (input.charAt(i) == 167) {
                    char c = input.charAt(i + 1);
                    if (c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F') {
                        return "§" + c;
                    }
                }
            }

            return "";
        } else {
            return "";
        }
    }

    public static int getBoldWidth(String string) {
        boolean bold = false;
        int additionalWidth = 0;

        for (int i = 0; i < string.length(); i++) {
            char c0 = string.charAt(i);
            if (c0 == 167 && i + 1 < string.length()) {
                int i2 = "0123456789abcdefklmnor"
                        .indexOf(string.toLowerCase(Locale.ENGLISH).charAt(i + 1));
                if (i2 == 17) {
                    bold = true;
                }

                i++;
            } else if (bold) {
                additionalWidth++;
            }
        }

        return additionalWidth;
    }

    public static void correctValue(SliderSetting c, SliderSetting d) {
        if (c.getInput() > d.getInput()) {
            double p = c.getInput();
            c.setValueWithEvent(d.getInput());
            d.setValueWithEvent(p);
        }
    }

    public static String generateRandomString(int n) {
        char[] array = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        StringBuilder sb = new StringBuilder();
        IntStream.range(0, n).forEach(p2 -> sb.append(array[rand.nextInt(array.length)]));
        return sb.toString();
    }

    public static boolean isFriended(PlayerEntity entityPlayer) {
        return entityPlayer != null && isFriended(entityPlayer.getName());
    }

    public static boolean isFriended(String name) {
        if (ModuleManager.relationships != null && !ModuleManager.relationships.isEnabled()) {
            return false;
        } else {
            return Feral.playerRelationsManager != null
                    ? Feral.playerRelationsManager.isFriend(name)
                    : name != null && !friends.isEmpty() && friends.contains(name.toLowerCase());
        }
    }

    public static double getRandomValue(SliderSetting a, SliderSetting b, Random r) {
        return a.getInput() == b.getInput()
                ? a.getInput()
                : a.getInput() + r.nextDouble() * (b.getInput() - a.getInput());
    }

    public static boolean nullCheck() {
        return mc.player != null && mc.world != null;
    }

    public static boolean isHypixel() {
        boolean isHypixel = !mc.isSingleplayer()
                && mc.getCurrentServerEntry() != null
                && mc.getCurrentServerEntry().ip.contains("hypixel.net");
        if (!isHypixel) {
            String footer = getSidebarFooter();
            isHypixel = footer != null && stripString(footer).equals("www.hypixel.net");
        }

        return isHypixel;
    }

    public static String getHitsToKillStr(PlayerEntity entityPlayer, ItemStack itemStack) {
        int n = (int) Math.ceil(getHitsToKill(entityPlayer, itemStack));
        return "§" + (n <= 1 ? "c" : (n <= 3 ? "6" : (n <= 5 ? "e" : "a"))) + n;
    }

    public static double getHitsToKill(PlayerEntity target, ItemStack usedItem) {
        double heldItemDamageLevel = 1.0;
        if (usedItem != null && (usedItem.getItem() instanceof SwordItem || usedItem.getItem() instanceof AxeItem)) {
            heldItemDamageLevel += getDamageLevel(usedItem);
        }

        double armorProtPercentage = 0.0;
        double totalEPF = 0.0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = target.inventory.getArmor(i);
            if (stack != null && stack.getItem() instanceof ArmorItem) {
                armorProtPercentage += ((ArmorItem) stack.getItem()).protection * 0.04;
                int protLevel = EnchantmentHelper.getLevel(Enchantment.PROTECTION.id, stack);
                if (protLevel != 0) {
                    double epf = Math.floor(0.75 * (6 + protLevel * protLevel) / 3.0);
                    totalEPF += epf;
                }
            }
        }

        totalEPF = 0.04 * Math.min(Math.ceil(Math.min(totalEPF, 25.0) * 0.75), 20.0);
        double armorReduction = armorProtPercentage + totalEPF * (1.0 - armorProtPercentage);
        double damage = heldItemDamageLevel * (1.0 - armorReduction);
        double hitsToKill = getTotalHealth(target) / damage;
        return round(hitsToKill, 1);
    }

    public static float n() {
        Float yaw = ModuleManager.targetStrafe == null ? null : ModuleManager.targetStrafe.getMovementYaw();
        return ae(yaw == null ? mc.player.yaw : yaw, mc.player.input.movementForward, mc.player.input.movementSideways);
    }

    public static String extractFileName(String name) {
        int firstIndex = name.indexOf("_");
        int lastIndex = name.lastIndexOf("_");
        return firstIndex != -1 && lastIndex != -1 && lastIndex > firstIndex
                ? name.substring(firstIndex + 1, lastIndex)
                : name;
    }

    public static int mergeAlpha(int color, int alpha) {
        return color & 16777215 | alpha << 24;
    }

    public static int clamp(int n) {
        if (n > 255) {
            return 255;
        } else {
            return n < 4 ? 4 : n;
        }
    }

    public static boolean hasArrows(ItemStack stack) {
        boolean flag =
                mc.player.abilities.creativeMode || EnchantmentHelper.getLevel(Enchantment.INFINITY.id, stack) > 0;
        return flag || mc.player.inventory.contains(Items.ARROW);
    }

    public static int darkenColor(int color, double percent) {
        int alpha = color >> 24 & 0xFF;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        percent = (100.0 - percent) / 100.0;
        red = (int) (red * percent);
        green = (int) (green * percent);
        blue = (int) (blue * percent);
        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static boolean isTeammate(Entity entity) {
        if (mc.player != null && entity instanceof LivingEntity) {
            LivingEntity teamMate = (LivingEntity) entity;
            if (mc.player.isInSameTeam(teamMate)) {
                return true;
            }

            String teammateName = Team.getMemberDisplayName(teamMate.getScoreboardTeam(), teamMate.getName());
            String teamColor = getFirstColorCode(teammateName);
            if (teamColor.isEmpty()) {
                return false;
            }

            String playerName = Team.getMemberDisplayName(mc.player.getScoreboardTeam(), mc.player.getName());
            return teamColor.equalsIgnoreCase(getFirstColorCode(playerName))
                    || teamColor.equalsIgnoreCase(getFirstColorCode(getNetworkDisplayName()));
        } else {
            return false;
        }
    }

    public static String getNetworkDisplayName() {
        try {
            PlayerInfo playerInfo = mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
            return Team.getMemberDisplayName(
                    playerInfo.getTeam(), playerInfo.getProfile().getName());
        } catch (Exception var1) {
            return "";
        }
    }

    public static void setSpeed(double n) {
        if (n == 0.0) {
            mc.player.velocityZ = 0.0;
            mc.player.velocityX = 0.0;
        } else {
            float n3 = n();
            mc.player.velocityX = -Math.sin(n3) * n;
            mc.player.velocityZ = Math.cos(n3) * n;
        }
    }

    public static void resetTimer() {
        ((IAccessorMinecraft) mc).getTimer().tpsScale = 1.0F;
    }

    public static void beginLocalPlayerSubUpdate() {
        LOCAL_PLAYER_SUB_UPDATE_DEPTH.set(LOCAL_PLAYER_SUB_UPDATE_DEPTH.get() + 1);
    }

    public static void endLocalPlayerSubUpdate() {
        int depth = LOCAL_PLAYER_SUB_UPDATE_DEPTH.get() - 1;
        LOCAL_PLAYER_SUB_UPDATE_DEPTH.set(Math.max(0, depth));
    }

    public static boolean isLocalPlayerSubUpdate() {
        return LOCAL_PLAYER_SUB_UPDATE_DEPTH.get() > 0;
    }

    public static boolean inInventory() {
        return !nullCheck()
                ? false
                : mc.screen != null
                        && mc.player.playerMenu != null
                        && mc.player.playerMenu instanceof PlayerMenu
                        && mc.screen instanceof SurvivalInventoryScreen;
    }

    public static int getSkyWarsStatus() {
        return getSkyWarsStatus(getSidebarLines());
    }

    public static int getSkyWarsStatus(List<String> sidebar) {
        if (sidebar.isEmpty()) {
            return -1;
        }

        if (!stripColor(sidebar.get(0)).startsWith("SKYWARS")) {
            return -1;
        }

        for (String line : sidebar) {
            line = stripColor(line);
            if (line.equals("Waiting...") || line.startsWith("Starting in ")) {
                return 1;
            }

            if (line.startsWith("Players left: ")) {
                return 2;
            }
        }

        return 0;
    }

    public static String getString(JsonObject type, String member) {
        try {
            return type.get(member).getAsString();
        } catch (Exception er) {
            return "";
        }
    }

    public static int getBedwarsStatus() {
        if (!nullCheck()) {
            return -1;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            return -1;
        }

        ScoreboardObjective objective = scoreboard.getDisplayObjective(1);
        if (objective != null) {
            String title = objective.getDisplayName();
            if (!stripString(title).contains("BED WARS")) {
                return -1;
            }
            List<String> lines = readSidebarLines(scoreboard, reverseSidebarScores(scoreboard, objective), title);
            if (lines.size() == 1) {
                return -1;
            }
            for (String line : lines) {
                line = stripString(line);
                String[] parts = line.split("  ");
                if (parts.length <= 1) {
                    if (!line.equals("Waiting...") && !line.startsWith("Starting in")) {
                        if (!line.startsWith("R Red:") && !line.startsWith("B Blue:")) {
                            continue;
                        }

                        return 2;
                    }

                    return 1;
                } else if (parts[1].startsWith("L")) {
                    return 0;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    public static String stripString(String s) {
        char[] nonValidatedString = StringUtils.stripFormatting(s).toCharArray();
        StringBuilder validated = new StringBuilder();

        for (char c : nonValidatedString) {
            if (c < 127 && c > 20) {
                validated.append(c);
            }
        }

        return validated.toString();
    }

    public static List<String> getSidebarLines() {
        List<String> lines = getSidebarLinesWithTitle();
        return lines == null || lines.size() == 1 ? new ArrayList<>() : lines;
    }

    /** Null without an objective; otherwise the title followed by up to 15 visible rows. */
    public static List<String> getSidebarLinesWithTitle() {
        Scoreboard scoreboard = mc.world == null ? null : mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null : scoreboard.getDisplayObjective(1);
        if (objective == null) {
            return null;
        }
        return readSidebarLines(scoreboard, reverseSidebarScores(scoreboard, objective), objective.getDisplayName());
    }

    private static List<String> readSidebarLines(
            Scoreboard scoreboard, ListIterator<ScoreboardScore> rows, String title) {
        List<String> lines = new ArrayList<>(16);
        lines.add(title);
        while (rows.hasPrevious() && lines.size() < 16) {
            ScoreboardScore score = rows.previous();
            String owner = score == null ? null : score.getOwner();
            if (owner != null && !owner.startsWith("#")) {
                lines.add(Team.getMemberDisplayName(scoreboard.getTeamOfMember(owner), owner));
            }
        }
        return lines;
    }

    private static ListIterator<ScoreboardScore> reverseSidebarScores(
            Scoreboard scoreboard, ScoreboardObjective objective) {
        Collection<ScoreboardScore> scores = scoreboard.getScores(objective);
        List<ScoreboardScore> ordered =
                scores instanceof List ? (List<ScoreboardScore>) scores : new ArrayList<>(scores);
        return ordered.listIterator(ordered.size());
    }

    private static String getSidebarFooter() {
        Scoreboard scoreboard = mc.world == null ? null : mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null : scoreboard.getDisplayObjective(1);
        if (objective == null) {
            return null;
        }
        ListIterator<ScoreboardScore> rows = reverseSidebarScores(scoreboard, objective);
        String footerOwner = null;
        int visibleRows = 0;
        while (rows.hasPrevious() && visibleRows < 15) {
            ScoreboardScore score = rows.previous();
            String owner = score == null ? null : score.getOwner();
            if (owner != null && !owner.startsWith("#")) {
                footerOwner = owner;
                visibleRows++;
            }
        }
        return footerOwner == null
                ? null
                : Team.getMemberDisplayName(scoreboard.getTeamOfMember(footerOwner), footerOwner);
    }

    public static boolean sidebarTitleContainsBedWars() {
        Scoreboard scoreboard = mc.world == null ? null : mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard == null ? null : scoreboard.getDisplayObjective(1);
        return objective != null && stripString(objective.getDisplayName()).contains("BED WARS");
    }

    public static Random getRandom() {
        return rand;
    }

    public static boolean isMoving() {
        return mc.player.forwardSpeed != 0.0F || mc.player.sidewaysSpeed != 0.0F;
    }

    public static void aim(Entity en, float offset, boolean sendPacket) {
        if (en != null) {
            float[] t = getRotationsOld(en);
            if (t != null) {
                float y = t[0];
                float p = t[1] + 4.0F + offset;
                if (sendPacket) {
                    mc.getNetworkHandler().sendPacket(new Angles(y, p, mc.player.onGround));
                } else {
                    mc.player.yaw = y;
                    mc.player.pitch = p;
                }
            }
        }
    }

    public static float[] getRotationsOld(Entity q) {
        if (q == null) {
            return null;
        }

        double diffX = q.x - mc.player.x;
        double diffY;
        if (q instanceof LivingEntity) {
            LivingEntity en = (LivingEntity) q;
            diffY = en.y + en.getEyeHeight() * 0.9 - (mc.player.y + mc.player.getEyeHeight());
        } else {
            diffY = (q.getShape().minY + q.getShape().maxY) / 2.0 - (mc.player.y + mc.player.getEyeHeight());
        }

        double diffZ = q.z - mc.player.z;
        double dist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(diffY, dist) * 180.0 / Math.PI));
        return new float[] {
            mc.player.yaw + MathHelper.wrapDegrees(yaw - mc.player.yaw),
            mc.player.pitch + MathHelper.wrapDegrees(pitch - mc.player.pitch)
        };
    }

    public static double aimDifference(Entity en, boolean useServerYaw) {
        return (((useServerYaw ? RotationUtils.serverRotations[0] : mc.player.yaw) - getYaw(en)) % 360.0 + 540.0)
                        % 360.0
                - 180.0;
    }

    public static double pitchDifference(Entity en, boolean useServerPitch) {
        return (((useServerPitch ? RotationUtils.serverRotations[1] : mc.player.pitch) - getPitch(en)) % 360.0 + 540.0)
                        % 360.0
                - 180.0;
    }

    public static float getYaw(Entity ent) {
        double x = ent.x - mc.player.x;
        double z = ent.z - mc.player.z;
        double yaw = Math.atan2(x, z) * (180.0 / Math.PI);
        return (float) (yaw * -1.0);
    }

    public static float getPitch(Entity ent) {
        double x = ent.x - mc.player.x;
        double z = ent.z - mc.player.z;
        double y = ent.y + ent.getEyeHeight() / 2.0F - (mc.player.y + mc.player.getEyeHeight());
        double pitch = Math.atan2(y, Math.sqrt(x * x + z * z)) * (180.0 / Math.PI);
        return (float) (pitch * -1.0);
    }

    public static void switchSlot(int slot, boolean instant) {
        mc.player.inventory.selectedSlot = slot;
        if (instant) {
            ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
        }
    }

    public static HitResult getTarget(double reach, float yaw, float pitch) {
        Vec3d eyeVec = mc.player.getEyePosition(1.0F);
        float y = -yaw * (float) (Math.PI / 180.0);
        float p = -pitch * (float) (Math.PI / 180.0);
        float f = MathHelper.cos(y - (float) Math.PI);
        float f2 = MathHelper.sin(y - (float) Math.PI);
        float f3 = -MathHelper.cos(p);
        float f4 = MathHelper.sin(p);
        Vec3d lookVec = new Vec3d(f2 * f3, f4, f * f3);
        Vec3d sumVec = eyeVec.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        return mc.world.rayTrace(eyeVec, sumVec, false, false, false);
    }

    public static boolean isPossibleToReach(BlockPos pos, double reach) {
        float[] rot = RotationUtils.getRotations(pos);
        Vec3d eyeVec = mc.player.getEyePosition(1.0F);
        float y = -rot[0] * (float) (Math.PI / 180.0);
        float p = -rot[1] * (float) (Math.PI / 180.0);
        float f = MathHelper.cos(y - (float) Math.PI);
        float f2 = MathHelper.sin(y - (float) Math.PI);
        float f3 = -MathHelper.cos(p);
        float f4 = MathHelper.sin(p);
        Vec3d lookVec = new Vec3d(f2 * f3, f4, f * f3);
        Vec3d sumVec = eyeVec.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
        Box axis = BlockUtils.getBlock(pos).getCollisionShape(mc.world, pos, BlockUtils.getBlockState(pos));
        if (axis == null) {
            return false;
        }

        HitResult mop = axis.clip(eyeVec, sumVec);
        return mop != null;
    }

    public static void setSpeed(double val, boolean checkMoving) {
        if (!checkMoving || isMoving()) {
            mc.player.velocityX = -Math.sin(gd()) * val;
            mc.player.velocityZ = Math.cos(gd()) * val;
        }
    }

    public static boolean keysDown() {
        return Keyboard.isKeyDown(mc.options.forwardKey.getKeyCode())
                || Keyboard.isKeyDown(mc.options.backKey.getKeyCode())
                || Keyboard.isKeyDown(mc.options.leftKey.getKeyCode())
                || Keyboard.isKeyDown(mc.options.rightKey.getKeyCode());
    }

    public static boolean jumpDown() {
        return Keyboard.isKeyDown(mc.options.jumpKey.getKeyCode());
    }

    public static double distanceToGround(Entity entity) {
        if (entity.onGround) {
            return 0.0;
        }

        double fallDistance = -1.0;
        double y = entity.y;
        if (entity.y % 1.0 == 0.0) {
            y--;
        }

        for (int i = (int) Math.floor(y); i > -1; i--) {
            if (!isPlaceable(new BlockPos(entity.x, i, entity.z))) {
                fallDistance = y - i;
                break;
            }
        }

        return fallDistance - 1.0;
    }

    public static float gd() {
        Float yaw = ModuleManager.targetStrafe == null ? null : ModuleManager.targetStrafe.getMovementYaw();
        float yw = yaw == null ? mc.player.yaw : yaw;
        if (mc.player.forwardSpeed < 0.0F) {
            yw += 180.0F;
        }

        float f;
        if (mc.player.forwardSpeed < 0.0F) {
            f = -0.5F;
        } else if (mc.player.forwardSpeed > 0.0F) {
            f = 0.5F;
        } else {
            f = 1.0F;
        }

        if (mc.player.sidewaysSpeed > 0.0F) {
            yw -= 90.0F * f;
        }

        if (mc.player.sidewaysSpeed < 0.0F) {
            yw += 90.0F * f;
        }

        return yw * (float) (Math.PI / 180.0);
    }

    public static float ae(float n, float n2, float n3) {
        float n4 = 1.0F;
        if (n2 < 0.0F) {
            n += 180.0F;
            n4 = -0.5F;
        } else if (n2 > 0.0F) {
            n4 = 0.5F;
        }

        if (n3 > 0.0F) {
            n -= 90.0F * n4;
        } else if (n3 < 0.0F) {
            n += 90.0F * n4;
        }

        return n * (float) (Math.PI / 180.0);
    }

    public static double getHorizontalSpeed() {
        return getHorizontalSpeed(mc.player);
    }

    public static double getHorizontalSpeed(Entity entity) {
        return Math.sqrt(entity.velocityX * entity.velocityX + entity.velocityZ * entity.velocityZ);
    }

    public static List<String> getTopLevelLines(String fileContents) {
        List<String> topLevelLines = new ArrayList<>();
        String[] lines = fileContents.split("\\r?\\n");
        int braceLevel = 0;
        boolean[] inBlockComment = new boolean[] {false};

        for (String line : lines) {
            String processedLine = stripComments(line, inBlockComment).trim();
            if (!processedLine.isEmpty()) {
                String lineWithoutStrings = removeStringLiterals(processedLine);
                int openBraces = 0;
                int closeBraces = 0;

                for (char ch : lineWithoutStrings.toCharArray()) {
                    if (ch == '{') {
                        openBraces++;
                    } else if (ch == '}') {
                        closeBraces++;
                    }
                }

                braceLevel += openBraces - closeBraces;
                if (braceLevel == 0
                        && !lineWithoutStrings.contains("{")
                        && !lineWithoutStrings.contains("}")
                        && !processedLine.startsWith("@")) {
                    topLevelLines.add(processedLine);
                }
            }
        }

        return topLevelLines;
    }

    private static String stripComments(String line, boolean[] inBlockComment) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\u0000';
            if (inBlockComment[0]) {
                if (ch == '*' && next == '/') {
                    inBlockComment[0] = false;
                    i++;
                }
            } else if (!inString && !inCharacter) {
                if (ch == '/' && next == '/') {
                    break;
                }

                if (ch == '/' && next == '*') {
                    inBlockComment[0] = true;
                    i++;
                } else {
                    result.append(ch);
                    if (ch == '"') {
                        inString = true;
                    } else if (ch == '\'') {
                        inCharacter = true;
                    }
                }
            } else {
                result.append(ch);
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (inString && ch == '"' || inCharacter && ch == '\'') {
                    inString = false;
                    inCharacter = false;
                }
            }
        }

        return result.toString();
    }

    public static boolean holdingEdible(ItemStack stack) {
        if (stack.getItem() instanceof FoodItem && mc.player.getHungerManager().getFoodLevel() == 20) {
            FoodItem food = (FoodItem) stack.getItem();
            return ((IAccessorItemFood) food).getAlwaysEdible();
        } else {
            return true;
        }
    }

    private static String removeStringLiterals(String line) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (!inString && !inCharacter) {
                if (ch == '"') {
                    inString = true;
                } else if (ch == '\'') {
                    inCharacter = true;
                } else {
                    sb.append(ch);
                }
            } else if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (inString && ch == '"' || inCharacter && ch == '\'') {
                inString = false;
                inCharacter = false;
            }
        }

        return sb.toString();
    }

    public static boolean blockAbove() {
        return !(BlockUtils.getBlock(new BlockPos(mc.player.x, mc.player.y + 2.0, mc.player.z)) instanceof AirBlock);
    }

    public static boolean onEdge() {
        return onEdge(mc.player);
    }

    public static boolean onEdge(Entity entity) {
        return mc.world
                .getCollisions(entity, entity.getShape().moved(entity.velocityX / 3.0, -1.0, entity.velocityZ / 3.0))
                .isEmpty();
    }

    public static boolean lookingAtBlock() {
        return mc.crosshairTarget != null
                && mc.crosshairTarget.type == Type.BLOCK
                && mc.crosshairTarget.getPos() != null;
    }

    public static boolean isDiagonal(boolean strict) {
        float yaw = (mc.player.yaw % 360.0F + 360.0F) % 360.0F;
        yaw = yaw > 180.0F ? yaw - 360.0F : yaw;
        boolean isYawDiagonal = inBetween(-170.0, 170.0, yaw)
                && !inBetween(-10.0, 10.0, yaw)
                && !inBetween(80.0, 100.0, yaw)
                && !inBetween(-100.0, -80.0, yaw);
        if (strict) {
            isYawDiagonal = inBetween(-178.5, 178.5, yaw)
                    && !inBetween(-1.5, 1.5, yaw)
                    && !inBetween(88.5, 91.5, yaw)
                    && !inBetween(-91.5, -88.5, yaw);
        }

        boolean isStrafing = Keyboard.isKeyDown(mc.options.leftKey.getKeyCode())
                || Keyboard.isKeyDown(mc.options.rightKey.getKeyCode());
        return isYawDiagonal || isStrafing;
    }

    public static double gbps(Entity en, int d) {
        double x = en.x - en.lastX;
        double z = en.z - en.lastZ;
        double sp = Math.sqrt(x * x + z * z) * 20.0;
        return d == 0 ? sp : round(sp, d);
    }

    public static boolean inBetween(double min, double max, double value) {
        return value >= min && value <= max;
    }

    public static String removeFormatCodes(String str) {
        return str.replace("§k", "")
                .replace("§l", "")
                .replace("§m", "")
                .replace("§n", "")
                .replace("§o", "")
                .replace("§r", "");
    }

    public static boolean isClicking() {
        return ModuleManager.autoClicker != null && ModuleManager.autoClicker.isEnabled()
                ? Mouse.isButtonDown(0)
                : MouseHelper.f() > 1 && System.currentTimeMillis() - MouseHelper.LL < 300L;
    }

    public static boolean isMining() {
        int keyCode = mc.options.attackKey.getKeyCode();
        if (keyCode == 0) {
            return false;
        }

        boolean attackDown = keyCode < 0 ? Mouse.isButtonDown(keyCode + 100) : Keyboard.isKeyDown(keyCode);
        if (!attackDown) {
            return false;
        }

        double reach = mc.interactionManager.getReach();
        float yaw = mc.player.yaw;
        float pitch = mc.player.pitch;
        HitResult entityHit = RotationUtils.rayTrace(reach, 1.0F, new float[] {yaw, pitch}, null);
        if (entityHit != null && entityHit.type == Type.ENTITY) {
            return false;
        }

        HitResult blockHit = RotationUtils.rayCastBlock(reach, yaw, pitch);
        return blockHit != null && blockHit.type == Type.BLOCK && blockHit.getPos() != null;
    }

    public static boolean isEdgeOfBlock() {
        BlockPos pos = new BlockPos(mc.player.x, mc.player.y - (mc.player.y % 1.0 == 0.0 ? 1 : 0), mc.player.z);
        return mc.world.isAir(pos);
    }

    public static long timeBetween(long val, long val2) {
        return Math.abs(val2 - val);
    }

    public static void sendModuleMessage(Module module, String s) {
        sendRawMessage("&3" + module.getName() + "&7: &r" + s);
    }

    public static LivingEntity raytrace(int range) {
        Entity entity = null;
        PlayerEntity self = (PlayerEntity) (Freecam.freeEntity == null ? mc.player : Freecam.freeEntity);
        HitResult rayTrace = self.rayTrace(range, 1.0F);
        Vec3d getPositionEyes = self.getEyePosition(1.0F);
        float rotationYaw = self.yaw;
        float rotationPitch = self.pitch;
        float cos = MathHelper.cos(-rotationYaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float sin = MathHelper.sin(-rotationYaw * (float) (Math.PI / 180.0) - (float) Math.PI);
        float n2 = -MathHelper.cos(-rotationPitch * (float) (Math.PI / 180.0));
        Vec3d vec3 = new Vec3d(sin * n2, MathHelper.sin(-rotationPitch * (float) (Math.PI / 180.0)), cos * n2);
        Vec3d addVector = getPositionEyes.add(vec3.x * range, vec3.y * range, vec3.z * range);
        Vec3d vec4 = null;
        List getEntitiesWithinAABBExcludingEntity = mc.world.getEntities(
                mc.getCamera(),
                mc.getCamera()
                        .getShape()
                        .expanded(vec3.x * range, vec3.y * range, vec3.z * range)
                        .grown(1.0, 1.0, 1.0));
        double n3 = range;

        for (int i = 0; i < getEntitiesWithinAABBExcludingEntity.size(); i++) {
            Entity entity2 = (Entity) getEntitiesWithinAABBExcludingEntity.get(i);
            if (entity2.hasCollision()) {
                float getCollisionBorderSize = entity2.getPickRadius();
                Box expand = entity2.getShape()
                        .grown(getCollisionBorderSize, getCollisionBorderSize, getCollisionBorderSize);
                HitResult calculateIntercept = expand.clip(getPositionEyes, addVector);
                if (expand.contains(getPositionEyes)) {
                    if (0.0 < n3 || n3 == 0.0) {
                        entity = entity2;
                        vec4 = calculateIntercept == null ? getPositionEyes : calculateIntercept.facePos;
                        n3 = 0.0;
                    }
                } else if (calculateIntercept != null) {
                    double distanceTo = getPositionEyes.distanceTo(calculateIntercept.facePos);
                    if (distanceTo < n3 || n3 == 0.0) {
                        if (entity2 != mc.getCamera().vehicle || ClientPlatform.canRiderInteract(entity2)) {
                            entity = entity2;
                            vec4 = calculateIntercept.facePos;
                            n3 = distanceTo;
                        } else if (n3 == 0.0) {
                            entity = entity2;
                            vec4 = calculateIntercept.facePos;
                        }
                    }
                }
            }
        }

        if (entity != null && (n3 < range || rayTrace == null)) {
            rayTrace = new HitResult(entity, vec4);
        }

        return rayTrace != null && rayTrace.type == Type.ENTITY && rayTrace.entity instanceof LivingEntity
                ? (LivingEntity) rayTrace.entity
                : null;
    }

    public static int getChroma(long speed, long... delay) {
        long time = System.currentTimeMillis() + (delay.length > 0 ? delay[0] : 0L);
        return Color.getHSBColor((float) (time % (15000L / speed)) / (15000.0F / (float) speed), 1.0F, 1.0F)
                .getRGB();
    }

    public static double round(double val, int decimalPlaces) {
        if (decimalPlaces == 0) {
            return Math.round(val);
        }

        double p = Math.pow(10.0, decimalPlaces);
        return Math.round(val * p) / p;
    }

    public static String stripColor(String string) {
        if (string.isEmpty()) {
            return string;
        }

        char[] array = StringUtils.stripFormatting(string).toCharArray();
        StringBuilder sb = new StringBuilder();

        for (char c : array) {
            if (c < 127 && c > 20) {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public static List<String> getScoreBoardOld() {
        List<String> lines = new ArrayList<>();
        if (mc.world == null) {
            return lines;
        }

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            return lines;
        }

        ScoreboardObjective objective = scoreboard.getDisplayObjective(1);
        if (objective == null) {
            return lines;
        }

        Collection<ScoreboardScore> scores = scoreboard.getScores(objective);
        List<ScoreboardScore> list = new ArrayList<>();

        for (ScoreboardScore score : scores) {
            if (score != null && score.getOwner() != null && !score.getOwner().startsWith("#")) {
                list.add(score);
            }
        }

        if (list.size() > 15) {
            scores = Lists.newArrayList(Iterables.skip(list, scores.size() - 15));
        } else {
            scores = list;
        }

        for (ScoreboardScore score : scores) {
            Team team = scoreboard.getTeamOfMember(score.getOwner());
            lines.add(Team.getMemberDisplayName(team, score.getOwner()));
        }

        return lines;
    }

    public static void setSwinging() {
        int armSwingEnd = mc.player.hasStatusEffect(StatusEffect.HASTE)
                ? 6 - (1 + mc.player.getEffectInstance(StatusEffect.HASTE).getAmplifier())
                : (mc.player.hasStatusEffect(StatusEffect.MINING_FATIGUE)
                        ? 6
                                + (1
                                                + mc.player
                                                        .getEffectInstance(StatusEffect.MINING_FATIGUE)
                                                        .getAmplifier())
                                        * 2
                        : 6);
        if (!mc.player.armSwinging || mc.player.armSwingingTicks >= armSwingEnd / 2 || mc.player.armSwingingTicks < 0) {
            mc.player.armSwingingTicks = -1;
            mc.player.armSwinging = true;
        }
    }

    public static String uppercaseFirst(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static boolean isPlaceable(BlockPos blockPos) {
        return BlockUtils.replaceable(blockPos) || BlockUtils.isFluid(BlockUtils.getBlock(blockPos));
    }

    public static boolean spectatorCheck() {
        return mc.player.inventory.getItem(8) != null
                        && mc.player.inventory.getItem(8).getHoverName().contains("Return")
                || stripString(((IAccessorGuiIngame) mc.gui).getDisplayedTitle())
                        .contains("YOU DIED");
    }

    public static boolean holdingWeapon() {
        return holdingWeapon(mc.player);
    }

    public static boolean holdingWeapon(LivingEntity entityLivingBase) {
        if (entityLivingBase.getDisplayItemInHand() == null) {
            return Settings.weaponFist.isToggled();
        }

        Item getItem = entityLivingBase.getDisplayItemInHand().getItem();
        return getItem instanceof SwordItem
                || Settings.weaponAxe.isToggled() && getItem instanceof AxeItem
                || Settings.weaponRod.isToggled() && getItem instanceof FishingRodItem
                || Settings.weaponStick.isToggled() && getItem == Items.STICK
                || Settings.weaponHoe.isToggled() && getItem instanceof HoeItem
                || Settings.weaponShovel.isToggled() && getItem instanceof ShovelItem
                || Settings.weaponEnchanted.isToggled()
                        && entityLivingBase.getDisplayItemInHand().hasEnchantments();
    }

    public static boolean holdingSword() {
        return mc.player.getDisplayItemInHand() == null
                ? false
                : mc.player.getDisplayItemInHand().getItem() instanceof SwordItem;
    }

    public static double getDamageLevel(ItemStack itemStack) {
        return ItemSortScoring.getMeleeDamage(itemStack);
    }

    public static float getDirection() {
        return getCustomDirection(mc.player.yaw, mc.player.input.movementForward, mc.player.input.movementSideways);
    }

    public static boolean isUserMoving() {
        return mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F;
    }

    public static float getCustomDirection(float yaw, float moveForward, float moveStrafe) {
        float forward = 1.0F;
        if (moveForward < 0.0F) {
            yaw += 180.0F;
            forward = -0.5F;
        } else if (moveForward > 0.0F) {
            forward = 0.5F;
        }

        if (moveStrafe > 0.0F) {
            yaw -= 90.0F * forward;
        } else if (moveStrafe < 0.0F) {
            yaw += 90.0F * forward;
        }

        return yaw * (float) (Math.PI / 180.0);
    }

    public static boolean canBePlaced(BlockItem itemBlock) {
        Block block = itemBlock.getBlock();
        return block == null
                ? false
                : !BlockUtils.isInteractable(block)
                        && !(block instanceof SnowLayerBlock)
                        && !(block instanceof CobwebBlock)
                        && !(block instanceof SaplingBlock)
                        && !(block instanceof DaylightDetectorBlock)
                        && !(block instanceof BeaconBlock)
                        && !(block instanceof BannerBlock)
                        && !(block instanceof EndPortalFrameBlock)
                        && !(block instanceof EndPortalBlock)
                        && !(block instanceof LeverBlock)
                        && !(block instanceof ButtonBlock)
                        && !(block instanceof SkullBlock)
                        && !(block instanceof LiquidBlock)
                        && !(block instanceof CactusBlock)
                        && !(block instanceof DoublePlantBlock)
                        && !(block instanceof LilyPadBlock)
                        && !(block instanceof CarpetBlock)
                        && !(block instanceof TripwireBlock)
                        && !(block instanceof TripwireHookBlock)
                        && !(block instanceof TallPlantBlock)
                        && !(block instanceof FlowerBlock)
                        && !(block instanceof FlowerPotBlock)
                        && !(block instanceof SignBlock)
                        && !(block instanceof LadderBlock)
                        && !(block instanceof TorchBlock)
                        && !(block instanceof RedstoneTorchBlock)
                        && !(block instanceof StairsBlock)
                        && !(block instanceof SlabBlock)
                        && !(block instanceof FenceBlock)
                        && !(block instanceof PaneBlock)
                        && !(block instanceof StainedGlassPaneBlock)
                        && !(block instanceof GravelBlock)
                        && !(block instanceof ClayBlock)
                        && !(block instanceof SandBlock)
                        && !(block instanceof SoulSandBlock)
                        && !(block instanceof AbstractRailBlock);
    }

    public static <E extends Enum<E>> E getEnum(Class<E> enumClass, String value) {
        for (E enumConstant : enumClass.getEnumConstants()) {
            if (enumConstant.name().equals(value)) {
                return enumConstant;
            }
        }

        return null;
    }

    public static int getSpeedAmplifier() {
        return mc.player.hasStatusEffect(StatusEffect.SPEED)
                ? 1 + mc.player.getEffectInstance(StatusEffect.SPEED).getAmplifier()
                : 0;
    }

    public static ItemStack getSpoofedItem(ItemStack original) {
        Scaffold scaffold = (Scaffold) ModuleManager.getModule(Scaffold.class);
        if (scaffold != null && mc.player != null && scaffold.getSpoofedSlot() != -1) {
            return mc.player.inventory.getItem(scaffold.getSpoofedSlot());
        } else {
            return ModuleManager.autoTool != null
                            && ModuleManager.autoTool.isEnabled()
                            && ModuleManager.autoTool.spoofItem.isToggled()
                            && mc.player != null
                    ? mc.player.inventory.getItem(
                            ModuleManager.autoTool.previousSlot == -1
                                    ? mc.player.inventory.selectedSlot
                                    : ModuleManager.autoTool.previousSlot)
                    : original;
        }
    }

    public static boolean scaffoldDiagonal(boolean strict) {
        return false;
    }

    public static String readInputStream(InputStream inputStream) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return stringBuilder.toString();
    }

    public static boolean isLobby() {
        if (isHypixel()) {
            List<String> sidebarLines = getSidebarLines();
            if (!sidebarLines.isEmpty()) {
                String[] parts = stripColor(sidebarLines.get(1)).split("  ");
                if (parts.length > 1 && parts[1].charAt(0) == 'L') {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isReplay() {
        if (nullCheck() && isHypixel()) {
            Scoreboard scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) {
                return false;
            }

            ScoreboardObjective objective = scoreboard.getDisplayObjective(1);
            return objective != null && stripString(objective.getDisplayName()).equals("REPLAY");
        } else {
            return false;
        }
    }

    public static boolean isBedwarsPracticeOrReplay() {
        if (isHypixel()) {
            if (!nullCheck()) {
                return false;
            }

            Scoreboard scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) {
                return false;
            }

            ScoreboardObjective objective = scoreboard.getDisplayObjective(1);
            if (objective == null) {
                return false;
            }

            String stripped = stripString(objective.getDisplayName());
            return stripped.contains("BED WARS PRACTICE") || stripped.contains("REPLAY");
        } else {
            return false;
        }
    }

    public static Vec3d getClosestPlayerPos(double maxDistSq) {
        if (mc.world != null && mc.player != null) {
            Vec3d closest = null;
            double bestDist = maxDistSq;

            for (PlayerEntity player : mc.world.players) {
                if (player != mc.player
                        && mc.getNetworkHandler() != null
                        && mc.getNetworkHandler().getOnlinePlayer(player.getUuid()) != null) {
                    double dx = player.x - mc.player.x;
                    double dy = player.y - mc.player.y;
                    double dz = player.z - mc.player.z;
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        closest = new Vec3d(player.x, player.y, player.z);
                    }
                }
            }

            return closest;
        } else {
            return null;
        }
    }

    /** Mode names for a {@link SliderSetting} made from an enum. */
    public static <E extends Enum<E>> String[] enumNames(Class<E> enumClass) {
        E[] constants = enumClass.getEnumConstants();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
    }

    /** The enum constant currently selected in a mode {@link SliderSetting} built with {@link #enumNames}. */
    public static <E extends Enum<E>> E enumOption(Class<E> enumClass, SliderSetting setting) {
        E[] constants = enumClass.getEnumConstants();
        int index = (int) Math.max(0, Math.min(constants.length - 1, Math.round(setting.getInput())));
        return constants[index];
    }

    /** Opens a folder in the system file manager (creates it first). */
    public static boolean openFolder(java.io.File folder) {
        folder.mkdirs();
        String path = folder.getAbsolutePath();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[] {"/usr/bin/open", path});
                return true;
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[] {"cmd.exe", "/C", "start", "\"Open folder\"", path});
                return true;
            } else if (os.contains("nux") || os.contains("nix")) {
                Runtime.getRuntime().exec(new String[] {"xdg-open", path});
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(folder);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}

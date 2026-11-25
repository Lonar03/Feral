package keystrokesmod.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityPlayer;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiIngame;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiPlayerTabOverlay;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiScreenBook;
import keystrokesmod.mixin.impl.accessor.IAccessorItemRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.impl.accessor.IAccessorNetworkManager;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AimAssist;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.network.Backtrack;
import keystrokesmod.module.impl.network.LagRange;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.script.model.Block;
import keystrokesmod.script.model.Bridge;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.Image;
import keystrokesmod.script.model.ItemStack;
import keystrokesmod.script.model.Message;
import keystrokesmod.script.model.NetworkPlayer;
import keystrokesmod.script.model.PlayerState;
import keystrokesmod.script.model.ScriptFont;
import keystrokesmod.script.model.TileEntity;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.script.packet.clientbound.SPacket;
import keystrokesmod.script.packet.serverbound.CPacket;
import keystrokesmod.script.packet.serverbound.PacketHandler;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.shader.BlurUtils;
import keystrokesmod.utility.shader.RoundedUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.pack.ResourcePacks.UnopenedPack;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.menu.CraftingTableMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.network.packet.Packet;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class ScriptDefaults {
    private static ExecutorService cachedExecutor;
    private static final Minecraft mc = Minecraft.getInstance();
    public static final Bridge bridge = new Bridge();
    private static final LinkedHashMap<String, Module> modulesMap = new LinkedHashMap<>();

    static String getScreenName(Screen screen) {
        return screen == null ? "" : ScriptClassNames.simpleName(screen.getClass());
    }

    public static void reloadModules() {
        modulesMap.clear();

        for (Module module : Feral.getModuleManager().getModules()) {
            modulesMap.put(module.getName(), module);
        }

        for (Module module : Feral.scriptManager.scripts.values()) {
            modulesMap.put(module.getName(), module);
        }
    }

    public static class client {
        public static boolean allowFlying() {
            return ScriptDefaults.mc.player.abilities.canFly;
        }

        public static void setItemInUse(boolean inUse) {
            ReflectionUtils.setItemInUse(inUse);
        }

        public static void setTabName(String name, String displayName) {
            if (name != null && displayName != null && ScriptDefaults.mc.getNetworkHandler() != null) {
                for (PlayerInfo info : ScriptDefaults.mc.getNetworkHandler().getOnlinePlayers()) {
                    if (info.getProfile() != null
                            && name.equalsIgnoreCase(info.getProfile().getName())) {
                        info.setDisplayName(new LiteralText(displayName));
                        return;
                    }
                }
            }
        }

        public static long nanoTime() {
            return System.nanoTime();
        }

        public static void removePotionEffect(int id) {
            if (ScriptDefaults.mc.player != null) {
                ScriptDefaults.mc.player.removeEffect(id);
            }
        }

        public static int getUID() {
            return 4;
        }

        public static String getUser() {
            return ScriptDefaults.mc.player.getName();
        }

        public static void addEnemy(String username) {
            Utils.addEnemy(username);
        }

        public static void addFriend(String username) {
            Utils.addFriend(username);
        }

        public static void async(Runnable method) {
            if (ScriptDefaults.cachedExecutor == null) {
                ScriptDefaults.cachedExecutor = Executors.newCachedThreadPool();
            }

            ScriptDefaults.cachedExecutor.execute(method);
        }

        public static int getFPS() {
            return Minecraft.getCurrentFps();
        }

        public static void chat(String message) {
            ScriptDefaults.mc.player.sendChat(message);
        }

        public static void print(String string) {
            Utils.sendRawMessage(string);
        }

        public static void print(Message component) {
            ScriptDefaults.mc.player.sendMessage(component.component);
        }

        public static void print(Object object) {
            String s = String.valueOf(object);
            Utils.sendRawMessage(s);
        }

        public static boolean isDiagonal() {
            return Utils.isDiagonal(false);
        }

        public static void setTimer(float timer) {
            ((IAccessorMinecraft) ScriptDefaults.mc).getTimer().tpsScale = timer;
        }

        public static void setTimer(double timer) {
            ((IAccessorMinecraft) ScriptDefaults.mc).getTimer().tpsScale = (float) timer;
        }

        public static boolean isCreative() {
            return ScriptDefaults.mc.player.abilities.creativeMode;
        }

        public static void processPacket(SPacket packet) {
            packet.packet.handle(((IAccessorNetworkManager)
                            ScriptDefaults.mc.getNetworkHandler().getConnection())
                    .getPacketListener());
        }

        public static void multiplyMotion(double factor) {
            ScriptDefaults.mc.player.velocityZ *= factor;
            ScriptDefaults.mc.player.velocityX *= factor;
        }

        public static void processPacketNoEvent(SPacket packet) {
            PacketUtils.receivePacketNoEvent(packet.packet);
        }

        public static String getTitle() {
            return ((IAccessorGuiIngame) ScriptDefaults.mc.gui).getDisplayedTitle();
        }

        public static String getSubTitle() {
            return ((IAccessorGuiIngame) ScriptDefaults.mc.gui).getDisplayedSubTitle();
        }

        public static String getRecordPlaying() {
            return ((IAccessorGuiIngame) ScriptDefaults.mc.gui).getRecordPlaying();
        }

        public static boolean isFlying() {
            return ScriptDefaults.mc.player.abilities.flying;
        }

        public static void attack(Entity entity) {
            Utils.attackEntity(entity.entity, true, true);
        }

        public static boolean isSinglePlayer() {
            return ScriptDefaults.mc.isSingleplayer();
        }

        public static boolean isSpectator() {
            return ScriptDefaults.mc.player.isSpectator();
        }

        public static void setFlying(boolean flying) {
            ScriptDefaults.mc.player.abilities.flying = flying;
        }

        public static void setJump(boolean jump) {
            ScriptDefaults.mc.player.input.jumping = jump;
        }

        public static void setJumping(boolean jump) {
            ScriptDefaults.mc.player.setJumping(jump);
        }

        public static void setRenderArmPitch(float pitch) {
            ScriptDefaults.mc.player.lastEasedPitch = pitch;
            ScriptDefaults.mc.player.easedPitch = pitch;
        }

        public static float getEquippedProgress() {
            return ((IAccessorItemRenderer) ScriptDefaults.mc.getItemInHandRenderer()).getEquippedProgress();
        }

        public static void disconnect() {
            boolean isLocal = ScriptDefaults.mc.isIntegratedServerRunning();
            boolean isRealms = ScriptDefaults.mc.isConnectedToRealms();
            ScriptDefaults.mc.world.disconnect();
            ScriptDefaults.mc.setWorld(null);
            if (isLocal) {
                ScriptDefaults.mc.openScreen(new TitleScreen());
            } else if (isRealms) {
                new RealmsBridge().createMainMenuScreen(new TitleScreen());
            } else {
                ScriptDefaults.mc.openScreen(new MultiplayerScreen(new TitleScreen()));
            }
        }

        public static float getRenderArmPitch() {
            return ScriptDefaults.mc.player.easedPitch;
        }

        public static void setRenderArmYaw(float yaw) {
            ScriptDefaults.mc.player.lastEasedYaw = yaw;
            ScriptDefaults.mc.player.easedYaw = yaw;
        }

        public static float getRenderArmYaw() {
            return ScriptDefaults.mc.player.easedYaw;
        }

        public static long getTotalMemory() {
            return Runtime.getRuntime().totalMemory();
        }

        public static long getFreeMemory() {
            return Runtime.getRuntime().freeMemory();
        }

        public static long getMaxMemory() {
            return Runtime.getRuntime().maxMemory();
        }

        public static List<String[]> getResourcePacks() {
            List<String[]> packs = new ArrayList<>();
            if (ScriptDefaults.mc.getResourcePacks().getSelectedPacks() != null
                    && !ScriptDefaults.mc.getResourcePacks().getSelectedPacks().isEmpty()) {
                for (UnopenedPack entry : ScriptDefaults.mc.getResourcePacks().getSelectedPacks()) {
                    packs.add(new String[] {entry.getName(), entry.getDescription()});
                }
            } else {
                packs.add(new String[] {ScriptDefaults.mc.defaultResourcePack.getName(), ""});
            }

            Collections.reverse(packs);
            return packs;
        }

        public static void jump() {
            ScriptDefaults.mc.player.jump();
        }

        public static boolean allowEditing() {
            return ScriptDefaults.mc.player != null && ScriptDefaults.mc.player.abilities != null
                    ? ScriptDefaults.mc.player.abilities.canModifyWorld
                    : false;
        }

        public static void setItemInUseCount(int count) {
            ((IAccessorEntityPlayer) ScriptDefaults.mc.player).setItemInUseCount(count);
        }

        public static int getItemInUseCount() {
            return ScriptDefaults.mc.player.getItemUseTimer();
        }

        public static int getItemInUseDuration() {
            return ScriptDefaults.mc.player.getRemainingItemUseDuration();
        }

        public static void log(Object obj) {
            Utils.log.info(obj);
        }

        public static void setSneaking(boolean sneak) {
            ScriptDefaults.mc.player.setSneaking(sneak);
        }

        public static void setSneak(boolean sneak) {
            ScriptDefaults.mc.player.input.sneaking = sneak;
        }

        public static boolean isSneak() {
            return ScriptDefaults.mc.player.input.sneaking;
        }

        public static Entity getPlayer() {
            return ScriptDefaults.mc != null && ScriptDefaults.mc.player != null
                    ? Entity.convert(ScriptDefaults.mc.player)
                    : null;
        }

        public static void removeEnemy(String username) {
            Utils.removeEnemy(username);
        }

        public static void removeFriend(String username) {
            Utils.removeFriend(username);
        }

        public static boolean isRiding() {
            return ScriptDefaults.mc.player.isRiding();
        }

        public static void setRidingEntity(Entity entity) {
            if (entity == null) {
                ScriptDefaults.mc.player.vehicle = null;
            } else {
                ScriptDefaults.mc.player.vehicle = entity.entity;
            }
        }

        public static Vec3 getMotion() {
            return new Vec3(
                    ScriptDefaults.mc.player.velocityX,
                    ScriptDefaults.mc.player.velocityY,
                    ScriptDefaults.mc.player.velocityZ);
        }

        public static void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException var3) {
            }
        }

        public static void ping() {
            ScriptDefaults.mc.player.playSound("note.pling", 1.0F, 1.0F);
        }

        public static void playSound(String name, float volume, float pitch) {
            ScriptDefaults.mc.player.playSound(name, volume, pitch);
        }

        public static boolean isMoving() {
            return Utils.isMoving();
        }

        public static boolean isJump() {
            return ScriptDefaults.mc.player.input.jumping;
        }

        public static float getStrafe() {
            return ScriptDefaults.mc.player.input.movementSideways;
        }

        public static void sleep(int ms) {
            try {
                Thread.sleep(ms);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static float getForward() {
            return ScriptDefaults.mc.player.input.movementForward;
        }

        public static void closeScreen() {
            if (ScriptDefaults.mc.screen instanceof ClickGui) {
                ScriptDefaults.mc.openScreen(null);
            } else {
                ScriptDefaults.mc.player.closeMenu();
            }
        }

        public static String getScreen() {
            return ScriptDefaults.getScreenName(ScriptDefaults.mc.screen);
        }

        public static float[] getRotationsToEntity(Entity entity) {
            return RotationUtils.getRotations(entity.entity);
        }

        public static void sendPacket(CPacket packet) {
            Packet packet1 = PacketHandler.convertCPacket(packet);
            if (packet1 != null) {
                ScriptDefaults.mc.player.networkHandler.sendPacket(packet1);
            }
        }

        public static void sendPacketNoEvent(CPacket packet) {
            Packet packet1 = PacketHandler.convertCPacket(packet);
            if (packet1 != null) {
                PacketUtils.sendPacketNoEvent(packet1);
            }
        }

        public static boolean inFocus() {
            return ScriptDefaults.mc.focused;
        }

        public static void dropItem(boolean dropStack) {
            ScriptDefaults.mc.player.dropItem(dropStack);
        }

        public static void setMotion(double x, double y, double z) {
            ScriptDefaults.mc.player.velocityX = x;
            ScriptDefaults.mc.player.velocityY = y;
            ScriptDefaults.mc.player.velocityZ = z;
        }

        public static void setSpeed(double speed) {
            Utils.setSpeed(speed);
        }

        public static void setForward(float forward) {
            ScriptDefaults.mc.player.input.movementForward = forward;
        }

        public static void setStrafe(float strafe) {
            ScriptDefaults.mc.player.input.movementSideways = strafe;
        }

        public static String getServerIP() {
            return ScriptDefaults.mc.getCurrentServerEntry() != null && !ScriptDefaults.mc.isSingleplayer()
                    ? ScriptDefaults.mc.getCurrentServerEntry().ip
                    : "";
        }

        public static int[] getDisplaySize() {
            Window scaledResolution = new Window(ScriptDefaults.mc);
            return new int[] {scaledResolution.getWidth(), scaledResolution.getHeight(), scaledResolution.getScale()};
        }

        public static float getServerDirection(PlayerState state) {
            return state.yaw;
        }

        public static Object[] raycastBlock(double distance) {
            return raycastBlock(distance, ScriptDefaults.mc.player.yaw, ScriptDefaults.mc.player.pitch);
        }

        public static Object[] raycastBlock(double distance, float yaw, float pitch) {
            Vec3d eyeVec = ScriptDefaults.mc.player.getEyePosition(1.0F);
            Vec3d lookVec = Utils.getLookVec(yaw, pitch);
            Vec3d sumVec = eyeVec.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
            HitResult mop = ScriptDefaults.mc.world.rayTrace(eyeVec, sumVec, false, false, false);
            if (mop != null && mop.type == Type.BLOCK) {
                Vec3 pos = new Vec3(mop.getPos());
                Vec3 offset = new Vec3(mop.facePos.x - pos.x, mop.facePos.y - pos.y, mop.facePos.z - pos.z);
                return new Object[] {pos, offset, mop.face.name()};
            } else {
                return null;
            }
        }

        public static Object[] raycastEntity(double distance) {
            return raycastEntity(distance, ScriptDefaults.mc.player.yaw, ScriptDefaults.mc.player.pitch);
        }

        public static Object[] raycastEntity(double distance, float yaw, float pitch) {
            net.minecraft.entity.Entity pointedEntity = null;
            HitResult mop = ScriptDefaults.mc.player.rayTrace(distance, 1.0F);
            Vec3d eyeVec = ScriptDefaults.mc.player.getEyePosition(1.0F);
            Vec3d lookVec = Utils.getLookVec(yaw, pitch);
            Vec3d vec32 = eyeVec.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
            Vec3d vec33 = null;
            List list = ScriptDefaults.mc.world.getEntities(
                    ScriptDefaults.mc.getCamera(),
                    ScriptDefaults.mc
                            .getCamera()
                            .getShape()
                            .expanded(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance)
                            .grown(1.0, 1.0, 1.0));
            double d2 = distance;

            for (int i = 0; i < list.size(); i++) {
                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) list.get(i);
                if (entity instanceof LivingEntity
                        && entity.hasCollision()
                        && ((LivingEntity) entity).deathTicks == 0) {
                    float cbs = entity.getPickRadius();
                    Box axisalignedbb = entity.getShape().grown(cbs, cbs, cbs);
                    HitResult movingobjectposition = axisalignedbb.clip(eyeVec, vec32);
                    if (axisalignedbb.contains(eyeVec)) {
                        if (0.0 < d2 || d2 == 0.0) {
                            pointedEntity = entity;
                            vec33 = movingobjectposition == null ? eyeVec : movingobjectposition.facePos;
                            d2 = 0.0;
                        }
                    } else if (movingobjectposition != null) {
                        double d3 = eyeVec.distanceTo(movingobjectposition.facePos);
                        if (d3 < d2 || d2 == 0.0) {
                            if (entity != ScriptDefaults.mc.getCamera().vehicle
                                    || ClientPlatform.canRiderInteract(entity)) {
                                pointedEntity = entity;
                                vec33 = movingobjectposition.facePos;
                                d2 = d3;
                            } else if (d2 == 0.0) {
                                pointedEntity = entity;
                                vec33 = movingobjectposition.facePos;
                            }
                        }
                    }
                }
            }

            if (pointedEntity == null || !(d2 < distance) && mop != null) {
                return null;
            }

            mop = new HitResult(pointedEntity, vec33);
            Vec3 offset = new Vec3(
                    mop.facePos.x - pointedEntity.x, mop.facePos.y - pointedEntity.y, mop.facePos.z - pointedEntity.z);
            return new Object[] {new Entity(mop.entity), offset, eyeVec.squaredDistanceTo(mop.facePos)};
        }

        public static boolean canPlaceBlock(ItemStack stack, Vec3 pos, String side) {
            return stack != null && stack.itemStack != null && stack.itemStack.getItem() != null && stack.isBlock
                    ? ((BlockItem) stack.itemStack.getItem())
                            .onPlace(
                                    ScriptDefaults.mc.world,
                                    Vec3.getBlockPos(pos),
                                    Utils.getEnum(Direction.class, side),
                                    ScriptDefaults.mc.player,
                                    stack.itemStack)
                    : false;
        }

        public static boolean placeBlock(Vec3 targetPos, String side, Vec3 hitVec) {
            return ScriptDefaults.mc.interactionManager.useBlock(
                    ScriptDefaults.mc.player,
                    ScriptDefaults.mc.world,
                    ScriptDefaults.mc.player.getDisplayItemInHand(),
                    Vec3.getBlockPos(targetPos),
                    Utils.getEnum(Direction.class, side),
                    Vec3.getVec3(hitVec));
        }

        public static void clickBlock(Vec3 blockpos, String side) {
            ScriptDefaults.mc.interactionManager.startMiningBlock(
                    Vec3.getBlockPos(blockpos), Utils.getEnum(Direction.class, side));
        }

        public static void enableMovementFix() {
            RotationHelper.get().forceMovementFix = true;
        }

        public static void disableMovementFix() {
            RotationHelper.get().forceMovementFix = false;
        }

        public static boolean isMovementFixActive() {
            return RotationHelper.get().fixMovement();
        }

        public static boolean isRotationActive() {
            return RotationHelper.get().isActive();
        }

        public static void setRotations(float yaw, float pitch) {
            RotationHelper.get().setRotations(yaw, pitch);
        }

        public static void setYaw(float yaw) {
            RotationHelper.get().setYaw(yaw);
        }

        public static void setPitch(float pitch) {
            RotationHelper.get().setPitch(pitch);
        }

        public static Float getServerYaw() {
            return RotationHelper.get().getServerYaw();
        }

        public static Float getServerPitch() {
            return RotationHelper.get().getServerPitch();
        }

        public static float[] getRotationsToBlock(Vec3 position) {
            return RotationUtils.getRotations(new BlockPos(position.x, position.y, position.z));
        }

        public static void setSprinting(boolean sprinting) {
            ScriptDefaults.mc.player.setSprinting(sprinting);
        }

        public static void swing() {
            ScriptDefaults.mc.player.swingArm();
        }

        public static void swingReset() {
            ScriptDefaults.mc.player.swingArm();
            ScriptDefaults.mc.getItemInHandRenderer().onBlockUsed();
        }

        public static long time() {
            return System.currentTimeMillis();
        }

        public static boolean isFriend(String username) {
            return Utils.isFriended(username);
        }

        public static boolean isEnemy(String username) {
            return Utils.isEnemy(username);
        }
    }

    public static class config {
        private static String CONFIG_DIR =
                ScriptDefaults.mc.gameDir + File.separator + "keystrokes" + File.separator + "script_config.txt";
        private static String SEPARATOR = ":";
        private static String SEPARATOR_FULL = SEPARATOR + " ";

        private static void ensureConfigFileExists() throws IOException {
            Path configPath = Paths.get(CONFIG_DIR);
            if (Files.notExists(configPath)) {
                Files.createDirectories(configPath.getParent());
                Files.createFile(configPath);
            }
        }

        public static boolean set(String key, String value) {
            if (key != null && !key.isEmpty()) {
                key = key.replace(SEPARATOR, "");
                String entry = key + SEPARATOR_FULL + value;

                try {
                    ensureConfigFileExists();
                    Path configPath = new File(CONFIG_DIR).toPath();
                    List<String> lines = new ArrayList<>(Files.readAllLines(configPath));
                    boolean keyExists = false;

                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.startsWith(key + SEPARATOR_FULL)) {
                            lines.set(i, entry);
                            keyExists = true;
                            break;
                        }
                    }

                    if (!keyExists) {
                        lines.add(entry);
                    }

                    Files.write(configPath, lines);
                    return true;
                } catch (IOException ex) {
                    return false;
                }
            } else {
                return false;
            }
        }

        public static String get(String key) {
            if (key != null && !key.isEmpty()) {
                try {
                    ensureConfigFileExists();
                    Path configPath = new File(CONFIG_DIR).toPath();

                    for (String line : Files.readAllLines(configPath)) {
                        if (line.startsWith(key + SEPARATOR_FULL)) {
                            return line.substring((key + SEPARATOR_FULL).length());
                        }
                    }
                } catch (IOException var5) {
                }

                return null;
            } else {
                return null;
            }
        }
    }

    public static class fonts {
        public static String[] getNames() {
            return FontManager.getHudFontOptions();
        }

        public static String getDefaultName() {
            return FontManager.getDefaultFontName();
        }

        public static ScriptFont getFont(String name, double size) {
            return new ScriptFont(name, size);
        }

        public static ScriptFont getFontForPixelHeight(String name, double height) {
            return new ScriptFont(name, height, true);
        }
    }

    public static class gl {
        public static void alpha(boolean alpha) {
            if (alpha) {
                GlStateManager.enableAlphaTest();
            } else {
                GlStateManager.disableAlphaTest();
            }
        }

        public static void begin(int mode) {
            GL11.glBegin(mode);
        }

        public static void blend(boolean blend) {
            if (blend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        }

        public static void color(float r, float g, float b, float a) {
            GlStateManager.color4f(r, g, b, a);
        }

        public static void color(double r, double g, double b, double a) {
            GlStateManager.color4f((float) r, (float) g, (float) b, (float) a);
        }

        public static void color(int r, int g, int b, int a) {
            if (r <= 1 && g <= 1 && b <= 1 && a <= 1) {
                GlStateManager.color4f(r, g, b, a);
            } else {
                GlStateManager.color4f(r / 255.0F, g / 255.0F, b / 255.0F, a / 255.0F);
            }
        }

        public static void depthFunc(int func) {
            GL11.glDepthFunc(func);
        }

        public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            GlStateManager.colorMask(red, green, blue, alpha);
        }

        public static void polygonOffset(float factor, float units) {
            GL11.glPolygonOffset(factor, units);
        }

        public static void texCoord2(double u, double v) {
            GL11.glTexCoord2d(u, v);
        }

        public static void texCoord2(float u, float v) {
            GL11.glTexCoord2f(u, v);
        }

        public static void bindTexture(String resource) {
            Identifier resLoc = new Identifier(resource);
            ScriptDefaults.mc.getTextureManager().bind(resLoc);
        }

        public static void bindTextureId(int textureId) {
            GlStateManager.bindTexture(textureId);
        }

        public static int getBoundTexture() {
            return GL11.glGetInteger(32873);
        }

        public static void polygonOffset(boolean enabled) {
            if (enabled) {
                GlStateManager.enablePolygonOffset();
            } else {
                GlStateManager.disablePolygonOffset();
            }
        }

        public static void cull(boolean cull) {
            if (cull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
        }

        public static void depth(boolean depth) {
            if (depth) {
                GlStateManager.enableDepthTest();
            } else {
                GlStateManager.disableDepthTest();
            }
        }

        public static void depthMask(boolean depthMask) {
            GlStateManager.depthMask(depthMask);
        }

        public static void disable(int cap) {
            setCapability(cap, false);
        }

        public static void disableItemLighting() {
            Lighting.turnOff();
        }

        public static void enable(int cap) {
            setCapability(cap, true);
        }

        public static void enableItemLighting(boolean gui) {
            if (gui) {
                Lighting.turnOnGui();
            } else {
                Lighting.turnOn();
            }
        }

        public static void resetColor() {
            GlStateManager.clearColor();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        public static void end() {
            GL11.glEnd();
        }

        public static void lighting(boolean lighting) {
            if (lighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }

        public static void lineSmooth(boolean lineSmooth) {
            setGLEnable(2848, lineSmooth);
        }

        public static void lineWidth(float width) {
            GL11.glLineWidth(width);
        }

        public static void lineWidth(double width) {
            GL11.glLineWidth((float) width);
        }

        public static void normal(float x, float y, float z) {
            GL11.glNormal3f(x, y, z);
        }

        public static void normal(double x, double y, double z) {
            GL11.glNormal3d(x, y, z);
        }

        public static void pop() {
            GL11.glPopMatrix();
        }

        public static void push() {
            GL11.glPushMatrix();
        }

        public static void rotate(float angle, float x, float y, float z) {
            GL11.glRotatef(angle, x, y, z);
        }

        public static void rotate(double angle, double x, double y, double z) {
            GL11.glRotated(angle, x, y, z);
        }

        public static void scale(float x, float y, float z) {
            GL11.glScalef(x, y, z);
        }

        public static void scale(double x, double y, double z) {
            GL11.glScaled(x, y, z);
        }

        public static void scissor(int x, int y, int width, int height) {
            GL11.glScissor(x, y, width, height);
        }

        public static void scissor(boolean scissor) {
            setGLEnable(3089, scissor);
        }

        public static void texture2d(boolean texture2d) {
            if (texture2d) {
                GlStateManager.enableTexture();
            } else {
                GlStateManager.disableTexture();
            }
        }

        public static void translate(float x, float y, float z) {
            GL11.glTranslatef(x, y, z);
        }

        public static void translate(double x, double y, double z) {
            GL11.glTranslated(x, y, z);
        }

        public static void vertex2(float x, float y) {
            GL11.glVertex2f(x, y);
        }

        public static void vertex3(float x, float y, float z) {
            GL11.glVertex3f(x, y, z);
        }

        public static void vertex3(double x, double y, double z) {
            GL11.glVertex3d(x, y, z);
        }

        private static void setGLEnable(int cap, boolean enable) {
            if (enable) {
                GL11.glEnable(cap);
            } else {
                GL11.glDisable(cap);
            }
        }

        private static void setCapability(int cap, boolean enable) {
            switch (cap) {
                case 2884:
                    cull(enable);
                    break;
                case 2896:
                    lighting(enable);
                    break;
                case 2912:
                    if (enable) {
                        GlStateManager.enableFog();
                    } else {
                        GlStateManager.disableFog();
                    }
                    break;
                case 2929:
                    depth(enable);
                    break;
                case 2977:
                    if (enable) {
                        GlStateManager.enableNormalize();
                    } else {
                        GlStateManager.disableNormalize();
                    }
                    break;
                case 3008:
                    alpha(enable);
                    break;
                case 3042:
                    blend(enable);
                    break;
                case 3058:
                    if (enable) {
                        GlStateManager.enableColorLogicOp();
                    } else {
                        GlStateManager.disableColorLogicOp();
                    }
                    break;
                case 3553:
                    texture2d(enable);
                    break;
                case 32823:
                    if (enable) {
                        GlStateManager.enablePolygonOffset();
                    } else {
                        GlStateManager.disablePolygonOffset();
                    }
                    break;
                case 32826:
                    if (enable) {
                        GlStateManager.enableRescaleNormal();
                    } else {
                        GlStateManager.disableRescaleNormal();
                    }
                    break;
                default:
                    setGLEnable(cap, enable);
            }
        }
    }

    public static class inventory {
        public static int getSlot() {
            return ScriptDefaults.mc.player.inventory.selectedSlot;
        }

        public static int getHoveringSlot() {
            if (!(ScriptDefaults.mc.screen instanceof InventoryMenuScreen)) {
                return -1;
            }

            InventorySlot slot = ClientPlatform.slotUnderMouse((InventoryMenuScreen) ScriptDefaults.mc.screen);
            return slot == null ? -1 : slot.index;
        }

        public static void setSlot(int slot) {
            ScriptDefaults.mc.player.inventory.selectedSlot = slot;
        }

        public static void click(int slot, int button, int mode) {
            ScriptDefaults.mc.interactionManager.clickSlot(
                    ScriptDefaults.mc.player.menu.networkId, slot, button, mode, ScriptDefaults.mc.player);
        }

        public static List<String> getBookContents() {
            if (ScriptDefaults.mc.screen instanceof BookEditScreen) {
                List<String> contents = new ArrayList<>();
                List<Text> bookContents = ((IAccessorGuiScreenBook) ScriptDefaults.mc.screen).getBookContents();
                if (bookContents == null) {
                    return contents;
                }

                int max = Math.min(128 / ScriptDefaults.mc.textRenderer.fontHeight, bookContents.size());

                for (int line = 0; line < max; line++) {
                    Text lineStr = bookContents.get(line);
                    contents.add(lineStr.getString());
                }

                if (!contents.isEmpty()) {
                    return contents;
                }
            }

            return null;
        }

        public static String getChest() {
            if (ScriptDefaults.mc.player.menu instanceof ChestMenu) {
                ChestMenu chest = (ChestMenu) ScriptDefaults.mc.player.menu;
                return chest == null ? "" : chest.getChest().getDisplayName().getString();
            } else {
                return "";
            }
        }

        public static String getContainer() {
            if (ScriptDefaults.mc.screen instanceof CreativeInventoryScreen) {
                CreativeModeTab creativetabs =
                        CreativeModeTab.ALL[((CreativeInventoryScreen) ScriptDefaults.mc.screen).getSelectedTab()];
                if (creativetabs != null) {
                    return I18n.translate(creativetabs.getDisplayName(), new Object[0]);
                }
            } else if (ScriptDefaults.mc.screen != null) {
                try {
                    return ((Inventory) ReflectionUtils.containerInventoryPlayer
                                    .get(ScriptDefaults.mc.screen.getClass())
                                    .get(ScriptDefaults.mc.screen))
                            .getDisplayName()
                            .getString();
                } catch (Exception var1) {
                }
            }

            return "";
        }

        public static int getSize() {
            return ScriptDefaults.mc.player.inventory.getSize();
        }

        public static int getChestSize() {
            if (ScriptDefaults.mc.player.menu instanceof ChestMenu) {
                ChestMenu chest = (ChestMenu) ScriptDefaults.mc.player.menu;
                return chest == null ? -1 : chest.getChest().getSize();
            } else {
                return -1;
            }
        }

        public static ItemStack getStackInSlot(int slot) {
            return ScriptDefaults.mc.player.inventory.getItem(slot) == null
                    ? null
                    : new ItemStack(ScriptDefaults.mc.player.inventory.getItem(slot), (byte) 0);
        }

        public static ItemStack getStackInChestSlot(int slot) {
            if (ScriptDefaults.mc.player.menu instanceof ChestMenu) {
                ChestMenu chest = (ChestMenu) ScriptDefaults.mc.player.menu;
                return chest.getChest().getItem(slot) == null
                        ? null
                        : new ItemStack(chest.getChest().getItem(slot), (byte) 0);
            } else {
                return null;
            }
        }

        public static ItemStack getStackInCraftingSlot(int slot) {
            if (ScriptDefaults.mc.player.menu instanceof CraftingTableMenu) {
                CraftingInventory craftMatrix = ((CraftingTableMenu) ScriptDefaults.mc.player.menu).inventory;
                return craftMatrix.getItem(slot) == null ? null : new ItemStack(craftMatrix.getItem(slot), (byte) 0);
            } else {
                return null;
            }
        }

        public static ItemStack getCraftResult() {
            if (ScriptDefaults.mc.player.menu instanceof CraftingTableMenu) {
                Inventory craftResult = ((CraftingTableMenu) ScriptDefaults.mc.player.menu).resultInventory;
                return craftResult.getItem(0) == null ? null : new ItemStack(craftResult.getItem(0), (byte) 0);
            } else {
                return null;
            }
        }

        public static void open() {
            KeyBinding inventoryKey = ScriptDefaults.mc.options.inventoryKey;
            int originalKeyCode = inventoryKey.getKeyCode();
            if (originalKeyCode == 0) {
                inventoryKey.setKeyCode(13);
                KeyBinding.resetMapping();
            }

            KeyBinding.set(inventoryKey.getKeyCode(), true);
            KeyBinding.click(inventoryKey.getKeyCode());
            KeyBinding.set(inventoryKey.getKeyCode(), false);
            if (originalKeyCode == 0) {
                inventoryKey.setKeyCode(0);
                KeyBinding.resetMapping();
            }
        }
    }

    public static class keybinds {
        public static int[] getMousePosition() {
            return new int[] {Mouse.getX(), Mouse.getY()};
        }

        public static boolean isPressed(String key) {
            KeyBinding keyBind = ReflectionUtils.keybinds.get(key);
            return keyBind != null && keyBind.isPressed();
        }

        public static void setPressed(String key, boolean pressed) {
            KeyBinding keyBind = ReflectionUtils.keybinds.get(key);
            if (keyBind != null) {
                KeyBinding.set(keyBind.getKeyCode(), pressed);
            }
        }

        public static void onTick(String key) {
            KeyBinding keyBind = ReflectionUtils.keybinds.get(key);
            if (keyBind != null) {
                KeyBinding.click(keyBind.getKeyCode());
            }
        }

        public static int getKeyCode(String key) {
            KeyBinding keyBind = ReflectionUtils.keybinds.get(key);
            return keyBind != null ? keyBind.getKeyCode() : -1;
        }

        public static int getKeycode(String key) {
            return getKeyCode(key);
        }

        public static int getKeyIndex(String key) {
            return Keyboard.getKeyIndex(key);
        }

        public static boolean isMouseDown(int mouseButton) {
            return Mouse.isButtonDown(mouseButton);
        }

        public static boolean isKeyDown(int key) {
            return Keyboard.isKeyDown(key);
        }

        public static void rightClick() {
            ((IAccessorMinecraft) ScriptDefaults.mc).callRightClickMouse();
        }

        public static void leftClick() {
            ((IAccessorMinecraft) ScriptDefaults.mc).callClickMouse();
        }

        public static int getScroll() {
            return Mouse.getDWheel();
        }
    }

    public static class modules {
        private String superName;

        public modules(String superName) {
            this.superName = superName;
        }

        private static Module getModule(String moduleName) {
            return ScriptDefaults.modulesMap.get(moduleName);
        }

        private static Module getScript(String name) {
            return ScriptDefaults.modulesMap.get(name);
        }

        private static Setting getSetting(Module module, String settingName) {
            if (module == null) {
                return null;
            }

            for (Setting setting : module.getSettings()) {
                if (setting.getName().equals(settingName)) {
                    return setting;
                }
            }

            return null;
        }

        private GroupSetting getGroupForString(String group) {
            if (group.isEmpty()) {
                return null;
            }

            for (Setting setting : getScript(this.superName).getSettings()) {
                if (setting instanceof GroupSetting && setting.getName().equals(group)) {
                    return (GroupSetting) setting;
                }
            }

            return null;
        }

        public void enable(String moduleName) {
            if (getModule(moduleName) != null) {
                getModule(moduleName).enable();
            }
        }

        public void disable(String moduleName) {
            if (getModule(moduleName) != null) {
                getModule(moduleName).disable();
            }
        }

        public boolean isEnabled(String moduleName) {
            return getModule(moduleName) == null ? false : getModule(moduleName).isEnabled();
        }

        public Entity getKillAuraTarget() {
            return KillAura.target == null ? null : Entity.convert(KillAura.target);
        }

        public Entity getAimAssistTarget() {
            AimAssist aimAssist = (AimAssist) ModuleManager.getModule(AimAssist.class);
            return aimAssist != null && aimAssist.isEnabled() ? Entity.convert(aimAssist.getAimAssistTarget()) : null;
        }

        public Entity getLagRangeTarget() {
            LagRange lagRange = (LagRange) ModuleManager.getModule(LagRange.class);
            return lagRange != null && lagRange.isEnabled() ? Entity.convert(lagRange.getLagRangeTarget()) : null;
        }

        public Entity getBacktrackTarget() {
            Backtrack backtrack = ModuleManager.backtrack;
            return backtrack != null && backtrack.isEnabled() ? Entity.convert(backtrack.getBacktrackTarget()) : null;
        }

        public Vec3 getLagRangePosition() {
            LagRange lagRange = (LagRange) ModuleManager.getModule(LagRange.class);
            if (lagRange != null && lagRange.isEnabled()) {
                Vec3d position = lagRange.getLagRangePosition();
                return position == null ? null : new Vec3(position);
            } else {
                return null;
            }
        }

        public Vec3 getBacktrackPosition() {
            Backtrack backtrack = ModuleManager.backtrack;
            if (backtrack != null && backtrack.isEnabled()) {
                Vec3d position = backtrack.getBacktrackPosition();
                return position == null ? null : new Vec3(position);
            } else {
                return null;
            }
        }

        public Map<String, Object> getSettings(String name) {
            Map<String, Object> settings = new HashMap<>();
            Module module = getModule(name);
            if (module == null) {
                return settings;
            }

            for (Setting setting : module.getSettings()) {
                if (setting instanceof SliderSetting) {
                    settings.put(setting.getName(), ((SliderSetting) setting).getInput());
                } else if (setting instanceof ButtonSetting) {
                    settings.put(setting.getName(), ((ButtonSetting) setting).isToggled());
                } else if (setting instanceof ColorSetting) {
                    settings.put(setting.getName(), ((ColorSetting) setting).getColor());
                }
            }

            return settings;
        }

        public Map<String, List<String>> getCategories() {
            Map<String, List<String>> categories = new HashMap<>();

            for (CategoryComponent categoryComponent : ClickGui.categories) {
                List<String> modules = new ArrayList<>();

                for (ModuleComponent module : categoryComponent.modules) {
                    modules.add(module.mod.getName());
                }

                categories.put(categoryComponent.category.name(), modules);
            }

            return categories;
        }

        public Vec3 getBedAuraPosition() {
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled()) {
                BlockPos p = ModuleManager.bedAura.getAuraTargetPos();
                return p == null ? null : new Vec3(p.getX(), p.getY(), p.getZ());
            } else {
                return null;
            }
        }

        public float[] getBedAuraProgress() {
            if (ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled()) {
                float d = ModuleManager.bedAura.getAuraBreakProgress();
                return new float[] {d, 0.0F};
            } else {
                return new float[] {0.0F, 0.0F};
            }
        }

        public boolean isScaffolding() {
            return false;
        }

        public boolean isTowering() {
            return false;
        }

        public boolean isHidden(String moduleName) {
            Module module = getModule(moduleName);
            return module != null ? module.isHidden() : false;
        }

        public void registerGroup(String name) {
            getScript(this.superName).registerSetting(new GroupSetting(name));
        }

        public void registerButton(String name, boolean defaultValue) {
            getScript(this.superName).registerSetting(new ButtonSetting(name, defaultValue));
        }

        public void registerButton(String group, String name, boolean defaultValue) {
            getScript(this.superName)
                    .registerSetting(new ButtonSetting(this.getGroupForString(group), name, defaultValue));
        }

        public void registerKey(String group, String name, int defaultKey) {
            getScript(this.superName).registerSetting(new KeySetting(this.getGroupForString(group), name, defaultKey));
        }

        public void registerKey(String name, int defaultKey) {
            getScript(this.superName).registerSetting(new KeySetting(name, defaultKey));
        }

        public void registerSlider(
                String group,
                String name,
                String suffix,
                double defaultValue,
                double minimum,
                double maximum,
                double interval) {
            getScript(this.superName)
                    .registerSetting(new SliderSetting(
                            this.getGroupForString(group), name, suffix, defaultValue, minimum, maximum, interval));
        }

        public void registerSlider(String group, String name, String suffix, int defaultValue, String[] stringArray) {
            getScript(this.superName)
                    .registerSetting(
                            new SliderSetting(this.getGroupForString(group), name, suffix, defaultValue, stringArray));
        }

        public void registerSlider(String name, double defaultValue, double minimum, double maximum, double interval) {
            this.registerSlider("", name, "", defaultValue, minimum, maximum, interval);
        }

        public void registerSlider(String name, int defaultValue, String[] stringArray) {
            this.registerSlider("", name, "", defaultValue, stringArray);
        }

        public void registerSlider(
                String name, String suffix, double defaultValue, double minimum, double maximum, double interval) {
            this.registerSlider("", name, suffix, defaultValue, minimum, maximum, interval);
        }

        public void registerSlider(String name, String suffix, int defaultValue, String[] stringArray) {
            this.registerSlider("", name, suffix, defaultValue, stringArray);
        }

        public void registerDescription(String description) {
            getScript(this.superName).registerSetting(new DescriptionSetting(description));
        }

        public void registerColor(String name, int red, int green, int blue) {
            getScript(this.superName).registerSetting(new ColorSetting(name, red, green, blue));
        }

        public void registerColor(String name, int red, int green, int blue, int alpha) {
            getScript(this.superName).registerSetting(new ColorSetting(name, red, green, blue, alpha));
        }

        public void registerColor(String group, String name, int red, int green, int blue) {
            getScript(this.superName)
                    .registerSetting(new ColorSetting(this.getGroupForString(group), name, red, green, blue));
        }

        public void registerColor(String group, String name, int red, int green, int blue, int alpha) {
            getScript(this.superName)
                    .registerSetting(new ColorSetting(this.getGroupForString(group), name, red, green, blue, alpha));
        }

        public int getColor(String moduleName, String name) {
            ColorSetting setting = (ColorSetting) getSetting(getModule(moduleName), name);
            return setting == null ? 0 : setting.getColor();
        }

        public void setColor(String moduleName, String name, int red, int green, int blue) {
            ColorSetting setting = (ColorSetting) getSetting(getModule(moduleName), name);
            if (setting != null) {
                setting.setColor(red, green, blue);
            }
        }

        public void setColor(String moduleName, String name, int red, int green, int blue, int alpha) {
            ColorSetting setting = (ColorSetting) getSetting(getModule(moduleName), name);
            if (setting != null) {
                setting.setColor(red, green, blue, alpha);
            }
        }

        public boolean getButton(String moduleName, String name) {
            ButtonSetting setting = (ButtonSetting) getSetting(getModule(moduleName), name);
            return setting == null ? false : setting.isToggled();
        }

        public double getSlider(String moduleName, String name) {
            SliderSetting setting = (SliderSetting) getSetting(getModule(moduleName), name);
            return setting == null ? 0.0 : setting.getInput();
        }

        public double getSliderMin(String moduleName, String name) {
            SliderSetting setting = (SliderSetting) getSetting(getModule(moduleName), name);
            return setting == null ? 0.0 : setting.getMin();
        }

        public double getSliderMax(String moduleName, String name) {
            SliderSetting setting = (SliderSetting) getSetting(getModule(moduleName), name);
            return setting == null ? 0.0 : setting.getMax();
        }

        public String getSliderString(String moduleName, String name) {
            SliderSetting setting = (SliderSetting) getSetting(getModule(moduleName), name);
            return setting != null && setting.isString ? setting.getSelectedOption() : "";
        }

        public boolean getKeyPressed(String moduleName, String name) {
            KeySetting setting = (KeySetting) getSetting(getModule(moduleName), name);
            return setting == null ? false : setting.isPressed();
        }

        public void setButton(String moduleName, String name, boolean value) {
            ButtonSetting setting = (ButtonSetting) getSetting(getModule(moduleName), name);
            if (setting != null) {
                setting.setEnabled(value);
            }
        }

        public void setSlider(String moduleName, String name, double value) {
            SliderSetting setting = (SliderSetting) getSetting(getModule(moduleName), name);
            if (setting != null) {
                setting.setValueRawWithEvent(value);
            }
        }

        public void setKey(String moduleName, String name, int code) {
            KeySetting setting = (KeySetting) getSetting(getModule(moduleName), name);
            if (setting != null) {
                setting.setKey(code);
            }
        }
    }

    public static class render {
        private static final double[] WORLD_TO_SCREEN_COORDS = new double[3];
        private static RenderUtils.ProjectionContext worldToScreenContext;
        private static boolean worldToScreenContextActive;

        static void beginWorldToScreen(int scaleFactor) {
            worldToScreenContext = RenderUtils.captureProjectionContext(worldToScreenContext, scaleFactor);
            worldToScreenContextActive = true;
        }

        static void endWorldToScreen() {
            worldToScreenContextActive = false;
        }

        public static void block(Vec3 position, int color, boolean outline, boolean shade) {
            RenderUtils.renderBlock(new BlockPos(position.x, position.y, position.z), color, outline, shade);
        }

        public static void beam(Vec3 position, double height, double width, int color, float alpha) {
            beam(position.x, position.y, position.z, height, width, color, alpha);
        }

        public static void beam(double x, double y, double z, double height, double width, int color, float alpha) {
            RenderUtils.drawBeam(x, y, z, height, width, color, alpha);
        }

        public static int getThemeRGB(String theme) {
            return Theme.getGradient(Utils.getEnum(Theme.class, theme).ordinal(), 0.0);
        }

        public static void block(int x, int y, int z, int color, boolean outline, boolean shade) {
            RenderUtils.renderBlock(new BlockPos(x, y, z), color, outline, shade);
        }

        public static void entity(Entity en, int color, float partialTicks, boolean outline, boolean shade) {
            net.minecraft.entity.Entity e = en.entity;
            double x = e.prevX + (e.x - e.prevX) * partialTicks - ScriptDefaults.mc.getEntityRenderDispatcher().cameraX;
            double y = e.prevY + (e.y - e.prevY) * partialTicks - ScriptDefaults.mc.getEntityRenderDispatcher().cameraY;
            double z = e.prevZ + (e.z - e.prevZ) * partialTicks - ScriptDefaults.mc.getEntityRenderDispatcher().cameraZ;
            Box bbox = e.getShape().grown(0.1, 0.1, 0.1);
            Box axis = new Box(
                    bbox.minX - e.x + x,
                    bbox.minY - e.y + y,
                    bbox.minZ - e.z + z,
                    bbox.maxX - e.x + x,
                    bbox.maxY - e.y + y,
                    bbox.maxZ - e.z + z);
            GL11.glPushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            float a = (color >> 24 & 0xFF) / 255.0F;
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            GL11.glColor4f(r, g, b, a);
            if (outline) {
                WorldRenderer.renderOutlineShape(axis);
            }

            if (shade) {
                RenderUtils.drawBoundingBox(axis, r, g, b);
            }

            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }

        public static void entityGui(Entity en, int x, int y, float mouseX, float mouseY, int scale) {
            if (en.isLiving) {
                GL11.glPushMatrix();
                SurvivalInventoryScreen.renderEntity(x, y, scale, mouseX, mouseY, (LivingEntity) en.entity);
                GL11.glPopMatrix();
            }
        }

        public static void resetEquippedProgress() {
            ScriptDefaults.mc.getItemInHandRenderer().onBlockUsed();
        }

        public static void tracer(Entity entity, float lineWidth, int color, float partialTicks) {
            RenderUtils.drawTracerLine(entity.entity, color, lineWidth, partialTicks);
        }

        public static void showGui() {
            ScriptDefaults.mc.openScreen(new ScriptDefaults.render.EmptyGuiScreen());
        }

        public static void item(ItemStack item, float x, float y, float scale) {
            GlStateManager.pushMatrix();
            ScriptDefaults.mc.gameRenderer.setupGuiState();
            if (scale != 1.0F) {
                GlStateManager.scalef(scale, scale, scale);
            }

            Lighting.turnOnGui();
            GlStateManager.disableBlend();
            GlStateManager.translatef(x / scale, y / scale, 0.0F);
            ScriptDefaults.mc.getItemRenderer().renderGuiItemModel(item.itemStack, 0, 0);
            GlStateManager.enableBlend();
            Lighting.turnOff();
            if (scale != 1.0F) {
                GlStateManager.scalef(scale, scale, scale);
            }

            GlStateManager.popMatrix();
        }

        public static void image(Image image, float x, float y, float width, float height) {
            if (image != null && image.isLoaded()) {
                if (image.textureId == -1) {
                    DynamicTexture dynamicTexture = new DynamicTexture(image.bufferedImage);
                    GL11.glTexParameteri(3553, 10240, 9728);
                    dynamicTexture.upload();
                    image.textureId = dynamicTexture.getGlId();
                }

                GlStateManager.pushMatrix();
                GlStateManager.enableTexture();
                GlStateManager.bindTexture(image.textureId);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glTexParameteri(3553, 10240, 9728);
                Tesselator tessellator = Tesselator.getInstance();
                BufferBuilder worldrenderer = tessellator.getBuffer();
                worldrenderer.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                worldrenderer
                        .vertex(x, y + height, 0.0)
                        .texture(0.0, 1.0)
                        .color(255, 255, 255, 255)
                        .nextVertex();
                worldrenderer
                        .vertex(x + width, y + height, 0.0)
                        .texture(1.0, 1.0)
                        .color(255, 255, 255, 255)
                        .nextVertex();
                worldrenderer
                        .vertex(x + width, y, 0.0)
                        .texture(1.0, 0.0)
                        .color(255, 255, 255, 255)
                        .nextVertex();
                worldrenderer
                        .vertex(x, y, 0.0)
                        .texture(0.0, 0.0)
                        .color(255, 255, 255, 255)
                        .nextVertex();
                tessellator.end();
                GlStateManager.popMatrix();
            }
        }

        public static Vec3 worldToScreen(double x, double y, double z, int scaleFactor, float partialTicks) {
            boolean temporaryContext = !worldToScreenContextActive;
            if (temporaryContext) {
                ((IAccessorEntityRenderer) ScriptDefaults.mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
                beginWorldToScreen(scaleFactor);
            }

            x -= ScriptDefaults.mc.getEntityRenderDispatcher().cameraX;
            y -= ScriptDefaults.mc.getEntityRenderDispatcher().cameraY;
            z -= ScriptDefaults.mc.getEntityRenderDispatcher().cameraZ;
            boolean projected =
                    RenderUtils.projectTo2D(worldToScreenContext, x, y, z, scaleFactor, WORLD_TO_SCREEN_COORDS);
            ScriptDefaults.mc.gameRenderer.setupGuiState();
            if (temporaryContext) {
                endWorldToScreen();
            }

            return !projected
                    ? null
                    : new Vec3(WORLD_TO_SCREEN_COORDS[0], WORLD_TO_SCREEN_COORDS[1], WORLD_TO_SCREEN_COORDS[2]);
        }

        public static void roundedRect(float startX, float startY, float endX, float endY, float radius, int color) {
            RoundedUtils.drawRoundedRectRise(
                    startX, startY, Math.abs(startX - endX), Math.abs(startY - endY), radius, color);
        }

        public static void gradientRect(
                float startX, float startY, float endX, float endY, int leftColor, int rightColor) {
            gradientRect(startX, startY, endX, endY, leftColor, leftColor, rightColor, rightColor);
        }

        public static void gradientRect(
                float startX,
                float startY,
                float endX,
                float endY,
                int topLeftColor,
                int bottomLeftColor,
                int topRightColor,
                int bottomRightColor) {
            RenderUtils.drawRoundedGradientRect(
                    startX, startY, endX, endY, 0.0F, topLeftColor, bottomLeftColor, topRightColor, bottomRightColor);
        }

        public static double[] getRotations() {
            return new double[] {
                ScriptDefaults.mc.getEntityRenderDispatcher().cameraYaw,
                ScriptDefaults.mc.getEntityRenderDispatcher().cameraPitch
            };
        }

        public static double[] getCameraRotations() {
            return new double[] {Utils.getCameraYaw(), Utils.getCameraPitch()};
        }

        public static int getFontWidth(String text) {
            return ScriptDefaults.mc.textRenderer.getWidth(text) + Utils.getBoldWidth(text);
        }

        public static int getFontHeight() {
            return ScriptDefaults.mc.textRenderer.fontHeight;
        }

        public static Vec3 getPosition() {
            return new Vec3(
                    ScriptDefaults.mc.getEntityRenderDispatcher().cameraX,
                    ScriptDefaults.mc.getEntityRenderDispatcher().cameraY,
                    ScriptDefaults.mc.getEntityRenderDispatcher().cameraZ);
        }

        public static void text(String text, float x, float y, float scale, int color, boolean shadow) {
            text2d(text, x, y, scale, color, shadow);
        }

        public static void text2d(String text, float x, float y, float scale, int color, boolean shadow) {
            GlStateManager.pushMatrix();
            if (scale != 1.0F) {
                GlStateManager.scalef(scale, scale, scale);
            }

            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            ScriptDefaults.mc.textRenderer.draw(text, x / scale, y / scale, color, shadow);
            GlStateManager.disableBlend();
            if (scale != 1.0F) {
                GlStateManager.scalef(1.0F, 1.0F, 1.0F);
            }

            GlStateManager.popMatrix();
        }

        public static void text3d(
                String text, Vec3 position, float scale, boolean shadow, boolean depth, boolean background, int color) {
            ((IAccessorEntityRenderer) ScriptDefaults.mc.gameRenderer)
                    .callSetupCameraTransform(((IAccessorMinecraft) ScriptDefaults.mc).getTimer().partialTick, 0);
            GlStateManager.pushMatrix();
            float partialTicks = ((IAccessorMinecraft) ScriptDefaults.mc).getTimer().partialTick;
            double px = ScriptDefaults.mc.player.lastX
                    + (ScriptDefaults.mc.player.x - ScriptDefaults.mc.player.lastX) * partialTicks;
            double py = ScriptDefaults.mc.player.lastY
                    + (ScriptDefaults.mc.player.y - ScriptDefaults.mc.player.lastY) * partialTicks;
            double pz = ScriptDefaults.mc.player.lastZ
                    + (ScriptDefaults.mc.player.z - ScriptDefaults.mc.player.lastZ) * partialTicks;
            GlStateManager.translated((float) position.x - px, (float) position.y - py, (float) position.z - pz);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(-ScriptDefaults.mc.getEntityRenderDispatcher().cameraYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(ScriptDefaults.mc.getEntityRenderDispatcher().cameraPitch, 1.0F, 0.0F, 0.0F);
            float f1 = 0.02666667F;
            GlStateManager.scalef(-f1, -f1, f1);
            if (depth) {
                GlStateManager.depthMask(false);
                GlStateManager.disableDepthTest();
            }

            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            if (background) {
                GlStateManager.disableTexture();
                int width = ScriptDefaults.mc.textRenderer.getWidth(text);
                Tesselator tessellator = Tesselator.getInstance();
                BufferBuilder worldrenderer = tessellator.getBuffer();
                worldrenderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
                worldrenderer
                        .vertex(-1.0, -1.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, 0.25F)
                        .nextVertex();
                worldrenderer
                        .vertex(-1.0, 7.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, 0.25F)
                        .nextVertex();
                worldrenderer
                        .vertex(width + 1, 7.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, 0.25F)
                        .nextVertex();
                worldrenderer
                        .vertex(width + 1, -1.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, 0.25F)
                        .nextVertex();
                tessellator.end();
                GlStateManager.enableTexture();
            }

            if (scale != 1.0F) {
                GlStateManager.scalef(scale, scale, scale);
            }

            ScriptDefaults.mc.textRenderer.draw(text, 0.0F, 0.0F, color, shadow);
            if (scale != 1.0F) {
                GlStateManager.scalef(1.0F, 1.0F, 1.0F);
            }

            if (depth) {
                GlStateManager.enableDepthTest();
                GlStateManager.depthMask(true);
            }

            GlStateManager.disableBlend();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }

        public static void rect(float startX, float startY, float endX, float endY, int color) {
            if (startX < endX) {
                float i = startX;
                startX = endX;
                endX = i;
            }

            if (startY < endY) {
                float j = startY;
                startY = endY;
                endY = j;
            }

            float f3 = (color >> 24 & 0xFF) / 255.0F;
            float f4 = (color >> 16 & 0xFF) / 255.0F;
            float f5 = (color >> 8 & 0xFF) / 255.0F;
            float f6 = (color & 0xFF) / 255.0F;
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder worldrenderer = tessellator.getBuffer();
            GL11.glPushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color4f(f4, f5, f6, f3);
            worldrenderer.begin(7, DefaultVertexFormat.POSITION);
            worldrenderer.vertex(startX, endY, 0.0).nextVertex();
            worldrenderer.vertex(endX, endY, 0.0).nextVertex();
            worldrenderer.vertex(endX, startY, 0.0).nextVertex();
            worldrenderer.vertex(startX, startY, 0.0).nextVertex();
            tessellator.end();
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
        }

        public static void line2D(double startX, double startY, double endX, double endY, float lineWidth, int color) {
            GL11.glPushMatrix();
            GL11.glEnable(2848);
            GL11.glDisable(3553);
            GL11.glEnable(3042);
            RenderUtils.glColor(color);
            GL11.glLineWidth(lineWidth);
            GL11.glBegin(1);
            GL11.glVertex2d(startX, startY);
            GL11.glVertex2d(endX, endY);
            GL11.glEnd();
            GL11.glDisable(3042);
            GL11.glEnable(3553);
            GL11.glDisable(2848);
            GL11.glPopMatrix();
        }

        public static void line3D(Vec3 pos1, Vec3 pos2, float lineWidth, int color) {
            line3D(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, lineWidth, color);
        }

        public static void line3D(
                double startX,
                double startY,
                double startZ,
                double endX,
                double endY,
                double endZ,
                float lineWidth,
                int color) {
            double vx = ScriptDefaults.mc.getEntityRenderDispatcher().cameraX;
            double vy = ScriptDefaults.mc.getEntityRenderDispatcher().cameraY;
            double vz = ScriptDefaults.mc.getEntityRenderDispatcher().cameraZ;
            double sx = startX - vx;
            double sy = startY - vy;
            double sz = startZ - vz;
            endX -= vx;
            endY -= vy;
            endZ -= vz;
            float a = (color >> 24 & 0xFF) / 255.0F;
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            GL11.glPushMatrix();
            GL11.glEnable(3042);
            GL11.glEnable(2848);
            GL11.glDisable(2929);
            GL11.glDisable(3553);
            GL11.glBlendFunc(770, 771);
            GL11.glLineWidth(lineWidth);
            GlStateManager.color4f(r, g, b, a);
            GL11.glBegin(2);
            GL11.glVertex3d(sx, sy, sz);
            GL11.glVertex3d(endX, endY, endZ);
            GL11.glEnd();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDisable(2848);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }

        public static boolean isInView(double d, double d2, double d3, double d4, double d5, double d6) {
            net.minecraft.entity.Entity entity = ScriptDefaults.mc.getCamera();
            RenderUtils.frustum.prepare(entity.x, entity.y, entity.z);
            return RenderUtils.frustum.isVisible(d, d2, d3, d4, d5, d6);
        }

        public static boolean isInView(Entity en) {
            return RenderUtils.isInViewFrustum(en.entity);
        }

        private static class EmptyGuiScreen extends Screen {
            private EmptyGuiScreen() {}
        }

        public static class bloom {
            public static void prepare() {
                BlurUtils.prepareBloom();
            }

            public static void apply(int passes, float radius) {
                BlurUtils.bloomEnd(passes, radius);
            }
        }

        public static class blur {
            public static void prepare() {
                BlurUtils.prepareBlur();
            }

            public static void apply(int passes, float radius) {
                BlurUtils.blurEnd(passes, radius);
            }
        }
    }

    public static class util {
        public static final String colorSymbol = "§";

        public static String color(String message) {
            return Utils.formatColor(message);
        }

        public static String strip(String string) {
            return Utils.stripColor(string);
        }

        public static double round(double value, int decimals) {
            return Utils.round(value, decimals);
        }

        public static int randomInt(int min, int max) {
            return Utils.randomizeInt(min, max);
        }

        public static double randomDouble(double min, double max) {
            return Utils.randomizeDouble(min, max);
        }
    }

    public static class world {
        public static boolean exists() {
            return ScriptDefaults.mc.world != null;
        }

        public static Entity spawnMob(String type, double x, double y, double z) {
            return ClientEntityManager.spawnMob(type, x, y, z);
        }

        public static Entity spawnMob(String type, Vec3 position) {
            return position == null ? null : spawnMob(type, position.x, position.y, position.z);
        }

        public static boolean removeClientEntity(Entity entity) {
            return ClientEntityManager.removeClientEntity(entity);
        }

        public static void clearClientEntities() {
            ClientEntityManager.clearClientEntities();
        }

        public static void playSound(String name, float volume, float pitch, double x, double y, double z) {
            ScriptDefaults.mc.world.playSound(x, y, z, name, volume, pitch, false);
        }

        public static boolean isValidEntity(Entity entity) {
            return ScriptDefaults.mc.world != null && ScriptDefaults.mc.world.entities.contains(entity);
        }

        public static Block getBlockAt(int x, int y, int z) {
            BlockState state = BlockUtils.getBlockState(new BlockPos(x, y, z));
            return state == null
                    ? new Block(Blocks.AIR, new BlockPos(x, y, z))
                    : new Block(state, new BlockPos(x, y, z));
        }

        public static String getTitleText() {
            return ScriptDefaults.client.getTitle();
        }

        public static void setTitleText(
                String title, String subTitle, int timeFadeIn, int displayTime, int timeFadeOut) {
            ScriptDefaults.mc.gui.setTitles(title, subTitle, timeFadeIn, displayTime, timeFadeOut);
        }

        public static void clearTitleText() {
            ScriptDefaults.mc.gui.setTitles(null, null, -1, -1, -1);
        }

        public static Block getBlockAt(Vec3 pos) {
            BlockState state = BlockUtils.getBlockState(new BlockPos(pos.x, pos.y, pos.z));
            return state == null
                    ? new Block(Blocks.AIR, new BlockPos(pos.x, pos.y, pos.z))
                    : new Block(state, new BlockPos(pos.x, pos.y, pos.z));
        }

        public static String getDimension() {
            return ScriptDefaults.mc.world == null ? "" : ScriptDefaults.mc.world.dimension.getName();
        }

        public static List<Entity> getEntities() {
            List<Entity> entities = new ArrayList<>();
            if (ScriptDefaults.mc.world == null) {
                return entities;
            }

            for (net.minecraft.entity.Entity entity : ScriptDefaults.mc.world.entities) {
                entities.add(Entity.convert(entity));
            }

            return entities;
        }

        public static Entity getEntityById(int entityId) {
            return ScriptDefaults.mc.world == null ? null : Entity.convert(ScriptDefaults.mc.world.getEntity(entityId));
        }

        public static List<NetworkPlayer> getNetworkPlayers() {
            List<NetworkPlayer> entities = new ArrayList<>();

            for (PlayerInfo networkPlayerInfo : Utils.getTablist(false)) {
                entities.add(NetworkPlayer.convert(networkPlayerInfo));
            }

            return entities;
        }

        public static List<Entity> getPlayerEntities() {
            List<Entity> entities = new ArrayList<>();

            for (net.minecraft.entity.Entity entity : ScriptDefaults.mc.world.players) {
                entities.add(Entity.convert(entity));
            }

            return entities;
        }

        public static List<String> getScoreboard() {
            List<String> sidebarLines = Utils.getSidebarLines();
            return sidebarLines.isEmpty() ? null : sidebarLines;
        }

        public static String getTabHeader() {
            if (ScriptDefaults.mc != null
                    && ScriptDefaults.mc.gui != null
                    && ScriptDefaults.mc.gui.getPlayerTabOverlay() != null) {
                Text header = ((IAccessorGuiPlayerTabOverlay) ScriptDefaults.mc.gui.getPlayerTabOverlay()).getHeader();
                return header != null ? header.getString() : "";
            } else {
                return "";
            }
        }

        public static String getTabFooter() {
            if (ScriptDefaults.mc != null
                    && ScriptDefaults.mc.gui != null
                    && ScriptDefaults.mc.gui.getPlayerTabOverlay() != null) {
                Text footer = ((IAccessorGuiPlayerTabOverlay) ScriptDefaults.mc.gui.getPlayerTabOverlay()).getFooter();
                return footer != null ? footer.getString() : "";
            } else {
                return "";
            }
        }

        public static Map<String, List<String>> getTeams() {
            Map<String, List<String>> teams = new HashMap<>();

            for (AbstractTeam team : ScriptDefaults.mc.world.getScoreboard().getTeams()) {
                List<String> members = new ArrayList<>();

                for (String member : team.getMembers()) {
                    members.add(member);
                }

                teams.put(team.getName(), members);
            }

            return teams;
        }

        public static List<TileEntity> getTileEntities() {
            List<TileEntity> tileEntities = new ArrayList<>();

            for (BlockEntity entity : ScriptDefaults.mc.world.blockEntities) {
                tileEntities.add(new TileEntity(entity));
            }

            return tileEntities;
        }
    }
}

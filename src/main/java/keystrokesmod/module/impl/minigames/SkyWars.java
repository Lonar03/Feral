package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class SkyWars extends Module {
    private static final long CLOSEST_ENEMY_ANIMATION_DURATION_MS = 100L;
    private static final long CLOSEST_ENEMY_FADE_DURATION_MS = 150L;
    private static final FloatBuffer ITEM_ALPHA_COLOR = BufferUtils.createFloatBuffer(4);
    public SliderSetting closestEnemy;
    public ButtonSetting strengthIndicator;
    public ButtonSetting onlyAuraHostileMobs;
    public ButtonSetting renderTimeWarp;
    public Map<PlayerEntity, Long> strengthPlayers = new HashMap<>();
    private Map<String, SkyWars.SpawnEggInfo> entitySpawnQueue = new LinkedHashMap<>();
    private Map<Vec3d, Long> timeWarpPositions = new LinkedHashMap<>();
    public List<Integer> spawnedMobs = new ArrayList<>();
    private final int STRENGTH_COLOR = new Color(255, 0, 0).getRGB();
    private final int TIME_WARP_COLOR = new Color(210, 0, 255, 64).getRGB();
    private String[] KILL_MESSAGES = new String[] {" by ", " to ", " with ", " of ", " from ", " knight ", " for "};
    private boolean thrownPearl;
    private float closestEnemyPosX = Float.NaN;
    private float closestEnemyPosY = Float.NaN;
    private float closestEnemyRelativePosX = Float.NaN;
    private float closestEnemyRelativePosY = Float.NaN;
    private final ScrollOffsetAnimation closestEnemyWidthAnimation = new ScrollOffsetAnimation(100L);
    private final ScrollOffsetAnimation closestEnemyHeightAnimation = new ScrollOffsetAnimation(100L);
    private final ScrollOffsetAnimation closestEnemyContentAnimation = new ScrollOffsetAnimation(100L);
    private final SkyWars.SmoothFloatAnimation closestEnemyVisibilityAnimation = new SkyWars.SmoothFloatAnimation(150L);
    private boolean closestEnemySizeInitialized;
    private boolean closestEnemyVisibilityInitialized;
    private List<Integer> closestEnemySelection;
    private List<SkyWars.EnemyHudEntry> lastClosestEnemyEntries = Collections.emptyList();
    public static boolean isSkyWarsTeams = false;

    public SkyWars() {
        super("Sky Wars", Module.category.minigames);
        this.liteModule = true;
        this.registerSetting(this.closestEnemy = new SliderSetting("Closest enemy", true, 1.0, 1.0, 5.0, 1.0));
        this.registerSetting(
                new ButtonSetting("Edit positions", () -> mc.openScreen(new SkyWars.EditPositionScreen())));
        this.registerSetting(this.onlyAuraHostileMobs = new ButtonSetting("Only aura hostile mobs", true));
        this.registerSetting(this.renderTimeWarp = new ButtonSetting("Render time warp", true));
        this.registerSetting(this.strengthIndicator = new ButtonSetting("Strength indicator", true));
    }

    @Override
    public void onDisable() {
        this.clear();
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.strengthIndicator.isToggled()
                && Utils.nullCheck()
                && !this.strengthPlayers.isEmpty()
                && Utils.getSkyWarsStatus() == 2) {
            int customMode = this.getCustomMode();
            if (customMode != 2) {
                isSkyWarsTeams = customMode == 1;
                long duration = isSkyWarsTeams ? 2000L : 5000L;

                for (PlayerEntity entityPlayer : new ArrayList<>(this.strengthPlayers.keySet())) {
                    long storedTime = this.strengthPlayers.get(entityPlayer);
                    long timePassed = System.currentTimeMillis() - storedTime;
                    if (timePassed >= duration || AntiBot.isBot(entityPlayer)) {
                        this.strengthPlayers.remove(entityPlayer);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent e) {
        if (e.type != 2 && Utils.nullCheck()) {
            String stripped = Utils.stripColor(e.message.getString());
            if (!stripped.isEmpty()) {
                if (stripped.equals("You will be warped back in 3 seconds!") && this.thrownPearl) {
                    this.timeWarpPositions.put(
                            new Vec3d(mc.player.prevX, mc.player.prevY, mc.player.prevZ), System.currentTimeMillis());
                    this.thrownPearl = false;
                } else {
                    if (this.strengthIndicator.isToggled() && Utils.getSkyWarsStatus() == 2) {
                        if (this.getCustomMode() == 2) {
                            return;
                        }

                        if (stripped.endsWith(".")
                                && Arrays.stream(this.KILL_MESSAGES).anyMatch(stripped::contains)) {
                            String[] parts = stripped.split(" ");

                            for (String part : parts) {
                                if (part.endsWith(".")) {
                                    String name = part.substring(0, part.length() - 1);

                                    for (PlayerEntity entity : mc.world.players) {
                                        if (entity.getName().trim().equals(name) && entity != mc.player) {
                                            this.strengthPlayers.put(entity, System.currentTimeMillis());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && Utils.getSkyWarsStatus() == 2) {
            if (this.strengthIndicator.isToggled()) {
                for (PlayerEntity entityPlayer : this.strengthPlayers.keySet()) {
                    if (!AntiBot.isBot(entityPlayer)) {
                        RenderUtils.renderEntity(entityPlayer, 2, 0.0, 0.0, this.STRENGTH_COLOR, false);
                    }
                }
            }

            if (this.renderTimeWarp.isToggled()) {
                Iterator<Entry<Vec3d, Long>> iterator =
                        this.timeWarpPositions.entrySet().iterator();
                long currentTime = System.currentTimeMillis();

                while (iterator.hasNext()) {
                    Entry<Vec3d, Long> entry = iterator.next();
                    Vec3d position = entry.getKey();
                    long timeThrown = entry.getValue();
                    if (currentTime - timeThrown >= 3050L) {
                        iterator.remove();
                    } else {
                        RenderUtils.drawPlayerBoundingBox(position, this.TIME_WARP_COLOR);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.clear();
        } else if (e.entity != null) {
            if (Utils.getSkyWarsStatus() != 2) {
                return;
            }

            String entityClassName = e.entity.getClass().getSimpleName();
            if (this.entitySpawnQueue.containsKey(entityClassName)) {
                Vec3d spawnPosition = new Vec3d(e.entity.x, e.entity.y, e.entity.z);
                SkyWars.SpawnEggInfo eggInfo = this.entitySpawnQueue.get(entityClassName);
                if (eggInfo.spawnPos.distanceTo(spawnPosition) > 3.0
                        || Utils.timeBetween(mc.player.ticks, eggInfo.tickSpawned) > 60L) {
                    return;
                }

                if (!this.entitySpawnQueue.remove(entityClassName, eggInfo)) {
                    return;
                }

                this.spawnedMobs.add(e.entity.getNetworkId());
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket p = (PlayerUseC2SPacket) e.getPacket();
            if (p.getFace() != 255
                    && p.getItemInHand() != null
                    && p.getItemInHand().getItem() != null) {
                if (!(p.getItemInHand().getItem() instanceof SpawnEggItem)) {
                    return;
                }

                Class<? extends Entity> oclass = (Class<? extends Entity>)
                        Entities.KEY_TO_TYPE.get(ClientPlatform.spawnEggEntityName(p.getItemInHand()));
                if (oclass == null) {
                    return;
                }

                this.entitySpawnQueue.put(
                        oclass.getSimpleName(), new SkyWars.SpawnEggInfo(p.getPos(), mc.player.ticks));
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onUseItem(UseItemEvent e) {
        if (e.usedItemStack != null
                && e.usedItemStack.getItem() instanceof EnderPearlItem
                && Utils.getSkyWarsStatus() == 2) {
            ItemStack stack = e.usedItemStack;
            if (Utils.stripString(stack.getHoverName()).equals("Time Warp Pearl")) {
                this.thrownPearl = true;
            } else if (stack.getHoverName().startsWith("§b§l")) {
                List<String> toolTip = stack.getTooltip(mc.player, true);
                if (toolTip != null
                        && toolTip.size() > 1
                        && Utils.stripString(toolTip.get(1)).contains("Teleports you back to your")) {
                    this.thrownPearl = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END
                && Utils.nullCheck()
                && mc.screen == null
                && !mc.options.debugEnabled) {
            boolean playingSkyWars = Utils.getSkyWarsStatus() == 2;
            boolean showClosestEnemyExit = !this.lastClosestEnemyEntries.isEmpty();
            boolean showClosestEnemy = playingSkyWars && this.closestEnemy.getInput() != -1.0;
            if (showClosestEnemy || showClosestEnemyExit) {
                Window resolution = new Window(mc);
                this.syncClosestEnemyPosition(resolution);
                if (showClosestEnemy) {
                    List<PlayerEntity> enemies = this.findClosestEnemies((int) this.closestEnemy.getInput());
                    List<SkyWars.EnemyHudEntry> entries = new ArrayList<>();

                    for (PlayerEntity enemy : enemies) {
                        double distance = Math.round(mc.player.distanceTo(enemy));
                        String name = enemy.getDisplayName().getFormattedString() + "§r";
                        String distanceText = Utils.formatColor(Utils.getDistanceColor(distance))
                                + Utils.asWholeNum(distance) + "m§r";
                        entries.add(new SkyWars.EnemyHudEntry(
                                enemy.getNetworkId(), name, distanceText, enemy.getDisplayItemInHand()));
                    }

                    this.drawEnemyHudBox(entries, this.closestEnemyPosX, this.closestEnemyPosY);
                } else if (showClosestEnemyExit) {
                    this.drawEnemyHudBox(Collections.emptyList(), this.closestEnemyPosX, this.closestEnemyPosY);
                }
            }
        }
    }

    private void clear() {
        this.strengthPlayers.clear();
        this.spawnedMobs.clear();
        this.entitySpawnQueue.clear();
        this.timeWarpPositions.clear();
        this.thrownPearl = false;
        this.resetClosestEnemyHud();
    }

    private List<PlayerEntity> findClosestEnemies(int players) {
        Map<UUID, PlayerEntity> enemiesById = new LinkedHashMap<>();

        for (PlayerEntity player : mc.world.players) {
            if (player != null
                    && player != mc.player
                    && player.isAlive()
                    && !player.isSpectator()
                    && !AntiBot.isBot(player)
                    && !Utils.isTeammate(player)
                    && !Utils.isFriended(player)) {
                UUID playerId = player.getUuid();
                PlayerEntity existing = enemiesById.get(playerId);
                if (existing == null
                        || player.ticks < existing.ticks
                        || player.ticks == existing.ticks && player.getNetworkId() > existing.getNetworkId()) {
                    enemiesById.put(playerId, player);
                }
            }
        }

        List<PlayerEntity> enemies = new ArrayList<>(enemiesById.values());
        enemies.sort(Comparator.comparingDouble(mc.player::distanceTo));
        int amount = Math.min(Math.max(players, 0), enemies.size());
        return new ArrayList<>(enemies.subList(0, amount));
    }

    private void resetClosestEnemyHud() {
        this.closestEnemySizeInitialized = false;
        this.closestEnemyVisibilityInitialized = false;
        this.closestEnemySelection = null;
        this.lastClosestEnemyEntries = Collections.emptyList();
    }

    public float getClosestEnemyPosX() {
        this.syncClosestEnemyPosition();
        return this.closestEnemyPosX;
    }

    public float getClosestEnemyPosY() {
        this.syncClosestEnemyPosition();
        return this.closestEnemyPosY;
    }

    public float getClosestEnemyRelativePosX() {
        this.syncClosestEnemyPosition();
        return this.closestEnemyRelativePosX;
    }

    public float getClosestEnemyRelativePosY() {
        this.syncClosestEnemyPosition();
        return this.closestEnemyRelativePosY;
    }

    public void setClosestEnemyRelativePosition(float normalizedX, float normalizedY) {
        this.closestEnemyRelativePosX = normalizedX;
        this.closestEnemyRelativePosY = normalizedY;
        this.syncClosestEnemyPosition();
    }

    public void setClosestEnemyAbsolutePosition(float absoluteX, float absoluteY) {
        this.setClosestEnemyAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public void resetClosestEnemyPosition() {
        this.resetClosestEnemyPosition(new Window(mc));
    }

    private void syncClosestEnemyPosition() {
        this.syncClosestEnemyPosition(new Window(mc));
    }

    private void syncClosestEnemyPosition(Window resolution) {
        int scaledWidth = Math.max(1, resolution.getWidth());
        int scaledHeight = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.closestEnemyRelativePosX) || Float.isNaN(this.closestEnemyRelativePosY)) {
            if (Float.isNaN(this.closestEnemyPosX) || Float.isNaN(this.closestEnemyPosY)) {
                this.closestEnemyPosX = 0.0F;
                this.closestEnemyPosY = scaledHeight / 4.0F - mc.textRenderer.fontHeight / 2.0F;
            }

            this.closestEnemyRelativePosX = this.closestEnemyPosX / scaledWidth;
            this.closestEnemyRelativePosY = this.closestEnemyPosY / scaledHeight;
        }

        this.closestEnemyPosX = this.closestEnemyRelativePosX * scaledWidth;
        this.closestEnemyPosY = this.closestEnemyRelativePosY * scaledHeight;
    }

    private void setClosestEnemyAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.closestEnemyPosX = absoluteX;
        this.closestEnemyPosY = absoluteY;
        this.closestEnemyRelativePosX = absoluteX / Math.max(1, resolution.getWidth());
        this.closestEnemyRelativePosY = absoluteY / Math.max(1, resolution.getHeight());
    }

    private void resetClosestEnemyPosition(Window resolution) {
        this.setClosestEnemyAbsolutePosition(
                0.0F, resolution.getHeight() / 4.0F - mc.textRenderer.fontHeight / 2.0F, resolution);
    }

    private void renderHeldItem(ItemStack heldItem, float x, float y, float opacity) {
        float scale = 0.6875F;
        boolean fading = opacity < 1.0F;
        GL11.glPushMatrix();

        try {
            GL11.glTranslatef(x, y, 0.0F);
            GL11.glScalef(scale, scale, 1.0F);
            if (fading) {
                this.setItemOpacity(opacity);
            }

            RenderUtils.renderItemAndEffectIntoGui3D(heldItem, 0, 0);
        } finally {
            if (fading) {
                this.resetItemOpacity();
            }

            GL11.glPopMatrix();
        }
    }

    private void setItemOpacity(float opacity) {
        GLX.activeTexture(GLX.GL_TEXTURE0);
        ((Buffer) ITEM_ALPHA_COLOR).clear();
        ((Buffer) ITEM_ALPHA_COLOR.put(1.0F).put(1.0F).put(1.0F).put(opacity)).flip();
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_ALPHA, GLX.GL_CONSTANT);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_ALPHA, 770);
        GL11.glTexEnv(8960, 8705, ITEM_ALPHA_COLOR);
    }

    private void resetItemOpacity() {
        GLX.activeTexture(GLX.GL_TEXTURE0);
        ((Buffer) ITEM_ALPHA_COLOR).clear();
        ((Buffer) ITEM_ALPHA_COLOR.put(1.0F).put(1.0F).put(1.0F).put(1.0F)).flip();
        GL11.glTexEnvi(8960, 8704, 8448);
        GL11.glTexEnv(8960, 8705, ITEM_ALPHA_COLOR);
    }

    private SkyWars.HudBoxBounds drawEnemyHudBox(List<SkyWars.EnemyHudEntry> entries, float x, float textY) {
        float verticalPadding = 4.0F;
        float left = x - 10.0F;
        float top = textY - verticalPadding;
        float contentProgress = this.updateClosestEnemySelection(entries);
        float visibility = this.updateClosestEnemyVisibility(entries);
        boolean fadingOut = entries.isEmpty();
        List<SkyWars.EnemyHudEntry> renderedEntries = fadingOut ? this.lastClosestEnemyEntries : entries;
        if (!renderedEntries.isEmpty() && !(visibility <= 0.0F)) {
            float horizontalPadding = 3.0F;
            float columnSpacing = 6.0F;
            float itemSpacing = 3.0F;
            float rowHeight = mc.textRenderer.fontHeight + 4.0F;
            float textX = x + horizontalPadding;
            float maxNameWidth = 0.0F;
            float maxDistanceWidth = 0.0F;
            boolean hasItem = false;

            for (SkyWars.EnemyHudEntry entry : renderedEntries) {
                maxNameWidth = Math.max(maxNameWidth, mc.textRenderer.getWidth(entry.name));
                maxDistanceWidth = Math.max(maxDistanceWidth, mc.textRenderer.getWidth(entry.distance));
                if (entry.heldItem != null) {
                    hasItem = true;
                }
            }

            float distanceX = textX + maxNameWidth + columnSpacing;
            float itemX = distanceX + maxDistanceWidth + itemSpacing;
            float contentOffset = (1.0F - contentProgress) * 6.0F;
            float contentOpacity = contentProgress * visibility;
            int contentColor = Utils.mergeAlpha(Color.WHITE.getRGB(), Math.max(4, Math.round(255.0F * contentOpacity)));
            float targetRight = distanceX + maxDistanceWidth + horizontalPadding + 1.0F;
            if (hasItem) {
                targetRight = itemX + 14.5F;
            }

            float targetBottom = textY
                    + (renderedEntries.size() - 1) * rowHeight
                    + mc.textRenderer.fontHeight
                    + verticalPadding
                    - 1.0F;
            if (!fadingOut) {
                this.updateClosestEnemySize(targetRight - left, targetBottom - top);
            }

            float right = left + this.closestEnemyWidthAnimation.getValue();
            float bottom = top + this.closestEnemyHeightAnimation.getValue();
            int backgroundColor = Utils.mergeAlpha(Color.BLACK.getRGB(), Math.round(120.0F * visibility));
            GL11.glPushAttrib(24577);

            try {
                this.enableClosestEnemyAlphaBlending();
                RenderUtils.drawRoundedRectangle(left, top, right, bottom, 7.0F, backgroundColor);

                for (int i = 0; i < renderedEntries.size(); i++) {
                    SkyWars.EnemyHudEntry entry = renderedEntries.get(i);
                    float rowY = textY + i * rowHeight;
                    this.enableClosestEnemyAlphaBlending();
                    mc.textRenderer.draw(entry.name, textX + contentOffset, rowY, contentColor, true);
                    float distanceWidth = mc.textRenderer.getWidth(entry.distance);
                    mc.textRenderer.draw(
                            entry.distance,
                            distanceX + maxDistanceWidth - distanceWidth + contentOffset,
                            rowY,
                            contentColor,
                            true);
                    if (entry.heldItem != null) {
                        this.renderHeldItem(entry.heldItem, itemX + contentOffset, rowY - 2.0F, contentOpacity);
                    }
                }
            } finally {
                GL11.glPopAttrib();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.disableBlend();
            }

            return new SkyWars.HudBoxBounds(left, top, right, bottom);
        } else {
            return null;
        }
    }

    private void enableClosestEnemyAlphaBlending() {
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }

    private float updateClosestEnemyVisibility(List<SkyWars.EnemyHudEntry> entries) {
        if (!entries.isEmpty()) {
            this.lastClosestEnemyEntries = new ArrayList<>(entries);
            if (!this.closestEnemyVisibilityInitialized) {
                this.closestEnemyVisibilityAnimation.reset(1.0F);
                this.closestEnemyVisibilityInitialized = true;
            } else if (Float.compare(this.closestEnemyVisibilityAnimation.getTarget(), 1.0F) != 0) {
                this.closestEnemyVisibilityAnimation.setTarget(1.0F);
            }
        } else {
            if (this.lastClosestEnemyEntries.isEmpty()) {
                return 0.0F;
            }

            if (!this.closestEnemyVisibilityInitialized) {
                this.closestEnemyVisibilityAnimation.reset(1.0F);
                this.closestEnemyVisibilityInitialized = true;
            }

            if (Float.compare(this.closestEnemyVisibilityAnimation.getTarget(), 0.0F) != 0) {
                if (this.closestEnemySizeInitialized) {
                    this.closestEnemyWidthAnimation.reset(this.closestEnemyWidthAnimation.getValue());
                    this.closestEnemyHeightAnimation.reset(this.closestEnemyHeightAnimation.getValue());
                }

                this.closestEnemyVisibilityAnimation.setTarget(0.0F);
            }
        }

        float visibility = this.closestEnemyVisibilityAnimation.getValue();
        if (entries.isEmpty() && visibility <= 0.0F) {
            this.lastClosestEnemyEntries = Collections.emptyList();
        }

        return visibility;
    }

    private float updateClosestEnemySelection(List<SkyWars.EnemyHudEntry> entries) {
        List<Integer> selection = new ArrayList<>(entries.size());

        for (SkyWars.EnemyHudEntry entry : entries) {
            selection.add(entry.entityId);
        }

        if (this.closestEnemySelection == null) {
            this.closestEnemySelection = selection;
            this.closestEnemyContentAnimation.reset(1.0F);
        } else if (!this.closestEnemySelection.equals(selection)) {
            this.closestEnemySelection = selection;
            if (selection.isEmpty()) {
                this.closestEnemyContentAnimation.reset(1.0F);
            } else {
                this.closestEnemyContentAnimation.reset(0.0F);
                this.closestEnemyContentAnimation.setTarget(1.0F);
            }
        }

        return this.closestEnemyContentAnimation.getValue();
    }

    private void updateClosestEnemySize(float width, float height) {
        if (!this.closestEnemySizeInitialized) {
            this.closestEnemyWidthAnimation.reset(width);
            this.closestEnemyHeightAnimation.reset(height);
            this.closestEnemySizeInitialized = true;
        } else {
            if (Float.compare(this.closestEnemyWidthAnimation.getTarget(), width) != 0) {
                this.closestEnemyWidthAnimation.setTarget(width);
            }

            if (Float.compare(this.closestEnemyHeightAnimation.getTarget(), height) != 0) {
                this.closestEnemyHeightAnimation.setTarget(height);
            }
        }
    }

    public static boolean onlyAuraHostiles() {
        return ModuleManager.skyWars != null
                && ModuleManager.skyWars.isEnabled()
                && ModuleManager.skyWars.onlyAuraHostileMobs.isToggled()
                && Utils.getSkyWarsStatus() == 2;
    }

    public int getCustomMode() {
        List<String> sidebar = Utils.getSidebarLines();
        if (sidebar.isEmpty()) {
            return -1;
        }

        for (String line : sidebar) {
            line = Utils.stripColor(line);
            if (line.startsWith("Teams left: ")) {
                return 1;
            }

            if (line.startsWith("Lab: ") || line.startsWith("Mode: Mini")) {
                return 2;
            }
        }

        return -1;
    }

    private class EditPositionScreen extends Screen {
        private ButtonWidget resetPosition;
        private SkyWars.HudBoxBounds bounds;
        private boolean dragging;
        private float actualX;
        private float actualY;
        private float dragStartX;
        private float dragStartY;
        private int lastMouseX;
        private int lastMouseY;

        private EditPositionScreen() {}

        public void init() {
            super.init();
            this.buttons.add(
                    this.resetPosition =
                            new ButtonWidget(1, this.width - 95, this.height - 25, 90, 20, "Reset position"));
            SkyWars.this.syncClosestEnemyPosition(new Window(this.minecraft));
            this.syncEditorPosition();
        }

        public void render(int mouseX, int mouseY, float partialTicks) {
            Window resolution = new Window(this.minecraft);
            if (!this.dragging) {
                SkyWars.this.syncClosestEnemyPosition(resolution);
                this.syncEditorPosition();
            }

            fill(0, 0, this.width, this.height, -1308622848);
            SkyWars.this.setClosestEnemyAbsolutePosition(this.actualX, this.actualY, resolution);
            List<SkyWars.EnemyHudEntry> previewEntries = new ArrayList<>();
            int previewPlayers = Math.max(1, (int) SkyWars.this.closestEnemy.getInput());

            for (int i = 0; i < previewPlayers; i++) {
                String name = this.minecraft.player.getDisplayName().getFormattedString() + "§r";
                String distance = "§a" + (16 + i * 3) + "m§r";
                previewEntries.add(new SkyWars.EnemyHudEntry(
                        this.minecraft.player.getNetworkId(),
                        name,
                        distance,
                        this.minecraft.player.getDisplayItemInHand()));
            }

            this.bounds = SkyWars.this.drawEnemyHudBox(previewEntries, this.actualX, this.actualY);
            String message = "Drag the box to reposition it.";
            int messageX = resolution.getWidth() / 2 - this.textRenderer.getWidth(message) / 2;
            int messageY = resolution.getHeight() / 2 - 10;
            RenderUtils.drawColoredString(message, '-', messageX, messageY, 2L, 0L, true, this.textRenderer);
            ClientPlatform.handleInput(this);
            super.render(mouseX, mouseY, partialTicks);
        }

        protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
            if (button == 0) {
                if (this.dragging) {
                    this.actualX = this.dragStartX + mouseX - this.lastMouseX;
                    this.actualY = this.dragStartY + mouseY - this.lastMouseY;
                } else if (this.bounds != null && this.bounds.contains(mouseX, mouseY)) {
                    this.dragging = true;
                    this.dragStartX = this.actualX;
                    this.dragStartY = this.actualY;
                    this.lastMouseX = mouseX;
                    this.lastMouseY = mouseY;
                }
            }
        }

        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        public void buttonClicked(ButtonWidget button) {
            if (button == this.resetPosition) {
                this.dragging = false;
                SkyWars.this.resetClosestEnemyPosition(new Window(this.minecraft));
                this.syncEditorPosition();
            }
        }

        private void syncEditorPosition() {
            this.actualX = SkyWars.this.closestEnemyPosX;
            this.actualY = SkyWars.this.closestEnemyPosY;
        }

        public boolean shouldPauseGame() {
            return false;
        }
    }

    private static final class EnemyHudEntry {
        private final int entityId;
        private final String name;
        private final String distance;
        private final ItemStack heldItem;

        private EnemyHudEntry(int entityId, String name, String distance, ItemStack heldItem) {
            this.entityId = entityId;
            this.name = name;
            this.distance = distance;
            this.heldItem = heldItem;
        }
    }

    private static final class HudBoxBounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private HudBoxBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.left && mouseX <= this.right && mouseY >= this.top && mouseY <= this.bottom;
        }
    }

    private static final class SmoothFloatAnimation {
        private final long durationMs;
        private float from;
        private float target;
        private long startedAt;

        private SmoothFloatAnimation(long durationMs) {
            this.durationMs = durationMs;
        }

        private void reset(float value) {
            this.from = value;
            this.target = value;
            this.startedAt = 0L;
        }

        private void setTarget(float value) {
            this.from = this.getValue();
            this.target = value;
            this.startedAt = System.currentTimeMillis();
        }

        private float getValue() {
            if (this.startedAt == 0L) {
                return this.target;
            } else {
                float progress = (float) (System.currentTimeMillis() - this.startedAt) / (float) this.durationMs;
                if (progress >= 1.0F) {
                    this.reset(this.target);
                    return this.target;
                } else {
                    progress = Math.max(0.0F, progress);
                    float eased = progress * progress * (3.0F - 2.0F * progress);
                    return this.from + (this.target - this.from) * eased;
                }
            }
        }

        private float getTarget() {
            return this.target;
        }
    }

    public static class SpawnEggInfo {
        public Vec3d spawnPos;
        public int tickSpawned;

        public SpawnEggInfo(BlockPos spawnPos, int tickSpawned) {
            this.spawnPos = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            this.tickSpawned = tickSpawned;
        }
    }
}

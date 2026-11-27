package keystrokesmod.module.impl.render;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class Nametags extends Module {
    private static final float AUTO_SCALE_THRESHOLD = 5.0F;
    private static final Comparator<Nametags.NametagRenderState> FAR_TO_NEAR =
            (a, b) -> Double.compare(b.distanceSq, a.distanceSq);
    private static final int ITEM_SPACING = 14;
    private static final int ENCHANT_LINE_HEIGHT = 8;
    private static final int ENCHANT_Y_OFFSET = 24;
    private SliderSetting scale;
    private SliderSetting font;
    private ButtonSetting autoScale;
    private ButtonSetting onlyRenderName;
    private SliderSetting background;
    private ButtonSetting bgBorder;
    private ButtonSetting showHealth;
    private ButtonSetting textShadow;
    private ButtonSetting showDistance;
    private ButtonSetting showInvis;
    private ButtonSetting showArmor;
    private ButtonSetting showEnchants;
    private ButtonSetting showStackSize;
    private ButtonSetting showDurability;
    private ButtonSetting renderSelf;
    private ButtonSetting showMobs;
    private ButtonSetting hideVanilla;
    private ColorSetting friendColor;
    private ColorSetting enemyColor;
    private final List<Nametags.NametagRenderState> renderStates = new ArrayList<>();
    private final FloatBuffer frontCameraModelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer frontNametagRotation = BufferUtils.createFloatBuffer(16);
    private int renderStateCount = 0;
    private static final int[] ARMOR_ENCHANT_IDS = new int[] {0, 7, 34};
    private static final String[] ARMOR_ENCHANT_ABBR = new String[] {"P", "T", "U"};
    private static final int[] SWORD_ENCHANT_IDS = new int[] {16, 20, 19};
    private static final String[] SWORD_ENCHANT_ABBR = new String[] {"S", "F", "K"};
    private static final int[] BOW_ENCHANT_IDS = new int[] {48, 49, 50};
    private static final String[] BOW_ENCHANT_ABBR = new String[] {"Pw", "Pu", "Fl"};
    private static final int[] TOOL_ENCHANT_IDS = new int[] {32, 35, 34};
    private static final String[] TOOL_ENCHANT_ABBR = new String[] {"E", "Fo", "U"};
    private static final int[] MISC_ENCHANT_IDS = new int[] {19};
    private static final String[] MISC_ENCHANT_ABBR = new String[] {"K"};

    public Nametags() {
        super("Nametags", Module.category.render, 0);
        this.liteModule = true;
        this.registerSetting(this.background = new SliderSetting("Background", true, 0.5, 0.0, 1.0, 0.05));
        this.registerSetting(this.scale = new SliderSetting("Scale", 1.0, 0.1, 2.0, 0.1));
        this.registerSetting(this.font = FontManager.createFontSetting("Font"));
        this.registerSetting(this.autoScale = new ButtonSetting("Auto-scale", false));
        this.registerSetting(this.onlyRenderName = new ButtonSetting("Only render name", false));
        this.registerSetting(this.renderSelf = new ButtonSetting("Render self", false));
        this.registerSetting(this.showMobs = new ButtonSetting("Show mobs", false));
        this.registerSetting(this.bgBorder = new ButtonSetting("Background border", false));
        this.registerSetting(this.showHealth = new ButtonSetting("Show health", false));
        this.registerSetting(this.textShadow = new ButtonSetting("Text shadow", false));
        this.registerSetting(this.hideVanilla = new ButtonSetting("Hide vanilla", true));
        this.registerSetting(this.showDistance = new ButtonSetting("Show distance", false));
        this.registerSetting(this.showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(this.friendColor = new ColorSetting("Friend color", 85, 255, 255));
        this.registerSetting(this.enemyColor = new ColorSetting("Enemy color", 255, 85, 85));
        this.registerSetting(new DescriptionSetting("Armor settings"));
        this.registerSetting(this.showArmor = new ButtonSetting("Show armor", false));
        this.registerSetting(this.showEnchants = new ButtonSetting("Show enchants", false));
        this.registerSetting(this.showDurability = new ButtonSetting("Show durability", false));
        this.registerSetting(this.showStackSize = new ButtonSetting("Show stack size", false));
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (Utils.nullCheck() && mc.world != null) {
                this.updateRenderStates();
            } else {
                this.renderStateCount = 0;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent event) {
        if (Utils.nullCheck()) {
            this.renderNametags(event.partialTicks);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderLiving(ForgeEvents.RenderLivingEvent.Specials.Pre event) {
        if (this.hideVanilla.isToggled()) {
            if (event.entity instanceof LivingEntity) {
                LivingEntity player = (LivingEntity) event.entity;
                if (this.shouldRenderNametag(player)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private void updateRenderStates() {
        FeralFontRenderer fontRenderer = this.getNametagFontRenderer();
        Entity viewer = mc.getCamera();
        if (viewer == null) {
            this.renderStateCount = 0;
        } else {
            boolean renderDistance = this.showDistance.isToggled();
            boolean renderArmor = this.showArmor.isToggled();
            float baseScale = this.computeBaseScaleValue();
            this.renderStateCount = 0;

            // Show mobs: every living entity instead of only players
            List<? extends Entity> candidates = this.showMobs.isToggled() ? mc.world.entities : mc.world.players;
            for (Entity candidate : candidates) {
                if (candidate instanceof LivingEntity && this.shouldRenderNametag((LivingEntity) candidate)) {
                    LivingEntity player = (LivingEntity) candidate;
                    double dx = player.x - viewer.x;
                    double dy = player.y - viewer.y;
                    double dz = player.z - viewer.z;
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    float distance = (float) Math.sqrt(distanceSq);
                    String displayName = this.buildDisplayName(player, renderDistance, distance);
                    int stringHalfWidth = fontRenderer.getStringWidth(displayName) / 2;
                    int relationshipColor = this.resolveRelationshipColor(player);
                    int[] playerNameRange = this.findVisiblePlayerNameRange(displayName, player.getName());
                    ItemStack heldItem = null;
                    ItemStack boots = null;
                    ItemStack leggings = null;
                    ItemStack chestplate = null;
                    ItemStack helmet = null;
                    int totalItems = 0;
                    if (renderArmor) {
                        heldItem = player.getEquipment(0);
                        if (heldItem != null) {
                            totalItems++;
                        }

                        boots = player.getEquipment(1);
                        if (boots != null) {
                            totalItems++;
                        }

                        leggings = player.getEquipment(2);
                        if (leggings != null) {
                            totalItems++;
                        }

                        chestplate = player.getEquipment(3);
                        if (chestplate != null) {
                            totalItems++;
                        }

                        helmet = player.getEquipment(4);
                        if (helmet != null) {
                            totalItems++;
                        }
                    }

                    if (this.renderStateCount >= this.renderStates.size()) {
                        this.renderStates.add(new Nametags.NametagRenderState());
                    }

                    this.renderStates
                            .get(this.renderStateCount++)
                            .set(
                                    player,
                                    displayName,
                                    stringHalfWidth,
                                    Utils.getColorFromEntity(player),
                                    relationshipColor,
                                    distanceSq,
                                    baseScale,
                                    (player.isSneaking() ? player.height - 0.3F : player.height) + 0.3F,
                                    heldItem,
                                    boots,
                                    leggings,
                                    chestplate,
                                    helmet,
                                    totalItems);
                }
            }

            if (this.renderStateCount > 1) {
                this.renderStates.subList(0, this.renderStateCount).sort(FAR_TO_NEAR);
            }
        }
    }

    private void renderNametags(float partialTicks) {
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        TextRenderer itemFontRenderer = mc.textRenderer;
        FeralFontRenderer textRenderer = this.getNametagFontRenderer();
        if (renderManager != null && itemFontRenderer != null && this.renderStateCount != 0) {
            ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
            boolean frontView = mc.options.perspective == 2;
            if (frontView) {
                this.updateFrontNametagRotation();
            }

            for (int i = 0; i < this.renderStateCount; i++) {
                Nametags.NametagRenderState renderState = this.renderStates.get(i);
                if (renderState.player != null && RenderUtils.isInViewFrustum(renderState.player)) {
                    this.renderCustomName(
                            renderState, partialTicks, renderManager, textRenderer, itemFontRenderer, frontView);
                }
            }

            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            GlStateManager.disableLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            GlStateManager.enableAlphaTest();
            GlStateManager.enableTexture();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean shouldRenderNametag(LivingEntity player) {
        if (player == null) {
            return false;
        }

        if (player != mc.player) {
            if (player.removed || player.deathTicks > 0) {
                return false;
            } else if (!this.showInvis.isToggled() && player.isInvisible()) {
                return false;
            } else if (player instanceof PlayerEntity) {
                return !AntiBot.isBot(player);
            } else {
                return this.showMobs.isToggled() && !(player instanceof ArmorStandEntity) && !isFakeMob(player);
            }
        } else {
            return this.renderSelf.isToggled() && mc.options.perspective != 0;
        }
    }

    /**
     * Hypixel uses invisible villagers as Zombies window hitboxes and invisible withers to draw boss bars, so they
     * aren't real mobs even with "Show invis" on.
     */
    private static boolean isFakeMob(LivingEntity entity) {
        return entity.isInvisible() && (entity instanceof VillagerEntity || entity instanceof WitherEntity);
    }

    private String buildDisplayName(LivingEntity entity, boolean showDist, float distance) {
        String name;
        if (this.onlyRenderName.isToggled()) {
            String formatted = Utils.getFirstColorCode(entity.getDisplayName().getFormattedString());
            String color = formatted.length() >= 2 && formatted.charAt(0) == 167 ? formatted : "";
            name = color + entity.getName();
        } else {
            name = entity.getDisplayName().getFormattedString();
        }

        if (entity instanceof PlayerEntity && ModuleManager.murderMystery != null) {
            name += ModuleManager.murderMystery.getNameIcon(entity.getUuid());
        }

        if (this.showHealth.isToggled()) {
            name = this.appendHealth(name, entity);
        }

        if (showDist) {
            int dist = (int) distance;
            String distColor = dist <= 8 ? "§c" : (dist <= 15 ? "§6" : (dist <= 25 ? "§e" : "§7"));
            name = distColor + dist + "m§r " + name;
        }

        return name;
    }

    private int resolveRelationshipColor(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity)) {
            return -1;
        } else if (Utils.isFriended((PlayerEntity) entity)) {
            return this.friendColor.getColor();
        } else {
            return Utils.isEnemy((PlayerEntity) entity) ? this.enemyColor.getColor() : -1;
        }
    }

    private float computeBaseScaleValue() {
        return (float) this.scale.getInput() * 0.02F;
    }

    private float computeScaleValue(float distance, boolean scaleByDistance) {
        float scaleValue = this.computeBaseScaleValue();
        if (!scaleByDistance) {
            return scaleValue;
        }

        float effectiveDistance = Math.max(1.0F, distance);
        float scaledValue = scaleValue * (effectiveDistance / 5.0F);
        return Math.max(scaleValue, scaledValue);
    }

    private void updateFrontNametagRotation() {
        ((Buffer) this.frontCameraModelView).clear();
        GL11.glGetFloat(2982, this.frontCameraModelView);
        ((Buffer) this.frontNametagRotation).clear();

        for (int row = 0; row < 3; row++) {
            float sign = row == 1 ? 1.0F : -1.0F;
            this.frontNametagRotation.put(sign * this.frontCameraModelView.get(row));
            this.frontNametagRotation.put(sign * this.frontCameraModelView.get(row + 4));
            this.frontNametagRotation.put(sign * this.frontCameraModelView.get(row + 8));
            this.frontNametagRotation.put(0.0F);
        }

        this.frontNametagRotation.put(0.0F).put(0.0F).put(0.0F).put(1.0F);
        ((Buffer) this.frontNametagRotation).flip();
    }

    private void renderCustomName(
            Nametags.NametagRenderState state,
            float partialTicks,
            EntityRenderDispatcher renderManager,
            FeralFontRenderer textRenderer,
            TextRenderer itemFontRenderer,
            boolean frontView) {
        LivingEntity entity = state.player;
        if (entity != null && !entity.removed && entity.deathTicks <= 0) {
            double x = entity.prevX + (entity.x - entity.prevX) * partialTicks - renderManager.cameraX;
            double y = entity.prevY + (entity.y - entity.prevY) * partialTicks - renderManager.cameraY;
            double z = entity.prevZ + (entity.z - entity.prevZ) * partialTicks - renderManager.cameraZ;
            float renderScale = state.baseScale;
            if (this.autoScale.isToggled()) {
                renderScale = this.computeScaleValue((float) Math.sqrt(x * x + y * y + z * z), true);
            }

            GlStateManager.pushMatrix();
            GlStateManager.translatef((float) x, (float) y + state.yOffset, (float) z);
            if (frontView) {
                GL11.glMultMatrix(this.frontNametagRotation);
            } else {
                GlStateManager.rotatef(-renderManager.cameraYaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(renderManager.cameraPitch, 1.0F, 0.0F, 0.0F);
            }

            GlStateManager.scalef(-renderScale, -renderScale, renderScale);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepthTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.translatef(0.0F, -10.0F, 0.0F);
            if (this.background.getInput() > 0.01 || this.bgBorder.isToggled() || state.relationshipColor != -1) {
                this.renderBackground(
                        state.stringHalfWidth, 0.0F, state.teamColor, state.relationshipColor, textRenderer);
                this.applyNametagTextState();
            }

            this.drawDisplayName(state, textRenderer);
            this.applyNametagTextState();
            if (state.totalItems > 0) {
                int iconX = -(state.totalItems * 14) / 2;
                int iconY = -20;
                if (state.heldItem != null) {
                    this.renderItemStack(state.heldItem, iconX, iconY, itemFontRenderer);
                    iconX += 14;
                }

                if (state.helmet != null) {
                    this.renderItemStack(state.helmet, iconX, iconY, itemFontRenderer);
                    iconX += 14;
                }

                if (state.chestplate != null) {
                    this.renderItemStack(state.chestplate, iconX, iconY, itemFontRenderer);
                    iconX += 14;
                }

                if (state.leggings != null) {
                    this.renderItemStack(state.leggings, iconX, iconY, itemFontRenderer);
                    iconX += 14;
                }

                if (state.boots != null) {
                    this.renderItemStack(state.boots, iconX, iconY, itemFontRenderer);
                }
            }

            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void applyNametagTextState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GL11.glTexEnvi(8960, 8704, 8448);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawDisplayName(Nametags.NametagRenderState state, FeralFontRenderer textRenderer) {
        textRenderer.drawString(state.displayName, -state.stringHalfWidth, 0.0F, -1, this.textShadow.isToggled());
    }

    private void renderBackground(
            int stringWidth, float textY, int teamColor, int relationshipColor, FeralFontRenderer fontRenderer) {
        GlStateManager.disableTexture();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder worldRenderer = tessellator.getBuffer();
        float alpha = (float) this.background.getInput();
        float innerLeft = -stringWidth - 3.0F;
        float innerRight = stringWidth + 3.0F;
        float innerTop = textY + fontRenderer.getTextTopOffset() - 3.0F;
        float innerBottom = textY + fontRenderer.getTextBottomOffset() + 2.0F;
        boolean renderBaseFill = alpha > 0.01F;
        if (renderBaseFill) {
            worldRenderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
            worldRenderer
                    .vertex(innerLeft, innerTop, 0.0)
                    .color(0.0F, 0.0F, 0.0F, alpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerLeft, innerBottom, 0.0)
                    .color(0.0F, 0.0F, 0.0F, alpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerRight, innerBottom, 0.0)
                    .color(0.0F, 0.0F, 0.0F, alpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerRight, innerTop, 0.0)
                    .color(0.0F, 0.0F, 0.0F, alpha)
                    .nextVertex();
            tessellator.end();
        }

        int borderColor = relationshipColor != -1 ? relationshipColor : teamColor;
        if (this.bgBorder.isToggled() || relationshipColor != -1) {
            float red;
            float green;
            float blue;
            if (borderColor != -1) {
                red = (borderColor >> 16 & 0xFF) / 255.0F;
                green = (borderColor >> 8 & 0xFF) / 255.0F;
                blue = (borderColor & 0xFF) / 255.0F;
            } else {
                red = 0.6F;
                green = 0.6F;
                blue = 0.6F;
            }

            float borderThickness = 1.0F;
            float borderAlpha = relationshipColor != -1 ? alpha : 1.0F;
            float left = innerLeft - borderThickness;
            float right = innerRight + borderThickness;
            float top = innerTop - borderThickness;
            float bottom = innerBottom + borderThickness;
            float borderZ = -0.001F;
            worldRenderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
            worldRenderer
                    .vertex(left, top, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(left, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, top, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(left, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(left, bottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, bottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(left, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(left, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerLeft, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerLeft, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerRight, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(innerRight, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, innerBottom, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            worldRenderer
                    .vertex(right, innerTop, borderZ)
                    .color(red, green, blue, borderAlpha)
                    .nextVertex();
            tessellator.end();
        }

        GlStateManager.enableTexture();
    }

    private int[] findVisiblePlayerNameRange(String formattedText, String playerName) {
        String strippedText = this.stripFormattingCodes(formattedText);
        int nameStart = strippedText.indexOf(playerName);
        return nameStart < 0 ? new int[] {-1, -1} : new int[] {nameStart, nameStart + playerName.length()};
    }

    private String stripFormattingCodes(String text) {
        StringBuilder builder = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == 167 && i + 1 < text.length()) {
                i++;
            } else {
                builder.append(character);
            }
        }

        return builder.toString();
    }

    private String getSelectedFontName() {
        if (this.font == null) {
            return FontManager.getDefaultFontName();
        }

        int index = (int) Math.max(0.0, Math.min(this.font.getOptions().length - 1, this.font.getInput()));
        return this.font.getOptions()[index];
    }

    private FeralFontRenderer getNametagFontRenderer() {
        return FontManager.getNametagRenderer(this.getSelectedFontName());
    }

    private String appendHealth(String name, LivingEntity entity) {
        float health = Math.max(0.0F, entity.getHealth());
        float maxHealth = entity.getMaxHealth();
        if (maxHealth <= 0.0F) {
            maxHealth = 20.0F;
        }

        boolean heartsMode = Settings.showHealthAsHearts.isToggled();
        double ratio = health / maxHealth;
        String color = ratio < 0.3 ? "§c" : (ratio < 0.5 ? "§6" : (ratio < 0.7 ? "§e" : "§a"));
        float displayValue = heartsMode ? health / 2.0F : health;
        String valueStr = this.fastOneDecimal(displayValue);
        String heartSuffix = Settings.showHeartSymbol.isToggled() ? " ❤" : "";
        name = name + " " + color + valueStr + heartSuffix;
        float absorption = entity.getAbsorption();
        if (absorption > 0.0F) {
            float absDisplay = heartsMode ? absorption / 2.0F : absorption;
            String absStr = this.fastOneDecimal(absDisplay);
            String absSuffix = Settings.showHeartSymbol.isToggled() ? " ❤" : "";
            name = name + " §6+" + absStr + absSuffix;
        }

        return name + "§r";
    }

    private String fastOneDecimal(float value) {
        int whole = (int) value;
        if (value == whole) {
            return String.valueOf(whole);
        }

        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        return intPart + "." + fracPart;
    }

    private void renderItemStack(ItemStack stack, int xPos, int yPos, TextRenderer fontRenderer) {
        if (stack != null) {
            RenderUtils.renderItemAndEffectIntoGui3D(stack, xPos, yPos);
            if (this.showEnchants.isToggled()) {
                GlStateManager.pushMatrix();
                GlStateManager.scaled(0.5, 0.5, 0.5);
                GlStateManager.translatef(0.0F, -10.0F, 0.0F);
                this.renderEnchantText(stack, xPos, yPos, fontRenderer);
                GlStateManager.popMatrix();
            }

            GlStateManager.disableDepthTest();
            if (stack.size > 1) {
                String countStr = String.valueOf(stack.size);
                fontRenderer.drawWithShadow(countStr, xPos + 17 - fontRenderer.getWidth(countStr), yPos + 9, 16777215);
            }

            if (this.showDurability.isToggled() && stack.isDamageable() && stack.getDamage() > 0) {
                int maxDamage = stack.getMaxDamage();
                int currentDamage = stack.getDamage();
                float durabilityRatio = 1.0F - (float) currentDamage / maxDamage;
                RenderUtils.drawDurabilityBar(xPos, yPos, durabilityRatio);
            }

            GlStateManager.enableDepthTest();
        }
    }

    private void renderEnchantText(ItemStack stack, int xPos, int yPos, TextRenderer fontRenderer) {
        Item item = stack.getItem();
        int[] ids;
        String[] abbreviations;
        if (item instanceof ArmorItem) {
            ids = ARMOR_ENCHANT_IDS;
            abbreviations = ARMOR_ENCHANT_ABBR;
        } else if (item instanceof SwordItem) {
            ids = SWORD_ENCHANT_IDS;
            abbreviations = SWORD_ENCHANT_ABBR;
        } else if (item instanceof BowItem) {
            ids = BOW_ENCHANT_IDS;
            abbreviations = BOW_ENCHANT_ABBR;
        } else if (item instanceof ToolItem) {
            ids = TOOL_ENCHANT_IDS;
            abbreviations = TOOL_ENCHANT_ABBR;
        } else {
            ids = MISC_ENCHANT_IDS;
            abbreviations = MISC_ENCHANT_ABBR;
        }

        int drawX = xPos * 2;
        int drawY = yPos - 24;

        for (int i = 0; i < ids.length; i++) {
            int level = EnchantmentHelper.getLevel(ids[i], stack);
            if (level > 0) {
                this.drawEnchantLine(fontRenderer, abbreviations[i], level, drawX, drawY);
                drawY += 8;
            }
        }
    }

    private void drawEnchantLine(TextRenderer fontRenderer, String abbreviation, int level, int x, int y) {
        fontRenderer.drawWithShadow(abbreviation, x, y, 16777215);
        int advance = fontRenderer.getWidth(abbreviation);
        fontRenderer.drawWithShadow(String.valueOf(level), x + advance, y, this.colorForEnchantLevel(level));
    }

    private int colorForEnchantLevel(int level) {
        if (level <= 5) {
            if (level == 1) {
                return 16777215;
            }

            if (level == 2) {
                return 5636095;
            }

            if (level == 3) {
                return 43690;
            }

            if (level == 4) {
                return 11141290;
            }

            if (level == 5) {
                return 16755200;
            }
        }

        return 16733695;
    }

    private static class NametagRenderState {
        private LivingEntity player;
        private String displayName;
        private int stringHalfWidth;
        private int teamColor;
        private int relationshipColor;
        private double distanceSq;
        private float baseScale;
        private float yOffset;
        private ItemStack heldItem;
        private ItemStack boots;
        private ItemStack leggings;
        private ItemStack chestplate;
        private ItemStack helmet;
        private int totalItems;

        private NametagRenderState() {}

        private void set(
                LivingEntity player,
                String displayName,
                int stringHalfWidth,
                int teamColor,
                int relationshipColor,
                double distanceSq,
                float baseScale,
                float yOffset,
                ItemStack heldItem,
                ItemStack boots,
                ItemStack leggings,
                ItemStack chestplate,
                ItemStack helmet,
                int totalItems) {
            this.player = player;
            this.displayName = displayName;
            this.stringHalfWidth = stringHalfWidth;
            this.teamColor = teamColor;
            this.relationshipColor = relationshipColor;
            this.distanceSq = distanceSq;
            this.baseScale = baseScale;
            this.yOffset = yOffset;
            this.heldItem = heldItem;
            this.boots = boots;
            this.leggings = leggings;
            this.chestplate = chestplate;
            this.helmet = helmet;
            this.totalItems = totalItems;
        }
    }
}

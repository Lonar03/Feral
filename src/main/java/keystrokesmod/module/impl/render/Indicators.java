package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityArrow;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class Indicators extends Module {
    private GroupSetting items;
    private ButtonSetting renderArrows;
    private ButtonSetting renderPearls;
    private ButtonSetting renderFireballs;
    private ButtonSetting renderEggs;
    private ButtonSetting renderSnowballs;
    private final RenderUtils.DirectionalGeometry geometry = new RenderUtils.DirectionalGeometry();
    private SliderSetting arrow;
    private SliderSetting radius;
    private SliderSetting scale;
    private SliderSetting font;
    private ButtonSetting tiltArrow;
    private ButtonSetting itemColors;
    private ButtonSetting renderItem;
    private ButtonSetting renderDistance;
    private ButtonSetting onlyWhenApproaching;
    private ButtonSetting renderOnlyOffScreen;
    private static final int APPROACH_INTERVAL_TICKS = 5;
    private static final double MIN_NET_TOWARD_BLOCKS = 1.0;
    private int tickCounter;
    private final Map<Entity, Vec3d> lastPosition = new HashMap<>();
    private final Set<Entity> entitiesToRender = new HashSet<>();

    public Indicators() {
        super("Indicators", Module.category.render);
        this.liteModule = true;
        this.registerSetting(
                this.arrow =
                        new SliderSetting("Arrow", 0, new String[] {"Chevron", "Caret", "Greater than", "Triangle"}));
        this.registerSetting(this.radius = new SliderSetting("Circle radius", 50.0, 30.0, 200.0, 5.0));
        this.registerSetting(this.scale = new SliderSetting("Sscale", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(this.font = FontManager.createFontSetting("Font"));
        this.registerSetting(this.tiltArrow = new ButtonSetting("Tilt arrow", false));
        this.registerSetting(this.itemColors = new ButtonSetting("Item colors", true));
        this.registerSetting(this.renderItem = new ButtonSetting("Render item", true));
        this.registerSetting(this.renderDistance = new ButtonSetting("Render distance", true));
        this.registerSetting(this.onlyWhenApproaching = new ButtonSetting("Only when approaching", false));
        this.registerSetting(this.renderOnlyOffScreen = new ButtonSetting("Render only offscreen", false));
        this.registerSetting(this.items = new GroupSetting("Items"));
        this.registerSetting(this.renderArrows = new ButtonSetting(this.items, "Render arrows", true));
        this.registerSetting(this.renderPearls = new ButtonSetting(this.items, "Render ender pearls", true));
        this.registerSetting(this.renderFireballs = new ButtonSetting(this.items, "Render fireballs", true));
        this.registerSetting(this.renderEggs = new ButtonSetting(this.items, "Render eggs", false));
        this.registerSetting(this.renderSnowballs = new ButtonSetting(this.items, "Render snowballs", false));
    }

    @Override
    public void onDisable() {
        this.lastPosition.clear();
        this.entitiesToRender.clear();
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (Utils.nullCheck()) {
                this.tickCounter++;
                if (this.tickCounter % 5 == 0) {
                    Set<Entity> seen = new HashSet<>();
                    this.entitiesToRender.clear();
                    double px = mc.player.x;
                    double py = mc.player.y;
                    double pz = mc.player.z;

                    for (Entity en : mc.world.entities) {
                        if (en != null && en != mc.player) {
                            ItemStack itemStack = this.getTrackedItemStack(en);
                            if (itemStack != null && this.canRender(en)) {
                                seen.add(en);
                                Vec3d posThen = this.lastPosition.get(en);
                                if (this.onlyWhenApproaching.isToggled()) {
                                    if (posThen == null) {
                                        this.lastPosition.put(en, new Vec3d(en.x, en.y, en.z));
                                        continue;
                                    }

                                    double distanceThen = Math.sqrt((px - posThen.x) * (px - posThen.x)
                                            + (py - posThen.y) * (py - posThen.y)
                                            + (pz - posThen.z) * (pz - posThen.z));
                                    double distanceNow = mc.player.distanceTo(en);
                                    if (distanceThen - distanceNow <= 1.0) {
                                        this.lastPosition.put(en, new Vec3d(en.x, en.y, en.z));
                                        continue;
                                    }
                                }

                                this.entitiesToRender.add(en);
                                this.lastPosition.put(en, new Vec3d(en.x, en.y, en.z));
                            }
                        }
                    }

                    this.lastPosition.keySet().retainAll(seen);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (mc.screen == null && Utils.nullCheck()) {
                try {
                    for (Entity en : this.entitiesToRender) {
                        ItemStack itemStack = this.getTrackedItemStack(en);
                        if (itemStack != null) {
                            this.renderIndicatorFor(en, itemStack, event.renderTickTime);
                        }
                    }
                } catch (Exception var5) {
                }
            }
        }
    }

    private ItemStack getTrackedItemStack(Entity en) {
        if (en == null) {
            return null;
        } else if (en instanceof ArrowEntity) {
            return ((IAccessorEntityArrow) en).getInGround() ? null : new ItemStack(Items.ARROW);
        } else if (en instanceof ProjectileEntity) {
            return new ItemStack(Items.FIRE_CHARGE);
        } else if (en instanceof EnderPearlEntity) {
            return new ItemStack(Items.ENDER_PEARL);
        } else if (en instanceof EggEntity) {
            return new ItemStack(Items.EGG);
        } else {
            return en instanceof SnowballEntity ? new ItemStack(Items.SNOWBALL) : null;
        }
    }

    private boolean canRender(Entity entity) {
        if (entity instanceof ArrowEntity
                && !((IAccessorEntityArrow) entity).getInGround()
                && this.renderArrows.isToggled()) {
            return true;
        } else if (entity instanceof FireballEntity && this.renderFireballs.isToggled()) {
            return true;
        } else if (entity instanceof EnderPearlEntity && this.renderPearls.isToggled()) {
            return true;
        } else {
            return entity instanceof EggEntity && this.renderEggs.isToggled()
                    ? true
                    : entity instanceof SnowballEntity && this.renderSnowballs.isToggled();
        }
    }

    private void renderIndicatorFor(Entity en, ItemStack itemStack, float partialTicks) {
        if (this.canRender(en)) {
            if (this.shouldRender(en, itemStack)) {
                if (!this.renderOnlyOffScreen.isToggled() || !RenderUtils.isInViewFrustum(en)) {
                    Color colorForStack = this.getColorForItem(itemStack);
                    int color = this.itemColors.isToggled() ? colorForStack.getRGB() : -1;
                    RenderUtils.DirectionalGeometry geometry = this.geometry;
                    geometry.interpolate(en, partialTicks);
                    Window scaledResolution = new Window(mc);
                    boolean render3D = this.tiltArrow.isToggled();
                    Vec3d vec = null;
                    if (!render3D) {
                        ((IAccessorEntityRenderer) mc.gameRenderer)
                                .callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().partialTick, 0);
                        vec = RenderUtils.convertTo2D(scaledResolution.getScale(), geometry.x, geometry.y, geometry.z);
                    }

                    if (render3D || vec != null) {
                        mc.gameRenderer.setupGuiState();
                        Window res = new Window(mc);
                        geometry.direction(
                                render3D,
                                vec,
                                res.getWidth(),
                                res.getHeight(),
                                render3D ? mc.getEntityRenderDispatcher().cameraYaw : 0.0F);
                        double radiusInput = this.radius.getInput();
                        double scaleInput = this.scale.getInput();
                        if (this.renderItem.isToggled()) {
                            radiusInput += 20.0;
                        }

                        if (geometry.inFrustum && geometry.hypotenuse < radiusInput + 15.0 * scaleInput) {
                            return;
                        }

                        geometry.preparePlacement(res.getWidth(), res.getHeight());
                        float tilt = RenderUtils.DirectionalGeometry.tilt(
                                render3D, render3D ? mc.getEntityRenderDispatcher().cameraPitch : 0.0F);
                        double verticalScale = Math.cos(Math.toRadians(tilt));
                        double renderX = geometry.x(radiusInput);
                        double renderY = geometry.y(radiusInput, verticalScale);
                        GlStateManager.pushMatrix();
                        GlStateManager.translated(renderX, renderY, 0.0);
                        GlStateManager.rotatef(tilt, 1.0F, 0.0F, 0.0F);
                        GlStateManager.rotatef((float) geometry.rotation, 0.0F, 0.0F, 1.0F);
                        GlStateManager.scaled(scaleInput, scaleInput, scaleInput);
                        int arrowInput = (int) this.arrow.getInput();
                        if (arrowInput == 0) {
                            if (color == -1) {
                                GL11.glColor3d(1.0, 1.0, 1.0);
                            } else {
                                GL11.glColor3d(
                                        colorForStack.getRed(), colorForStack.getGreen(), colorForStack.getBlue());
                            }

                            GL11.glEnable(3042);
                            GL11.glDisable(3553);
                            GL11.glBlendFunc(770, 771);
                            GL11.glEnable(2848);
                            double halfAngle = 0.61086524F;
                            double size = 9.0;
                            double offsetY = 5.0;
                            GL11.glLineWidth((float) (3.0 * scaleInput));
                            GL11.glBegin(3);
                            GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                            GL11.glVertex2d(0.0, -offsetY);
                            GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                            GL11.glEnd();
                            GL11.glEnable(3553);
                            GL11.glDisable(3042);
                            GL11.glDisable(2848);
                        } else if (arrowInput == 1) {
                            RenderUtils.drawFilledCaret(10.0 / scaledResolution.getScale(), color);
                        } else if (arrowInput == 2) {
                            GlStateManager.rotatef(-90.0F, 0.0F, 0.0F, 1.0F);
                            GlStateManager.scaled(1.5, 1.5, 1.5);
                            FeralFontRenderer fr = this.getIndicatorFontRenderer();
                            fr.drawString(">", -2.0F, -4.0F, color, false);
                        } else if (arrowInput == 3) {
                            RenderUtils.draw2DPolygon(0.0, 0.0, 5.0, 3, Utils.mergeAlpha(color, 255));
                        }

                        GlStateManager.popMatrix();
                        renderX = geometry.x(radiusInput - 13.0 * scaleInput);
                        renderY = geometry.y(radiusInput - 13.0 * scaleInput, verticalScale);
                        GlStateManager.pushMatrix();
                        GlStateManager.translated(renderX, renderY, 0.0);
                        GlStateManager.scaled(0.8 * scaleInput, 0.8 * scaleInput, 0.8 * scaleInput);
                        if (this.renderDistance.isToggled()) {
                            String text = (int) mc.player.distanceTo(en) + "m";
                            FeralFontRenderer fr = this.getIndicatorFontRenderer();
                            fr.drawString(text, -fr.getStringWidth(text) / 2, -4.0F, -1, true);
                        }

                        GlStateManager.popMatrix();
                        if (this.renderItem.isToggled() && itemStack != null) {
                            GlStateManager.pushMatrix();
                            if (itemStack.getItem() == Items.ARROW) {
                                renderX = geometry.x(radiusInput - 26.0 * Math.max(1.0, scaleInput));
                                renderY = geometry.y(radiusInput - 26.0 * Math.max(1.0, scaleInput), verticalScale);
                                GlStateManager.translated(renderX, renderY, 0.0);
                                GlStateManager.scalef(1.0F, 1.0F, 1.0F);
                                GlStateManager.rotatef((float) geometry.rotation - 45.0F, 0.0F, 0.0F, 1.0F);
                                mc.getItemRenderer().renderGuiItemModel(itemStack, -12, -4);
                            } else {
                                renderX = geometry.x(radiusInput - 29.0 * Math.max(1.0, scaleInput));
                                renderY = geometry.y(radiusInput - 29.0 * Math.max(1.0, scaleInput), verticalScale);
                                GlStateManager.translated(renderX, renderY, 0.0);
                                GlStateManager.scalef(1.0F, 1.0F, 1.0F);
                                mc.getItemRenderer().renderGuiItemModel(itemStack, -8, -9);
                            }

                            GlStateManager.popMatrix();
                        }
                    }
                }
            }
        }
    }

    private Color getColorForItem(ItemStack itemStack) {
        if (itemStack == null) {
            return Color.WHITE;
        } else if (itemStack.getItem() == Items.ENDER_PEARL) {
            return new Color(210, 0, 255);
        } else if (itemStack.getItem() == Items.FIRE_CHARGE) {
            return new Color(255, 150, 0);
        } else {
            return itemStack.getItem() == Items.EGG ? new Color(255, 238, 154) : Color.WHITE;
        }
    }

    private boolean shouldRender(Entity en, ItemStack stack) {
        return true;
    }

    private String getSelectedFontName() {
        if (this.font == null) {
            return FontManager.getDefaultFontName();
        }

        int index = (int) Math.max(0.0, Math.min(this.font.getOptions().length - 1, this.font.getInput()));
        return this.font.getOptions()[index];
    }

    private FeralFontRenderer getIndicatorFontRenderer() {
        return FontManager.getNametagRenderer(this.getSelectedFontName());
    }
}

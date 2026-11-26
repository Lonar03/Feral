package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderLivingEvent.Specials.Pre;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.WorldEntityScan.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;

/**
 * Makes hologram text (armor stand names: NPC and lobby text) render bright and through walls.
 * Feral draws the labels in the world-render-last pass (like Nametags) instead of inside the entity
 * renderer, because the OneClient pack's renderer (Argentum) batches entity rendering.
 */
public class HoloVis extends Module {
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    public HoloVis() {
        super("Holo Vis", Module.category.render);
    }

    /** Hides the vanilla label of named armor stands; {@link #onRenderWorldLast} draws them instead. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpecialsPre(Pre event) {
        if (event.entity instanceof ArmorStandEntity && isHologram((ArmorStandEntity) event.entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        EntityRenderDispatcher rm = mc.getEntityRenderDispatcher();
        float partialTicks = event.partialTicks;
        for (Entity entity : OverlayEntityScans.render(event, mc, Kind.ARMOR_STANDS)) {
            if (!(entity instanceof ArmorStandEntity) || !isHologram((ArmorStandEntity) entity)) {
                continue;
            }

            double x = entity.prevX + (entity.x - entity.prevX) * partialTicks - rm.cameraX;
            double y = entity.prevY + (entity.y - entity.prevY) * partialTicks - rm.cameraY;
            double z = entity.prevZ + (entity.z - entity.prevZ) * partialTicks - rm.cameraZ;
            if (x * x + y * y + z * z <= MAX_DISTANCE_SQ) {
                this.renderBrightName((ArmorStandEntity) entity, x, y, z);
            }
        }
    }

    /** Armor stands whose name vanilla shows all the time (holograms), not ones that only show it when hovered. */
    private static boolean isHologram(ArmorStandEntity stand) {
        return stand.hasCustomName() && stand.isCustomNameVisible();
    }

    private void renderBrightName(ArmorStandEntity stand, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher rm = mc.getEntityRenderDispatcher();
        TextRenderer fr = mc.textRenderer;
        String text = stand.getDisplayName().getFormattedString();
        if (text == null || text.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translatef((float) x, (float) y + stand.height + 0.5F, (float) z);
        GlStateManager.rotatef(-rm.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(rm.cameraPitch, 1.0F, 0.0F, 0.0F);
        float scale = 0.02666667F;
        GlStateManager.scalef(-scale, -scale, scale);
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        int width = fr.getWidth(text) / 2;
        fr.draw(text, -width, 0, -1);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}

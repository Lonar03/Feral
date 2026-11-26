package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.OverlayEntityScans;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.WorldEntityScan.Kind;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PrimedTntEntity;
import org.lwjgl.opengl.GL11;

public class TNTTimer extends Module {
    private static final int BEDWARS_FUSE_OFFSET = 28;
    private static final float BEDWARS_MAX_FUSE = 52.0F;
    private static final float NORMAL_MAX_FUSE = 80.0F;
    private final SliderSetting scale;
    private final DecimalFormat timeFormatter = new DecimalFormat("0.0");
    private final ArrayList<PrimedTntEntity> trackedTnt = new ArrayList<>();
    private int trackedTntCount = 0;
    private boolean trackedBedwars = false;

    public TNTTimer() {
        super("TNT Timer", Module.category.render, 0);
        this.registerSetting(this.scale = new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1));
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            this.updateTrackedTnt(event);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.trackedTntCount != 0) {
            EntityRenderDispatcher rm = mc.getEntityRenderDispatcher();
            TextRenderer fr = mc.textRenderer;
            float partialTicks = e.partialTicks;
            boolean bedwars = this.trackedBedwars;

            for (int i = 0; i < this.trackedTntCount; i++) {
                PrimedTntEntity tnt = this.trackedTnt.get(i);
                if (tnt != null && !tnt.removed) {
                    int fuse = bedwars ? tnt.fuseTimer - 28 : tnt.fuseTimer;
                    if (fuse >= 1) {
                        double x = tnt.prevX + (tnt.x - tnt.prevX) * partialTicks - rm.cameraX;
                        double y = tnt.prevY + (tnt.y - tnt.prevY) * partialTicks - rm.cameraY;
                        double z = tnt.prevZ + (tnt.z - tnt.prevZ) * partialTicks - rm.cameraZ;
                        this.renderTimer(fr, rm, tnt, x, y, z, fuse, partialTicks, bedwars);
                    }
                }
            }
        }
    }

    private void updateTrackedTnt(ForgeEvents.TickEvent.ClientTickEvent event) {
        this.trackedTntCount = 0;
        this.trackedBedwars = false;
        if (Utils.nullCheck() && mc.world != null) {
            this.trackedBedwars = Utils.sidebarTitleContainsBedWars();

            for (Entity entity : OverlayEntityScans.tick(event, mc, Kind.PRIMED_TNT)) {
                if (entity instanceof PrimedTntEntity) {
                    PrimedTntEntity tnt = (PrimedTntEntity) entity;
                    int fuse = this.trackedBedwars ? tnt.fuseTimer - 28 : tnt.fuseTimer;
                    if (fuse >= 1 && !(tnt.squaredDistanceTo(mc.player) > 4096.0)) {
                        if (this.trackedTntCount >= this.trackedTnt.size()) {
                            this.trackedTnt.add(tnt);
                        } else {
                            this.trackedTnt.set(this.trackedTntCount, tnt);
                        }

                        this.trackedTntCount++;
                    }
                }
            }
        }
    }

    private void renderTimer(
            TextRenderer fr,
            EntityRenderDispatcher rm,
            PrimedTntEntity tnt,
            double x,
            double y,
            double z,
            int fuse,
            float partialTicks,
            boolean bedwars) {
        String time = this.timeFormatter.format((fuse - partialTicks) / 20.0F);
        float maxFuse = bedwars ? 52.0F : 80.0F;
        float green = Math.min(fuse / maxFuse, 1.0F);
        Color color = new Color(1.0F - green, green, 0.0F);
        float s = 0.02666667F * (float) this.scale.getInput();
        GL11.glPushMatrix();
        GlStateManager.translatef((float) x, (float) y + tnt.height + 0.5F, (float) z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-rm.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(rm.cameraPitch * (mc.options.perspective == 2 ? -1 : 1), 1.0F, 0.0F, 0.0F);
        GlStateManager.scalef(-s, -s, s);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        int halfWidth = fr.getWidth(time) >> 1;
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.disableTexture();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
        wr.vertex(-halfWidth - 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
        wr.vertex(-halfWidth - 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
        wr.vertex(halfWidth + 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
        wr.vertex(halfWidth + 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
        tessellator.end();
        GlStateManager.enableTexture();
        fr.draw(time, -halfWidth, 0, color.getRGB());
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }
}

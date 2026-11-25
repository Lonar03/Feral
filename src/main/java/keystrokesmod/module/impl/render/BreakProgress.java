package keystrokesmod.module.impl.render;

import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.opengl.GL11;

public class BreakProgress extends Module {
    private SliderSetting mode;
    private ButtonSetting manual;
    private ButtonSetting bedAura;
    private ButtonSetting fadeIn;
    private String[] MODES = new String[] {"Percentage", "Second", "Decimal"};
    private float progress;
    private BlockPos block;
    private String progressStr;

    public BreakProgress() {
        super("BreakProgress", Module.category.render);
        this.liteModule = true;
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, this.MODES));
        this.registerSetting(this.manual = new ButtonSetting("Show manual", true));
        this.registerSetting(this.bedAura = new ButtonSetting("Show BedAura", true));
        this.registerSetting(this.fadeIn = new ButtonSetting("Fade in", false));
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (this.progress != 0.0F && this.block != null && Utils.nullCheck()) {
            double x = this.block.getX() + 0.5 - mc.getEntityRenderDispatcher().cameraX;
            double y = this.block.getY() + 0.5 - mc.getEntityRenderDispatcher().cameraY;
            double z = this.block.getZ() + 0.5 - mc.getEntityRenderDispatcher().cameraZ;
            GlStateManager.pushMatrix();
            GlStateManager.translatef((float) x, (float) y, (float) z);
            GlStateManager.rotatef(-mc.getEntityRenderDispatcher().cameraYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(
                    (mc.options.perspective == 2 ? -1 : 1) * mc.getEntityRenderDispatcher().cameraPitch,
                    1.0F,
                    0.0F,
                    0.0F);
            GlStateManager.scalef(-0.02266667F, -0.02266667F, -0.02266667F);
            GlStateManager.depthMask(false);
            GlStateManager.disableDepthTest();
            GL11.glEnable(3042);
            int colorAlpha = Utils.mergeAlpha(-1, Math.max(10, (int) (255.0F * this.progress)));
            mc.textRenderer.draw(
                    this.progressStr,
                    -mc.textRenderer.getWidth(this.progressStr) / 2,
                    -3.0F,
                    this.fadeIn.isToggled() ? colorAlpha : -1,
                    true);
            GL11.glDisable(3042);
            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void setProgress() {
        switch ((int) this.mode.getInput()) {
            case 0:
                this.progressStr = (int) (100.0 * (this.progress / 1.0)) + "%";
                break;
            case 1:
                double timeLeft = Utils.round(
                        (1.0F - this.progress)
                                / BlockUtils.getBlockHardness(
                                        BlockUtils.getBlock(this.block), mc.player.getDisplayItemInHand(), false, false)
                                / 20.0,
                        1);
                this.progressStr = timeLeft == 0.0 ? "0" : timeLeft + "s";
                break;
            case 2:
                this.progressStr = String.valueOf(Utils.round(this.progress, 2));
        }
    }

    @Override
    public void onUpdate() {
        if (!mc.player.abilities.creativeMode && mc.player.abilities.canModifyWorld) {
            if (this.bedAura.isToggled() && ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled()) {
                BlockPos ap = ModuleManager.bedAura.getAuraTargetPos();
                float bp = ModuleManager.bedAura.getAuraBreakProgress();
                if (ap != null && bp > 0.0F) {
                    this.progress = Math.min(1.0F, bp);
                    this.block = ap;
                    this.setProgress();
                    return;
                }
            }

            if (this.manual.isToggled() && mc.crosshairTarget != null && mc.crosshairTarget.type == Type.BLOCK) {
                this.progress = ((IAccessorPlayerControllerMP) mc.interactionManager).getCurBlockDamageMP();
                if (this.progress == 0.0F) {
                    this.resetVariables();
                } else {
                    this.block = mc.crosshairTarget.getPos();
                    this.setProgress();
                }
            } else {
                this.resetVariables();
            }
        } else {
            this.resetVariables();
        }
    }

    @Override
    public void onDisable() {
        this.resetVariables();
    }

    private void resetVariables() {
        this.progress = 0.0F;
        this.block = null;
        this.progressStr = "";
    }
}

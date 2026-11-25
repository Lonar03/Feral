package keystrokesmod.module.impl.combat;

import java.awt.Color;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;

public class HitBox extends Module {
    public static SliderSetting multiplier;
    public ButtonSetting showHitbox;
    public ButtonSetting playersOnly;
    public ButtonSetting weaponOnly;
    private Entity pointedEntity;
    private HitResult mv;

    public HitBox() {
        super("Hitboxes", Module.category.combat, 0);
        this.registerSetting(multiplier = new SliderSetting("Multiplier", "x", 1.2, 1.0, 5.0, 0.05));
        this.registerSetting(this.playersOnly = new ButtonSetting("Players only", true));
        this.registerSetting(this.showHitbox = new ButtonSetting("Show new hitbox", false));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return ((int) multiplier.getInput() == multiplier.getInput()
                        ? (int) multiplier.getInput() + ""
                        : multiplier.getInput())
                + multiplier.getSuffix();
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.button == 0
                && e.buttonstate
                && Utils.nullCheck()
                && multiplier.getInput() != 1.0
                && !mc.player.isSwordBlocking()
                && mc.screen == null) {
            if (!this.weaponOnly.isToggled() || Utils.holdingWeapon()) {
                Entity c = this.getEntity(1.0F);
                if (c != null) {
                    if (c instanceof PlayerEntity) {
                        if (Utils.isFriended((PlayerEntity) c)) {
                            return;
                        }
                    } else if (this.playersOnly.isToggled()) {
                        return;
                    }

                    mc.crosshairTarget = this.mv;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (this.showHitbox.isToggled() && Utils.nullCheck()) {
            for (Entity en : mc.world.entities) {
                if (en != mc.player
                        && en instanceof LivingEntity
                        && ((LivingEntity) en).deathTicks == 0
                        && !(en instanceof ArmorStandEntity)
                        && !en.isInvisible()) {
                    this.rh(en, Color.WHITE);
                }
            }
        }
    }

    public static double getExpand(Entity en) {
        return multiplier.getInput();
    }

    public Entity getEntity(float partialTicks) {
        if (mc.getCamera() != null && mc.world != null) {
            mc.targetEntity = null;
            this.pointedEntity = null;
            double d0 = mc.interactionManager.hasExtendedReach()
                    ? 6.0
                    : (ModuleManager.reach.isEnabled()
                            ? Utils.getRandomValue(Reach.min, Reach.max, Utils.getRandom())
                            : 3.0);
            this.mv = mc.getCamera().rayTrace(d0, partialTicks);
            double d2 = d0;
            Vec3d vec3 = mc.getCamera().getEyePosition(partialTicks);
            if (this.mv != null) {
                d2 = this.mv.facePos.distanceTo(vec3);
            }

            Vec3d vec4 = mc.getCamera().getRotationVector(partialTicks);
            Vec3d vec5 = vec3.add(vec4.x * d0, vec4.y * d0, vec4.z * d0);
            Vec3d vec6 = null;
            float f1 = 1.0F;
            List list = mc.world.getEntities(
                    mc.getCamera(),
                    mc.getCamera()
                            .getShape()
                            .expanded(vec4.x * d0, vec4.y * d0, vec4.z * d0)
                            .grown(f1, f1, f1));
            double d3 = d2;

            for (Object o : list) {
                Entity entity = (Entity) o;
                if (entity.hasCollision()) {
                    float ex = (float) (entity.getPickRadius() * getExpand(entity));
                    Box ax = entity.getShape().grown(ex, ex, ex);
                    HitResult mop = ax.clip(vec3, vec5);
                    if (ax.contains(vec3)) {
                        if (0.0 < d3 || d3 == 0.0) {
                            this.pointedEntity = entity;
                            vec6 = mop == null ? vec3 : mop.facePos;
                            d3 = 0.0;
                        }
                    } else if (mop != null) {
                        double d4 = vec3.distanceTo(mop.facePos);
                        if (d4 < d3 || d3 == 0.0) {
                            if (entity != mc.getCamera().vehicle || ClientPlatform.canRiderInteract(entity)) {
                                this.pointedEntity = entity;
                                vec6 = mop.facePos;
                                d3 = d4;
                            } else if (d3 == 0.0) {
                                this.pointedEntity = entity;
                                vec6 = mop.facePos;
                            }
                        }
                    }
                }
            }

            if (this.pointedEntity != null && (d3 < d2 || this.mv == null)) {
                this.mv = new HitResult(this.pointedEntity, vec6);
                if (this.pointedEntity instanceof LivingEntity || this.pointedEntity instanceof ItemFrameEntity) {
                    return this.pointedEntity;
                }
            }
        }

        return null;
    }

    private void rh(Entity e, Color c) {
        if (e instanceof LivingEntity) {
            float partialTicks = ((IAccessorMinecraft) mc).getTimer().partialTick;
            double x = e.prevX + (e.x - e.prevX) * partialTicks - mc.getEntityRenderDispatcher().cameraX;
            double y = e.prevY + (e.y - e.prevY) * partialTicks - mc.getEntityRenderDispatcher().cameraY;
            double z = e.prevZ + (e.z - e.prevZ) * partialTicks - mc.getEntityRenderDispatcher().cameraZ;
            float ex = (float) (e.getPickRadius() * multiplier.getInput());
            Box bbox = e.getShape().grown(ex, ex, ex);
            Box axis = new Box(
                    bbox.minX - e.x + x,
                    bbox.minY - e.y + y,
                    bbox.minZ - e.z + z,
                    bbox.maxX - e.x + x,
                    bbox.maxY - e.y + y,
                    bbox.maxZ - e.z + z);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            GL11.glColor3d(c.getRed(), c.getGreen(), c.getBlue());
            WorldRenderer.renderOutlineShape(axis);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
        }
    }
}

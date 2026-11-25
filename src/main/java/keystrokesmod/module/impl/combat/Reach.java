package keystrokesmod.module.impl.combat;

import java.util.List;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

public class Reach extends Module {
    public static SliderSetting min;
    public static SliderSetting max;
    public static ButtonSetting weaponOnly;
    public static ButtonSetting movingOnly;
    public static ButtonSetting sprintOnly;
    public static ButtonSetting hitThroughBlocks;

    public Reach() {
        super("Reach", Module.category.combat, 0);
        this.liteModule = true;
        this.registerSetting(min = new SliderSetting("Min", 3.1, 3.0, 6.0, 0.05));
        this.registerSetting(max = new SliderSetting("Max", 3.3, 3.0, 6.0, 0.05));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(movingOnly = new ButtonSetting("Moving only", false));
        this.registerSetting(sprintOnly = new ButtonSetting("Sprint only", false));
        this.registerSetting(hitThroughBlocks = new ButtonSetting("Hit through blocks", false));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        Utils.correctValue(min, max);
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent ev) {
        if (ev.button >= 0 && ev.buttonstate && Utils.nullCheck()) {
            call();
        }
    }

    public static boolean call() {
        if (!Utils.nullCheck()) {
            return false;
        }

        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }

        if (movingOnly.isToggled() && mc.player.forwardSpeed == 0.0 && mc.player.sidewaysSpeed == 0.0) {
            return false;
        }

        if (sprintOnly.isToggled() && !mc.player.isSprinting()) {
            return false;
        }

        if (!hitThroughBlocks.isToggled() && mc.crosshairTarget != null) {
            BlockPos p = mc.crosshairTarget.getPos();
            if (p != null && mc.world.getBlockState(p).getBlock() != Blocks.AIR) {
                return false;
            }
        }

        double reach = Utils.getRandomValue(min, max, Utils.getRandom());
        Object[] entity = getEntity(reach, 0.0);
        if (entity == null) {
            return false;
        }

        Entity en = (Entity) entity[0];
        mc.crosshairTarget = new HitResult(en, (Vec3d) entity[1]);
        mc.targetEntity = en;
        return true;
    }

    private static Object[] getEntity(double reach, double expand) {
        if (!ModuleManager.reach.isEnabled()) {
            reach = mc.interactionManager.hasExtendedReach() ? 6.0 : 3.0;
        }

        return getEntity(reach, expand, null);
    }

    public static Object[] getEntity(double reach, double expand, float[] rotations) {
        Entity zz2 = mc.getCamera();
        Entity entity = null;
        if (zz2 == null) {
            return null;
        }

        Vec3d zz3 = zz2.getEyePosition(1.0F);
        Vec3d zz4;
        if (rotations != null) {
            zz4 = RotationUtils.getVectorForRotation(rotations[1], rotations[0]);
        } else {
            zz4 = zz2.getRotationVector(1.0F);
        }

        Vec3d zz5 = zz3.add(zz4.x * reach, zz4.y * reach, zz4.z * reach);
        Vec3d hitVec = null;
        List zz8 = mc.world.getEntities(
                zz2,
                zz2.getShape()
                        .expanded(zz4.x * reach, zz4.y * reach, zz4.z * reach)
                        .grown(1.0, 1.0, 1.0));
        double zz9 = reach;

        for (int zz10 = 0; zz10 < zz8.size(); zz10++) {
            Entity zz11 = (Entity) zz8.get(zz10);
            if (zz11.hasCollision()) {
                float ex = (float) (zz11.getPickRadius() * HitBox.getExpand(zz11));
                Box zz13 = zz11.getShape().grown(ex, ex, ex);
                zz13 = zz13.grown(expand, expand, expand);
                HitResult zz14 = zz13.clip(zz3, zz5);
                if (zz13.contains(zz3)) {
                    if (0.0 < zz9 || zz9 == 0.0) {
                        entity = zz11;
                        hitVec = zz14 == null ? zz3 : zz14.facePos;
                        zz9 = 0.0;
                    }
                } else if (zz14 != null) {
                    double zz15 = zz3.distanceTo(zz14.facePos);
                    if (zz15 < zz9 || zz9 == 0.0) {
                        if (zz11 == zz2.vehicle) {
                            if (zz9 == 0.0) {
                                entity = zz11;
                                hitVec = zz14.facePos;
                            }
                        } else {
                            entity = zz11;
                            hitVec = zz14.facePos;
                            zz9 = zz15;
                        }
                    }
                }
            }
        }

        if (zz9 < reach && !(entity instanceof LivingEntity) && !(entity instanceof ItemFrameEntity)) {
            entity = null;
        }

        return entity != null && hitVec != null ? new Object[] {entity, hitVec} : null;
    }
}

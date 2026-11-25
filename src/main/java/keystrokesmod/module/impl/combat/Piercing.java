package keystrokesmod.module.impl.combat;

import com.google.common.base.Predicates;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class Piercing extends Module {
    private SliderSetting sortMode;
    private ButtonSetting ignoreBlocks;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting ignoreNonPlayer;
    private ButtonSetting weaponOnly;
    private ButtonSetting insideHitboxOnly;
    private int lastMouseOverTick = -1;
    private String[] sortModes = new String[] {"Hurt time", "Health"};

    public Piercing() {
        super("Piercing", Module.category.combat);
        this.registerSetting(this.sortMode = new SliderSetting("Sort mode", 0, this.sortModes));
        this.registerSetting(this.ignoreBlocks = new ButtonSetting("Ignore blocks", false));
        this.registerSetting(this.ignoreNonPlayer = new ButtonSetting("Ignore non-players", true));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(this.insideHitboxOnly = new ButtonSetting("Inside hitbox only", false));
    }

    @Override
    public String getInfo() {
        return this.sortModes[(int) this.sortMode.getInput()];
    }

    public boolean shouldOverrideMouseOver() {
        if (!this.isEnabled()) {
            return false;
        } else if (mc == null || mc.player == null || mc.world == null) {
            return false;
        } else {
            return this.weaponOnly.isToggled() && !Utils.holdingWeapon()
                    ? false
                    : this.ignoreBlocks.isToggled()
                            || mc.crosshairTarget == null
                            || mc.crosshairTarget.type != Type.BLOCK;
        }
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (this.shouldOverrideMouseOver()) {
            this.keystrokesmod$modifyMouseOverVanillaLook(partialTicks);
        }
    }

    private void keystrokesmod$modifyMouseOverVanillaLook(float partialTicks) {
        Entity viewEntity = mc.getCamera();
        if (viewEntity != null && mc.world != null) {
            double reach = mc.interactionManager.getReach();
            Vec3d eyes = viewEntity.getEyePosition(partialTicks);
            if (mc.interactionManager.hasExtendedReach()) {
                reach = 6.0;
            }

            Vec3d look = viewEntity.getRotationVector(partialTicks);
            Vec3d rayEnd = eyes.add(look.x * reach, look.y * reach, look.z * reach);
            Entity best = null;
            Vec3d bestHit = null;
            double bestDist = Double.MAX_VALUE;
            boolean bestLiving = false;
            int bestHurt = Integer.MAX_VALUE;
            float bestHp = Float.POSITIVE_INFINITY;
            int modeSel = (int) this.sortMode.getInput();

            for (Entity e : mc.world.getEntities(
                    viewEntity,
                    viewEntity
                            .getShape()
                            .expanded(look.x * reach, look.y * reach, look.z * reach)
                            .grown(1.0, 1.0, 1.0),
                    Predicates.and(EntityFilter.NOT_SPECTATOR, Entity::hasCollision))) {
                if ((!this.ignoreNonPlayer.isToggled() || e instanceof PlayerEntity)
                        && (!this.ignoreTeammates.isToggled() || !Utils.isTeammate(e))
                        && !AntiBot.isBot(e)
                        && (!(e instanceof PlayerEntity) || !Utils.isFriended((PlayerEntity) e))) {
                    float cb = e.getPickRadius();
                    Box bb = e.getShape().grown(cb, cb, cb);
                    HitResult hit = bb.clip(eyes, rayEnd);
                    boolean inside = bb.contains(eyes);
                    if (inside || hit != null) {
                        double dist = inside ? 0.0 : eyes.distanceTo(hit.facePos);
                        if ((mc.interactionManager.hasExtendedReach() || !(dist > 3.0))
                                && !(dist > reach)
                                && !(dist >= bestDist)
                                && (!this.insideHitboxOnly.isToggled() || !(dist > 0.1F))
                                && (e != viewEntity.vehicle || best == null)) {
                            boolean living = e instanceof LivingEntity;
                            int hurt = living ? ((LivingEntity) e).damagedTimer : Integer.MAX_VALUE;
                            float hp = living ? ((LivingEntity) e).getHealth() : Float.POSITIVE_INFINITY;
                            boolean take = false;
                            if (best == null) {
                                take = true;
                            } else if (living && !bestLiving) {
                                take = true;
                            } else if (living == bestLiving) {
                                if (!living) {
                                    take = dist < bestDist;
                                } else if (modeSel == 0) {
                                    if (hurt < bestHurt) {
                                        take = true;
                                    } else if (hurt == bestHurt && dist < bestDist) {
                                        take = true;
                                    }
                                } else if (hp < bestHp) {
                                    take = true;
                                } else if (hp == bestHp && dist < bestDist) {
                                    take = true;
                                }
                            }

                            if (take) {
                                best = e;
                                bestHit = inside ? (hit == null ? eyes : hit.facePos) : hit.facePos;
                                bestDist = dist;
                                bestLiving = living;
                                bestHurt = hurt;
                                bestHp = hp;
                            }
                        }
                    }
                }
            }

            if (best != null && reach > 3.0 && bestDist > 3.0 && !mc.interactionManager.hasExtendedReach()) {
                mc.crosshairTarget = new HitResult(Type.MISS, bestHit, null, new BlockPos(bestHit));
            } else {
                if (best != null) {
                    mc.crosshairTarget = new HitResult(best, bestHit);
                    if (best instanceof LivingEntity || best instanceof ItemFrameEntity) {
                        mc.targetEntity = best;
                    }
                }
            }
        }
    }
}

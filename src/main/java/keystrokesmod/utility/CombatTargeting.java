package keystrokesmod.utility;

import keystrokesmod.module.impl.world.AntiBot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;

public final class CombatTargeting implements IMinecraftInstance {
    private CombatTargeting() {}

    public static PlayerEntity findTarget(double maxDistanceSq) {
        return findTarget(maxDistanceSq, true);
    }

    public static PlayerEntity findTarget(double maxDistanceSq, boolean ignoreTeammates) {
        PlayerEntity mouseOverTarget = getMouseOverTarget(maxDistanceSq, ignoreTeammates);
        return mouseOverTarget != null ? mouseOverTarget : findClosestTarget(maxDistanceSq, ignoreTeammates);
    }

    public static PlayerEntity findClosestTarget(double maxDistanceSq) {
        return findClosestTarget(maxDistanceSq, true);
    }

    public static PlayerEntity findClosestTarget(double maxDistanceSq, boolean ignoreTeammates) {
        if (mc != null && mc.world != null) {
            PlayerEntity closest = null;
            double closestDistanceSq = Double.MAX_VALUE;

            for (PlayerEntity player : mc.world.players) {
                if (isValidPlayer(player, maxDistanceSq, ignoreTeammates)) {
                    double distanceSq = RotationUtils.distanceSqFromEyeToClosestOnAABB(player);
                    if (distanceSq < closestDistanceSq) {
                        closestDistanceSq = distanceSq;
                        closest = player;
                    }
                }
            }

            return closest;
        } else {
            return null;
        }
    }

    public static PlayerEntity getMouseOverTarget(double maxDistanceSq) {
        return getMouseOverTarget(maxDistanceSq, true);
    }

    public static PlayerEntity getMouseOverTarget(double maxDistanceSq, boolean ignoreTeammates) {
        if (mc != null && mc.crosshairTarget != null) {
            HitResult objectMouseOver = mc.crosshairTarget;
            return asValidPlayer(objectMouseOver.entity, maxDistanceSq, ignoreTeammates);
        } else {
            return null;
        }
    }

    public static PlayerEntity asValidPlayer(Entity entity, double maxDistanceSq) {
        return asValidPlayer(entity, maxDistanceSq, true);
    }

    public static PlayerEntity asValidPlayer(Entity entity, double maxDistanceSq, boolean ignoreTeammates) {
        if (!(entity instanceof PlayerEntity)) {
            return null;
        }

        PlayerEntity player = (PlayerEntity) entity;
        return isValidPlayer(player, maxDistanceSq, ignoreTeammates) ? player : null;
    }

    public static boolean isValidPlayer(PlayerEntity player, double maxDistanceSq) {
        return isValidPlayer(player, maxDistanceSq, true);
    }

    public static boolean isValidPlayer(PlayerEntity player, double maxDistanceSq, boolean ignoreTeammates) {
        return isTrackablePlayer(player, ignoreTeammates) && isWithinRange(player, maxDistanceSq);
    }

    public static boolean isTrackablePlayer(PlayerEntity player) {
        return isTrackablePlayer(player, true);
    }

    public static boolean isTrackablePlayer(PlayerEntity player, boolean ignoreTeammates) {
        if (!Utils.nullCheck() || player == null || player == mc.player || player.removed || player.deathTicks != 0) {
            return false;
        } else {
            return Utils.isFriended(player) || AntiBot.isBot(player)
                    ? false
                    : !ignoreTeammates || !Utils.isTeammate(player);
        }
    }

    public static boolean isWithinRange(PlayerEntity player, double maxDistanceSq) {
        return player == null ? false : RotationUtils.distanceSqFromEyeToClosestOnAABB(player) <= maxDistanceSq;
    }
}

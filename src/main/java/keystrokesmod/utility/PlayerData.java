package keystrokesmod.utility;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;

public class PlayerData {
    public double speed;
    public int aboveVoidTicks;
    public int fastTick;
    public int autoBlockTicks;
    public int ticksExisted;
    public int lastSneakTick;
    public double posZ;
    public int sneakTicks;
    public int noSlowTicks;
    public double posY;
    public boolean sneaking;
    public double posX;
    public double serverPosX;
    public double serverPosY;
    public double serverPosZ;

    public void update(PlayerEntity entityPlayer) {
        int ticksExisted = entityPlayer.ticks;
        this.posX = entityPlayer.x - entityPlayer.prevX;
        this.posY = entityPlayer.y - entityPlayer.prevY;
        this.posZ = entityPlayer.z - entityPlayer.prevZ;
        this.speed = Math.max(Math.abs(this.posX), Math.abs(this.posZ));
        if (this.speed >= 0.07) {
            this.fastTick++;
            this.ticksExisted = ticksExisted;
        } else {
            this.fastTick = 0;
        }

        if (Math.abs(this.posY) >= 0.1) {
            this.aboveVoidTicks = ticksExisted;
        }

        if (entityPlayer.isSneaking()) {
            this.lastSneakTick = ticksExisted;
        }

        if (entityPlayer.armSwinging && entityPlayer.isSwordBlocking()) {
            this.autoBlockTicks++;
        } else {
            this.autoBlockTicks = 0;
        }

        if (entityPlayer.isSprinting() && entityPlayer.hasItemInUse()) {
            this.noSlowTicks++;
        } else {
            this.noSlowTicks = 0;
        }

        if (!(entityPlayer.pitch >= 70.0F)
                || entityPlayer.getDisplayItemInHand() == null
                || !(entityPlayer.getDisplayItemInHand().getItem() instanceof BlockItem)) {
            this.sneakTicks = 0;
        } else if (entityPlayer.armSwingingTicks == 1) {
            if (!this.sneaking && entityPlayer.isSneaking()) {
                this.sneakTicks++;
            } else {
                this.sneakTicks = 0;
            }
        }
    }

    public void updateSneak(PlayerEntity entityPlayer) {
        this.sneaking = entityPlayer.isSneaking();
    }

    public void updateServerPos(PlayerEntity entityPlayer) {
        this.serverPosX = entityPlayer.lastKnownX / 32;
        this.serverPosY = entityPlayer.lastKnownY / 32;
        this.serverPosZ = entityPlayer.lastKnownZ / 32;
    }
}

package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.model.ItemStack;
import keystrokesmod.script.model.Vec3;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;

public class C08 extends CPacket {
    public ItemStack itemStack;
    public Vec3 position;
    public int direction;
    public Vec3 offset;

    public C08(ItemStack itemStack, Vec3 position, int direction, Vec3 offset) {
        super(null);
        this.itemStack = itemStack;
        this.position = position;
        this.direction = direction;
        this.offset = offset;
    }

    public C08(PlayerUseC2SPacket packet) {
        super(packet);
        this.itemStack = ItemStack.convert(packet.getItemInHand());
        this.position = Vec3.convert(packet.getPos());
        this.direction = packet.getFace();
        this.offset = new Vec3(packet.getFaceX(), packet.getFaceY(), packet.getFaceZ());
    }

    public PlayerUseC2SPacket convert() {
        return new PlayerUseC2SPacket(
                Vec3.getBlockPos(this.position),
                this.direction,
                this.itemStack != null ? this.itemStack.itemStack : null,
                (float) this.offset.x,
                (float) this.offset.y,
                (float) this.offset.z);
    }
}

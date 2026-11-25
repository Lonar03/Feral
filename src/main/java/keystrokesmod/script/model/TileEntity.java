package keystrokesmod.script.model;

import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.script.ScriptClassNames;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;

public class TileEntity {
    private BlockEntity tileEntity;
    private Vec3 position;
    public String type;
    public String name;

    public TileEntity(BlockEntity tileEntity) {
        this.tileEntity = tileEntity;
        this.position = new Vec3(
                tileEntity.getPos().getX(),
                tileEntity.getPos().getY(),
                tileEntity.getPos().getZ());
        this.type = ScriptClassNames.simpleName(tileEntity.getBlock().getClass());
        this.name = ClientPlatform.registryName(tileEntity.getBlock()).replace("minecraft:", "");
    }

    public Vec3 getPosition() {
        return this.position;
    }

    public Object[] getSkullData() {
        if (this.tileEntity instanceof SkullBlockEntity) {
            SkullBlockEntity skull = (SkullBlockEntity) this.tileEntity;
            Object[] skullData = new Object[] {skull.getType(), skull.getRotation(), null, null, null};
            if (skull.getProfile() == null) {
                skullData[2] = skullData[3] = null;
            } else {
                skullData[2] = skull.getProfile().getName();
                skullData[3] = skull.getProfile().getId();
            }

            return skullData;
        } else {
            return null;
        }
    }
}

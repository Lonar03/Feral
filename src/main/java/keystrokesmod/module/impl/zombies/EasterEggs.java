package keystrokesmod.module.impl.zombies;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesMap;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;

/**
 * Highlights Zombies easter egg blocks: the beds and bookshelves on Prison, the lever and buttons on Dead End and the
 * skull on Bad Blood. The debug logger (hold the key, look at a block) is how the positions were found.
 */
public class EasterEggs extends Module {
    /**
     * Prison positions come from a Housing recreation, shifted by (98, -48, 56): its bed at -11 128 -45 is the real
     * bed at 87 80 11.
     */
    private static final BlockPos[] PRISON = {
        // Beds (the other half is found from the bed itself)
        new BlockPos(87, 78, 11),
        new BlockPos(73, 80, 11),
        new BlockPos(59, 78, 11),
        new BlockPos(63, 80, -11),
        new BlockPos(70, 78, -11),
        new BlockPos(84, 80, -11),
        new BlockPos(84, 78, -11),
        // Bookshelves
        new BlockPos(122, 78, 1),
        new BlockPos(128, 81, 1),
        new BlockPos(125, 81, -12),
        new BlockPos(122, 78, -17),
        new BlockPos(132, 78, -17)
    };

    private static final BlockPos[] DEAD_END = {
        // Lever
        new BlockPos(-11, 68, 23),
        // Buttons
        new BlockPos(6, 76, 51),
        new BlockPos(6, 76, 55),
        new BlockPos(-8, 76, 54),
        new BlockPos(-8, 76, 50)
    };

    private static final BlockPos[] BAD_BLOOD = {
        // Skull
        new BlockPos(-14, 70, 10)
    };

    private final ButtonSetting prison;
    private final ButtonSetting deadEnd;
    private final ButtonSetting badBlood;
    private final ColorSetting color;
    private final ButtonSetting outline;
    private final ButtonSetting fill;
    private final ButtonSetting fpsDebugInfo;
    private final KeySetting debugKey;
    private BlockPos lastLogged;

    public EasterEggs() {
        super("Easter Eggs", Module.category.zombies);
        this.registerSetting(this.prison = new ButtonSetting("Prison", true));
        this.registerSetting(this.deadEnd = new ButtonSetting("Dead End", true));
        this.registerSetting(this.badBlood = new ButtonSetting("Bad Blood", true));
        this.registerSetting(this.color = new ColorSetting("Color", 255, 215, 0));
        this.registerSetting(this.outline = new ButtonSetting("Outline", true));
        this.registerSetting(this.fill = new ButtonSetting("Fill", true));
        this.registerSetting(new DescriptionSetting("Debug: hold the key, look at blocks."));
        this.registerSetting(this.fpsDebugInfo = new ButtonSetting("Debug log", false));
        this.registerSetting(this.debugKey = new KeySetting("Debug key", 0));
    }

    @Override
    public void guiUpdate() {
        this.debugKey.setVisible(this.fpsDebugInfo.isToggled(), this);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }

        BlockPos[] eggs;
        switch (ZombiesMap.current()) {
            case PRISON:
                eggs = this.prison.isToggled() ? PRISON : null;
                break;
            case DEAD_END:
                eggs = this.deadEnd.isToggled() ? DEAD_END : null;
                break;
            case BAD_BLOOD:
                eggs = this.badBlood.isToggled() ? BAD_BLOOD : null;
                break;
            default:
                eggs = null;
        }

        if (eggs == null || !this.outline.isToggled() && !this.fill.isToggled()) {
            return;
        }

        double vx = mc.getEntityRenderDispatcher().cameraX;
        double vy = mc.getEntityRenderDispatcher().cameraY;
        double vz = mc.getEntityRenderDispatcher().cameraZ;
        float r = this.color.getRed() / 255.0F;
        float g = this.color.getGreen() / 255.0F;
        float b = this.color.getBlue() / 255.0F;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        for (BlockPos pos : eggs) {
            Box box = bounds(pos);
            if (!RenderUtils.isInViewFrustum(box)) {
                continue;
            }

            box = box.moved(-vx, -vy, -vz);
            if (this.fill.isToggled()) {
                RenderUtils.drawBoundingBox(box, r, g, b, 0.25F);
            }

            if (this.outline.isToggled()) {
                GL11.glColor4f(r, g, b, 1.0F);
                WorldRenderer.renderOutlineShape(box);
            }
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    /**
     * The block's own outline (a button or lever is small), a bed's covers both halves. A position that isn't loaded
     * or has nothing there gets a full block, so a wrong coordinate still shows up instead of vanishing.
     */
    private static Box bounds(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        Box full = new Box(pos, pos.add(1, 1, 1));
        if (block.getMaterial().isReplaceable()) {
            return full;
        }

        block.updateShape(mc.world, pos);
        Box box = block.getOutlineShape(mc.world, pos);
        if (box == null) {
            return full;
        }

        if (block instanceof BedBlock) {
            Direction facing = state.get(BedBlock.FACING);
            // The head lies in the facing direction from the foot.
            BlockPos other = state.get(BedBlock.PART) == BedBlock.Part.HEAD
                    ? pos.offset(facing.getOpposite())
                    : pos.offset(facing);
            if (mc.world.getBlockState(other).getBlock() instanceof BedBlock) {
                box = box.union(
                        new Box(other.getX(), box.minY, other.getZ(), other.getX() + 1, box.maxY, other.getZ() + 1));
            }
        }

        return box.grown(0.002, 0.002, 0.002);
    }

    @Override
    public void onUpdate() {
        if (!this.fpsDebugInfo.isToggled() || !Utils.nullCheck() || mc.screen != null || !this.debugKey.isPressed()) {
            // Releasing the key lets the same block be logged again next time.
            this.lastLogged = null;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.type != HitResult.Type.BLOCK || hit.getPos() == null) {
            return;
        }

        BlockPos pos = hit.getPos();
        // Log each block once while the key stays held, instead of every tick.
        if (pos.equals(this.lastLogged)) {
            return;
        }

        this.lastLogged = pos;
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        Utils.sendMessage("&b" + Block.REGISTRY.getKey(block)
                + " &7meta " + block.getMetadataFromState(state)
                + " &7at &f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " &7map &e" + ZombiesMap.current());
    }
}

package keystrokesmod.module.impl.minigames;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class SumoFences extends Module {
    public SliderSetting fenceHeight;
    public SliderSetting blockType;
    private Timer timer;
    private final List<String> MAPS = Arrays.asList("Sumo", "Space Mine", "White Crystal", "Fort");
    private BlockState fenceState;
    private final List<BlockPos> f_p = Arrays.asList(
            new BlockPos(9, 65, -2),
            new BlockPos(9, 65, -1),
            new BlockPos(9, 65, 0),
            new BlockPos(9, 65, 1),
            new BlockPos(9, 65, 2),
            new BlockPos(9, 65, 3),
            new BlockPos(8, 65, 3),
            new BlockPos(8, 65, 4),
            new BlockPos(8, 65, 5),
            new BlockPos(7, 65, 5),
            new BlockPos(7, 65, 6),
            new BlockPos(7, 65, 7),
            new BlockPos(6, 65, 7),
            new BlockPos(5, 65, 7),
            new BlockPos(5, 65, 8),
            new BlockPos(4, 65, 8),
            new BlockPos(3, 65, 8),
            new BlockPos(3, 65, 9),
            new BlockPos(2, 65, 9),
            new BlockPos(1, 65, 9),
            new BlockPos(0, 65, 9),
            new BlockPos(-1, 65, 9),
            new BlockPos(-2, 65, 9),
            new BlockPos(-3, 65, 9),
            new BlockPos(-3, 65, 8),
            new BlockPos(-4, 65, 8),
            new BlockPos(-5, 65, 8),
            new BlockPos(-5, 65, 7),
            new BlockPos(-6, 65, 7),
            new BlockPos(-7, 65, 7),
            new BlockPos(-7, 65, 6),
            new BlockPos(-7, 65, 5),
            new BlockPos(-8, 65, 5),
            new BlockPos(-8, 65, 4),
            new BlockPos(-8, 65, 3),
            new BlockPos(-9, 65, 3),
            new BlockPos(-9, 65, 2),
            new BlockPos(-9, 65, 1),
            new BlockPos(-9, 65, 0),
            new BlockPos(-9, 65, -1),
            new BlockPos(-9, 65, -2),
            new BlockPos(-9, 65, -3),
            new BlockPos(-8, 65, -3),
            new BlockPos(-8, 65, -4),
            new BlockPos(-8, 65, -5),
            new BlockPos(-7, 65, -5),
            new BlockPos(-7, 65, -6),
            new BlockPos(-7, 65, -7),
            new BlockPos(-6, 65, -7),
            new BlockPos(-5, 65, -7),
            new BlockPos(-5, 65, -8),
            new BlockPos(-4, 65, -8),
            new BlockPos(-3, 65, -8),
            new BlockPos(-3, 65, -9),
            new BlockPos(-2, 65, -9),
            new BlockPos(-1, 65, -9),
            new BlockPos(0, 65, -9),
            new BlockPos(1, 65, -9),
            new BlockPos(2, 65, -9),
            new BlockPos(3, 65, -9),
            new BlockPos(3, 65, -8),
            new BlockPos(4, 65, -8),
            new BlockPos(5, 65, -8),
            new BlockPos(5, 65, -7),
            new BlockPos(6, 65, -7),
            new BlockPos(7, 65, -7),
            new BlockPos(7, 65, -6),
            new BlockPos(7, 65, -5),
            new BlockPos(8, 65, -5),
            new BlockPos(8, 65, -4),
            new BlockPos(8, 65, -3),
            new BlockPos(9, 65, -3));
    private final String[] blockTypes = new String[] {"Oak fence", "Leaves", "Glass", "Barrier"};

    public SumoFences() {
        super("Sumo Fences", Module.category.minigames, 0);
        this.fenceState = Blocks.FENCE.defaultState();
        this.registerSetting(new DescriptionSetting("Fences for Hypixel sumo."));
        this.registerSetting(this.fenceHeight = new SliderSetting("Fence height", 4.0, 1.0, 6.0, 1.0));
        this.registerSetting(this.blockType = new SliderSetting("Block type", 0, this.blockTypes));
    }

    @Override
    public void onEnable() {
        (this.timer = new Timer()).scheduleAtFixedRate(this.t(), 0L, 500L);
    }

    @Override
    public void onDisable() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer.purge();
            this.timer = null;
        }

        for (BlockPos p : this.f_p) {
            for (int i = 0; i < this.fenceHeight.getInput(); i++) {
                BlockPos p2 = new BlockPos(p.getX(), p.getY() + i, p.getZ());
                if (mc.world.getBlockState(p2).getBlock() == this.fenceState) {
                    mc.world.setBlockState(p2, Blocks.AIR.defaultState());
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.buttonstate && (e.button == 0 || e.button == 1) && Utils.nullCheck() && this.isSumo()) {
            HitResult over = mc.crosshairTarget;
            if (over != null && over.type == Type.BLOCK) {
                int x = over.getPos().getX();
                int z = over.getPos().getZ();

                for (BlockPos pos : this.f_p) {
                    if (pos.getX() == x && pos.getZ() == z) {
                        e.setCanceled(true);
                        if (e.button == 0) {
                            Utils.setSwinging();
                        }

                        Mouse.poll();
                        break;
                    }
                }
            }
        }
    }

    public TimerTask t() {
        return new TimerTask() {
            @Override
            public void run() {
                if (SumoFences.this.isSumo()) {
                    for (BlockPos p : SumoFences.this.f_p) {
                        for (int i = 0; i < SumoFences.this.fenceHeight.getInput(); i++) {
                            BlockPos p2 = new BlockPos(p.getX(), p.getY() + i, p.getZ());
                            if (SumoFences.mc.world.getBlockState(p2).getBlock() == Blocks.AIR) {
                                SumoFences.mc.world.setBlockState(p2, SumoFences.this.fenceState);
                            }
                        }
                    }
                }
            }
        };
    }

    private boolean isSumo() {
        if (Utils.isHypixel()) {
            for (String l : Utils.getScoreBoardOld()) {
                String s = Utils.stripColor(l);
                if (s.startsWith("Map:")) {
                    if (this.MAPS.contains(s.substring(5))) {
                        return true;
                    }
                } else if (s.equals("Mode: Sumo Duel")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void guiUpdate() {
        switch ((int) this.blockType.getInput()) {
            case 0:
                this.fenceState = Blocks.FENCE.defaultState();
                break;
            case 1:
                this.fenceState = Blocks.LEAVES.defaultState();
                break;
            case 2:
                this.fenceState = Blocks.GLASS.defaultState();
                break;
            case 3:
                this.fenceState = Blocks.BARRIER.defaultState();
        }
    }
}

package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.Feral;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;

public class Xray extends Module {
    private SliderSetting range;
    private SliderSetting rate;
    private ButtonSetting iron;
    private ButtonSetting gold;
    private ButtonSetting diamond;
    private ButtonSetting emerald;
    private ButtonSetting lapis;
    private ButtonSetting redstone;
    private ButtonSetting coal;
    private ButtonSetting spawner;
    private ButtonSetting obsidian;
    private Set<BlockPos> blocks = ConcurrentHashMap.newKeySet();
    private long lastCheck = 0L;

    public Xray() {
        super("Xray", Module.category.render);
        this.registerSetting(this.range = new SliderSetting("Range", 20.0, 5.0, 50.0, 1.0));
        this.registerSetting(this.rate = new SliderSetting("Rate", " second", 0.5, 0.1, 3.0, 0.1));
        this.registerSetting(this.coal = new ButtonSetting("Coal", true));
        this.registerSetting(this.diamond = new ButtonSetting("Diamond", true));
        this.registerSetting(this.emerald = new ButtonSetting("Emerald", true));
        this.registerSetting(this.gold = new ButtonSetting("Gold", true));
        this.registerSetting(this.iron = new ButtonSetting("Iron", true));
        this.registerSetting(this.lapis = new ButtonSetting("Lapis", true));
        this.registerSetting(this.obsidian = new ButtonSetting("Obsidian", true));
        this.registerSetting(this.redstone = new ButtonSetting("Redstone", true));
        this.registerSetting(this.spawner = new ButtonSetting("Spawner", true));
    }

    @Override
    public void onDisable() {
        this.blocks.clear();
    }

    @Override
    public void onUpdate() {
        if (!(System.currentTimeMillis() - this.lastCheck < this.rate.getInput() * 1000.0)) {
            this.lastCheck = System.currentTimeMillis();
            Feral.getCachedExecutor().execute(() -> {
                int n = (int) this.range.getInput();
                int playerX = (int) mc.player.x;
                int playerY = (int) mc.player.y;
                int playerZ = (int) mc.player.z;
                int minY = Math.max(-n, -playerY);
                Mutable mutablePos = new Mutable();

                for (int y = n; y >= minY; y--) {
                    for (int x = -n; x <= n; x++) {
                        for (int z = -n; z <= n; z++) {
                            int blockY = playerY + y;
                            if (blockY >= 0) {
                                mutablePos.set(playerX + x, blockY, playerZ + z);
                                if (!this.blocks.contains(mutablePos)) {
                                    Block blockState = BlockUtils.getBlock(mutablePos);
                                    if (blockState != null && this.canBreak(blockState)) {
                                        this.blocks.add(
                                                new BlockPos(mutablePos.getX(), mutablePos.getY(), mutablePos.getZ()));
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.blocks.clear();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent ev) {
        if (Utils.nullCheck()) {
            if (!this.blocks.isEmpty()) {
                Iterator<BlockPos> iterator = this.blocks.iterator();

                while (iterator.hasNext()) {
                    BlockPos blockPos = iterator.next();
                    Block block = BlockUtils.getBlock(blockPos);
                    if (block != null && this.canBreak(block)) {
                        this.drawBox(blockPos);
                    } else {
                        iterator.remove();
                    }
                }
            }
        }
    }

    private void drawBox(BlockPos p) {
        if (p != null) {
            int[] rgb = this.getColor(BlockUtils.getBlock(p));
            if (rgb[0] + rgb[1] + rgb[2] != 0) {
                RenderUtils.renderBlock(p, new Color(rgb[0], rgb[1], rgb[2]).getRGB(), false, true);
            }
        }
    }

    private int[] getColor(Block b) {
        int red = 0;
        int green = 0;
        int blue = 0;
        if (b.equals(Blocks.IRON_ORE)) {
            red = 255;
            green = 255;
            blue = 255;
        } else if (b.equals(Blocks.GOLD_ORE)) {
            red = 255;
            green = 255;
        } else if (b.equals(Blocks.DIAMOND_ORE)) {
            green = 220;
            blue = 255;
        } else if (b.equals(Blocks.EMERALD_ORE)) {
            red = 35;
            green = 255;
        } else if (b.equals(Blocks.LAPIS_ORE)) {
            green = 50;
            blue = 255;
        } else if (b.equals(Blocks.REDSTONE_ORE)) {
            red = 255;
        } else if (b.equals(Blocks.MOB_SPAWNER)) {
            red = 30;
            blue = 135;
        }

        return new int[] {red, green, blue};
    }

    public boolean canBreak(Block block) {
        return this.iron.isToggled() && block.equals(Blocks.IRON_ORE)
                || this.gold.isToggled() && block.equals(Blocks.GOLD_ORE)
                || this.diamond.isToggled() && block.equals(Blocks.DIAMOND_ORE)
                || this.emerald.isToggled() && block.equals(Blocks.EMERALD_ORE)
                || this.lapis.isToggled() && block.equals(Blocks.LAPIS_ORE)
                || this.redstone.isToggled() && block.equals(Blocks.REDSTONE_ORE)
                || this.coal.isToggled() && block.equals(Blocks.COAL_ORE)
                || this.spawner.isToggled() && block.equals(Blocks.MOB_SPAWNER)
                || this.obsidian.isToggled() && block.equals(Blocks.OBSIDIAN);
    }
}

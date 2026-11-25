package keystrokesmod.module.impl.player;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.player.PlayerEntity;

/** Lets you aim through blocks (and optionally mobs) at chests, buttons, levers and other interactables. */
public class IgnoreBlock extends Module {
    private final ButtonSetting ignoreEntities;

    public IgnoreBlock() {
        super("Ignore Block", Module.category.player);
        this.registerSetting(this.ignoreEntities = new ButtonSetting("Ignore entities", false));
    }

    /** Whether the crosshair should pass through this block. */
    public boolean shouldIgnore(Block block) {
        return this.isEnabled()
                && block != Blocks.CHEST
                && block != Blocks.STONE_BUTTON
                && block != Blocks.WOODEN_BUTTON
                && block != Blocks.LEVER
                && block != Blocks.STAINED_GLASS
                && block != Blocks.DISPENSER
                && block != Blocks.CAULDRON;
    }

    /** Whether the crosshair should pass through this entity. Players and armor stands stay targetable. */
    public boolean shouldIgnore(Entity entity) {
        return this.isEnabled()
                && this.ignoreEntities.isToggled()
                && !(entity instanceof ArmorStandEntity)
                && !(entity instanceof PlayerEntity);
    }
}

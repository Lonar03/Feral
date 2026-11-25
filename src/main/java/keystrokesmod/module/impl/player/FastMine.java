package keystrokesmod.module.impl.player;

import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ShearsItem;
import net.minecraft.resource.Identifier;
import net.minecraft.world.HitResult.Type;

public class FastMine extends Module {
    private SliderSetting delay;
    public SliderSetting multiplier;
    private ButtonSetting creativeDisable;
    private ButtonSetting decreaseBreakDelay;
    private ButtonSetting ignoreShears;
    private final ButtonSetting blockBlacklistToggle;
    private final BlockListSetting blockBlacklist;
    private BlockState blockHitDelayState;
    private int passiveBlockHitLastSeen;

    public FastMine() {
        super("Fast Mine", Module.category.player);
        this.liteModule = true;
        this.registerSetting(new DescriptionSetting("Vanilla is 250ms delay & 1x speed."));
        this.registerSetting(this.delay = new SliderSetting("Break delay", "ms", 250.0, 0.0, 250.0, 50.0));
        this.registerSetting(this.multiplier = new SliderSetting("Break speed", "x", 1.0, 1.0, 2.0, 0.02));
        this.registerSetting(this.creativeDisable = new ButtonSetting("Disable in creative", true));
        this.registerSetting(this.decreaseBreakDelay = new ButtonSetting("Decrease break delay", false));
        this.registerSetting(this.ignoreShears = new ButtonSetting("Ignore shears", false));
        this.registerSetting(this.blockBlacklistToggle = new ButtonSetting("Block blacklist", false));
        this.registerSetting(this.blockBlacklist = new BlockListSetting("Blacklisted blocks"));
        this.closetModule = true;
    }

    @Override
    public void guiUpdate() {
        this.blockBlacklist.setVisible(this.blockBlacklistToggle.isToggled(), this);
    }

    private boolean isBlacklisted(BlockState state) {
        if (this.blockBlacklistToggle.isToggled() && state != null) {
            Block block = state.getBlock();
            Identifier registryName = (Identifier) Block.REGISTRY.getKey(block);
            if (registryName == null) {
                return false;
            }

            String registryId = registryName.toString();
            int meta = block.getMetadataFromState(state);
            String storageId = meta != 0 ? registryId + ":" + meta : registryId;
            return this.blockBlacklist.contains(storageId) || this.blockBlacklist.contains(registryId);
        } else {
            return false;
        }
    }

    private boolean isTargetBlacklisted() {
        return mc.world != null
                && mc.crosshairTarget != null
                && mc.crosshairTarget.type == Type.BLOCK
                && this.isBlacklisted(mc.world.getBlockState(mc.crosshairTarget.getPos()));
    }

    public float getBreakSpeedMultiplier(BlockState state) {
        if (!this.isEnabled() || this.isBlacklisted(state)) {
            return 1.0F;
        }

        if (this.creativeDisable.isToggled() && mc.player != null && mc.player.abilities.creativeMode) {
            return 1.0F;
        }

        if (this.ignoreShears.isToggled()
                && mc.player != null
                && mc.player.getDisplayItemInHand() != null
                && mc.player.getDisplayItemInHand().getItem() instanceof ShearsItem) {
            return 1.0F;
        }

        float m = (float) this.multiplier.getInput();
        return m > 1.0F ? m : 1.0F;
    }

    public boolean shouldPassiveDecreaseBlockHitDelay() {
        return this.isEnabled()
                && this.decreaseBreakDelay.isToggled()
                && Utils.nullCheck()
                && !this.isBlacklisted(this.blockHitDelayState)
                && !this.isTargetBlacklisted();
    }

    public void tickPassiveBlockHitDecay(Minecraft mc) {
        if (mc.interactionManager != null) {
            IAccessorPlayerControllerMP acc = (IAccessorPlayerControllerMP) mc.interactionManager;
            int delay = acc.getBlockHitDelay();
            if (delay <= 0) {
                this.blockHitDelayState = null;
            }

            if (!this.shouldPassiveDecreaseBlockHitDelay()) {
                this.passiveBlockHitLastSeen = delay;
            } else {
                if (delay > 0 && delay == this.passiveBlockHitLastSeen) {
                    acc.setBlockHitDelay(delay - 1);
                }

                this.passiveBlockHitLastSeen = delay;
            }
        }
    }

    public int getBlockHitDelayOverrideOrMinusOne(BlockState state) {
        this.blockHitDelayState = state;
        if (!this.isEnabled() || !Utils.nullCheck() || !mc.focused || this.isBlacklisted(state)) {
            return -1;
        }

        if (this.creativeDisable.isToggled() && mc.player != null && mc.player.abilities.creativeMode) {
            return -1;
        }

        int ticks = (int) (this.delay.getInput() / 50.0);
        return ticks >= 5 ? -1 : ticks;
    }

    @Override
    public String getInfo() {
        return ((int) this.multiplier.getInput() == this.multiplier.getInput()
                        ? (int) this.multiplier.getInput() + ""
                        : this.multiplier.getInput())
                + this.multiplier.getSuffix();
    }
}

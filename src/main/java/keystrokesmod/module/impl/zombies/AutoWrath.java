package keystrokesmod.module.impl.zombies;

import keystrokesmod.helper.zombies.ZombiesGameHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.RenderTickEvent;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Clicks the dragon egg (Wrath) in an open container just before the round's last wave spawns. */
public class AutoWrath extends Module {
    private static final int BASE_LEAD_MS = 2000;
    private static final int MS_PER_TICK = 50;
    public static SliderSetting leadTicks;
    private boolean hasClicked = false;
    private int lastRound = 0;

    public AutoWrath() {
        super("Auto Wrath", Module.category.zombies);
        this.registerSetting(leadTicks = new SliderSetting("Lead Ticks", 0.0, 0.0, 5.0, 1.0));
    }

    @Override
    public void onEnable() {
        this.hasClicked = false;
        this.lastRound = 0;
    }

    @Override
    public void onDisable() {
        this.hasClicked = false;
        this.lastRound = 0;
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != Phase.START || !Utils.nullCheck() || !(mc.screen instanceof InventoryMenuScreen)) {
            return;
        }

        int currentRound = ZombiesGameHelper.getCurrentRound();
        if (currentRound == 0) {
            return;
        }
        if (currentRound != this.lastRound) {
            this.hasClicked = false;
            this.lastRound = currentRound;
        }

        int[] roundTimes = ZombiesGameHelper.getWaveTimes();
        if (this.hasClicked || roundTimes == null || roundTimes.length == 0) {
            return;
        }

        int lastWaveTimeMs = roundTimes[roundTimes.length - 1] * 1000;
        int currentRoundTimeMs = ZombiesGameHelper.getRoundTimeMs();
        int leadMs = BASE_LEAD_MS + (int) (leadTicks.getInput() * MS_PER_TICK);
        if (lastWaveTimeMs - currentRoundTimeMs > leadMs) {
            return;
        }

        int dragonEggSlot = this.findDragonEggSlot();
        if (dragonEggSlot != -1 && this.clickSlot(dragonEggSlot)) {
            this.hasClicked = true;
        }
    }

    private int findDragonEggSlot() {
        InventoryMenu container = mc.player.menu;
        if (container == null) {
            return -1;
        }

        Item dragonEgg = Item.byBlock(Blocks.DRAGON_EGG);
        for (InventorySlot slot : container.slots) {
            ItemStack stack = slot == null ? null : slot.getItem();
            if (stack != null && stack.getItem() == dragonEgg) {
                return slot.index;
            }
        }
        return -1;
    }

    private boolean clickSlot(int index) {
        InventoryMenu container = mc.player.menu;
        if (container == null || container.networkId <= 0) {
            return false;
        }

        mc.interactionManager.clickSlot(container.networkId, index, 0, 0, mc.player);
        return true;
    }
}

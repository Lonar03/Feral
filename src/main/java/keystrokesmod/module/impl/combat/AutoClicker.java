package keystrokesmod.module.impl.combat;

import java.util.Random;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LiquidBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class AutoClicker extends Module {
    public SliderSetting targetCPS;
    public ButtonSetting simulateExhaust;
    public ButtonSetting notUsingItem;
    public ButtonSetting breakBlocks;
    public ButtonSetting weaponOnly;
    public ButtonSetting disableCreative;
    public ButtonSetting inventory;
    public SliderSetting inventoryStartDelay;
    private long nextClickTime;
    private long inventoryNextClickTime;
    private boolean isControllingAttack;
    private boolean displaceRecoveryClickRequested;
    private Random rand;

    public AutoClicker() {
        super("Auto Clicker", Module.category.combat, 0);
        this.registerSetting(new DescriptionSetting("Best with delay remover."));
        this.registerSetting(this.targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(this.simulateExhaust = new ButtonSetting("Simulate exhaust", true));
        this.registerSetting(this.notUsingItem = new ButtonSetting("Not using item", false));
        this.registerSetting(this.breakBlocks = new ButtonSetting("Break blocks", false));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(this.disableCreative = new ButtonSetting("Disable in creative", false));
        this.registerSetting(this.inventory = new ButtonSetting("Inventory", false));
        this.registerSetting(
                this.inventoryStartDelay = new SliderSetting("Start delay", "ms", 100.0, 0.0, 250.0, 10.0));
        this.closetModule = true;
        this.liteModule = true;
    }

    @Override
    public String getInfo() {
        double cps = this.targetCPS.getInput();
        return cps == Math.rint(cps) ? Integer.toString((int) cps) : Double.toString(Utils.round(cps, 1));
    }

    @Override
    public void onEnable() {
        this.rand = new Random();
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.isControllingAttack = false;
        this.displaceRecoveryClickRequested = false;
    }

    @Override
    public void onDisable() {
        this.releaseAttackControl();
        this.nextClickTime = 0L;
        this.inventoryNextClickTime = 0L;
        this.isControllingAttack = false;
        this.displaceRecoveryClickRequested = false;
    }

    private boolean consumeDisplaceRecoveryClickRequest() {
        boolean requested = this.displaceRecoveryClickRequested;
        this.displaceRecoveryClickRequested = false;
        return requested;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END) {
            if (this.inventory.isToggled()) {
                if (Utils.nullCheck()) {
                    if (!(mc.screen instanceof InventoryMenuScreen)) {
                        this.inventoryNextClickTime = 0L;
                    } else if (!Mouse.isButtonDown(0)) {
                        this.inventoryNextClickTime = 0L;
                    } else {
                        long now = System.currentTimeMillis();
                        if (this.inventoryNextClickTime == 0L) {
                            this.inventoryNextClickTime = now + (long) this.inventoryStartDelay.getInput();
                        }

                        int clicks = 0;

                        while (this.inventoryNextClickTime <= now) {
                            clicks++;
                            this.inventoryNextClickTime = this.inventoryNextClickTime + this.nextDelay();
                        }

                        if (clicks > 0) {
                            InventoryMenuScreen gui = (InventoryMenuScreen) mc.screen;
                            InventorySlot slot = ClientPlatform.slotUnderMouse(gui);
                            if (slot != null && slot.index >= 0) {
                                int windowId = gui.menu.networkId;
                                int slotId = slot.index;
                                int mode = Screen.isShiftDown() ? 1 : 0;
                                if (mc.interactionManager != null && mc.player != null) {
                                    for (int i = 0; i < clicks; i++) {
                                        mc.interactionManager.clickSlot(windowId, slotId, 0, mode, mc.player);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        boolean recoveryClickRequested = this.consumeDisplaceRecoveryClickRequest();
        if (Utils.nullCheck()
                && (ModuleManager.killAura == null || !ModuleManager.killAura.isEnabled() || KillAura.target == null)
                && (!this.notUsingItem.isToggled() || !mc.player.hasItemInUse())
                && (!this.disableCreative.isToggled() || !mc.player.abilities.creativeMode)
                && mc.screen == null
                && mc.focused
                && (!this.weaponOnly.isToggled() || Utils.holdingWeapon())) {
            int key = mc.options.attackKey.getKeyCode();
            if (Mouse.isButtonDown(0)) {
                this.isControllingAttack = true;
                if (this.breakBlocks.isToggled()
                        && mc.player.abilities.canModifyWorld
                        && mc.crosshairTarget != null
                        && mc.crosshairTarget.type == Type.BLOCK) {
                    BlockPos pos = mc.crosshairTarget.getPos();
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (block != Blocks.AIR && !(block instanceof LiquidBlock)) {
                        KeyBinding.set(key, true);
                        this.nextClickTime = 0L;
                        return;
                    }
                }

                KeyBinding.set(key, false);
                long now = System.currentTimeMillis();
                int clicks = 0;
                if (this.nextClickTime == 0L) {
                    if (recoveryClickRequested) {
                        clicks = 1;
                    }

                    this.nextClickTime = now + this.nextDelay();
                } else {
                    while (this.nextClickTime <= now) {
                        clicks++;
                        this.nextClickTime = this.nextClickTime + this.nextDelay();
                    }

                    if (recoveryClickRequested && clicks == 0) {
                        clicks = 1;
                        this.nextClickTime = now + this.nextDelay();
                    }
                }

                for (int i = 0; i < clicks; i++) {
                    KeyBinding.click(key);
                    ReflectionUtils.setButton(0, true);
                }
            } else {
                this.nextClickTime = 0L;
                this.isControllingAttack = false;
                KeyBinding.set(key, false);
                ReflectionUtils.setButton(0, false);
            }
        } else {
            this.releaseAttackControl();
        }
    }

    private void releaseAttackControl() {
        if (this.isControllingAttack) {
            KeyBinding.set(mc.options.attackKey.getKeyCode(), mc.screen == null && mc.focused && Mouse.isButtonDown(0));
            this.isControllingAttack = false;
        }

        this.nextClickTime = 0L;
    }

    private long nextDelay() {
        int target = Math.max(1, (int) this.targetCPS.getInput());
        int baseDelay = 1000 / target;
        int finalDelay;
        if (this.simulateExhaust.isToggled()) {
            int variation = this.rand.nextInt(baseDelay + 1) - baseDelay / 2;
            finalDelay = baseDelay + variation;
            if (this.rand.nextInt(100) < 15) {
                if (this.rand.nextBoolean()) {
                    finalDelay = 25 + this.rand.nextInt(16);
                } else {
                    finalDelay = baseDelay + 50 + this.rand.nextInt(41);
                }
            }

            if (this.rand.nextInt(100) < 8) {
                int spikeMult = 50 + this.rand.nextInt(151);
                finalDelay = finalDelay * spikeMult / 100;
            }

            if (this.rand.nextInt(100) < 10) {
                finalDelay += 10 + this.rand.nextInt(26);
            }
        } else {
            finalDelay = baseDelay + (this.rand.nextInt(21) - 10);
        }

        return Math.max(33, Math.min(180, finalDelay));
    }
}

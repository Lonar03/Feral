package keystrokesmod.helper;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import keystrokesmod.event.KeyEvent;
import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.MouseEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Tracks key/mouse presses for the Keystrokes HUD in two flavours:
 * <ul>
 *   <li><b>All</b> (what the server gets): keyboard keys from every {@code KeyBinding.onTick} call (real presses
 *       and modules pressing keys); left/right clicks and hotbar keys from the swing, place/use and slot packets
 *       actually sent, so silent modules (Clutch, AutoAim, KillAura...) show up however they click.</li>
 *   <li><b>Physical</b>: only real LWJGL key/mouse events (what a normal keystrokes mod sees).</li>
 * </ul>
 * Key codes use the vanilla convention: mouse button {@code b} is {@code b - 100}.
 */
public final class KeyInputTracker {
    private static final long CPS_WINDOW_MS = 1000L;
    private static final long SLOT_PRESS_WINDOW_MS = 150L;
    private static final int VANILLA_USE_REPEAT_TICKS = 4;

    /** Count right clicks repeated faster than vanilla while holding (Fast Place) as clicks. Set by Keystrokes. */
    public static volatile boolean countFastRepeats = true;

    private static final Map<Integer, Deque<Long>> allPresses = new HashMap<>();
    private static final Map<Integer, Deque<Long>> physicalPresses = new HashMap<>();
    private static final Map<Integer, Long> lastAllPress = new HashMap<>();
    private static final Map<Integer, Long> lastPressTick = new HashMap<>();
    // Packets a lag module held back and sent again later must only count once.
    private static final Set<Packet<?>> seenPackets = Collections.newSetFromMap(new WeakHashMap<>());
    private static long tick;
    private static long lastBlockClickTick = -1;
    private static long lastCountedUseTick = -100;
    private static long lastPhysicalScroll;
    // This tick's swings, attacks, place/use packets and attack key presses; settled into left clicks next tick.
    private static int tickSwings;
    private static int tickAttacks;
    private static int tickPlaces;
    private static int tickAttackPresses;

    public KeyInputTracker() {}

    /** Called from {@code MixinKeyBinding} for every {@code KeyBinding.onTick(keyCode)}. */
    public static void onKeyBindingTick(int keyCode) {
        if (keyCode == 0) {
            return;
        }

        lastPressTick.put(keyCode, tick);
        if (keyCode == Minecraft.getInstance().options.attackKey.getKeyCode()) {
            tickAttackPresses++;
        }
        // Attack/use clicks are counted from the packets they send instead.
        if (!isClickKey(keyCode)) {
            record(allPresses, keyCode);
        }
    }

    private static boolean isClickKey(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        return keyCode == mc.options.attackKey.getKeyCode() || keyCode == mc.options.useKey.getKeyCode();
    }

    private static void record(Map<Integer, Deque<Long>> map, int keyCode) {
        long now = System.currentTimeMillis();
        Deque<Long> presses = map.computeIfAbsent(keyCode, k -> new ArrayDeque<>());
        presses.addLast(now);
        prune(presses, now);
        if (map == allPresses) {
            lastAllPress.put(keyCode, now);
        }
    }

    private static void prune(Deque<Long> presses, long now) {
        while (!presses.isEmpty() && presses.peekFirst() + CPS_WINDOW_MS < now) {
            presses.pollFirst();
        }
    }

    /** Presses in the last second for a key code. */
    public static int getCps(int keyCode, boolean physicalOnly) {
        Deque<Long> presses = (physicalOnly ? physicalPresses : allPresses).get(keyCode);
        if (presses == null) {
            return 0;
        }

        prune(presses, System.currentTimeMillis());
        return presses.size();
    }

    /** Time of the last recorded press (physical or module) for a key code, 0 if never. */
    public static long getLastPress(int keyCode) {
        Long time = lastAllPress.get(keyCode);
        return time == null ? 0L : time;
    }

    private static boolean pressedThisTick(int keyCode) {
        Long pressTick = lastPressTick.get(keyCode);
        return pressTick != null && pressTick == tick;
    }

    public static boolean isPhysicallyDown(int keyCode) {
        if (keyCode < 0) {
            int button = keyCode + 100;
            return button >= 0 && button < Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }

        return keyCode > 0 && keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
    }

    /** True when any key binding on this key code is held, which includes states set by modules. */
    public static boolean isBindingDown(int keyCode) {
        if (keyCode == 0) {
            return false;
        }

        for (KeyBinding binding : Minecraft.getInstance().options.keyBindings) {
            if (binding.getKeyCode() == keyCode && binding.isPressed()) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------- physical input

    /** Called for every real mouse event (OrnitheClientHooks), like Forge's MouseInputEvent. */
    public static void onRawMouseInput() {
        if (Mouse.getEventButtonState() && Mouse.getEventButton() >= 0) {
            record(physicalPresses, Mouse.getEventButton() - 100);
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyEvent event) {
        if (Keyboard.getEventKeyState() && Keyboard.getEventKey() > 0) {
            record(physicalPresses, Keyboard.getEventKey());
        }
    }

    // Forge MouseEvent also gets posted by fake clicks (ReflectionUtils.setButton), but those never carry a wheel
    // delta.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouse(MouseEvent event) {
        if (event.dwheel != 0) {
            lastPhysicalScroll = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.START) {
            settleLeftClicks();
            tick++;
        }
    }

    // ---------------------------------------------------------------- packets

    // Counted when the packet is first sent, even if a lag module cancels it to send it later.
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onSendPacket(SendPacketEvent event) {
        this.onPacket(event.getPacket());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onNoEventPacket(NoEventPacketEvent event) {
        this.onPacket(event.getPacket());
    }

    private void onPacket(Packet<?> packet) {
        if (!(packet instanceof ArmSwingC2SPacket
                        || packet instanceof PlayerUseC2SPacket
                        || packet instanceof SelectSlotC2SPacket
                        || packet instanceof PlayerInteractEntityC2SPacket)
                || !seenPackets.add(packet)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (packet instanceof ArmSwingC2SPacket) {
            this.onSwing(mc);
        } else if (packet instanceof PlayerUseC2SPacket) {
            tickPlaces++;
            this.onPlace(mc, (PlayerUseC2SPacket) packet);
        } else if (packet instanceof PlayerInteractEntityC2SPacket) {
            if (((PlayerInteractEntityC2SPacket) packet).getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK) {
                tickAttacks++;
            }
        } else {
            this.onSlot(mc, ((SelectSlotC2SPacket) packet).getSlot());
        }
    }

    /** Swings vanilla sends each tick while you hold LMB on a block are mining, not clicks. */
    private void onSwing(Minecraft mc) {
        boolean miningHold = mc.interactionManager.isMiningBlock()
                && mc.options.attackKey.isPressed()
                && !pressedThisTick(mc.options.attackKey.getKeyCode());
        if (!miningHold) {
            tickSwings++;
        }
    }

    /**
     * Placing a block or casting a rod swings the arm too, so a tick's swings only count as left clicks beyond one
     * per place/use packet; an attack packet or an attack key press always makes its swing a left click (KillAura
     * attacking while autoblocking is both).
     */
    private static void settleLeftClicks() {
        int left = Math.max(0, tickSwings - tickPlaces);
        left = Math.max(left, Math.min(tickSwings, tickAttacks));
        left = Math.max(left, Math.min(tickSwings, tickAttackPresses));
        if (left > 0) {
            int attack = Minecraft.getInstance().options.attackKey.getKeyCode();
            for (int i = 0; i < left; i++) {
                record(allPresses, attack);
            }
        }

        tickSwings = 0;
        tickAttacks = 0;
        tickPlaces = 0;
        tickAttackPresses = 0;
    }

    /**
     * A place/use packet is a right click. Clicking a block with a non-block item sends a block click and a use
     * item packet in the same tick, which counts once; vanilla's repeat every 4 ticks while holding RMB doesn't
     * count (like a normal keystrokes mod), faster repeats (Fast Place) do when {@link #countFastRepeats} is on.
     */
    private void onPlace(Minecraft mc, PlayerUseC2SPacket packet) {
        boolean useItem = packet.getFace() == 255;
        if (useItem && lastBlockClickTick == tick) {
            return;
        }

        if (!useItem) {
            lastBlockClickTick = tick;
        }

        int use = mc.options.useKey.getKeyCode();
        boolean heldRepeat = mc.options.useKey.isPressed() && !pressedThisTick(use);
        if (heldRepeat && (!countFastRepeats || tick - lastCountedUseTick >= VANILLA_USE_REPEAT_TICKS)) {
            return;
        }

        lastCountedUseTick = tick;
        record(allPresses, use);
    }

    /** A slot change you didn't make with the hotbar key or the scroll wheel counts as pressing that slot's key. */
    private void onSlot(Minecraft mc, int slot) {
        if (slot < 0 || slot >= 9) {
            return;
        }

        long now = System.currentTimeMillis();
        int keyCode = mc.options.hotbarKeyBindings[slot].getKeyCode();
        if (keyCode != 0
                && now - getLastPress(keyCode) > SLOT_PRESS_WINDOW_MS
                && now - lastPhysicalScroll > SLOT_PRESS_WINDOW_MS) {
            record(allPresses, keyCode);
        }
    }
}

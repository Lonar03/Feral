package keystrokesmod.utility;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screen.game.inventory.DispenserScreen;
import net.minecraft.client.gui.screen.game.inventory.EnchantingTableScreen;
import net.minecraft.client.gui.screen.game.inventory.FurnaceScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.CraftingTableMenu;
import net.minecraft.inventory.menu.HopperMenu;
import net.minecraft.inventory.menu.HorseMenu;
import net.minecraft.inventory.menu.TraderMenu;

public class ReflectionUtils {
    public static Field button;
    public static Field buttonstate;
    public static HashMap<Class, Field> containerInventoryPlayer = new HashMap<>();
    private static List<Class> containerClasses = Arrays.asList(
            FurnaceScreen.class,
            BrewingStandScreen.class,
            EnchantingTableScreen.class,
            HopperMenu.class,
            DispenserScreen.class,
            CraftingTableMenu.class,
            TraderMenu.class,
            HorseMenu.class);
    public static boolean ERROR = false;
    public static Map<String, KeyBinding> keybinds = new HashMap<>();

    public static void setupFields() {
        try {
            button = ForgeEvents.MouseEvent.class.getDeclaredField("button");
            buttonstate = ForgeEvents.MouseEvent.class.getDeclaredField("buttonstate");

            for (Class clazz : containerClasses) {
                for (Field field : clazz.getDeclaredFields()) {
                    addToMap(clazz, field);
                }
            }
        } catch (Exception var2) {
            System.out.println("There was an error, relaunch the game.");
            var2.printStackTrace();
            ERROR = true;
        }
    }

    public static void setKeyBindings() {
        for (KeyBinding keyBind : Minecraft.getInstance().options.keyBindings) {
            String keyName = keyBind.getName().replaceFirst("key\\.", "");
            keybinds.put(keyName, keyBind);
        }
    }

    public static void setButton(int button, boolean state) {
        if (ReflectionUtils.button != null && buttonstate != null) {
            ForgeEvents.MouseEvent m = new ForgeEvents.MouseEvent();

            try {
                ReflectionUtils.button.setAccessible(true);
                ReflectionUtils.button.set(m, button);
                buttonstate.setAccessible(true);
                buttonstate.set(m, state);
                ForgeEvents.MinecraftForge.EVENT_BUS.post(m);
            } catch (IllegalAccessException var4) {
            }
        }
    }

    private static void addToMap(Class clazz, Field field) {
        if (field != null && field.getType() == Inventory.class) {
            field = ClientPlatform.ReflectionHelper.findField(clazz, field.getName());
            if (field != null) {
                field.setAccessible(true);
                containerInventoryPlayer.put(clazz, field);
            }
        }
    }

    public static boolean setItemInUse(boolean blocking) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getItemInHandRenderer() != null) {
            ((IMixinItemRenderer) mc.getItemInHandRenderer()).setRenderItemInUse(blocking);
        }

        return blocking;
    }
}

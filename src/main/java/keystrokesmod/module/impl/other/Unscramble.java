package keystrokesmod.module.impl.other;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;

/** Makes scrambled (§k) text readable everywhere it's drawn. Was "Anti Shuffle" (render) in Raven bS. */
public class Unscramble extends Module {
    private static String shuffleStr = "§k";

    public Unscramble() {
        super("Unscramble", Module.category.other, 0);
        this.registerSetting(new DescriptionSetting("Makes scrambled text readable (" + shuffleStr + "hey§r)."));
    }

    public static String removeObfuscation(String s) {
        return s.replace(shuffleStr, "");
    }
}

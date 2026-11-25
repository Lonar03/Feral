// Custom skin loading ported from Meowtils' SkinManager (wtf.tatp.meowtils), modified for Meower and Feral
package keystrokesmod.utility;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.resource.Identifier;

/** Loads the .png skins in .minecraft/keystrokes/skins for Account Hider's custom skin. */
public final class SkinManager {
    private static final long MAX_SKIN_BYTES = 4194304L;
    private static final File SKIN_DIRECTORY =
            new File(new File(Minecraft.getInstance().gameDir, "keystrokes"), "skins");
    private static volatile List<Identifier> skinTextures = Collections.emptyList();
    private static volatile String[] skinOptions = new String[] {"None"};

    private SkinManager() {}

    public static File getSkinDirectory() {
        ensureSkinDirectory();
        return SKIN_DIRECTORY;
    }

    public static boolean openSkinDirectory() {
        ensureSkinDirectory();
        return Utils.openFolder(SKIN_DIRECTORY);
    }

    public static String[] getSkinOptions() {
        return skinOptions.clone();
    }

    /** Option 0 is "None"; the rest map to the loaded files in name order. */
    public static Identifier getSkin(int optionIndex) {
        List<Identifier> textures = skinTextures;
        int index = optionIndex - 1;
        return index >= 0 && index < textures.size() ? textures.get(index) : null;
    }

    public static synchronized ReloadResult reloadSkins() {
        ensureSkinDirectory();
        for (Identifier texture : skinTextures) {
            Minecraft.getInstance().getTextureManager().close(texture);
        }

        List<String> names = new ArrayList<>();
        List<Identifier> textures = new ArrayList<>();
        names.add("None");
        int loaded = 0;
        int failed = 0;
        File[] files = SKIN_DIRECTORY.listFiles(
                file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                try {
                    BufferedImage image =
                            file.length() > 0L && file.length() <= MAX_SKIN_BYTES ? ImageIO.read(file) : null;
                    if (!isValidSkin(image)) {
                        failed++;
                        continue;
                    }

                    String name = file.getName().substring(0, file.getName().length() - 4);
                    textures.add(Minecraft.getInstance()
                            .getTextureManager()
                            .register("feral_skin_" + loaded, new DynamicTexture(image)));
                    names.add(name);
                    loaded++;
                } catch (Throwable ignored) {
                    failed++;
                }
            }
        }

        skinTextures = Collections.unmodifiableList(textures);
        skinOptions = names.toArray(new String[0]);
        return new ReloadResult(loaded, failed);
    }

    // 64x64 (modern) or 64x32 (legacy) layouts, at any multiple of that size for HD skins.
    private static boolean isValidSkin(BufferedImage image) {
        return image != null
                && image.getWidth() >= 64
                && image.getWidth() % 64 == 0
                && (image.getHeight() == image.getWidth() || image.getHeight() * 2 == image.getWidth());
    }

    private static void ensureSkinDirectory() {
        if (!SKIN_DIRECTORY.exists()) {
            SKIN_DIRECTORY.mkdirs();
        }
    }

    public static final class ReloadResult {
        private final int loaded;
        private final int failed;

        private ReloadResult(int loaded, int failed) {
            this.loaded = loaded;
            this.failed = failed;
        }

        public int getLoaded() {
            return this.loaded;
        }

        public int getFailed() {
            return this.failed;
        }
    }
}

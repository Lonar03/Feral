package keystrokesmod.utility;

import java.awt.Desktop;
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

public final class CapeManager {
    private static final long MAX_CAPE_BYTES = 16777216L;
    private static final int MAX_CAPE_DIMENSION = 8192;
    private static final CapeManager.BundledCape[] BUNDLED_CAPES = new CapeManager.BundledCape[] {
        new CapeManager.BundledCape("Anime", "anime.png"),
        new CapeManager.BundledCape("Aqua", "rvn_aqua.png"),
        new CapeManager.BundledCape("Green", "rvn_green.png"),
        new CapeManager.BundledCape("Purple", "rvn_purple.png"),
        new CapeManager.BundledCape("Red", "rvn_red.png"),
        new CapeManager.BundledCape("White", "rvn_white.png"),
        new CapeManager.BundledCape("Yellow", "rvn_yellow.png")
    };
    private static final File CAPE_DIRECTORY =
            new File(new File(Minecraft.getInstance().gameDir, "keystrokes"), "capes");
    private static volatile List<Identifier> capeTextures = Collections.emptyList();
    private static volatile List<Identifier> customCapeTextures = Collections.emptyList();
    private static volatile String[] capeOptions = new String[] {"None"};

    private CapeManager() {}

    public static File getCapeDirectory() {
        ensureCapeDirectory();
        return CAPE_DIRECTORY;
    }

    public static boolean openCapeDirectory() {
        ensureCapeDirectory();

        try {
            if (!Desktop.isDesktopSupported()) {
                return false;
            }

            Desktop.getDesktop().open(CAPE_DIRECTORY);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String[] getCapeOptions() {
        return (String[]) capeOptions.clone();
    }

    public static Identifier getCape(int optionIndex) {
        List<Identifier> currentTextures = capeTextures;
        int textureIndex = optionIndex - 1;
        return textureIndex >= 0 && textureIndex < currentTextures.size() ? currentTextures.get(textureIndex) : null;
    }

    public static synchronized CapeManager.ReloadResult reloadCustomCapes() {
        ensureCapeDirectory();
        deleteOldCustomTextures();
        List<String> optionNames = new ArrayList<>();
        List<Identifier> textures = new ArrayList<>();
        List<Identifier> dynamicTextures = new ArrayList<>();
        optionNames.add("None");

        for (CapeManager.BundledCape cape : BUNDLED_CAPES) {
            optionNames.add(cape.displayName);
            textures.add(new Identifier("keystrokesmod", "textures/capes/" + cape.fileName));
        }

        int loaded = 0;
        int failed = 0;
        File[] files = CAPE_DIRECTORY.listFiles(filex ->
                filex.isFile() && filex.getName().toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            for (File file : files) {
                if (file.length() > 0L && file.length() <= 16777216L) {
                    try {
                        BufferedImage image = ImageIO.read(file);
                        if (!isValidCape(image)) {
                            failed++;
                        } else {
                            String displayName = makeCustomDisplayName(file, optionNames);
                            Identifier texture = Minecraft.getInstance()
                                    .getTextureManager()
                                    .register("custom_cape_" + loaded, new DynamicTexture(image));
                            optionNames.add(displayName);
                            textures.add(texture);
                            dynamicTextures.add(texture);
                            loaded++;
                        }
                    } catch (Throwable ignored) {
                        failed++;
                    }
                } else {
                    failed++;
                }
            }
        }

        capeTextures = Collections.unmodifiableList(textures);
        customCapeTextures = Collections.unmodifiableList(dynamicTextures);
        capeOptions = optionNames.toArray(new String[optionNames.size()]);
        return new CapeManager.ReloadResult(loaded, failed);
    }

    private static boolean isValidCape(BufferedImage image) {
        return image != null
                && image.getWidth() > 0
                && image.getHeight() > 0
                && image.getWidth() <= 8192
                && image.getHeight() <= 8192
                && image.getWidth() == image.getHeight() * 2;
    }

    private static String makeCustomDisplayName(File file, List<String> existingNames) {
        String fileName = file.getName();
        int extensionIndex = fileName.lastIndexOf(46);
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String displayName = baseName;
        if (!containsIgnoreCase(existingNames, displayName)) {
            return displayName;
        }

        displayName = fileName;
        int suffix = 2;

        while (containsIgnoreCase(existingNames, displayName)) {
            displayName = fileName + " (" + suffix++ + ")";
        }

        return displayName;
    }

    private static boolean containsIgnoreCase(List<String> values, String requestedValue) {
        for (String value : values) {
            if (value.equalsIgnoreCase(requestedValue)) {
                return true;
            }
        }

        return false;
    }

    private static void deleteOldCustomTextures() {
        for (Identifier texture : customCapeTextures) {
            Minecraft.getInstance().getTextureManager().close(texture);
        }

        customCapeTextures = Collections.emptyList();
    }

    private static void ensureCapeDirectory() {
        if (!CAPE_DIRECTORY.exists()) {
            CAPE_DIRECTORY.mkdirs();
        }
    }

    private static final class BundledCape {
        private final String displayName;
        private final String fileName;

        private BundledCape(String displayName, String fileName) {
            this.displayName = displayName;
            this.fileName = fileName;
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

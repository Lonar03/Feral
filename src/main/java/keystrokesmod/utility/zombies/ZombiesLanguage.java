package keystrokesmod.utility.zombies;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import keystrokesmod.utility.Utils;
import net.minecraft.resource.Identifier;

/**
 * Reads Hypixel Zombies text (titles, chat, scoreboard, powerup names) in any language Hypixel
 * supports. Keys like {@code zombies.game.round} are looked up in {@code zombies.lang} for the
 * detected language, falling back to English.
 */
public final class ZombiesLanguage {
    private static final Identifier LANG_FILE = new Identifier("keystrokesmod", "zombies.lang");
    private static final String ENGLISH = "EN_US";
    private static final Pattern EMOJI = Pattern.compile(
            "(?:[\ud83c\udf00-\ud83d\uddff]|[\ud83e\udd00-\ud83e\uddff]|[\ud83d\ude00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]|[☀-⛿]️?|[✀-➿]️?|Ⓜ️?|[\ud83c\udde6-\ud83c\uddff]{1,2}|[\ud83c\udd70\ud83c\udd71\ud83c\udd7e\ud83c\udd7f\ud83c\udd8e\ud83c\udd91-\ud83c\udd9a]️?|[#*0-9]️?⃣|[↔-↙↩-↪]️?|[⬅-⬇⬛⬜⭐⭕]️?|[⤴⤵]️?|[〰〽]️?|[㊗㊙]️?|[\ud83c\ude01\ud83c\ude02\ud83c\ude1a\ud83c\ude2f\ud83c\ude32-\ud83c\ude3a\ud83c\ude50\ud83c\ude51]️?|[‼⁉]️?|[▪▫▶◀◻-◾]️?|[©®]️?|[™ℹ]️?|\ud83c\udc04️?|\ud83c\udccf️?|[⌚⌛⌨⏏⏩-⏳⏸-⏺]️?)");
    private static final Pattern COLOR_CODE = Pattern.compile("§[a-zA-Z0-9]");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** The "Zombies Left" scoreboard label in each language, mapped to that language's code. */
    private static final Map<String, String> ZOMBIES_LEFT_LABELS = new LinkedHashMap<>();

    private static final Map<String, String> translations = new HashMap<>();
    private static String language = ENGLISH;

    static {
        ZOMBIES_LEFT_LABELS.put("Zombies Left", ENGLISH);
        ZOMBIES_LEFT_LABELS.put("剩余僵尸", "ZH_CN");
        ZOMBIES_LEFT_LABELS.put("剩下殭屍數", "ZH_TW");
        ZOMBIES_LEFT_LABELS.put("Zbývající zombie", "CS");
        ZOMBIES_LEFT_LABELS.put("Zombier tilbage", "DA");
        ZOMBIES_LEFT_LABELS.put("Zombies over", "NL");
        ZOMBIES_LEFT_LABELS.put("Zombeja jäljellä", "FI");
        ZOMBIES_LEFT_LABELS.put("Zombies restants", "FR");
        ZOMBIES_LEFT_LABELS.put("Zombies übrig", "DE");
        ZOMBIES_LEFT_LABELS.put("Ζόμπι που Απομένουν", "EL");
        ZOMBIES_LEFT_LABELS.put("Hátralévő Zombik", "HU");
        ZOMBIES_LEFT_LABELS.put("Zombi Rimanenti", "IT");
        ZOMBIES_LEFT_LABELS.put("残りゾンビ", "JA");
        ZOMBIES_LEFT_LABELS.put("남은 좀비", "KO");
        ZOMBIES_LEFT_LABELS.put("Zombier igjen", "NO");
        ZOMBIES_LEFT_LABELS.put("Pozostałe zombi", "PL");
        ZOMBIES_LEFT_LABELS.put("Zombies restantes", "PT_PT");
        ZOMBIES_LEFT_LABELS.put("Zumbis restantes", "PT_BR");
        ZOMBIES_LEFT_LABELS.put("Zombi Rămași", "RO");
        ZOMBIES_LEFT_LABELS.put("Осталось зомби", "RU");
        ZOMBIES_LEFT_LABELS.put("Zombies Restantes", "ES_ES");
        ZOMBIES_LEFT_LABELS.put("Zombier kvar", "SV_SE");
        ZOMBIES_LEFT_LABELS.put("Kalan Zombi", "TR");
        ZOMBIES_LEFT_LABELS.put("Залишилося зомбі", "UK");
    }

    private ZombiesLanguage() {}

    /** Loads {@code zombies.lang}. Lines look like {@code zombies.game.round.EN_US = Round}. */
    public static void load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Utils.mc.getResourceManager().getResource(LANG_FILE).asStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" = ", 2);
                if (parts.length == 2) {
                    translations.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Strips colour codes and emoji Hypixel decorates its text with. */
    public static String clean(String text) {
        String noEmoji = EMOJI.matcher(text).replaceAll("");
        return COLOR_CODE.matcher(noEmoji).replaceAll("").trim();
    }

    /** Switches language when {@code scoreboardLine} is the "Zombies Left: N" line. */
    public static void detectLanguage(String scoreboardLine) {
        String lang = ZOMBIES_LEFT_LABELS.get(labelOf(scoreboardLine));
        if (lang != null) {
            language = lang;
        }
    }

    /** Whether a scoreboard line is the "Zombies Left: N" line, in any language. */
    public static boolean isZombiesLeftLine(String scoreboardLine) {
        return ZOMBIES_LEFT_LABELS.containsKey(labelOf(scoreboardLine));
    }

    public static boolean isZombiesScoreboardTitle(String title) {
        String text = clean(title);
        return text.contains("ZOMBIES") || text.contains("僵尸末日") || text.contains("殭屍末日");
    }

    public static boolean isRoundTitle(String title) {
        return contains(title, "zombies.game.round");
    }

    /** The round number in a "Round N" title, or 0 if it isn't one. */
    public static int roundNumber(String title) {
        if (!isRoundTitle(title)) {
            return 0;
        }
        Matcher matcher = NUMBER.matcher(clean(title));
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    /** {@code text} equals the translation of {@code key} (current language or English), ignoring case. */
    public static boolean equals(String text, String key) {
        String cleaned = clean(text);
        String local = translate(key, language);
        String english = translate(key, ENGLISH);
        return !local.isEmpty() && cleaned.equalsIgnoreCase(local)
                || !english.isEmpty() && cleaned.equalsIgnoreCase(english);
    }

    /** {@code text} contains the translation of {@code key} (current language or English). */
    public static boolean contains(String text, String key) {
        String cleaned = clean(text);
        String local = translate(key, language);
        String english = translate(key, ENGLISH);
        return !local.isEmpty() && cleaned.contains(local) || !english.isEmpty() && cleaned.contains(english);
    }

    private static String translate(String key, String lang) {
        return translations.getOrDefault(key + "." + lang, "");
    }

    /** The part before the colon ("Zombies Left" in "Zombies Left: 12"); full-width colons too. */
    private static String labelOf(String scoreboardLine) {
        if (scoreboardLine == null) {
            return "";
        }
        String text = clean(scoreboardLine);
        int colon = text.indexOf(':');
        if (colon < 0) {
            colon = text.indexOf('：');
        }
        return colon < 0 ? text : text.substring(0, colon).trim();
    }
}

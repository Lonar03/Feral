// Ported from Meowtils (wtf.tatp.meowtils), modified for Meower and Feral
package keystrokesmod.module.impl.other;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import keystrokesmod.mixin.impl.accessor.IAccessorGuiScreenBook;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.ClientChatReceivedEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.GuiOpenEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.GuiScreenEvent.DrawScreenEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import org.lwjgl.input.Keyboard;

/**
 * Rerolls Hypixel's random nick (/nick help setrandom) until one passes the enabled filters, then claims it.
 * Runs unattended: with "Rejoin from limbo" it goes back to the lobby and carries on if Hypixel parks you in
 * limbo. Press ESC to stop. Word filters are edited with .nickbot add/remove/list.
 */
public class NickBot extends Module {
    private static final File LIST_FILE =
            new File(new File(Minecraft.getInstance().gameDir, "keystrokes"), "nickbot_list.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Character, Character> ALT_LETTERS = new HashMap<>();
    private static final Set<String> words = new LinkedHashSet<>();

    private final SliderSetting delay;
    private final ButtonSetting autoAccept;
    private final ButtonSetting rejoinFromLimbo;
    private final ButtonSetting listSkipped;
    private final ButtonSetting legacyNicks;
    private final ButtonSetting fourChar;
    private final ButtonSetting maxChar;
    private final ButtonSetting plainText;
    private final ButtonSetting specialAffix;
    private final ButtonSetting customWords;

    private boolean active;
    private String currentNick = "";
    private int skippedNicks;
    private final List<Task> tasks = new ArrayList<>();

    static {
        ALT_LETTERS.put('0', 'o');
        ALT_LETTERS.put('1', 'i');
        ALT_LETTERS.put('3', 'e');
        ALT_LETTERS.put('4', 'a');
        ALT_LETTERS.put('5', 's');
        ALT_LETTERS.put('6', 'g');
        ALT_LETTERS.put('7', 't');
        ALT_LETTERS.put('8', 'b');
        ALT_LETTERS.put('9', 'g');
    }

    public NickBot() {
        super("NickBot", Module.category.other);
        load();
        this.registerSetting(new ButtonSetting("Start", this::start));
        this.registerSetting(this.delay = new SliderSetting("Delay", "ms", 500.0, 0.0, 2000.0, 50.0));
        this.registerSetting(this.autoAccept = new ButtonSetting("Auto accept good nick", true));
        this.registerSetting(this.rejoinFromLimbo = new ButtonSetting("Rejoin from limbo", false));
        this.registerSetting(this.listSkipped = new ButtonSetting("List skipped nicks", false));
        this.registerSetting(new DescriptionSetting("Accept nicks that are"));
        this.registerSetting(this.legacyNicks = new ButtonSetting("Legacy nicks", true));
        this.registerSetting(this.fourChar = new ButtonSetting("Four char", true));
        this.registerSetting(this.maxChar = new ButtonSetting("Max char", false));
        this.registerSetting(this.plainText = new ButtonSetting("Plain text", false));
        this.registerSetting(this.specialAffix = new ButtonSetting("Special affixes", true));
        this.registerSetting(this.customWords = new ButtonSetting("Custom words", true));
    }

    // ============================== control ==============================

    public void start() {
        if (!Utils.nullCheck() || !Utils.isHypixel()) {
            Utils.sendMessage("&cNickBot only works on Hypixel.");
            return;
        }

        if (!this.isEnabled()) {
            this.enable();
        }

        this.active = true;
        this.skippedNicks = 0;
        mc.player.sendChat("/nick reuse");
        Utils.sendMessage("&aStarted &9NickBot&a. Press &cESC &ato stop.");
    }

    public void stop() {
        if (this.active) {
            this.active = false;
            this.tasks.clear();
            Utils.sendMessage("&cStopped &9NickBot&c.");
        }
    }

    public boolean isActive() {
        return this.active;
    }

    @Override
    public void onDisable() {
        this.stop();
        this.currentNick = "";
    }

    private void reroll() {
        mc.player.sendChat("/nick help setrandom");
    }

    private void schedule(long delayMs, Runnable action) {
        this.tasks.add(new Task(System.currentTimeMillis() + delayMs, action));
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent e) {
        if (e.phase != Phase.END) {
            return;
        }

        if (this.active && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
            this.stop();
        }

        long now = System.currentTimeMillis();
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = this.tasks.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (now >= task.runAt) {
                it.remove();
                due.add(task);
            }
        }

        for (Task task : due) {
            if (Utils.nullCheck()) {
                task.action.run();
            }
        }
    }

    // ============================== book handling ==============================

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent e) {
        if (!this.active || !(e.gui instanceof BookEditScreen)) {
            return;
        }

        BookEditScreen book = (BookEditScreen) e.gui;
        this.schedule((long) this.delay.getInput(), () -> this.readBook(book));
    }

    private void readBook(BookEditScreen book) {
        if (!this.active) {
            return;
        }

        if (mc.screen != book) {
            // The book was closed (ESC) before we got to it.
            this.stop();
            return;
        }

        String text = getBookText(book);
        if (text == null) {
            Utils.sendMessage("&cNickBot: unable to read the nick book.");
            this.stop();
        } else if (text.contains("Uh-oh!")) {
            Utils.sendMessage("&cError occurred, rolling nick again.");
            this.skippedNicks++;
            this.schedule(2000L, this::reroll);
        } else if (text.contains("USE NAME") && text.contains("TRY AGAIN")) {
            String nick = extractNick(text);
            if (nick == null || nick.isEmpty()) {
                Utils.sendMessage("&cNickBot: nick was empty.");
                this.stop();
                return;
            }

            String reason = this.goodNickReason(nick);
            if (reason != null) {
                Utils.sendMessage(reason + ": &e" + nick);
                if (this.autoAccept.isToggled()) {
                    mc.player.sendChat("/nick actuallyset " + nick);
                }

                Utils.sendMessage("Skipped nicks: &e" + this.skippedNicks);
                this.active = false;
                this.skippedNicks = 0;
            } else {
                if (this.listSkipped.isToggled()) {
                    Utils.sendMessage("Skipped: &c&o" + nick);
                }

                this.skippedNicks++;
                this.reroll();
            }
        } else if (text.contains("TRY AGAIN")) {
            Utils.sendMessage("&cAn error occurred in &9NickBot&c, re-rolling...");
            this.reroll();
        } else {
            this.stop();
        }
    }

    private static String getBookText(BookEditScreen gui) {
        ItemStack book = ((IAccessorGuiScreenBook) gui).getBookObj();
        if (book == null || !book.hasNbt()) {
            return null;
        }

        NbtList pages = book.getNbt().getList("pages", 8);
        if (pages.size() == 0) {
            return null;
        }

        String page = pages.getString(0);
        try {
            Text component = Text.Serializer.fromJson(page);
            return component.getString();
        } catch (Exception e) {
            return Formatting.strip(page);
        }
    }

    // The nick is on the second line of the book.
    private static String extractNick(String text) {
        String[] lines = text.split("\n");
        return lines.length < 2 ? null : Utils.stripColor(lines[1]).trim();
    }

    @SubscribeEvent
    public void onDrawScreen(DrawScreenEvent.Post e) {
        if (this.active && e.gui instanceof BookEditScreen) {
            mc.textRenderer.drawWithShadow("NickBot Active", 10, 10, -1);
            mc.textRenderer.drawWithShadow("Press §cESC§f to stop.", 10, 20, -1);
            mc.textRenderer.drawWithShadow("Skipped nicks: §a" + this.skippedNicks, 10, 30, -1);
        }
    }

    // ============================== chat ==============================

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onChat(ClientChatReceivedEvent e) {
        if (e.type == 2 || e.message == null) {
            return;
        }

        String msg = Utils.stripColor(e.message.getString());
        if (this.active) {
            if (msg.contains("You are now nicked as ")) {
                this.currentNick = msg.replace("You are now nicked as ", "")
                        .replace("!", "")
                        .replace(" ", "");
                this.reroll();
            } else if (msg.equals("You are not allowed to do this!")) {
                this.active = false;
                Utils.sendMessage("&cUnable to nick.");
            } else if (msg.equals("You were spawned in Limbo.") && this.rejoinFromLimbo.isToggled()) {
                // Only while a run is in progress (Meowtils restarted on any limbo).
                Utils.sendMessage("&9NickBot&f: rejoining from limbo...");
                this.active = false;
                this.schedule(500L, () -> mc.player.sendChat("/lobby"));
                this.schedule(1500L, this::start);
            }

            // Hide the reroll spam while the bot runs.
            e.setCanceled(true);
        }
    }

    // ============================== filters ==============================

    /** Why this nick is worth keeping, or null to reroll. */
    private String goodNickReason(String nick) {
        String lower = nick.toLowerCase(Locale.ROOT);
        String normalized = normalizeAlt(lower);
        if (this.currentNick.equalsIgnoreCase(nick)) {
            return null;
        }

        if (this.specialAffix.isToggled() && (lower.startsWith("xx") || lower.endsWith("xx"))) {
            return "Special affix nick";
        }

        if (this.fourChar.isToggled() && lower.length() == 4) {
            return "Four char nick";
        }

        if (this.maxChar.isToggled() && lower.length() == 16 && normalized.matches("^[a-z]+$")) {
            return "Max char nick";
        }

        if (this.plainText.isToggled() && normalized.matches("^[a-z_]+$")) {
            return "Plain text nick";
        }

        // Meowtils checked the lowercased name here, so this filter could never pass; use the real casing.
        if (this.legacyNicks.isToggled() && isLegacyNick(nick)) {
            return "Legacy nick";
        }

        if (this.customWords.isToggled() && (matchesWords(lower) || matchesWords(normalized))) {
            return "Nick matched list";
        }

        return null;
    }

    // Capitalised first letter only, with a vowel repeated three or more times (e.g. "Zaaaap").
    private static boolean isLegacyNick(String nick) {
        if (!Character.isUpperCase(nick.charAt(0))) {
            return false;
        }

        for (int i = 1; i < nick.length(); i++) {
            if (Character.isUpperCase(nick.charAt(i))) {
                return false;
            }
        }

        String lower = nick.toLowerCase(Locale.ROOT);
        char last = 0;
        int count = 1;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ("aeiouy".indexOf(c) >= 0 && c == last) {
                if (++count >= 3) {
                    return true;
                }
            } else {
                last = c;
                count = 1;
            }
        }

        return false;
    }

    private static String normalizeAlt(String nick) {
        StringBuilder sb = new StringBuilder();
        for (char c : nick.toCharArray()) {
            sb.append(ALT_LETTERS.getOrDefault(c, c));
        }

        return sb.toString();
    }

    // Entries: "?x" contains, "=x" equals, "<x" starts with, ">x" ends with, plain "x" contains.
    private static boolean matchesWords(String name) {
        for (String entry : words) {
            if (entry.length() < 2) {
                continue;
            }

            String value = entry.substring(1);
            switch (entry.charAt(0)) {
                case '<':
                    if (name.startsWith(value)) {
                        return true;
                    }
                    break;
                case '=':
                    if (name.equals(value)) {
                        return true;
                    }
                    break;
                case '>':
                    if (name.endsWith(value)) {
                        return true;
                    }
                    break;
                case '?':
                    if (name.contains(value)) {
                        return true;
                    }
                    break;
                default:
                    if (name.contains(entry)) {
                        return true;
                    }
            }
        }

        return false;
    }

    // ============================== word list (.nickbot) ==============================

    private static String normalizeEntry(String entry) {
        return entry.toLowerCase(Locale.ROOT).replace(" ", "");
    }

    public static Set<String> getWords() {
        return words;
    }

    public static boolean addWord(String entry) {
        boolean added = words.add(normalizeEntry(entry));
        if (added) {
            save();
        }

        return added;
    }

    public static boolean removeWord(String entry) {
        boolean removed = words.remove(normalizeEntry(entry));
        if (removed) {
            save();
        }

        return removed;
    }

    private static void load() {
        words.clear();
        if (!LIST_FILE.isFile()) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(LIST_FILE.toPath(), StandardCharsets.UTF_8)) {
            Set<String> data = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            if (data != null) {
                words.addAll(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        LIST_FILE.getParentFile().mkdirs();
        try (Writer writer = Files.newBufferedWriter(LIST_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(words, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final class Task {
        private final long runAt;
        private final Runnable action;

        private Task(long runAt, Runnable action) {
            this.runAt = runAt;
            this.action = action;
        }
    }
}

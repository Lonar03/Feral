package keystrokesmod.script;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.NetworkUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;

public class ScriptManager {
    private static final char[] INVALID_SCRIPT_NAME_CHARS = new char[] {'\\', '/', ':', '*', '?', '"', '<', '>', '|'};
    private Minecraft mc = Minecraft.getInstance();
    public LinkedHashMap<Script, Module> scripts = new LinkedHashMap<>();
    public JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    public boolean deleteTempFiles = true;
    public File directory;
    public List<String> imports = Arrays.asList(
            Color.class.getName(),
            Collections.class.getName(),
            List.class.getName(),
            ArrayList.class.getName(),
            Arrays.class.getName(),
            Map.class.getName(),
            Set.class.getName(),
            HashMap.class.getName(),
            HashSet.class.getName(),
            ConcurrentHashMap.class.getName(),
            LinkedHashMap.class.getName(),
            LinkedHashSet.class.getName(),
            Iterator.class.getName(),
            Comparator.class.getName(),
            AtomicInteger.class.getName(),
            AtomicLong.class.getName(),
            AtomicBoolean.class.getName(),
            Random.class.getName(),
            Matcher.class.getName(),
            Pattern.class.getName());
    public String COMPILED_DIR = Utils.getCompilerDirectory();
    private Map<String, String> loadedHashes = new HashMap<>();

    public ScriptManager() {
        this.directory = new File(this.mc.gameDir + File.separator + "keystrokes", "scripts");
    }

    public void onEnable(Script script) {
        if (script.event == null) {
            script.event = new ScriptEvents(this.getModule(script));
            ForgeEvents.MinecraftForge.EVENT_BUS.register(script.event);
        }

        script.invoke("onEnable");
    }

    public Module getModule(Script script) {
        for (Entry<Script, Module> entry : this.scripts.entrySet()) {
            if (entry.getKey().equals(script)) {
                return entry.getValue();
            }
        }

        return null;
    }

    public String createScript(String requestedName) {
        String scriptName = this.normalizeScriptName(requestedName);
        String validationError = this.validateScriptName(scriptName, null);
        if (validationError != null) {
            Utils.sendMessage("&c" + validationError);
            return null;
        }

        try {
            if (!this.directory.exists() && !this.directory.mkdirs()) {
                Utils.sendMessage("&cFailed to create scripts folder.");
                return null;
            } else {
                Files.write(
                        new File(this.directory, scriptName + ".java").toPath(),
                        this.buildDefaultScriptTemplate().getBytes(StandardCharsets.UTF_8));
                this.loadScripts();
                return scriptName;
            }
        } catch (Exception e) {
            Utils.sendMessage("&cFailed to create script: &b" + scriptName);
            e.printStackTrace();
            return null;
        }
    }

    public void loadScripts() {
        for (Module module : this.scripts.values()) {
            module.disable();
        }

        if (this.deleteTempFiles) {
            this.deleteTempFiles = false;
            File tempDirectory = new File(this.COMPILED_DIR);
            if (tempDirectory.exists() && tempDirectory.isDirectory()) {
                File[] tempFiles = tempDirectory.listFiles();
                if (tempFiles != null) {
                    for (File tempFile : tempFiles) {
                        if (!tempFile.delete()) {
                            System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                        }
                    }
                }
            }
        } else if (!this.scripts.isEmpty()) {
            Iterator<Entry<Script, Module>> iterator = this.scripts.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<Script, Module> entry = iterator.next();
                String fileName = entry.getKey().file.getName();
                String hash = this.calculateHash(entry.getKey().file);
                String cachedHash = this.loadedHashes.get(fileName);
                if (cachedHash == null
                        || !cachedHash.equals(hash)
                        || entry.getKey().error
                        || entry.getKey().usesLoadString) {
                    entry.getKey().delete();
                    iterator.remove();
                    this.loadedHashes.remove(fileName);
                }
            }
        } else {
            this.loadedHashes.clear();
        }

        File scriptDirectory = this.directory;
        if (scriptDirectory.exists() && scriptDirectory.isDirectory()) {
            File[] scriptFiles = scriptDirectory.listFiles();
            if (scriptFiles != null) {
                for (File scriptFile : scriptFiles) {
                    if (scriptFile.isFile() && scriptFile.getName().endsWith(".java")) {
                        String fileName = scriptFile.getName();
                        String hash = this.calculateHash(scriptFile);
                        String cachedHash = this.loadedHashes.get(fileName);
                        if (cachedHash == null || !cachedHash.equals(hash)) {
                            this.parseFile(scriptFile);
                            this.loadedHashes.put(scriptFile.getName(), hash);
                        }
                    }
                }
            }
        } else if (scriptDirectory.mkdirs()) {
            System.out.println("Created script directory: " + scriptDirectory.getAbsolutePath());
        } else {
            System.err.println("Failed to create script directory: " + scriptDirectory.getAbsolutePath());
        }

        for (Module module : this.scripts.values()) {
            module.disable();
        }

        this.refreshScriptModules();
        ScriptDefaults.reloadModules();
        File tempDirectory = new File(this.COMPILED_DIR);
        if (tempDirectory.exists() && tempDirectory.isDirectory()) {
            File[] tempFiles = tempDirectory.listFiles();
            if (tempFiles != null) {
                for (File tempFile : tempFiles) {
                    if (!tempFile.delete()) {
                        System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    private boolean parseFile(File file) {
        if (!file.getName().startsWith("_") && file.getName().endsWith(".java")) {
            String scriptName = file.getName().replace(".java", "");
            if (scriptName.isEmpty()) {
                return false;
            }

            StringBuilder scriptContents = new StringBuilder();

            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

                String line;
                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        scriptContents.append(line).append("\n");
                    }
                } catch (Throwable var13) {
                    try {
                        bufferedReader.close();
                    } catch (Throwable var12) {
                        var13.addSuppressed(var12);
                    }

                    throw var13;
                }

                bufferedReader.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            if (scriptContents.length() == 0) {
                return false;
            }

            String source = this.normalizeLineSeparators(scriptContents.toString());
            List<String> topLevelLines = Utils.getTopLevelLines(source);
            boolean usesLoadString = false;

            for (String line : topLevelLines) {
                if (line.startsWith("load - \"") && line.endsWith("\"")) {
                    usesLoadString = true;
                    int loadIndex = source.indexOf(line);
                    if (loadIndex != -1) {
                        source = source.substring(0, loadIndex) + source.substring(loadIndex + line.length());
                    }

                    if (!Manager.enableHttpRequests.isToggled()) {
                        Utils.sendMessage(
                                "&7Blocked &cload string&7 in &b" + scriptName + "&7, http requests are not enabled.");
                    } else {
                        String url = line.substring("load - \"".length(), line.length() - 1);
                        String externalContents = this.normalizeLineSeparators(NetworkUtils.getTextFromURL(url, true));
                        if (externalContents.isEmpty()) {
                            break;
                        }

                        if (loadIndex != -1) {
                            source = source.substring(0, loadIndex) + externalContents + source.substring(loadIndex);
                        }
                    }
                }
            }

            Script script = new Script(scriptName);
            script.file = file;
            script.usesLoadString = usesLoadString;
            script.setCode(source);
            script.run();
            Module module = new Module(script);
            this.attachManagerSettings(script, module);
            Feral.scriptManager.scripts.put(script, module);
            ScriptDefaults.reloadModules();
            Feral.scriptManager.invoke("onLoad", module);
            return !script.error;
        } else {
            return false;
        }
    }

    private String normalizeLineSeparators(String source) {
        return source == null
                ? ""
                : source.replace('\u2028', '\n').replace('\u2029', '\n').replace('\u0085', '\n');
    }

    public void onDisable(Script script) {
        if (script.event != null) {
            ForgeEvents.MinecraftForge.EVENT_BUS.unregister(script.event);
            script.event = null;
        }

        script.invoke("onDisable");
    }

    public void invoke(String methodName, Module module, Object... args) {
        for (Entry<Script, Module> entry : this.scripts.entrySet()) {
            if ((entry.getValue().canBeEnabled() && entry.getValue().isEnabled() || methodName.equals("onLoad"))
                    && entry.getValue().equals(module)) {
                entry.getKey().invoke(methodName, args);
            }
        }
    }

    public int invokeBoolean(String methodName, Module module, Object... args) {
        for (Entry<Script, Module> entry : this.scripts.entrySet()) {
            if (entry.getValue().canBeEnabled()
                    && entry.getValue().isEnabled()
                    && entry.getValue().equals(module)) {
                int c = entry.getKey().getBoolean(methodName, args);
                if (c != -1) {
                    return c;
                }
            }
        }

        return -1;
    }

    public Float[] invokeFloatArray(String method, Module module, Object... args) {
        for (Entry<Script, Module> entry : this.scripts.entrySet()) {
            if (entry.getValue().canBeEnabled()
                    && entry.getValue().isEnabled()
                    && entry.getValue().equals(module)) {
                Float[] val = entry.getKey().getFloatArray(method, args);
                if (val != null) {
                    return val;
                }
            }
        }

        return null;
    }

    private String calculateHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(Paths.get(file.getPath()));
            byte[] hashBytes = digest.digest(fileBytes);
            StringBuilder hexString = new StringBuilder();

            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public boolean renameScript(Script script, String requestedName) {
        if (script != null && script.file != null) {
            String oldName = script.name;
            String newName = this.normalizeScriptName(requestedName);
            String validationError = this.validateScriptName(newName, oldName);
            if (validationError != null) {
                Utils.sendMessage("&c" + validationError);
                return false;
            }

            if (oldName.equals(newName)) {
                return true;
            }

            File newFile = new File(this.directory, newName + ".java");

            try {
                Files.move(script.file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.loadScripts();
                return true;
            } catch (Exception e) {
                Utils.sendMessage("&cFailed to rename script: &b" + oldName);
                e.printStackTrace();
                return false;
            }
        } else {
            Utils.sendMessage("&cFailed to rename script.");
            return false;
        }
    }

    private void refreshScriptModules() {
        for (CategoryComponent categoryComponent : ClickGui.categories) {
            if (categoryComponent.category == Module.category.scripts) {
                categoryComponent.reloadModules(false);
                break;
            }
        }
    }

    private void attachManagerSettings(Script script, Module module) {
        TextSetting[] scriptNameSetting = new TextSetting[1];
        scriptNameSetting[0] = new TextSetting(
                "Script name",
                script.name,
                "Type a new script name...",
                32,
                () -> this.renameScript(script, module, scriptNameSetting[0].getText()));
        module.registerSetting(scriptNameSetting[0]);
    }

    private void renameScript(Script script, Module module, String requestedName) {
        String oldName = module.getName();
        if (this.renameScript(script, requestedName) && !oldName.equals(requestedName.trim())) {
            Utils.sendMessage("&7Renamed script: &b" + oldName + " &7to &b" + requestedName.trim());
        }
    }

    private String validateScriptName(String scriptName, String currentName) {
        if (scriptName.isEmpty()) {
            return "Script name cannot be empty.";
        }

        if (!scriptName.endsWith(".") && !scriptName.endsWith(" ")) {
            for (char c : scriptName.toCharArray()) {
                if (c < ' ' || this.containsInvalidScriptChar(c)) {
                    return "Script name contains invalid characters.";
                }
            }

            File scriptFile = new File(this.directory, scriptName + ".java");
            return (currentName == null || !currentName.equalsIgnoreCase(scriptName)) && scriptFile.exists()
                    ? "Script already exists: " + scriptName
                    : null;
        } else {
            return "Script name cannot end with a space or period.";
        }
    }

    private boolean containsInvalidScriptChar(char c) {
        for (char invalidChar : INVALID_SCRIPT_NAME_CHARS) {
            if (invalidChar == c) {
                return true;
            }
        }

        return false;
    }

    private String normalizeScriptName(String name) {
        return name == null ? "" : name.trim();
    }

    private String buildDefaultScriptTemplate() {
        return "// New Feral script\nvoid onLoad() {\n}\n\nvoid onEnable() {\n}\n\nvoid onDisable() {\n}\n";
    }
}

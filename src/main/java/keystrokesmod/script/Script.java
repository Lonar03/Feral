package keystrokesmod.script;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import keystrokesmod.Feral;
import keystrokesmod.utility.Utils;

public class Script {
    public String name;
    public Class clazz;
    public Object instance;
    public String scriptName;
    public String codeStr;
    public boolean error = false;
    public boolean usesLoadString = false;
    public int STARTING_LINE;
    public ScriptEvents event;
    public File file;

    public Script(String name) {
        this.name = name;
        this.scriptName =
                "sc_" + name.replace(" ", "").replace(")", "_").replace("(", "_") + "_" + Utils.generateRandomString(5);
    }

    public boolean run() {
        this.error = true;
        this.clazz = null;
        this.instance = null;
        if (this.scriptName != null && this.codeStr != null) {
            ScriptManager manager = Feral.scriptManager;
            if (manager.compiler == null) {
                String message = "Feral scripting requires a Java runtime with the JDK compiler.";
                System.err.println(message);
                Utils.sendMessage("&c" + message);
                return false;
            }

            File outputDirectory = new File(manager.COMPILED_DIR);
            ScriptDiagnosticListener diagnostics = new ScriptDiagnosticListener(this.name, this.STARTING_LINE);

            try {
                StandardJavaFileManager fileManager = manager.compiler.getStandardFileManager(diagnostics, null, null);

                label86:
                {
                    boolean var9;
                    try {
                        Files.createDirectories(outputDirectory.toPath());
                        fileManager.setLocation(
                                StandardLocation.CLASS_OUTPUT, Collections.singletonList(outputDirectory));
                        File apiLocation = getApiLocation();
                        Set<File> classpath = new LinkedHashSet<>();
                        classpath.add(apiLocation);
                        Iterable<? extends File> defaultClasspath =
                                fileManager.getLocation(StandardLocation.CLASS_PATH);
                        if (apiLocation.isDirectory() && defaultClasspath != null) {
                            defaultClasspath.forEach(classpath::add);
                        }

                        fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
                        JavaSourceFromString source =
                                new JavaSourceFromString(this.scriptName, this.codeStr, this.STARTING_LINE);
                        if (manager.compiler
                                .getTask(
                                        null,
                                        fileManager,
                                        diagnostics,
                                        Arrays.asList("-proc:none", "-XDuseUnsharedTable"),
                                        null,
                                        Collections.singletonList(source))
                                .call()) {
                            break label86;
                        }

                        var9 = false;
                    } catch (Throwable var14) {
                        if (fileManager != null) {
                            try {
                                fileManager.close();
                            } catch (Throwable var11) {
                                var14.addSuppressed(var11);
                            }
                        }

                        throw var14;
                    }

                    if (fileManager != null) {
                        fileManager.close();
                    }

                    return var9;
                }

                if (fileManager != null) {
                    fileManager.close();
                }
            } catch (Exception exception) {
                System.err.println("Failed to compile Feral script " + this.name);
                exception.printStackTrace();
                Utils.sendMessage("&cFailed to compile script &b" + this.name + "&c. See the game log for details.");
                return false;
            }

            try {
                SecureClassLoader classLoader = new SecureClassLoader(
                        new URL[] {outputDirectory.toURI().toURL()}, Script.class.getClassLoader());

                try {
                    this.clazz = classLoader.loadClass(this.scriptName);
                    this.loadGeneratedNestedClasses(classLoader, outputDirectory);
                    this.instance = this.clazz.getDeclaredConstructor().newInstance();
                } catch (Throwable var12) {
                    try {
                        classLoader.close();
                    } catch (Throwable var10) {
                        var12.addSuppressed(var10);
                    }

                    throw var12;
                }

                classLoader.close();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                Utils.sendMessage("&cFailed to load script &b" + this.name + "&c. See the game log for details.");
                return false;
            }

            this.error = false;
            return true;
        } else {
            return false;
        }
    }

    private static File getApiLocation() throws IOException, URISyntaxException {
        CodeSource source = Script.class.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        if (location == null) {
            throw new IOException("Could not locate the Feral classes for the script compiler");
        }

        if ("jar".equals(location.getProtocol())) {
            location = ((JarURLConnection) location.openConnection()).getJarFileURL();
        }

        File file = new File(location.toURI());
        if (!file.isFile() && !file.isDirectory()) {
            throw new IOException("Feral compiler classpath entry does not exist: " + file);
        } else {
            return file;
        }
    }

    public int getBoolean(String s, Object... array) {
        if (this.clazz != null && this.instance != null) {
            Method method = null;

            for (Method method2 : this.clazz.getDeclaredMethods()) {
                if (method2.getName().equalsIgnoreCase(s)
                        && method2.getParameterCount() == array.length
                        && method2.getReturnType().equals(boolean.class)) {
                    method = method2;
                    break;
                }
            }

            if (method != null) {
                try {
                    method.setAccessible(true);
                    Object invoke = method.invoke(this.instance, array);
                    if (invoke instanceof Boolean) {
                        return (Boolean) invoke ? 1 : 0;
                    }
                } catch (IllegalAccessException | InvocationTargetException er) {
                    Utils.sendMessage("&7Runtime error during script &b" + this.name);
                    if (er.getCause() == null) {
                        Utils.sendMessage(" &7err: &cThrowable");
                    } else {
                        Utils.sendMessage(
                                " &7err: &c" + er.getCause().getClass().getSimpleName());
                        StackTraceElement[] stArr = er.getCause().getStackTrace();
                        if (stArr.length > 0) {
                            StackTraceElement st = stArr[0];

                            for (StackTraceElement element : er.getCause().getStackTrace()) {
                                if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                    st = element;
                                    break;
                                }
                            }

                            Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - this.STARTING_LINE));
                            Utils.sendMessage(" &7src: &c" + st.getMethodName());
                        }
                    }
                }
            }

            return -1;
        } else {
            return -1;
        }
    }

    public String getString(String s, Object... array) {
        if (this.clazz != null && this.instance != null) {
            Method method = null;

            for (Method method2 : this.clazz.getDeclaredMethods()) {
                if (method2.getName().equalsIgnoreCase(s)
                        && method2.getParameterCount() == array.length
                        && method2.getReturnType().equals(String.class)) {
                    method = method2;
                    break;
                }
            }

            if (method != null) {
                try {
                    method.setAccessible(true);
                    Object invoke = method.invoke(this.instance, array);
                    if (invoke instanceof String) {
                        return (String) invoke;
                    }
                } catch (IllegalAccessException | InvocationTargetException er) {
                    Utils.sendMessage("&7Runtime error during script &b" + this.name);
                    if (er.getCause() == null) {
                        Utils.sendMessage(" &7err: &cThrowable");
                    } else {
                        Utils.sendMessage(
                                " &7err: &c" + er.getCause().getClass().getSimpleName());
                        StackTraceElement[] stArr = er.getCause().getStackTrace();
                        if (stArr.length > 0) {
                            StackTraceElement st = stArr[0];

                            for (StackTraceElement element : er.getCause().getStackTrace()) {
                                if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                    st = element;
                                    break;
                                }
                            }

                            Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - this.STARTING_LINE));
                            Utils.sendMessage(" &7src: &c" + st.getMethodName());
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public Float[] getFloatArray(String methodName, Object... args) {
        if (this.clazz != null && this.instance != null) {
            Method method = null;

            for (Method _method : this.clazz.getDeclaredMethods()) {
                if (_method.getName().equals(methodName)
                        && _method.getReturnType().equals(Float[].class)
                        && _method.getParameterCount() == args.length) {
                    method = _method;
                    break;
                }
            }

            if (method != null) {
                try {
                    method.setAccessible(true);
                    Object result = method.invoke(this.instance, args);
                    if (result instanceof Float[]) {
                        return (Float[]) result;
                    }
                } catch (IllegalAccessException | InvocationTargetException er) {
                    Utils.sendMessage("&7Runtime error during script &b" + this.name);
                    if (er.getCause() == null) {
                        Utils.sendMessage(" &7err: &cThrowable");
                    } else {
                        Utils.sendMessage(
                                " &7err: &c" + er.getCause().getClass().getSimpleName());
                        StackTraceElement[] stArr = er.getCause().getStackTrace();
                        if (stArr.length > 0) {
                            StackTraceElement st = stArr[0];

                            for (StackTraceElement element : er.getCause().getStackTrace()) {
                                if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                    st = element;
                                    break;
                                }
                            }

                            Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - this.STARTING_LINE));
                            Utils.sendMessage(" &7src: &c" + st.getMethodName());
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public void delete() {
        this.clazz = null;
        this.instance = null;
        File file = new File(Feral.scriptManager.COMPILED_DIR + File.separator + this.scriptName + ".class");
        if (file.exists()) {
            file.delete();
        }
    }

    public void setCode(String code) {
        this.STARTING_LINE = 0;
        StringBuilder fileCodeContents = new StringBuilder();
        Iterator<String> iterator = Feral.scriptManager.imports.iterator();

        while (iterator.hasNext()) {
            this.STARTING_LINE++;
            fileCodeContents.append("import ").append(iterator.next()).append(";\n");
        }

        fileCodeContents.append("import keystrokesmod.script.model.*;\n");
        fileCodeContents.append("import keystrokesmod.script.packet.clientbound.*;\n");
        fileCodeContents.append("import keystrokesmod.script.packet.serverbound.*;\n");
        String name = Utils.extractFileName(this.name);
        String transformedCode =
                ScriptSourceTransformer.supportNestedTypes(ScriptSourceTransformer.remapRavenClasses(code));
        this.codeStr = fileCodeContents
                + "@SuppressWarnings(\"unchecked\")public class "
                + this.scriptName
                + " extends "
                + ScriptDefaults.class.getName()
                + " {public static final "
                + ScriptDefaults.modules.class.getName().replace("$", ".")
                + " modules = new "
                + ScriptDefaults.modules.class.getName().replace("$", ".")
                + "(\""
                + name
                + "\");public static final String scriptName = \""
                + name
                + "\";\n"
                + transformedCode
                + "\n}";
        this.STARTING_LINE += 4;
    }

    private void loadGeneratedNestedClasses(SecureClassLoader classLoader, File outputDirectory)
            throws ClassNotFoundException {
        File[] nestedClassFiles = outputDirectory.listFiles(
                (directory, fileNamex) -> fileNamex.startsWith(this.scriptName + "$") && fileNamex.endsWith(".class"));
        if (nestedClassFiles != null && nestedClassFiles.length != 0) {
            Arrays.sort(
                    nestedClassFiles,
                    Comparator.<File>comparingInt(classFile -> this.nestedClassDepth(classFile.getName()))
                            .thenComparing(File::getName));

            for (File nestedClassFile : nestedClassFiles) {
                String fileName = nestedClassFile.getName();
                classLoader.loadClass(fileName.substring(0, fileName.length() - ".class".length()));
            }
        }
    }

    private int nestedClassDepth(String fileName) {
        int depth = 0;

        for (int i = 0; i < fileName.length(); i++) {
            if (fileName.charAt(i) == '$') {
                depth++;
            }
        }

        return depth;
    }

    public boolean invoke(String s, Object... array) {
        if (this.clazz != null && this.instance != null) {
            Method method = null;

            for (Method method2 : this.clazz.getDeclaredMethods()) {
                if (method2.getName().equalsIgnoreCase(s)
                        && method2.getParameterCount() == array.length
                        && method2.getReturnType().equals(void.class)) {
                    method = method2;
                    break;
                }
            }

            if (method != null) {
                try {
                    method.setAccessible(true);
                    method.invoke(this.instance, array);
                    return true;
                } catch (IllegalAccessException | InvocationTargetException er) {
                    Utils.sendMessage("&7Runtime error during script &b" + this.name);
                    if (er.getCause() == null) {
                        Utils.sendMessage(" &7err: &cThrowable");
                    } else {
                        Utils.sendMessage(
                                " &7err: &c" + er.getCause().getClass().getSimpleName());
                        StackTraceElement[] stArr = er.getCause().getStackTrace();
                        if (stArr.length > 0) {
                            StackTraceElement st = stArr[0];

                            for (StackTraceElement element : er.getCause().getStackTrace()) {
                                if (element.getClassName().equalsIgnoreCase(this.scriptName)) {
                                    st = element;
                                    break;
                                }
                            }

                            Utils.sendMessage(" &7line: &c" + (st.getLineNumber() - this.STARTING_LINE));
                            Utils.sendMessage(" &7src: &c" + st.getMethodName());
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }
}

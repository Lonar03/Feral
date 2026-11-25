package keystrokesmod.script;

import keystrokesmod.platform.ClientPlatform;

public final class ScriptClassNames {
    private ScriptClassNames() {}

    public static String simpleName(Class<?> type) {
        return ClientPlatform.className(type);
    }
}

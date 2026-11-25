package keystrokesmod.script;

import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import keystrokesmod.utility.Utils;

public class ScriptDiagnosticListener implements DiagnosticListener<JavaFileObject> {
    private final String scriptName;
    private final int generatedLines;
    private boolean reported;

    public ScriptDiagnosticListener(String scriptName, int generatedLines) {
        this.scriptName = scriptName;
        this.generatedLines = generatedLines;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        if (!this.reported && diagnostic.getKind() == Kind.ERROR) {
            this.reported = true;
            String message = diagnostic.getMessage(Locale.getDefault());
            long line = diagnostic.getLineNumber() - this.generatedLines;
            String location = line > 0L ? " at line " + line : "";
            System.err.println("Feral script " + this.scriptName + location + ": " + message);
            Utils.sendMessage(
                    "&cError loading script &b" + this.scriptName + "&c" + location + ": " + message.split("\n", 2)[0]);
        }
    }
}

package keystrokesmod.utility.system;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import keystrokesmod.utility.Utils;

public class SystemUtils {
    public static void addToClipboard(String string) {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection(string);
            clipboard.setContents(stringSelection, null);
        } catch (Exception e) {
            Utils.sendMessage("&cFailed to copy &b" + string);
        }
    }
}

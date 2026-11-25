package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.utility.Utils;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class Message {
    public LiteralText component;

    public Message(String message) {
        this.component = new LiteralText(message);
    }

    public void appendStyle(String style, String action, String styleMessage, String message) {
        Style chatStyle = new Style();
        if (style.equals("HOVER")) {
            chatStyle.setHoverEvent(new HoverEvent(
                    Utils.getEnum(net.minecraft.text.HoverEvent.Action.class, action), new LiteralText(styleMessage)));
        } else if (style.equals("CLICK")) {
            chatStyle.setClickEvent(
                    new ClickEvent(Utils.getEnum(net.minecraft.text.ClickEvent.Action.class, action), styleMessage));
        }

        this.component.append(new LiteralText(message).setStyle(chatStyle));
    }

    public void append(String append) {
        this.component.append(new LiteralText(append));
    }

    public List<Message> getSiblings() {
        List<Message> siblings = new ArrayList<>();

        for (Text sibling : this.component.getSiblings()) {
            siblings.add(new Message(sibling.getContent()));
        }

        return siblings;
    }

    public String getStyle() {
        return this.component.getStyle().toString();
    }

    public String getText() {
        return this.component.getContent();
    }

    @Override
    public String toString() {
        return "TextComponent{text='"
                + this.component.getContent()
                + '\''
                + ", siblings="
                + this.component.getSiblings()
                + ", style="
                + this.component.getStyle()
                + '}';
    }
}

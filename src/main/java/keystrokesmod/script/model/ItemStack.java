package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.script.ScriptClassNames;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.locale.I18n;
import net.minecraft.resource.Identifier;

public class ItemStack {
    public String type;
    public String name;
    public String displayName;
    public int stackSize;
    public int maxStackSize;
    public int durability;
    public int maxDurability;
    public boolean isBlock;
    public net.minecraft.item.ItemStack itemStack;
    public int meta;

    public ItemStack(net.minecraft.item.ItemStack itemStack, byte f1) {
        if (itemStack != null) {
            this.itemStack = itemStack;
            this.isBlock = itemStack.getItem() instanceof BlockItem;
            Class<?> typeClass = this.isBlock
                    ? ((BlockItem) itemStack.getItem()).getBlock().getClass()
                    : itemStack.getItem().getClass();
            this.type = ScriptClassNames.simpleName(typeClass);
            this.name = ClientPlatform.registryName(itemStack.getItem()).substring(10);
            this.displayName = itemStack.getHoverName();
            this.stackSize = itemStack.size;
            this.maxStackSize = itemStack.getMaxSize();
            this.durability = itemStack.getMaxDamage() - itemStack.getDamage();
            this.maxDurability = itemStack.getMaxDamage();
            this.meta = itemStack.getMetadata();
        }
    }

    public ItemStack(String name) {
        this(withMeta(name), (byte) 0);
    }

    private static net.minecraft.item.ItemStack withMeta(String name) {
        String[] parts = name.split(":");
        String itemName = parts[0];
        int meta = 0;
        if (parts.length > 1) {
            meta = parseMeta(parts[1]);
        }

        Item item = (Item) Item.REGISTRY.get(new Identifier("minecraft:" + itemName));
        return new net.minecraft.item.ItemStack(item, 1, meta);
    }

    private static int parseMeta(String metaStr) {
        try {
            return Integer.parseInt(metaStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public List<String> getTooltip() {
        return this.itemStack.getTooltip(Minecraft.getInstance().player, true);
    }

    public List<Object[]> getEnchantments() {
        Map<Integer, Integer> enchants = EnchantmentHelper.getEnchantments(this.itemStack);
        if (enchants.isEmpty()) {
            return null;
        }

        List<Object[]> enchantments = new ArrayList<>();

        for (Entry<Integer, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = Enchantment.byId(entry.getKey());
            String name = I18n.translateDefault(enchant.getTranslationKey())
                    .toLowerCase()
                    .replace(" ", "_");
            enchantments.add(new Object[] {name, entry.getValue()});
        }

        return enchantments;
    }

    public static ItemStack convert(net.minecraft.item.ItemStack itemStack) {
        return itemStack == null ? null : new ItemStack(itemStack, (byte) 0);
    }

    @Override
    public String toString() {
        return "ItemStack(" + this.type + "," + this.name + ")";
    }
}

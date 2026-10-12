package net.nullcoil.nidalib.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.nullcoil.nidalib.Nidalib;
import net.nullcoil.nidalib.util.TranslationKeys;

public class ModAttributes {
    public static final Holder<Attribute> CORRUPTION;
    static {
        CORRUPTION = Registry.registerForHolder(
                BuiltInRegistries.ATTRIBUTE,
                Nidalib.id("corruption"),
                new RangedAttribute(TranslationKeys.CORRUPTION, 0d,0d,1024d).setSyncable(true));
    }
}

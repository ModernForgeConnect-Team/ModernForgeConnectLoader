package github.com.gengyoubo.mfcl.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRegistryIdsTest {
    @Test void givesLegacyItemsReadableNamesAndKeepsLegalIds() {
        assertEquals("baubles:ring",LegacyRegistryIds.id("Baubles:Ring").toString());
        assertEquals("thaumcraft:item_research_notes",LegacyRegistryIds.id("Thaumcraft:ItemResearchNotes").toString());
        assertEquals("thaumcraft:block_table",LegacyRegistryIds.id("Thaumcraft:blockTable").toString());
        assertEquals("example:xml_parser",LegacyRegistryIds.id("Example:XMLParser").toString());
        assertEquals("example:existing/path_1",LegacyRegistryIds.id("example:existing/path_1").toString());
        assertEquals("minecraft:diamond",LegacyRegistryIds.id("minecraft:diamond").toString());
    }
    @Test void escapesUnsupportedCharactersWithoutHidingTheWholeName() {
        assertEquals("mod_7c_addon:my_20_item",LegacyRegistryIds.id("Mod|Addon:My Item").toString());
        assertEquals("example:folder/my_item",LegacyRegistryIds.id("Example:folder/MyItem").toString());
    }
    @Test void rejectsNamesThatWouldMergeDistinctLegacyItems() {
        var failure=assertThrows(IllegalArgumentException.class,()->LegacyRegistryIds.validate(List.of("Thaumcraft:ItemResearchNotes","thaumcraft:item_research_notes")));
        assertTrue(failure.getMessage().contains("Thaumcraft:ItemResearchNotes"));
        assertTrue(failure.getMessage().contains("thaumcraft:item_research_notes"));
    }
    @Test void remapsSavedHexadecimalNamesOnlyToTheirLoadedOriginalEntry() throws Exception {
        String key="Baubles:Ring",target="ring";
        var old=ResourceLocation.parse("mfcl:legacy/426175626c65733a52696e67");
        assertEquals(old,LegacyRegistryIds.previousId(key));
        @SuppressWarnings("unchecked") IForgeRegistry<String> registry=(IForgeRegistry<String>)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IForgeRegistry.class},
                (proxy,method,args)->method.getName().equals("getKey")&&target.equals(args[0])?LegacyRegistryIds.id(key):null);
        var saved=new MissingMappingsEvent.Mapping<>(registry,registry,old,7);
        var absent=new MissingMappingsEvent.Mapping<>(registry,registry,LegacyRegistryIds.previousId("Absent:Item"),8);
        LegacyRegistryIds.remap(List.of(saved,absent),Map.of(key,target));
        var action=saved.getClass().getDeclaredField("action");action.setAccessible(true);
        var mapped=saved.getClass().getDeclaredField("target");mapped.setAccessible(true);
        assertEquals(MissingMappingsEvent.Action.REMAP,action.get(saved));assertSame(target,mapped.get(saved));
        assertEquals(MissingMappingsEvent.Action.DEFAULT,action.get(absent));
    }
}

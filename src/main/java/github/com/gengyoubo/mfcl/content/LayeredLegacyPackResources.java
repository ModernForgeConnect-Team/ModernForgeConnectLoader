package github.com.gengyoubo.mfcl.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Session-generated resources override the immutable cached base, as one logical pack. */
final class LayeredLegacyPackResources implements PackResources {
    private final PathPackResources base,session;
    LayeredLegacyPackResources(String id,Path basePath,Path sessionPath){base=new PathPackResources(id,basePath,false);session=new PathPackResources(id,sessionPath,false);}
    @Override public IoSupplier<InputStream> getRootResource(String... name){var value=session.getRootResource(name);return value!=null?value:base.getRootResource(name);}
    @Override public IoSupplier<InputStream> getResource(PackType type,ResourceLocation id){var value=session.getResource(type,id);return value!=null?value:base.getResource(type,id);}
    @Override public void listResources(PackType type,String namespace,String path,ResourceOutput output){
        Map<ResourceLocation,IoSupplier<InputStream>> merged=new LinkedHashMap<>();
        base.listResources(type,namespace,path,merged::put);session.listResources(type,namespace,path,merged::put);merged.forEach(output);
    }
    @Override public Set<String> getNamespaces(PackType type){var result=new HashSet<>(base.getNamespaces(type));result.addAll(session.getNamespaces(type));return result;}
    @Override public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer)throws IOException{return base.getMetadataSection(serializer);}
    @Override public String packId(){return base.packId();}
    @Override public void close(){session.close();base.close();}
}

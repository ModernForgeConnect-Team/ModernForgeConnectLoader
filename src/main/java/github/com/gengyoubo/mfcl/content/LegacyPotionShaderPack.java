package github.com.gengyoubo.mfcl.content;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Keep original post-effect passes isolated from the native shader library, upgrading GLSL 1.20 to 1.50. */
final class LegacyPotionShaderPack {
    private final LegacyServices services;
    private final LegacyContentPack pack;
    private final Set<String> written=new HashSet<>();
    LegacyPotionShaderPack(LegacyServices services,LegacyContentPack pack){this.services=services;this.pack=pack;}
    static ResourceLocation location(String resource){
        var id=ResourceLocation.parse(resource);return ResourceLocation.fromNamespaceAndPath("mfcl","shaders/post/legacy_potions/"+id.getNamespace()+"/"+id.getPath().substring("shaders/post/".length()));
    }
    private String namespace(String name){int colon=name.indexOf(':');return colon<0?"minecraft":name.substring(0,colon);}
    private String path(String name){return name.substring(name.indexOf(':')+1);}
    private String program(String name){return ("mfcl:legacy_potions/"+namespace(name)+"/"+path(name)).toLowerCase(Locale.ROOT);}
    private String read(String resource)throws IOException{
        try(var input=services.loader().getResourceAsStream("assets/"+namespace(resource)+"/"+path(resource))){
            if(input==null)throw new FileNotFoundException("Original potion shader: "+resource);return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    void export(String resource)throws IOException{
        var json=JsonParser.parseString(read(resource)).getAsJsonObject();
        for(var pass:json.getAsJsonArray("passes")){
            var object=pass.getAsJsonObject();String name=object.get("name").getAsString();exportProgram(name);object.addProperty("name",program(name));
        }
        var target=location(resource);pack.json("assets/"+target.getNamespace()+"/"+target.getPath(),json);
    }
    private void exportProgram(String name)throws IOException{
        if(!written.add(name))return;
        var json=JsonParser.parseString(read(namespace(name)+":shaders/program/"+path(name)+".json")).getAsJsonObject();
        for(String stage:List.of("vertex","fragment")){
            String original=json.get(stage).getAsString();String extension=stage.equals("vertex")?".vsh":".fsh";
            String glsl=read(namespace(original)+":shaders/program/"+path(original)+extension);
            glsl=glsl.replace("#version 120","#version 150").replaceAll("\\battribute\\b","in").replaceAll("\\bvarying\\b",stage.equals("vertex")?"out":"in").replaceAll("\\btexture2D\\b","texture");
            if(stage.equals("fragment")&&glsl.contains("gl_FragColor"))glsl=glsl.replace("#version 150","#version 150\nout vec4 mfclFragColor;").replace("gl_FragColor","mfclFragColor");
            var host=ResourceLocation.parse(program(original));var path=pack.directory().resolve("assets/"+host.getNamespace()+"/shaders/program/"+host.getPath()+extension);
            Files.createDirectories(path.getParent());Files.writeString(path,glsl,StandardCharsets.UTF_8);json.addProperty(stage,program(original));
        }
        var host=ResourceLocation.parse(program(name));pack.json("assets/"+host.getNamespace()+"/shaders/program/"+host.getPath()+".json",json);
    }
}

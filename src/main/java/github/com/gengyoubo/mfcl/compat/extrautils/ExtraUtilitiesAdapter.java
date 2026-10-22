package github.com.gengyoubo.mfcl.compat.extrautils;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Optional original Extra Utilities integration. */
public final class ExtraUtilitiesAdapter implements LegacyModAdapter {
    public String id(){return "extrautilities";}
    public boolean supports(Set<String> mods){return mods.contains("ExtraUtilities");}
    public void beforeBoot(ClassLoader loader) throws Exception {
        LegacyModAdapter.registerBytecodeAdapters(loader,"github.com.gengyoubo.mfcl.legacy1710.compat.extrautils.ExtraUtilitiesPortalAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.extrautils.ExtraUtilitiesPipeAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.extrautils.ExtraUtilitiesTextAdapter");
    }
    public Map<String,Object> initialize(LegacyServices services) throws Exception {
        String config="com.rwtema.extrautils.ExtraUtils";
        var dimensions=new ArrayList<String>();
        var dark=services.dimensions().registerConfigured("extrautils:deep_dark",config,"underdarkDimID","minecraft:plains");
        if(dark!=null){services.dimensions().terrain(dark.hostKey());dimensions.add(dark.hostKey());}
        var millennium=services.dimensions().registerConfigured("extrautils:last_millennium",config,"endoftimeDimID","minecraft:plains");
        if(millennium!=null)dimensions.add(millennium.hostKey());
        ExtraUtilitiesTravel.install(services,dark==null?0:dark.legacyId(),millennium==null?0:millennium.legacyId());
        // Modern Forge transforms event classes through the current context loader. This callback starts in the
        // private 1.7.10 context, so install native listeners using the host loader before referring to event types.
        ClassLoader previous=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(ExtraUtilitiesAdapter.class.getClassLoader());
            ExtraUtilitiesFlight.install(services);
        }finally{Thread.currentThread().setContextClassLoader(previous);}
        return Map.of("status","READY","dimensions",dimensions);
    }
}

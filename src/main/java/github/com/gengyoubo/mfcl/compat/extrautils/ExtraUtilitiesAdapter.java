package github.com.gengyoubo.mfcl.compat.extrautils;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Optional original Extra Utilities integration. */
public final class ExtraUtilitiesAdapter implements LegacyModAdapter {
    public String id(){return "extrautilities";}
    public boolean supports(Set<String> mods){return mods.contains("ExtraUtilities");}
    public void beforeBoot(ClassLoader loader) throws Exception {
        LegacyModAdapter.registerBytecodeAdapters(loader,"github.com.gengyoubo.mfcl.legacy1710.compat.extrautils.ExtraUtilitiesPortalAdapter");
    }
    public Map<String,Object> initialize(LegacyServices services) throws Exception {
        var config=services.loader().loadClass("com.rwtema.extrautils.ExtraUtils");
        int dark=config.getField("underdarkDimID").getInt(null),millennium=config.getField("endoftimeDimID").getInt(null);
        var dimensions=new ArrayList<String>();
        if(dark!=0){
            services.dimensions().register("extrautils:deep_dark",dark,"minecraft:plains");
            services.dimensions().terrain("extrautils:deep_dark");dimensions.add("extrautils:deep_dark");
        }
        if(millennium!=0){
            services.dimensions().register("extrautils:last_millennium",millennium,"minecraft:plains");dimensions.add("extrautils:last_millennium");
        }
        ExtraUtilitiesTravel.install(services,dark,millennium);
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

package github.com.gengyoubo.mfcl.compat.codechicken;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Namespace/reflection compatibility shared by every mod using this old library. */
public final class CodeChickenAdapter implements LegacyModAdapter {
    @Override public String id() { return "codechicken"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains("CodeChickenCore"); }
    @Override public void beforeBoot(ClassLoader loader) throws Exception {
        loader.getClass().getMethod("registerBytecodeAdapter", String.class).invoke(loader,
                "github.com.gengyoubo.mfcl.legacy1710.compat.codechicken.CodeChickenNamespaceAdapter");
    }
    @Override public Map<String, Object> initialize(LegacyServices services) { return Map.of("status", "READY"); }
}

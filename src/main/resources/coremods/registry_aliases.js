function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    return {
        'mfcl_registry_snapshot': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraftforge.registries.GameData',
                'methodName': 'injectSnapshot',
                'methodDesc': '(Ljava/util/Map;ZZ)Lcom/google/common/collect/Multimap;'
            },
            'transformer': function(method) {
                var hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ILOAD, 2));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    'github/com/gengyoubo/mfcl/content/SplitRegistryAliases', 'beforeSnapshot', '(Ljava/util/Map;ZZ)V', false));
                method.instructions.insert(hook);
                return method;
            }
        }
    };
}

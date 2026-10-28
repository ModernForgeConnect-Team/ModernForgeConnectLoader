package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Generic old Minecraft drawing boundaries; mod layout and event code remains original. */
public final class GuiCaptureTransformer implements IClassTransformer {
    private static final String API = "github/com/gengyoubo/mfcl/bridge/LegacyGuiGraphics";
    public byte[] transform(String name,String transformedName,byte[] bytes) {
        if (bytes == null || !(name.equals("net.minecraft.client.gui.Gui") || name.equals("net.minecraft.client.gui.GuiScreen") || name.equals("net.minecraft.client.gui.FontRenderer")
                || name.equals("net.minecraft.client.renderer.entity.RenderItem") || name.equals("net.minecraft.client.renderer.texture.TextureManager")
                || name.equals("net.minecraft.client.Minecraft") || name.equals("net.minecraft.client.entity.AbstractClientPlayer") || name.equals("net.minecraft.entity.player.EntityPlayer")
                || name.equals("net.minecraft.client.entity.EntityPlayerSP") || name.equals("net.minecraft.client.entity.EntityClientPlayerMP")
                || name.equals("net.minecraft.entity.player.EntityPlayerMP") || name.equals("net.minecraft.network.NetHandlerPlayServer")
                || name.equals("net.minecraft.tileentity.MobSpawnerBaseLogic")
                || name.equals("net.minecraft.client.gui.GuiButton") || name.equals("net.minecraft.client.gui.GuiNewChat")
                || name.equals("net.minecraft.client.gui.inventory.GuiContainer")
                || name.equals("net.minecraft.client.multiplayer.PlayerControllerMP")
                || name.equals("cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper")
                || name.equals("cpw.mods.fml.common.network.FMLOutboundHandler")
                || name.equals("net.minecraft.client.audio.SoundHandler")
                || name.equals("net.minecraft.client.renderer.RenderHelper") || name.equals("net.minecraft.client.renderer.OpenGlHelper")
                || name.equals("cpw.mods.fml.common.eventhandler.EventBus"))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node,0);
        for (MethodNode m : node.methods) {
            if(name.equals("net.minecraft.client.entity.EntityClientPlayerMP")&&m.name.equals("func_71053_j")&&m.desc.equals("()V")) {
                InsnList close=new InsnList();LabelNode original=new LabelNode();
                close.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceInput","closeContainer","()Z",false));
                close.add(new JumpInsnNode(Opcodes.IFEQ,original));close.add(new InsnNode(Opcodes.RETURN));
                close.add(original);close.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(close);continue;
            }
            if (name.equals("net.minecraft.entity.player.EntityPlayerMP") && m.name.equals("func_71112_a") && m.desc.equals("(Lnet/minecraft/inventory/Container;II)V")) {
                InsnList progress = new InsnList(); LabelNode original = new LabelNode();
                progress.add(new VarInsnNode(Opcodes.ALOAD,0)); progress.add(new VarInsnNode(Opcodes.ALOAD,1));
                progress.add(new VarInsnNode(Opcodes.ILOAD,2)); progress.add(new VarInsnNode(Opcodes.ILOAD,3));
                progress.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyContainerProgress","send","(Ljava/lang/Object;Ljava/lang/Object;II)Z",false));
                progress.add(new JumpInsnNode(Opcodes.IFEQ,original)); progress.add(new InsnNode(Opcodes.RETURN));
                progress.add(original); progress.add(new FrameNode(Opcodes.F_SAME,0,null,0,null)); m.instructions.insert(progress); continue;
            }
            if (name.equals("net.minecraft.client.gui.GuiNewChat") && m.name.equals("func_146242_c") && m.desc.equals("(I)V")) {
                m.instructions.clear(); m.tryCatchBlocks.clear(); if (m.localVariables != null) m.localVariables.clear();
                m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0)); m.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));
                m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyClientChat","delete","(Ljava/lang/Object;I)V",false));
                m.instructions.add(new InsnNode(Opcodes.RETURN)); continue;
            }
            if (name.equals("net.minecraft.client.gui.GuiNewChat") && m.name.equals("func_146234_a") && m.desc.equals("(Lnet/minecraft/util/IChatComponent;I)V")) {
                m.instructions.clear(); m.tryCatchBlocks.clear(); if (m.localVariables != null) m.localVariables.clear();
                m.instructions.add(new VarInsnNode(Opcodes.ALOAD,1)); m.instructions.add(new VarInsnNode(Opcodes.ILOAD,2));
                m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyClientChat","message","(Ljava/lang/Object;I)V",false));
                m.instructions.add(new InsnNode(Opcodes.RETURN)); continue;
            }
            if(name.equals("net.minecraft.entity.player.EntityPlayerMP")&&m.name.equals("func_71016_p")&&m.desc.equals("()V")) {
                InsnList sync=new InsnList();sync.add(new VarInsnNode(Opcodes.ALOAD,0));
                sync.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyPlayerFlight","send","(Ljava/lang/Object;)V",false));m.instructions.insert(sync);
            }
            if(name.equals("net.minecraft.tileentity.MobSpawnerBaseLogic")&&m.name.equals("func_98272_a")&&m.desc.equals("(Ljava/lang/String;)V")) {
                for(AbstractInsnNode instruction:m.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN){
                    InsnList update=new InsnList();update.add(new VarInsnNode(Opcodes.ALOAD,0));update.add(new VarInsnNode(Opcodes.ALOAD,1));
                    update.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","spawnerType","(Ljava/lang/Object;Ljava/lang/String;)V",false));m.instructions.insertBefore(instruction,update);
                }
            }
            if (name.endsWith(".FMLOutboundHandler") && m.name.equals("write")) {
                InsnList send=new InsnList();LabelNode original=new LabelNode();
                load(send,Opcodes.ALOAD,1,3);
                send.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyEmbeddedPackets","send","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                send.add(new JumpInsnNode(Opcodes.IFEQ,original));send.add(new InsnNode(Opcodes.RETURN));
                send.add(original);send.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(send);continue;
            }
            if (name.equals("cpw.mods.fml.common.eventhandler.EventBus")) {
                // Records which old event types are actually delivered, for the undelivered-subscription diagnostic.
                if(m.name.equals("post")&&m.desc.equals("(Lcpw/mods/fml/common/eventhandler/Event;)Z")) {
                    InsnList posted=new InsnList();posted.add(new VarInsnNode(Opcodes.ALOAD,1));
                    posted.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDiagnostics","posted","(Ljava/lang/Object;)V",false));m.instructions.insert(posted);
                    try { Class.forName("github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics",true,getClass().getClassLoader()).getMethod("eventTrackingInstalled").invoke(null); }
                    catch (ReflectiveOperationException | LinkageError unavailable) { }
                }
                continue;
            }
            if (name.endsWith(".SimpleNetworkWrapper")) {
                String packets="github/com/gengyoubo/mfcl/bridge/LegacyPackets";
                if(m.name.equals("<init>")) {
                    for(AbstractInsnNode instruction:m.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN){
                        InsnList register=new InsnList();register.add(new VarInsnNode(Opcodes.ALOAD,0));register.add(new VarInsnNode(Opcodes.ALOAD,1));
                        register.add(new MethodInsnNode(Opcodes.INVOKESTATIC,packets,"channel","(Ljava/lang/Object;Ljava/lang/String;)V",false));m.instructions.insertBefore(instruction,register);
                    }
                } else if(m.name.equals("registerMessage")&&m.desc.startsWith("(Lcpw/mods/fml/common/network/simpleimpl/IMessageHandler;")) {
                    InsnList register=new InsnList();register.add(new VarInsnNode(Opcodes.ALOAD,0));register.add(new VarInsnNode(Opcodes.ALOAD,1));register.add(new VarInsnNode(Opcodes.ALOAD,2));register.add(new VarInsnNode(Opcodes.ILOAD,3));register.add(new VarInsnNode(Opcodes.ALOAD,4));
                    register.add(new MethodInsnNode(Opcodes.INVOKESTATIC,packets,"register","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;ILjava/lang/Object;)V",false));m.instructions.insert(register);
                } else if(m.name.equals("sendToAll")||m.name.equals("sendToDimension")||m.name.equals("sendToAllAround")) {
                    InsnList send=new InsnList();send.add(new VarInsnNode(Opcodes.ALOAD,0));send.add(new VarInsnNode(Opcodes.ALOAD,1));
                    int mode=m.name.equals("sendToAll")?0:m.name.equals("sendToDimension")?1:2;
                    if(mode==0)send.add(new InsnNode(Opcodes.ACONST_NULL));else if(mode==1){send.add(new VarInsnNode(Opcodes.ILOAD,2));send.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false));}else send.add(new VarInsnNode(Opcodes.ALOAD,2));
                    send.add(new InsnNode(Opcodes.ICONST_0+mode));send.add(new MethodInsnNode(Opcodes.INVOKESTATIC,packets,"distribute","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)Z",false));send.add(new InsnNode(Opcodes.POP));send.add(new InsnNode(Opcodes.RETURN));m.instructions=send;m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();
                } else if(m.name.equals("sendToServer")) {
                    InsnList send=new InsnList();LabelNode original=new LabelNode();send.add(new VarInsnNode(Opcodes.ALOAD,0));send.add(new VarInsnNode(Opcodes.ALOAD,1));
                    send.add(new MethodInsnNode(Opcodes.INVOKESTATIC,packets,"sendToServer","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));send.add(new JumpInsnNode(Opcodes.IFEQ,original));send.add(new InsnNode(Opcodes.RETURN));send.add(original);send.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(send);
                } else if(m.name.equals("sendTo")) {
                    InsnList send=new InsnList();LabelNode original=new LabelNode();send.add(new VarInsnNode(Opcodes.ALOAD,0));send.add(new VarInsnNode(Opcodes.ALOAD,1));send.add(new VarInsnNode(Opcodes.ALOAD,2));
                    send.add(new MethodInsnNode(Opcodes.INVOKESTATIC,packets,"sendTo","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",false));send.add(new JumpInsnNode(Opcodes.IFEQ,original));send.add(new InsnNode(Opcodes.RETURN));send.add(original);send.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(send);
                }
                continue;
            }
            if(name.endsWith(".PlayerControllerMP")&&m.name.equals("func_78756_a")) {
                InsnList button=new InsnList();LabelNode original=new LabelNode();
                button.add(new VarInsnNode(Opcodes.ILOAD,1));button.add(new VarInsnNode(Opcodes.ILOAD,2));
                button.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceInput","sendButton","(II)Z",false));
                button.add(new JumpInsnNode(Opcodes.IFEQ,original));button.add(new InsnNode(Opcodes.RETURN));
                button.add(original);button.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(button);continue;
            }
            if(name.endsWith(".GuiContainer")&&(m.name.equals("func_73864_a")||m.name.equals("func_146286_b"))) {
                InsnList input=new InsnList();LabelNode original=new LabelNode();
                input.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceInput","active","()Z",false));input.add(new JumpInsnNode(Opcodes.IFEQ,original));
                input.add(new VarInsnNode(Opcodes.ALOAD,0));load(input,Opcodes.ILOAD,1,3);
                input.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"net/minecraft/client/gui/GuiScreen",m.name,m.desc,false));input.add(new InsnNode(Opcodes.RETURN));
                input.add(original);input.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(input);continue;
            }
            if (name.endsWith(".GuiScreen") && m.name.equals("drawHoveringText") && m.desc.equals("(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V")) {
                InsnList tooltip = new InsnList(); tooltip.add(new VarInsnNode(Opcodes.ALOAD,1));
                tooltip.add(new MethodInsnNode(Opcodes.INVOKESTATIC,API,"tooltip","(Ljava/util/List;)V",false));
                m.instructions.insert(tooltip); continue;
            }
            if (name.equals("net.minecraft.network.NetHandlerPlayServer") && m.name.equals("func_147359_a")) {
                // MFCL server actors carry a connection-less handler; their packets have no recipient.
                InsnList drop = new InsnList(); LabelNode original = new LabelNode();
                drop.add(new VarInsnNode(Opcodes.ALOAD,0)); drop.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/network/NetHandlerPlayServer","field_147371_a","Lnet/minecraft/network/NetworkManager;"));
                drop.add(new JumpInsnNode(Opcodes.IFNONNULL,original)); drop.add(new InsnNode(Opcodes.RETURN));
                drop.add(original); drop.add(new FrameNode(Opcodes.F_SAME,0,null,0,null)); m.instructions.insert(drop); continue;
            }
            // addChatComponentMessage (XU quarry/pump/soul feedback...) bypasses addChatMessage and writes S02 to the
            // connection-less actor handler, which drops it; deliver it through the same chat capture instead.
            if ((m.name.equals("func_145747_a") || name.equals("net.minecraft.entity.player.EntityPlayerMP") && m.name.equals("func_146105_b")) && (m.access & Opcodes.ACC_ABSTRACT) == 0) {
                InsnList chat = new InsnList(); LabelNode original = new LabelNode();
                chat.add(new VarInsnNode(Opcodes.ALOAD,0)); chat.add(new VarInsnNode(Opcodes.ALOAD,1));
                chat.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyBlockLogic","chat","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                chat.add(new JumpInsnNode(Opcodes.IFEQ,original)); chat.add(new InsnNode(Opcodes.RETURN));
                chat.add(original); chat.add(new FrameNode(Opcodes.F_SAME,0,null,0,null)); m.instructions.insert(chat); continue;
            }
            if (name.endsWith(".EntityPlayer") && m.name.equals("openGui")) {
                InsnList open=new InsnList();LabelNode original=new LabelNode();
                open.add(new VarInsnNode(Opcodes.ALOAD,0));open.add(new VarInsnNode(Opcodes.ALOAD,1));open.add(new VarInsnNode(Opcodes.ILOAD,2));open.add(new VarInsnNode(Opcodes.ALOAD,3));load(open,Opcodes.ILOAD,4,3);
                open.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceContainers","openGui","(Ljava/lang/Object;Ljava/lang/Object;ILjava/lang/Object;III)Z",false));
                open.add(new JumpInsnNode(Opcodes.IFEQ,original));open.add(new InsnNode(Opcodes.RETURN));open.add(original);open.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(open);
                continue;
            }
            if(name.endsWith(".EntityPlayerMP")&&m.name.equals("func_71007_a")) {
                InsnList open=new InsnList();LabelNode original=new LabelNode();
                open.add(new VarInsnNode(Opcodes.ALOAD,0));open.add(new VarInsnNode(Opcodes.ALOAD,1));
                open.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceContainers","openInventory","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                open.add(new JumpInsnNode(Opcodes.IFEQ,original));open.add(new InsnNode(Opcodes.RETURN));open.add(original);open.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(open);
                continue;
            }
            if (name.endsWith("AbstractClientPlayer") && m.name.equals("func_110306_p")) {
                // Resolve preview arm textures at replay time, using the modern player's current skin.
                InsnList skin = new InsnList(); LabelNode original = new LabelNode();
                skin.add(new VarInsnNode(Opcodes.ALOAD,0));
                skin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyItemRendering","playerSkin","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                skin.add(new InsnNode(Opcodes.DUP)); skin.add(new JumpInsnNode(Opcodes.IFNULL,original));
                skin.add(new TypeInsnNode(Opcodes.CHECKCAST,"net/minecraft/util/ResourceLocation")); skin.add(new InsnNode(Opcodes.ARETURN));
                skin.add(original); skin.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Object"}));
                skin.add(new InsnNode(Opcodes.POP)); m.instructions.insert(skin);
                continue;
            }
            InsnList code = new InsnList(); String target=null, descriptor=null;
            int ret=Opcodes.RETURN;
            if(name.endsWith(".Minecraft")&&(m.name.equals("func_71381_h")||m.name.equals("func_71364_i"))) { /* Host screen transitions own mouse focus. */ }
            else if (name.endsWith(".Gui") && m.name.equals("func_73734_a")) { load(code,Opcodes.ILOAD,0,5); target="rect"; descriptor="(IIIII)V"; }
            else if (name.endsWith(".Minecraft") && m.name.equals("func_147108_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1)); target="showScreen";descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith(".GuiButton") && m.name.equals("func_146113_a")) { target="clickSound";descriptor="()V"; }
            else if (name.endsWith(".SoundHandler") && m.name.equals("func_147682_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1));target="sound";descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith(".Gui") && m.name.equals("func_73733_a")) { load(code,Opcodes.ILOAD,1,6); target="gradient"; descriptor="(IIIIII)V"; }
            else if (name.endsWith(".Gui") && m.name.equals("func_73729_b")) { load(code,Opcodes.ILOAD,1,6); target="texture"; descriptor="(IIIIII)V"; }
            else if (name.endsWith("FontRenderer") && (m.name.equals("func_85187_a")||m.name.equals("func_78258_a"))) {
                code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new VarInsnNode(Opcodes.ALOAD,1)); load(code,Opcodes.ILOAD,2,4); target="fontText"; descriptor="(Ljava/lang/Object;Ljava/lang/String;IIIZ)I"; ret=Opcodes.IRETURN;
            } else if (name.endsWith("TextureManager") && m.name.equals("func_110577_a")) { code.add(new VarInsnNode(Opcodes.ALOAD,1)); target="bind"; descriptor="(Ljava/lang/Object;)V"; }
            else if (name.endsWith("RenderItem") && (m.name.equals("func_82406_b") || m.name.equals("func_77015_a"))) {
                code.add(new VarInsnNode(Opcodes.ALOAD,3)); load(code,Opcodes.ILOAD,4,2); code.add(new InsnNode(Opcodes.ICONST_0)); code.add(new InsnNode(Opcodes.ACONST_NULL));
                target="item"; descriptor="(Ljava/lang/Object;IIZLjava/lang/String;)V";
            } else if (name.endsWith("RenderItem") && m.name.equals("func_94148_a")) {
                code.add(new VarInsnNode(Opcodes.ALOAD,3)); load(code,Opcodes.ILOAD,4,2); code.add(new InsnNode(Opcodes.ICONST_1)); code.add(new VarInsnNode(Opcodes.ALOAD,6));
                target="item"; descriptor="(Ljava/lang/Object;IIZLjava/lang/String;)V";
            } else if (name.endsWith("OpenGlHelper") && m.name.equals("func_77472_b") && m.desc.equals("(I)V")) {
                code.add(new VarInsnNode(Opcodes.ILOAD,0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorldGraphics","clientActiveTexture","(I)V",false));
            } else if ((name.endsWith("RenderHelper") || name.endsWith("OpenGlHelper")) && Type.getReturnType(m.desc).getSort()==Type.VOID && !m.name.startsWith("<")) { }
            else continue;
            if (target != null) code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,API,target,descriptor,false));
            code.add(new InsnNode(ret)); m.instructions=code; m.tryCatchBlocks.clear(); if(m.localVariables!=null)m.localVariables.clear();
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void load(InsnList code,int opcode,int first,int count) { for(int i=0;i<count;i++)code.add(new VarInsnNode(opcode,first+i)); }
}

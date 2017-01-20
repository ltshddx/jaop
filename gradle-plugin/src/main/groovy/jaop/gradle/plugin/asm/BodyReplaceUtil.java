package jaop.gradle.plugin.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import jaop.gradle.plugin.Config;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.Expr;

/**
 * Created by liting06 on 2017/1/18.
 */

public class BodyReplaceUtil {

    public static CtClass doit(CtMethod ctMethod, Config config) throws Exception {
        ClassPool classPool = ctMethod.getDeclaringClass().getClassPool();
        byte[] srcBytes = classPool.get(ctMethod.getDeclaringClass().getName()).toBytecode();

        ClassNode classNode = ASMHelper.getClassNode(srcBytes);
        MethodNode srcMethod = ASMHelper.getMethod(classNode, ctMethod.getName());

        CtMethod configMethod = config.getCtMethod();
        MethodNode bodyConfig = ASMHelper.getMethod(
                ASMHelper.getClassNode(configMethod.getDeclaringClass().toBytecode()), configMethod.getName());

        ASMHelper.ParamTypeLsit params = ASMHelper.getArgTypes(srcMethod.desc);
        String returnType = ASMHelper.getReturnType(srcMethod.desc).name;
        int targetSize = ((srcMethod.access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
        int configSize = ((bodyConfig.access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;;

        InsnList srcInsnList = srcMethod.instructions;
        srcMethod.instructions = new InsnList();
        ListIterator<AbstractInsnNode> newIterator = srcMethod.instructions.iterator();

        if (configSize == 1) {
            ASMHelper.newInstance(newIterator, configMethod.getDeclaringClass().getName().replace(".", "/"));
            newIterator.add(new VarInsnNode(Opcodes.ASTORE, srcMethod.maxLocals));
        }
        ASMHelper.newInstance(newIterator, "jaop/domain/internal/HookImplForPlugin");
        newIterator.add(new VarInsnNode(Opcodes.ASTORE, srcMethod.maxLocals + configSize));

        if (targetSize == 1) {
            newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
            newIterator.add(new VarInsnNode(Opcodes.ALOAD, 0));
            newIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "callThis", "Ljava/lang/Object;"));

            newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
            newIterator.add(new VarInsnNode(Opcodes.ALOAD, 0));
            newIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "target", "Ljava/lang/Object;"));
        }

        newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
        ASMHelper.intInsnNode(newIterator, params.itemSize());
        newIterator.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        int localIndex = 0, arrayIndex = 0;
        for (ASMHelper.ParamTypeItem param : params) {
            newIterator.add(new InsnNode(Opcodes.DUP));
            ASMHelper.intInsnNode(newIterator, arrayIndex);
            ASMHelper.loadNode(newIterator, param.name, targetSize + localIndex);
            ASMHelper.baseToObj(newIterator, param.name);
            newIterator.add(new InsnNode(Opcodes.AASTORE));
            localIndex += param.length;
            arrayIndex ++;
        }
        newIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "args", "[Ljava/lang/Object;"));
        // 上面是准备环境 下面开始把bodyhook里面的代码放在 newIterator

        ListIterator bodyIterator = bodyConfig.instructions.iterator();
        while (bodyIterator.hasNext()) {
            AbstractInsnNode next = (AbstractInsnNode) bodyIterator.next();
            if (next instanceof InsnNode) {
                InsnNode insnNode = (InsnNode) next;
                int opcode = insnNode.getOpcode();
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                    if (!"V".equals(returnType)) {
                        // real return
                        newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
                        newIterator.add(new FieldInsnNode(Opcodes.GETFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));
                        ASMHelper.parseToBase(newIterator, returnType);
                    }
                    ASMHelper.returnNode(newIterator, returnType);
                    continue;
                }
            } else if (next instanceof VarInsnNode) {
                VarInsnNode varInsnNode = (VarInsnNode) next;
                varInsnNode.var += srcMethod.maxLocals;
            } else if (next instanceof LineNumberNode) {
                LineNumberNode lineNumberNod = (LineNumberNode) next;
                if (lineNumberNod.line < 15536) {
                    lineNumberNod.line += 50000;
                }
            } else if (next instanceof IincInsnNode) {
                IincInsnNode varInsnNode = (IincInsnNode) next;
                varInsnNode.var += srcMethod.maxLocals;
            } else if (next instanceof MethodInsnNode &&
                    ((MethodInsnNode) next).name.equals("process") &&
                    ((MethodInsnNode) next).owner.equals("jaop/domain/MethodBodyHook")) {
                newIterator.add(new InsnNode(Opcodes.POP));
                if (srcInsnList == null) {
                    srcInsnList = ASMHelper.getMethod(ASMHelper.getClassNode(srcBytes), ctMethod.getName()).instructions;
                }
                ListIterator<AbstractInsnNode> srcIterator = srcInsnList.iterator();
                srcInsnList = null;
                List<JumpInsnNode> jumpInsnNodes = new ArrayList<>();
                LabelNode lastLabelNode = null;
                while (srcIterator.hasNext()) {
                    AbstractInsnNode srcNext = srcIterator.next();
                    if (srcNext instanceof InsnNode) {
                        InsnNode insnNode = (InsnNode) srcNext;
                        int opcode = insnNode.getOpcode();
                        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                            if (!"V".equals(returnType)) {
                                ASMHelper.storeNode(newIterator, returnType, srcMethod.maxLocals + bodyConfig.maxLocals);
                                newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
                                ASMHelper.loadNode(newIterator, returnType, srcMethod.maxLocals + bodyConfig.maxLocals);
                                ASMHelper.baseToObj(newIterator, returnType);
                                newIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));
                            }
                            if (srcIterator.hasNext()) {
                                // config里面会有return，把它替换成goto到最后一个label
                                JumpInsnNode jumpInsnNode = new JumpInsnNode(Opcodes.GOTO, null);
                                jumpInsnNodes.add(jumpInsnNode);
                                newIterator.add(jumpInsnNode);
                            }
                            continue;
                        }
                    } else if (srcNext instanceof LabelNode) {
                        lastLabelNode = (LabelNode) srcNext;
                    }
                    newIterator.add(srcNext);
                }
                for (JumpInsnNode node : jumpInsnNodes) {
                    node.label = lastLabelNode;
                }
                newIterator.add(new VarInsnNode(Opcodes.ALOAD, srcMethod.maxLocals + configSize));
                newIterator.add(new FieldInsnNode(Opcodes.GETFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));

                continue;
            }
            newIterator.add(next);
        }
        srcMethod.tryCatchBlocks.addAll(bodyConfig.tryCatchBlocks);

        ClassWriter writer = new JaopClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS, classPool);
        classNode.accept(writer);
        byte[] bytes = writer.toByteArray();
        return classPool.makeClass(new ByteArrayInputStream(bytes), false);
    }
}

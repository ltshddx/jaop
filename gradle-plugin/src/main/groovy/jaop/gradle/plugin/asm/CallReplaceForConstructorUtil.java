package jaop.gradle.plugin.asm;

import org.gradle.api.GradleException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
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
import javassist.expr.NewExpr;

/**
 * Created by liting06 on 2017/1/18.
 */

public class CallReplaceForConstructorUtil {

    public static CtClass doit(NewExpr newExpr, Config config) throws Exception {
        ClassPool classPool = newExpr.getEnclosingClass().getClassPool();
        final CtClass declaringClass = classPool.get(newExpr.where().getDeclaringClass().getName());
        ClassNode classNode = ASMHelper.getClassNode(declaringClass.toBytecode());
        MethodNode inWhichMethod = ASMHelper.getMethod(classNode, newExpr.where().getName());
        if (inWhichMethod == null) {
            throw new RuntimeException("method not found");
        }

        String configTargetClass = newExpr.getClassName().replace(".", "/");

        CtMethod configMethod = config.getCtMethod();

        ListIterator<AbstractInsnNode> srcIterator = inWhichMethod.instructions.iterator();
        while (srcIterator.hasNext()) {
            AbstractInsnNode srcNext = srcIterator.next();
            if (srcNext instanceof TypeInsnNode) {
                TypeInsnNode typeInsnNode = (TypeInsnNode) srcNext;
                if (typeInsnNode.getOpcode() == Opcodes.NEW && typeInsnNode.desc.equals(configTargetClass)) {
                    System.out.println("new " + configTargetClass);
                    srcIterator.remove();
                    srcIterator.next();
                    srcIterator.remove();
                }
            } else if (srcNext instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) srcNext;
                if ((methodInsnNode.owner + "/" + methodInsnNode.name).equals(configTargetClass + "/<init>")) {
                    ClassNode callClass = ASMHelper.getClassNode(configMethod.getDeclaringClass().toBytecode());
                    MethodNode call = ASMHelper.getMethod(callClass, configMethod.getName());
                    ASMHelper.ParamTypeLsit params = ASMHelper.getArgTypes(methodInsnNode.desc);
                    srcIterator.remove();
                    Iterator<ASMHelper.ParamTypeItem> descendingIterator = params.descendingIterator();
                    int cao1 = params.size();
                    int targetSize = 1;
                    while (descendingIterator.hasNext()) {
                        ASMHelper.ParamTypeItem next = descendingIterator.next();
                        cao1 -= next.length;
                        ASMHelper.storeNode(srcIterator, next.name, inWhichMethod.maxLocals + targetSize + cao1);
                    }

                    int configSize = (call.access & Opcodes.ACC_STATIC) > 0 ? 0 : 1;
                    if (configSize == 1) {
                        ASMHelper.newInstance(srcIterator, configMethod.getDeclaringClass().getName().replace(".", "/"));
                        srcIterator.add(new VarInsnNode(Opcodes.ASTORE, inWhichMethod.maxLocals + targetSize + params.size()));
                    }
                    ASMHelper.newInstance(srcIterator, "jaop/domain/internal/HookImplForPlugin");
                    srcIterator.add(new VarInsnNode(Opcodes.ASTORE, inWhichMethod.maxLocals + targetSize + params.size() + configSize));
                    if ((inWhichMethod.access & Opcodes.ACC_STATIC) == 0) {
                        srcIterator.add(new VarInsnNode(Opcodes.ALOAD, inWhichMethod.maxLocals + targetSize + params.size() + configSize));
                        srcIterator.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        srcIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "callThis", "Ljava/lang/Object;"));
                    }
                    srcIterator.add(new VarInsnNode(Opcodes.ALOAD, inWhichMethod.maxLocals + targetSize + params.size() + configSize));
                    ASMHelper.intInsnNode(srcIterator, params.itemSize());
                    srcIterator.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
                    int localIndex = 0, arrayIndex = 0;
                    for (ASMHelper.ParamTypeItem param : params) {
                        // 把参数存到数组
                        srcIterator.add(new InsnNode(Opcodes.DUP));
                        ASMHelper.intInsnNode(srcIterator, arrayIndex);
                        ASMHelper.loadNode(srcIterator, param.name, inWhichMethod.maxLocals + targetSize + localIndex);
                        ASMHelper.baseToObj(srcIterator, param.name);
                        srcIterator.add(new InsnNode(Opcodes.AASTORE));
                        localIndex += param.length;
                        arrayIndex ++;
                    }
                    srcIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "args", "[Ljava/lang/Object;"));
                    ListIterator callIterator = call.instructions.iterator();
                    List<JumpInsnNode> jumpInsnNodes = new ArrayList<>();
                    LabelNode lastLabelNode = null;
                    while (callIterator.hasNext()) {
                        AbstractInsnNode next = (AbstractInsnNode) callIterator.next();
                        if (next instanceof InsnNode) {
                            InsnNode insnNode = (InsnNode) next;
                            int opcode = insnNode.getOpcode();
                            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                                // 如果config有返回值  直接丢弃
                                switch (opcode) {
                                    case Opcodes.IRETURN:
                                    case Opcodes.FRETURN:
                                    case Opcodes.ARETURN:
                                        srcIterator.add(new InsnNode(Opcodes.POP));
                                        break;
                                    case Opcodes.LRETURN:
                                    case Opcodes.DRETURN:
                                        srcIterator.add(new InsnNode(Opcodes.POP2));
                                        break;
                                    default:
                                        break;
                                }
                                if (callIterator.hasNext()) {
                                    // config里面会有return，把它替换成goto到最后一个label
                                    JumpInsnNode jumpInsnNode = new JumpInsnNode(Opcodes.GOTO, null);
                                    jumpInsnNodes.add(jumpInsnNode);
                                    srcIterator.add(jumpInsnNode);
                                }
                                continue;
                            }
                        } else if (next instanceof LabelNode) {
                            lastLabelNode = (LabelNode) next;
                        } else if (next instanceof VarInsnNode) {
                            VarInsnNode varInsnNode = (VarInsnNode) next;
                            varInsnNode.var += (inWhichMethod.maxLocals + targetSize + params.size());
                        } else if (next instanceof IincInsnNode) {
                            IincInsnNode varInsnNode = (IincInsnNode) next;
                            varInsnNode.var += (inWhichMethod.maxLocals + targetSize + params.size());
                        } else if (next instanceof LineNumberNode) {
                            LineNumberNode lineNumberNod = (LineNumberNode) next;
                            if (lineNumberNod.line < 15536) {
                                lineNumberNod.line += 50000;
                            }
                        } else if (next instanceof MethodInsnNode &&
                                ((MethodInsnNode) next).owner.equals(callClass.name) &&
                                !((MethodInsnNode) next).name.equals("<init>") &&
                                next.getOpcode() == Opcodes.INVOKESPECIAL) {
                            throw new GradleException("in-line jaop config method must be public: "+ callClass.name + "/" + ((MethodInsnNode) next).name);
                        } else if (next instanceof MethodInsnNode &&
                                ((MethodInsnNode) next).name.equals("process") &&
                                ((MethodInsnNode) next).owner.equals("jaop/domain/MethodCallHook")) {
                            // stack top is hook or hook&args_array
//                            ASMHelper.ParamTypeLsit processArgTypes = ASMHelper.getArgTypes(((MethodInsnNode) next).desc);
//                            if (processArgTypes.size() == 1) {
//                                // process args
//                                localIndex = 0;
//                                arrayIndex = 0;
//                                for (ASMHelper.ParamTypeItem item : params) {
//                                    System.out.println("save " + item.name);
//                                    srcIterator.add(new InsnNode(Opcodes.DUP));
//                                    ASMHelper.intInsnNode(srcIterator, arrayIndex);
//                                    srcIterator.add(new InsnNode(Opcodes.AALOAD));
//                                    ASMHelper.parseToBase(srcIterator, item.name);
//                                    ASMHelper.storeNode(srcIterator, item.name, inWhichMethod.maxLocals + targetSize + localIndex);
//                                    localIndex += item.length;
//                                    arrayIndex ++;
//                                }
//                                srcIterator.add(new InsnNode(Opcodes.POP));
//                            }
                            // stack top is hook
                            srcIterator.add(new TypeInsnNode(Opcodes.NEW, configTargetClass));
                            srcIterator.add(new InsnNode(Opcodes.DUP));
//                            srcIterator.add(new InsnNode(Opcodes.DUP));
//                            srcIterator.add(new VarInsnNode(Opcodes.ASTORE, inWhichMethod.maxLocals));
                            int cao2 = 0;
                            for (ASMHelper.ParamTypeItem item : params) {
                                ASMHelper.loadNode(srcIterator, item.name, inWhichMethod.maxLocals + targetSize + cao2);
                                cao2 += item.length;
                            }
                            MethodInsnNode newSrcMethod = new MethodInsnNode(methodInsnNode.getOpcode(), methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc, methodInsnNode.itf);
                            srcIterator.add(newSrcMethod);
                            srcIterator.add(new FieldInsnNode(Opcodes.PUTFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));
                            srcIterator.add(new VarInsnNode(Opcodes.ALOAD, inWhichMethod.maxLocals + targetSize + params.size() + configSize));
                            srcIterator.add(new FieldInsnNode(Opcodes.GETFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));
                            continue;
                        }
                        srcIterator.add(next);
                    }
                    for (JumpInsnNode node : jumpInsnNodes) {
                        node.label = lastLabelNode;
                    }
                    inWhichMethod.tryCatchBlocks.addAll(call.tryCatchBlocks);
                    // 如果之前有返回值 把result还给它
                    srcIterator.add(new VarInsnNode(Opcodes.ALOAD, inWhichMethod.maxLocals + targetSize + params.size() + configSize));
                    srcIterator.add(new FieldInsnNode(Opcodes.GETFIELD, "jaop/domain/internal/HookImplForPlugin", "result", "Ljava/lang/Object;"));

//                    print1.maxLocals += (call.maxLocals + targetSize + params.size());
//                    inWhichMethod.maxStack += call.maxStack;
                }
            }
        }

        ClassWriter writer = new JaopClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS, classPool);
        classNode.accept(writer);
        byte[] bytes = writer.toByteArray();
        return classPool.makeClass(new ByteArrayInputStream(bytes), false);
    }
}

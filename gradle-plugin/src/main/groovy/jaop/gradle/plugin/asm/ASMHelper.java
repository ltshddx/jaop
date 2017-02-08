package jaop.gradle.plugin.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by liting06 on 2017/1/18.
 */

public class ASMHelper {
    public static void newInstance(ListIterator<AbstractInsnNode> iterator, String className) {
        iterator.add(new TypeInsnNode(Opcodes.NEW, className));
        iterator.add(new InsnNode(Opcodes.DUP));
        iterator.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, className, "<init>", "()V", false));
    }

    public static void intInsnNode(ListIterator<AbstractInsnNode> iterator, int i) {
        if (i >=0 && i < 6) {
//            int ICONST_0 = 3; // -
//            int ICONST_1 = 4; // -
//            int ICONST_2 = 5; // -
//            int ICONST_3 = 6; // -
//            int ICONST_4 = 7; // -
//            int ICONST_5 = 8; // -
            iterator.add(new InsnNode(Opcodes.ICONST_0 + i));
        } else {
            iterator.add(new IntInsnNode(Opcodes.BIPUSH, i));
        }
    }

    public static void storeNode(ListIterator<AbstractInsnNode> iterator, Object paramType, int index) {
        //(Ljava/lang/String;IDCBSJZF)V
        if (Opcodes.INTEGER.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.ISTORE, index));
        } else if (Opcodes.LONG.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.LSTORE, index));
        } else if (Opcodes.FLOAT.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.FSTORE, index));
        } else if (Opcodes.DOUBLE.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.DSTORE, index));
        } else {
            iterator.add(new VarInsnNode(Opcodes.ASTORE, index));
        }
    }

    public static int getSize(Object paramType) {
        if (Opcodes.LONG.equals(paramType) || Opcodes.DOUBLE.equals(paramType) || Opcodes.TOP.equals(paramType)) {
            return 2;
        } else {
            return 1;
        }
    }

    public static void loadNode(ListIterator<AbstractInsnNode> iterator, Object paramType, int index) {
        if (Opcodes.INTEGER.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.ILOAD, index));
        } else if (Opcodes.LONG.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.LLOAD, index));
        } else if (Opcodes.FLOAT.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.FLOAD, index));
        } else if (Opcodes.DOUBLE.equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.DLOAD, index));
        } else {
            iterator.add(new VarInsnNode(Opcodes.ALOAD, index));
        }
    }

    public static void storeNode(ListIterator<AbstractInsnNode> iterator, String paramType, int index) {
        //(Ljava/lang/String;IDCBSJZF)V
        if ("I".equals(paramType)
                || "C".equals(paramType)
                || "B".equals(paramType)
                || "S".equals(paramType)
                || "Z".equals(paramType)
                ) {
            iterator.add(new VarInsnNode(Opcodes.ISTORE, index));
        } else if ("J".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.LSTORE, index));
        } else if ("F".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.FSTORE, index));
        } else if ("D".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.DSTORE, index));
            // wrong
//        } else if ("[I".equals(paramType)) {
//            return new VarInsnNode(Opcodes.IASTORE + typeDiff, index);
//        } else if ("[L".equals(paramType)) {
//            return new VarInsnNode(Opcodes.LASTORE + typeDiff, index);
//        } else if ("[F".equals(paramType)) {
//            return new VarInsnNode(Opcodes.FASTORE + typeDiff, index);
//        } else if ("[D".equals(paramType)) {
//            return new VarInsnNode(Opcodes.DASTORE + typeDiff, index);
//        } else if (paramType.startsWith("[")) {
//            return new VarInsnNode(Opcodes.AASTORE + typeDiff, index);
        } else {
            iterator.add(new VarInsnNode(Opcodes.ASTORE, index));
        }
    }

    public static void returnNode(ListIterator<AbstractInsnNode> iterator, String paramType) {
        //(Ljava/lang/String;IDCBSJZF)V
        if ("I".equals(paramType)
                || "C".equals(paramType)
                || "B".equals(paramType)
                || "S".equals(paramType)
                || "Z".equals(paramType)
                ) {
            iterator.add(new InsnNode(Opcodes.IRETURN));
        } else if ("J".equals(paramType)) {
            iterator.add(new InsnNode(Opcodes.LRETURN));
        } else if ("F".equals(paramType)) {
            iterator.add(new InsnNode(Opcodes.FRETURN));
        } else if ("D".equals(paramType)) {
            iterator.add(new InsnNode(Opcodes.DRETURN));
        } else if ("V".equals(paramType)) {
            iterator.add(new InsnNode(Opcodes.RETURN));
        } else {
            iterator.add(new InsnNode(Opcodes.ARETURN));
        }
    }

    public static void loadNode(ListIterator<AbstractInsnNode> iterator, String paramType, int index) {
        //(Ljava/lang/String;IDCBSJZF)V
        if ("I".equals(paramType)
                || "C".equals(paramType)
                || "B".equals(paramType)
                || "S".equals(paramType)
                || "Z".equals(paramType)
                ) {
            iterator.add(new VarInsnNode(Opcodes.ILOAD, index));
        } else if ("J".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.LLOAD, index));
        } else if ("F".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.FLOAD, index));
        } else if ("D".equals(paramType)) {
            iterator.add(new VarInsnNode(Opcodes.DLOAD, index));
        } else {
            iterator.add(new VarInsnNode(Opcodes.ALOAD, index));
        }
    }

    public static void parseToBase(ListIterator<AbstractInsnNode> srcIterator, String type) {
//        srcIterator.add(new InsnNode(Opcodes.DUP));
//        LabelNode labelNode1 = new LabelNode();
//        srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
        if ("I".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Integer"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.ICONST_0));
            srcIterator.add(labelNode2);
        } else if ("C".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Character"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.ICONST_0));
            srcIterator.add(labelNode2);
        } else if ("B".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Byte"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.ICONST_0));
            srcIterator.add(labelNode2);
        } else if ("S".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Short"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.ICONST_0));
            srcIterator.add(labelNode2);
        } else if ("Z".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.ICONST_0));
            srcIterator.add(labelNode2);
        } else if ("J".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.LCONST_0));
            srcIterator.add(labelNode2);
        } else if ("F".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Float"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.FCONST_0));
            srcIterator.add(labelNode2);
        } else if ("D".equals(type)) {
            srcIterator.add(new InsnNode(Opcodes.DUP));
            LabelNode labelNode1 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.IFNULL, labelNode1));
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Double"));
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
            LabelNode labelNode2 = new LabelNode();
            srcIterator.add(new JumpInsnNode(Opcodes.GOTO, labelNode2));
            srcIterator.add(labelNode1);
            srcIterator.add(new InsnNode(Opcodes.POP));
            srcIterator.add(new InsnNode(Opcodes.DCONST_0));
            srcIterator.add(labelNode2);
        } else if (!"V".equals(type)) {
            srcIterator.add(new TypeInsnNode(Opcodes.CHECKCAST, type));
        }
//        srcIterator.add(labelNode);
    }


    public static void baseToObj(ListIterator<AbstractInsnNode> srcIterator, String type) {
        if ("I".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
        } else if ("C".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
        } else if ("B".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false));
        } else if ("S".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false));
        } else if ( "Z".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
        } else if ("J".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
        } else if ("F".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
        } else if ("D".equals(type)) {
            srcIterator.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
        }
    }

    private static int getTypeLength(String paramType) {
        if ("J".equals(paramType) || "D".equals(paramType)) {
            return 2;
        } else {
            return 1;
        }
    }

    public static MethodNode getMethod(String className, String method) {
        Iterator methodIterator = getClassNode(className).methods.iterator();
        while (methodIterator.hasNext()) {
            MethodNode methodNode = (MethodNode) methodIterator.next();
            if (methodNode.name.equals(method)) {
                return methodNode;
            }
        }
        return null;
    }


    public static MethodNode getMethod(ClassNode classNode, String method) {
        Iterator methodIterator = classNode.methods.iterator();
        boolean isConstructor = classNode.name.endsWith("/" + method);
        while (methodIterator.hasNext()) {
            MethodNode methodNode = (MethodNode) methodIterator.next();
            if (isConstructor && methodNode.name.equals("<init>")) {
                return methodNode;
            } else if (!isConstructor && methodNode.name.equals(method)) {
                return methodNode;
            }
        }
        return null;
    }

    public static ClassNode getClassNode(String className) {
        try {
            ClassReader reader = new ClassReader(className);
            ClassNode classNode = new ClassNode(Opcodes.ASM5);
            reader.accept(classNode, ClassReader.EXPAND_FRAMES);
            return classNode;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static ClassNode getClassNode(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode(Opcodes.ASM5);
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }


    public static void insertBefore(MethodNode dest, String configClass, String configMethod) {
        MethodNode src = getMethod(getClassNode(configClass), configMethod);
        if (src == null) {
            return;
        }

        List<AbstractInsnNode> print2nodes = new ArrayList<>();
        int destLocals = dest.maxLocals;

        if (src.maxStack > dest.maxStack) {
            dest.maxStack = src.maxStack;
        }
        dest.maxLocals = src.maxLocals + dest.maxLocals;


        ListIterator iterator = src.instructions.iterator();
        LabelNode lastLabelNode = null;
        List<JumpInsnNode> jumpInsnNodes = new ArrayList<>();
        while (iterator.hasNext()) {
            AbstractInsnNode next = (AbstractInsnNode) iterator.next();
            if (next instanceof InsnNode) {
                InsnNode insnNode = (InsnNode) next;
                int opcode = insnNode.getOpcode();
                if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                    if (iterator.hasNext()) {
                        JumpInsnNode jumpInsnNode = new JumpInsnNode(Opcodes.GOTO, null);
                        print2nodes.add(jumpInsnNode);
                        jumpInsnNodes.add(jumpInsnNode);
                    }
                    continue;
                }
            } else if (next instanceof LabelNode) {
                lastLabelNode = (LabelNode) next;
            } else if (next instanceof VarInsnNode) {
                VarInsnNode varInsnNode = (VarInsnNode) next;
                varInsnNode.var += destLocals;
            }
            print2nodes.add(next);
        }
        for (JumpInsnNode node : jumpInsnNodes) {
            node.label = lastLabelNode;
        }

        for (int i = print2nodes.size() - 1; i >0; i--) {
            dest.instructions.insert(print2nodes.get(i));
        }
    }

    public static ParamTypeLsit getArgTypes(String signature) {
        final ParamTypeLsit args = new ParamTypeLsit();
        if (signature != null) {
            SignatureReader reader = new SignatureReader(signature);
            reader.accept(new SignatureVisitor(Opcodes.ASM5) {
                boolean isReturn = false;
                boolean isArray = false;

                @Override
                public SignatureVisitor visitParameterType() {
                    isReturn = false;
                    return super.visitParameterType();
                }

                @Override
                public SignatureVisitor visitReturnType() {
                    isReturn = true;
                    return super.visitReturnType();
                }

                @Override
                public void visitTypeVariable(String name) {
                    super.visitTypeVariable(name);
                }

                @Override
                public void visitBaseType(char descriptor) {
                    if (!isReturn) {
                        String prefix = isArray ? "[" : "";
                        isArray = false;
                        ParamTypeItem item = new ParamTypeItem();
                        item.name = prefix + descriptor;
                        item.length = getTypeLength(item.name);
                        args.add(item);
                    }
                    super.visitBaseType(descriptor);
                }

                @Override
                public SignatureVisitor visitArrayType() {
                    isArray = true;
                    return super.visitArrayType();
                }

                @Override
                public void visitClassType(String name) {
                    if (!isReturn) {
                        String prefix = isArray ? "[" : "";
                        isArray = false;
                        ParamTypeItem item = new ParamTypeItem();
                        item.name = prefix + name;
                        item.length = getTypeLength(item.name);
                        args.add(item);
                    }
                    super.visitClassType(name);
                }
            });
        }
        return args;
    }

    public static ParamTypeItem getReturnType(String signature) {
        final ParamTypeItem item = new ParamTypeItem();
        if (signature != null) {
            SignatureReader reader = new SignatureReader(signature);
            reader.accept(new SignatureVisitor(Opcodes.ASM5) {
                boolean isReturn = false;
                boolean isArray = false;

                @Override
                public SignatureVisitor visitParameterType() {
                    isReturn = false;
                    return super.visitParameterType();
                }

                @Override
                public SignatureVisitor visitReturnType() {
                    isReturn = true;
                    return super.visitReturnType();
                }

                @Override
                public void visitTypeVariable(String name) {
                    super.visitTypeVariable(name);
                }

                @Override
                public void visitBaseType(char descriptor) {
                    if (isReturn) {
                        String prefix = isArray ? "[" : "";
                        isArray = false;
                        item.name = prefix + descriptor;
                        item.length = getTypeLength(item.name);
                    }
                    super.visitBaseType(descriptor);
                }

                @Override
                public SignatureVisitor visitArrayType() {
                    return super.visitArrayType();
                }

                @Override
                public void visitClassType(String name) {
                    if (isReturn) {
                        String prefix = isArray ? "[" : "";
                        isArray = false;
                        item.name = prefix + name;
                        item.length = getTypeLength(item.name);
                    }
                    super.visitClassType(name);
                }
            });
        }
        return item;
    }

    public static class ParamTypeLsit implements Iterable<ParamTypeItem> {
        private LinkedList<ParamTypeItem> list = new LinkedList<>();
        private int size = 0;
        public void add(ParamTypeItem param) {
            list.add(param);
            size += param.length;
        }

        public int size() {
            return size;
        }

        public int itemSize() {
            return list.size();
        }

        public Iterator<ParamTypeItem> iterator() {
            return list.iterator();
        }

        public Iterator<ParamTypeItem> descendingIterator() {
            return list.descendingIterator();
        }
    }

    public static class ParamTypeItem  {
        public String name;
        public int length;
    }
}

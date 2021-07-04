/*
 * Copyright (c) 2011 - Georgios Gousios <gousiosg@gmail.com>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package gr.gousiosg.javacg.stat;

import gr.gousiosg.javacg.common.Constants;
import gr.gousiosg.javacg.util.CommonUtil;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.*;

import java.util.*;

/**
 * The simplest of method visitors, prints any invoked method
 * signature for all method invocations.
 * <p>
 * Class copied with modifications from CJKM: http://www.spinellis.gr/sw/ckjm/
 */
public class MethodVisitor extends EmptyVisitor {

    private JavaClass visitedClass;
    private MethodGen mg;
    private ConstantPoolGen cp;
    private String format;
    private List<String> methodCalls = new ArrayList<>();

    // added by adrninistrator
    private Map<String, Set<String>> calleeMethodMap;
    private Map<String, Boolean> runnableImplClassMap;
    private Map<String, Boolean> threadChildClassMap;
    private Map<String, Set<String>> methodAnnotationMap;
    // added end

    public MethodVisitor(MethodGen m, JavaClass jc, Map<String, Set<String>> calleeMethodMap, Map<String, Boolean> runnableImplClassMap,
                         Map<String, Boolean> threadChildClassMap, Map<String, Set<String>> methodAnnotationMap) {
        visitedClass = jc;
        mg = m;
        cp = mg.getConstantPool();

        // modified by adrninistrator
        this.calleeMethodMap = calleeMethodMap;
        this.runnableImplClassMap = runnableImplClassMap;
        this.threadChildClassMap = threadChildClassMap;
        this.methodAnnotationMap = methodAnnotationMap;

        String fullMethod = visitedClass.getClassName() + ":" + mg.getName() + CommonUtil.argumentList(mg.getArgumentTypes());

        handleAnnotationName(fullMethod);

        format = "M:" + fullMethod + " " + "(%s)%s:%s%s";
    }

    // added by adrninistrator
    private void handleAnnotationName(String fullMethod) {
        AnnotationEntryGen[] annotationEntryGens = mg.getAnnotationEntries();
        if (annotationEntryGens == null || annotationEntryGens.length == 0) {
            return;
        }

        Set<String> annotationNameSet = methodAnnotationMap.get(fullMethod);
        if (annotationNameSet != null) {
            return;
        }

        annotationNameSet = new HashSet<>();
        for (AnnotationEntryGen annotationEntryGen : annotationEntryGens) {
            String annotationName = getAnnotationName(annotationEntryGen.getTypeName());
            annotationNameSet.add(annotationName);
        }
        methodAnnotationMap.put(fullMethod, annotationNameSet);
    }

    private String getAnnotationName(String origName) {
        String tmpName;
        if (origName.startsWith("L") && origName.endsWith(";")) {
            tmpName = origName.substring(1, origName.length() - 1);
        } else {
            tmpName = origName;
        }
        return tmpName.replace("/", ".");
    }
    // added end

    public List<String> start() {
        if (mg.isAbstract() || mg.isNative())
            return Collections.emptyList();

        for (InstructionHandle ih = mg.getInstructionList().getStart();
             ih != null; ih = ih.getNext()) {
            Instruction i = ih.getInstruction();

            if (!visitInstruction(i))
                i.accept(this);
        }
        return methodCalls;
    }

    private boolean visitInstruction(Instruction i) {
        short opcode = i.getOpcode();
        return ((InstructionConst.getInstruction(opcode) != null)
                && !(i instanceof ConstantPushInstruction)
                && !(i instanceof ReturnInstruction));
    }

    @Override
    public void visitINVOKEVIRTUAL(INVOKEVIRTUAL i) {
        addMethodCalls("M", i.getReferenceType(cp).toString(), i.getMethodName(cp), CommonUtil.argumentList(i.getArgumentTypes(cp)));
    }

    @Override
    public void visitINVOKEINTERFACE(INVOKEINTERFACE i) {
        addMethodCalls("I", i.getReferenceType(cp).toString(), i.getMethodName(cp), CommonUtil.argumentList(i.getArgumentTypes(cp)));
    }

    @Override
    public void visitINVOKESPECIAL(INVOKESPECIAL i) {
        addMethodCalls("O", i.getReferenceType(cp).toString(), i.getMethodName(cp), CommonUtil.argumentList(i.getArgumentTypes(cp)));
    }

    @Override
    public void visitINVOKESTATIC(INVOKESTATIC i) {
        addMethodCalls("S", i.getReferenceType(cp).toString(), i.getMethodName(cp), CommonUtil.argumentList(i.getArgumentTypes(cp)));
    }

    @Override
    public void visitINVOKEDYNAMIC(INVOKEDYNAMIC i) {
        addMethodCalls("D", i.getType(cp).toString(), i.getMethodName(cp), CommonUtil.argumentList(i.getArgumentTypes(cp)));
    }

    // added by adrninistrator
    private void addMethodCalls(String type, String calleeClassName, String calleeMethodName, String calleeMethodArgs) {

        // add callee method info
        Set<String> calleeMethodWithArgsSet = calleeMethodMap.get(calleeClassName);
        if (calleeMethodWithArgsSet == null) {
            calleeMethodWithArgsSet = new HashSet<>();
            calleeMethodMap.put(calleeClassName, calleeMethodWithArgsSet);
        }
        calleeMethodWithArgsSet.add(calleeMethodName + calleeMethodArgs);

        boolean skipRawMethodCall = false;

        if ("<init>".equals(calleeMethodName)) {
            // handle Runnable impl classes
            Boolean recordedRunnableImpl = runnableImplClassMap.get(calleeClassName);
            if (recordedRunnableImpl != null) {
                // do not record original call type
                skipRawMethodCall = true;
                // other function call runnable impl class <init>
                methodCalls.add(String.format(format, Constants.CALL_TYPE_RUNNABLE_INIT_RUN, calleeClassName, calleeMethodName, calleeMethodArgs));

                if (Boolean.FALSE.equals(recordedRunnableImpl)) {
                    // runnable impl class <init> call runnable impl class run()
                    String runnableImplClassMethod = String.format("M:%s:%s%s (%s)%s:run()", calleeClassName, calleeMethodName, calleeMethodArgs,
                            Constants.CALL_TYPE_RUNNABLE_INIT_RUN, calleeClassName);
                    methodCalls.add(runnableImplClassMethod);

                    runnableImplClassMap.put(calleeClassName, Boolean.TRUE);
                }
            }

            // handle Thread child classes
            Boolean recordedThreadChildClass = threadChildClassMap.get(calleeClassName);
            if (recordedThreadChildClass != null) {
                // do not record original call type
                skipRawMethodCall = true;
                // other function call thread child class <init>
                methodCalls.add(String.format(format, Constants.CALL_TYPE_THREAD_INIT_RUN, calleeClassName, calleeMethodName, calleeMethodArgs));

                if (Boolean.FALSE.equals(recordedThreadChildClass)) {
                    // thread child class <init> call thread child class run()
                    String threadChildClassMethod = String.format("M:%s:%s%s (%s)%s:run()", calleeClassName, calleeMethodName, calleeMethodArgs,
                            Constants.CALL_TYPE_THREAD_INIT_RUN, calleeClassName);
                    methodCalls.add(threadChildClassMethod);

                    threadChildClassMap.put(calleeClassName, Boolean.TRUE);
                }
            }
        }

        if (skipRawMethodCall) {
            return;
        }

        methodCalls.add(String.format(format, type, calleeClassName, calleeMethodName, calleeMethodArgs));
    }
    // added end
}

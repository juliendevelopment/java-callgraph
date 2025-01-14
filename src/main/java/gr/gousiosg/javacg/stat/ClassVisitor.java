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
import gr.gousiosg.javacg.dto.MethodCallDto;
import gr.gousiosg.javacg.enums.CallTypeEnum;
import gr.gousiosg.javacg.util.CommonUtil;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The simplest of class visitors, invokes the method visitor class for each
 * method found.
 */
public class ClassVisitor extends EmptyVisitor {

    private JavaClass clazz;
    private ConstantPoolGen constants;
    private String classReferenceFormat;
    private final DynamicCallManager DCManager = new DynamicCallManager();
    // modified by adrninistrator
    private List<MethodCallDto> methodCalls = new ArrayList<>();
    // modified end

    // added by adrninistrator
    private Map<String, Set<String>> calleeMethodMap;
    private Map<String, Boolean> runnableImplClassMap;
    private Map<String, Boolean> callableImplClassMap;
    private Map<String, Boolean> threadChildClassMap;
    private Map<String, Set<String>> methodAnnotationMap;
    // added end

    public ClassVisitor(JavaClass jc) {
        clazz = jc;
        constants = new ConstantPoolGen(clazz.getConstantPool());
        classReferenceFormat = "C:" + clazz.getClassName() + " %s";
    }

    @Override
    public void visitJavaClass(JavaClass jc) {
        jc.getConstantPool().accept(this);
        Method[] methods = jc.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            // modified by adrninistrator
            DCManager.clearLambdaMethodNameSet();
            DCManager.retrieveCalls(method, jc);
            DCManager.linkCalls(method);

            Set<String> lambdaMethodNameSet = DCManager.getLambdaMethodNameSet();
            // record lambda method call
            recordLambdaMethodCall(lambdaMethodNameSet, jc, method);
            // modified end

            method.accept(this);
        }
    }

    @Override
    public void visitConstantPool(ConstantPool constantPool) {
        for (int i = 0; i < constantPool.getLength(); i++) {
            Constant constant = constantPool.getConstant(i);
            if (constant == null)
                continue;
            if (constant.getTag() == 7) {
                String referencedClass =
                        constantPool.constantToString(constant);

                // modified by adrninistrator
                MethodCallDto methodCallDto = MethodCallDto.genInstance(String.format(classReferenceFormat, referencedClass),
                        Constants.NONE_LINE_NUMBER);
                methodCalls.add(methodCallDto);
                // modified end
            }
        }
    }

    @Override
    public void visitMethod(Method method) {
        MethodGen mg = new MethodGen(method, clazz.getClassName(), constants);
		MethodVisitorExtended visitor = new MethodVisitorExtended(mg, clazz);
        visitor.setCalleeMethodMap(calleeMethodMap);
        visitor.setRunnableImplClassMap(runnableImplClassMap);
        visitor.setCallableImplClassMap(callableImplClassMap);
        visitor.setThreadChildClassMap(threadChildClassMap);
        visitor.setMethodAnnotationMap(methodAnnotationMap);
        visitor.beforeStart();
        List<MethodCallDto> methodCallDtos = visitor.start();
        methodCalls.addAll(methodCallDtos);
    }

    public ClassVisitor start() {
        visitJavaClass(clazz);
        return this;
    }

    public List<MethodCallDto> methodCalls() {
        return this.methodCalls;
    }

    // added by adrninistrator
    public void setCalleeMethodMap(Map<String, Set<String>> calleeMethodMap) {
        this.calleeMethodMap = calleeMethodMap;
    }

    public void setRunnableImplClassMap(Map<String, Boolean> runnableImplClassMap) {
        this.runnableImplClassMap = runnableImplClassMap;
    }

    public void setCallableImplClassMap(Map<String, Boolean> callableImplClassMap) {
        this.callableImplClassMap = callableImplClassMap;
    }

    public void setThreadChildClassMap(Map<String, Boolean> threadChildClassMap) {
        this.threadChildClassMap = threadChildClassMap;
    }

    public void setMethodAnnotationMap(Map<String, Set<String>> methodAnnotationMap) {
        this.methodAnnotationMap = methodAnnotationMap;
    }

    // record lambda method call
    private void recordLambdaMethodCall(Set<String> lambdaMethodNameSet, JavaClass jc, Method origMethod) {
        if (lambdaMethodNameSet.isEmpty()) {
            return;
        }
        for (String lambdaMethodName : lambdaMethodNameSet) {
            Method[] methods = jc.getMethods();
            for (Method method : methods) {
                if (!lambdaMethodName.equals(method.getName())) {
                    continue;
                }
                String lambdaMethodCall = String.format("M:%s:%s%s (%s)%s:%s%s", jc.getClassName(), origMethod.getName(),
                        CommonUtil.argumentList(origMethod.getArgumentTypes()),
                        CallTypeEnum.CTE_LM.getType(), jc.getClassName(), lambdaMethodName, CommonUtil.argumentList(method.getArgumentTypes()));

                MethodCallDto methodCallDto = MethodCallDto.genInstance(lambdaMethodCall, Constants.DEFAULT_LINE_NUMBER);
                methodCalls.add(methodCallDto);
                break;
            }
        }
    }

    // added end
}

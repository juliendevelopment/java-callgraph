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
import gr.gousiosg.javacg.dto.*;
import gr.gousiosg.javacg.enums.CallTypeEnum;
import gr.gousiosg.javacg.util.CommonUtil;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Constructs a callgraph out of a JAR archive. Can combine multiple archives
 * into a single call graph.
 *
 * @author Georgios Gousios <gousiosg@gmail.com>
 */
public class JCallGraph {

    public static final int INIT_SIZE_100 = 100;
    public static final int INIT_SIZE_500 = 500;
    public static final int INIT_SIZE_1000 = 1000;

    // added by adrninistrator
    private static Map<String, Set<String>> calleeMethodMapGlobal;
    private static Map<String, ClassInterfaceMethodInfo> classInterfaceMethodInfoMap;
    private static Map<String, List<String>> interfaceMethodWithArgsMap;
    private static Map<String, Boolean> runnableImplClassMap;
    private static Map<String, Boolean> callableImplClassMap;
    private static Map<String, Boolean> threadChildClassMap;
    private static Map<String, Set<String>> methodAnnotationMap;
    private static Set<String> extendsClassesSet;
    private static Map<String, ExtendsClassMethodInfo> extendsClassMethodInfoMap;
    private static Map<String, List<String>> childrenClassInfoMap;

    private static final String RUNNABLE_CLASS_NAME = Runnable.class.getName();
    private static final String CALLABLE_CLASS_NAME = Callable.class.getName();
    private static final String THREAD_CLASS_NAME = Thread.class.getName();

    private static int jarNum = 0;
    // added end

    public static void main(String[] args) {
        run(args);
    }

    public static boolean run(String[] args) {

        // added by adrninistrator
        String outputFilePath = System.getProperty("output.file");
        if (outputFilePath == null || outputFilePath.isEmpty()) {
            System.err.println("please use \"-Doutput.file=xxx\" to specify the output file");
            return false;
        }
        // added end

        // modified by adrninistrator
        Map<String, Integer> filePathSet = new HashMap<>(args.length);

        String annotationOutputFilePath = outputFilePath + "-annotation.txt";
        System.out.println("write method annotation information to file: " + annotationOutputFilePath);

        try (BufferedWriter resultWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(outputFilePath))));
             BufferedWriter annotationOut = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(annotationOutputFilePath),
                     StandardCharsets.UTF_8))) {
            for (String arg : args) {
                String jarFilePath = CommonUtil.getCanonicalPath(arg);
                if (jarFilePath == null) {
                    System.err.println("getCanonicalPath fail: " + arg);
                    return false;
                }

                if (filePathSet.get(jarFilePath) != null) {
                    System.out.println(arg + " skip jar file: " + jarFilePath);
                    continue;
                }

                filePathSet.put(jarFilePath, ++jarNum);

                System.out.println(arg + " handle jar file: " + jarFilePath);
                File f = new File(jarFilePath);

                if (!f.exists()) {
                    System.err.println("Jar file " + jarFilePath + " does not exist");
                }

                try (JarFile jar = new JarFile(f)) {
                    // added by adrninistrator
                    writeResult(resultWriter, "J:" + jarNum + " " + jarFilePath);
                    writeResult(resultWriter, Constants.NEW_LINE);

                    init();

                    // pre handle classes
                    if (!preHandleClasses(jarFilePath, f)) {
                        return false;
                    }
                    // added end

                    for (Enumeration<JarEntry> enumeration = jar.entries(); enumeration.hasMoreElements(); ) {
                        JarEntry jarEntry = enumeration.nextElement();
                        if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".class")) {
                            ClassParser cp = new ClassParser(jarFilePath, jarEntry.getName());
                            JavaClass javaClass = cp.parse();

                            System.out.println("handle class: " + javaClass.getClassName());

                            if (javaClass.isClass() && extendsClassesSet.contains(javaClass.getClassName())) {
                                findExtendsClassesInfo(javaClass);
                            }

                            ClassVisitor classVisitor = new ClassVisitor(javaClass);
                            classVisitor.setCalleeMethodMap(calleeMethodMapGlobal);
                            classVisitor.setRunnableImplClassMap(runnableImplClassMap);
                            classVisitor.setCallableImplClassMap(callableImplClassMap);
                            classVisitor.setThreadChildClassMap(threadChildClassMap);
                            classVisitor.setMethodAnnotationMap(methodAnnotationMap);
                            classVisitor.start();

                            // modified by adrninistrator
                            List<MethodCallDto> methodCalls = classVisitor.methodCalls();
                            for (MethodCallDto methodCallDto : methodCalls) {
                                writeResult(resultWriter, methodCallDto.getMethodCall());
                                if (methodCallDto.getSourceLine() != Constants.NONE_LINE_NUMBER) {
                                    writeResult(resultWriter, " " + methodCallDto.getSourceLine());
                                    writeResult(resultWriter, " " + jarNum);
                                }
                                writeResult(resultWriter, Constants.NEW_LINE);
                            }
                            // modified end
                        }
                    }

                    // added by adrninistrator
                    // add abstract method in interface into abstract super class
                    if (!addInterfaceMethod4SuperClass()) {
                        return false;
                    }

                    // record super class call children method and child class call super method
                    if (!recordExtendsClassMethod(resultWriter)) {
                        return false;
                    }

                    // record interface call implementation class method
                    recordInterfaceCallClassMethod(resultWriter);

                    // record method annotation information
                    recordMethodAnnotationInfo(annotationOut);
                    // added end
                }
            }

            return true;
        } catch (IOException e) {
            System.err.println("Error while processing jar: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        // modified end
    }

    // added by adrninistrator
    private static void init() {
        // calleeMethodMapGlobal used for all jar files, only initialize once
        if (calleeMethodMapGlobal == null) {
            calleeMethodMapGlobal = new HashMap<>(INIT_SIZE_1000);
        }
        classInterfaceMethodInfoMap = new HashMap<>(INIT_SIZE_100);
        interfaceMethodWithArgsMap = new HashMap<>(INIT_SIZE_100);
        runnableImplClassMap = new HashMap<>(INIT_SIZE_100);
        callableImplClassMap = new HashMap<>(INIT_SIZE_100);
        threadChildClassMap = new HashMap<>(INIT_SIZE_100);
        methodAnnotationMap = new HashMap<>(INIT_SIZE_100);
        extendsClassesSet = new HashSet<>(INIT_SIZE_500);
        extendsClassMethodInfoMap = new HashMap<>(INIT_SIZE_500);
        childrenClassInfoMap = new HashMap<>(INIT_SIZE_500);
    }

    // add abstract method in interface into abstract super class
    private static boolean addInterfaceMethod4SuperClass() {
        for (Map.Entry<String, List<String>> childrenClassInfoEntry : childrenClassInfoMap.entrySet()) {
            String superClassName = childrenClassInfoEntry.getKey();
            ExtendsClassMethodInfo extendsClassMethodInfo = extendsClassMethodInfoMap.get(superClassName);
            if (extendsClassMethodInfo == null) {
                // class in other jar can be found, but can't find its methods
                continue;
            }

            if (!extendsClassMethodInfo.isAbstractClass()) {
                continue;
            }

            ClassInterfaceMethodInfo classInterfaceMethodInfo = classInterfaceMethodInfoMap.get(superClassName);
            if (classInterfaceMethodInfo == null) {
                continue;
            }

            Map<String, MethodAttribute> methodAttributeMap = extendsClassMethodInfo.getMethodAttributeMap();
            MethodAttribute methodAttribute = new MethodAttribute();
            methodAttribute.setAbstractMethod(true);
            methodAttribute.setPublicMethod(true);
            methodAttribute.setProtectedMethod(false);

            List<String> interfaceNameList = classInterfaceMethodInfo.getInterfaceNameList();
            for (String interfaceName : interfaceNameList) {
                List<String> interfaceMethodWithArgsList = interfaceMethodWithArgsMap.get(interfaceName);
                if (interfaceMethodWithArgsList == null) {
                    continue;
                }

                for (String interfaceMethodWithArgs : interfaceMethodWithArgsList) {
                    if (methodAttributeMap.get(interfaceMethodWithArgs) == null) {
                        methodAttributeMap.put(interfaceMethodWithArgs, methodAttribute);
                    }
                }
            }
        }

        return true;
    }

    // record super class call children method and child class call super method
    private static boolean recordExtendsClassMethod(BufferedWriter resultWriter) throws IOException {
        Set<String> topSuperClassNameSet = new HashSet<>();

        // get top super class name
        for (Map.Entry<String, ExtendsClassMethodInfo> extendsClassMethodInfoEntry : extendsClassMethodInfoMap.entrySet()) {
            String className = extendsClassMethodInfoEntry.getKey();
            ExtendsClassMethodInfo extendsClassMethodInfo = extendsClassMethodInfoEntry.getValue();
            String superClassName = extendsClassMethodInfo.getSuperClassName();
            if (superClassName.startsWith("java.")) {
                topSuperClassNameSet.add(className);
            }
        }

        for (String topSuperClassName : topSuperClassNameSet) {
            // handle one top super class
            if (!handleOneTopSuperClass(topSuperClassName, resultWriter)) {
                return false;
            }
        }
        return true;
    }

    // handle one top super class
    private static boolean handleOneTopSuperClass(String topSuperClassName, BufferedWriter resultWriter) throws IOException {
        System.out.println("handleOneTopSuperClass: " + topSuperClassName);
        List<TmpNode4ExtendsClassMethod> tmpNodeList = new ArrayList<>();
        int currentLevel = 0;

        // init node list
        TmpNode4ExtendsClassMethod topNode = TmpNode4ExtendsClassMethod.genInstance(topSuperClassName, -1);
        tmpNodeList.add(topNode);

        // begin loop
        while (true) {
            TmpNode4ExtendsClassMethod currentNode = tmpNodeList.get(currentLevel);
            List<String> childrenClassInfoList = childrenClassInfoMap.get(currentNode.getSuperClassName());
            if (childrenClassInfoList == null) {
                System.err.println("can't find top super class: " + currentNode.getSuperClassName());
                return false;
            }

            int currentChildClassIndex = currentNode.getChildClassIndex() + 1;
            if (currentChildClassIndex >= childrenClassInfoList.size()) {
                if (currentLevel == 0) {
                    return true;
                }
                currentLevel--;
                continue;
            }

            // handle current child class
            String childClassName = childrenClassInfoList.get(currentChildClassIndex);

            // handle super and child class call method
            if (!handleSuperAndChildClass(currentNode.getSuperClassName(), childClassName, resultWriter)) {
                return false;
            }

            // handle next child class
            currentNode.setChildClassIndex(currentChildClassIndex);

            List<String> nextChildClassList = childrenClassInfoMap.get(childClassName);
            if (nextChildClassList == null) {
                // current child has no child
                continue;
            }

            // current child has children
            currentLevel++;

            if (currentLevel + 1 > tmpNodeList.size()) {
                TmpNode4ExtendsClassMethod nextNode = TmpNode4ExtendsClassMethod.genInstance(childClassName, -1);
                tmpNodeList.add(nextNode);
            } else {
                TmpNode4ExtendsClassMethod nextNode = tmpNodeList.get(currentLevel);
                nextNode.setSuperClassName(childClassName);
                nextNode.setChildClassIndex(-1);
            }
        }
    }

    // handle super and child class call method
    private static boolean handleSuperAndChildClass(String superClassName, String childClassName, BufferedWriter resultWriter) throws IOException {
        ExtendsClassMethodInfo superClassMethodInfo = extendsClassMethodInfoMap.get(superClassName);
        if (superClassMethodInfo == null) {
            System.err.println("can't find information for super class: " + superClassName);
            return false;
        }

        ExtendsClassMethodInfo childClassMethodInfo = extendsClassMethodInfoMap.get(childClassName);
        if (childClassMethodInfo == null) {
            System.err.println("can't find information for child class: " + childClassName);
            return false;
        }

        Map<String, MethodAttribute> superMethodAttributeMap = superClassMethodInfo.getMethodAttributeMap();
        Map<String, MethodAttribute> childMethodAttributeMap = childClassMethodInfo.getMethodAttributeMap();

        for (Map.Entry<String, MethodAttribute> superMethodAttributeEntry : superMethodAttributeMap.entrySet()) {
            String superMethodWithArgs = superMethodAttributeEntry.getKey();
            MethodAttribute superMethodAttribute = superMethodAttributeEntry.getValue();
            if (superMethodAttribute.isAbstractMethod()) {
                // super abstract method
                MethodAttribute childMethodAttribute = childMethodAttributeMap.get(superMethodWithArgs);
                if (childMethodAttribute == null) {
                    childMethodAttributeMap.put(superMethodWithArgs, superMethodAttribute);
                }
                // add super class call child class method
                String superCallChildClassMethod = String.format("M:%s:%s (%s)%s:%s %d", superClassName, superMethodWithArgs,
                        CallTypeEnum.CTE_SCC.getType(), childClassName, superMethodWithArgs, Constants.DEFAULT_LINE_NUMBER);
                writeResult(resultWriter, superCallChildClassMethod);
                writeResult(resultWriter, " " + jarNum);
                writeResult(resultWriter, Constants.NEW_LINE);
                continue;
            }
            if (superMethodAttribute.isPublicMethod() || superMethodAttribute.isProtectedMethod()) {
                // super public/protected not abstract method
                if (childMethodAttributeMap.get(superMethodWithArgs) != null) {
                    continue;
                }
                Set<String> childCalleeMethodWithArgsSet = calleeMethodMapGlobal.get(childClassName);
                if (!childClassMethodInfo.isAbstractClass() &&
                        (childCalleeMethodWithArgsSet == null || !childCalleeMethodWithArgsSet.contains(superMethodWithArgs))) {
                    continue;
                }

                childMethodAttributeMap.put(superMethodWithArgs, superMethodAttribute);

                // add child class call super class method
                String childCallSuperClassMethod = String.format("M:%s:%s (%s)%s:%s %d", childClassName, superMethodWithArgs,
                        CallTypeEnum.CTE_CCS.getType(), superClassName, superMethodWithArgs, Constants.DEFAULT_LINE_NUMBER);
                writeResult(resultWriter, childCallSuperClassMethod);
                writeResult(resultWriter, " " + jarNum);
                writeResult(resultWriter, Constants.NEW_LINE);
            }
        }
        return true;
    }

    // record interface call implementation class method
    private static void recordInterfaceCallClassMethod(BufferedWriter resultWriter) throws IOException {
        if (classInterfaceMethodInfoMap.isEmpty() || interfaceMethodWithArgsMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ClassInterfaceMethodInfo> classMethodInfo : classInterfaceMethodInfoMap.entrySet()) {
            String className = classMethodInfo.getKey();
            ClassInterfaceMethodInfo classInterfaceMethodInfo = classMethodInfo.getValue();
            List<String> interfaceNameList = classInterfaceMethodInfo.getInterfaceNameList();

            /*
                find the same method both in interface and implementation class
                and the method should be used
             */
            for (String interfaceName : interfaceNameList) {
                Set<String> calleeMethodWithArgsSet = calleeMethodMapGlobal.get(interfaceName);
                if (calleeMethodWithArgsSet == null) {
                    continue;
                }

                List<String> interfaceMethodWithArgsList = interfaceMethodWithArgsMap.get(interfaceName);
                if (interfaceMethodWithArgsList == null || interfaceMethodWithArgsList.isEmpty()) {
                    continue;
                }

                List<String> classMethodWithArgsList = classInterfaceMethodInfo.getMethodWithArgsList();
                for (String classMethodWithArgs : classMethodWithArgsList) {
                    if (!interfaceMethodWithArgsList.contains(classMethodWithArgs) || !calleeMethodWithArgsSet.contains(classMethodWithArgs)) {
                        continue;
                    }

                    String interfaceCallClassMethod = String.format("M:%s:%s (%s)%s:%s %d", interfaceName, classMethodWithArgs,
                            CallTypeEnum.CTE_ITF.getType(), className, classMethodWithArgs, Constants.DEFAULT_LINE_NUMBER);
                    writeResult(resultWriter, interfaceCallClassMethod);
                    writeResult(resultWriter, " " + jarNum);
                    writeResult(resultWriter, Constants.NEW_LINE);
                }
            }
        }
    }

    private static List<String> genImplClassMethodWithArgs(Method[] methods) {
        List<String> methodInfoList = new ArrayList<>(methods.length);
        for (Method method : methods) {
            String methodName = method.getName();
            // ignore "<init>" and "<clinit>"
            if (!methodName.startsWith("<") && method.isPublic() && !method.isAbstract() && !method.isStatic()) {
                methodInfoList.add(methodName + CommonUtil.argumentList(method.getArgumentTypes()));
            }
        }
        return methodInfoList;
    }

    private static List<String> genInterfaceAbstractMethodWithArgs(Method[] methods) {
        List<String> methodInfoList = new ArrayList<>(methods.length);
        for (Method method : methods) {
            if (method.isAbstract()) {
                methodInfoList.add(method.getName() + CommonUtil.argumentList(method.getArgumentTypes()));
            }
        }
        return methodInfoList;
    }

    // pre handle classes
    private static boolean preHandleClasses(String jarFilePath, File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            for (Enumeration<JarEntry> enumeration = jar.entries(); enumeration.hasMoreElements(); ) {
                JarEntry jarEntry = enumeration.nextElement();
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".class")) {
                    ClassParser cp = new ClassParser(jarFilePath, jarEntry.getName());
                    JavaClass javaClass = cp.parse();

                    String className = javaClass.getClassName();
                    if (javaClass.isClass()) {
                        // pre handle class
                        preHandleClass(javaClass);
                    } else if (javaClass.isInterface()) {
                        Method[] methods = javaClass.getMethods();
                        if (methods != null && methods.length > 0 &&
                                interfaceMethodWithArgsMap.get(className) == null) {
                            List<String> interfaceMethodWithArgsList = genInterfaceAbstractMethodWithArgs(methods);
                            interfaceMethodWithArgsMap.put(className, interfaceMethodWithArgsList);
                        }
                    }

                    // get super and children class
                    String superClassName = javaClass.getSuperclassName();
                    if (THREAD_CLASS_NAME.equals(superClassName)) {
                        // find Thread child class
                        threadChildClassMap.put(javaClass.getClassName(), Boolean.FALSE);
                    }

                    if (!superClassName.startsWith("java.")) {
                        extendsClassesSet.add(javaClass.getClassName());
                        extendsClassesSet.add(superClassName);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // pre handle class
    private static void preHandleClass(JavaClass javaClass) {
        String className = javaClass.getClassName();
        String[] interfaceNames = javaClass.getInterfaceNames();
        Method[] methods = javaClass.getMethods();

        if (interfaceNames != null && interfaceNames.length > 0 &&
                methods != null && methods.length > 0 &&
                classInterfaceMethodInfoMap.get(className) == null) {
            ClassInterfaceMethodInfo classInterfaceMethodInfo = new ClassInterfaceMethodInfo();

            List<String> interfaceNameList = new ArrayList<>(interfaceNames.length);
            interfaceNameList.addAll(Arrays.asList(interfaceNames));

            List<String> implClassMethodWithArgsList = genImplClassMethodWithArgs(methods);
            classInterfaceMethodInfo.setInterfaceNameList(interfaceNameList);
            classInterfaceMethodInfo.setMethodWithArgsList(implClassMethodWithArgsList);

            classInterfaceMethodInfoMap.put(className, classInterfaceMethodInfo);

            if (!javaClass.isAbstract()) {
                if (interfaceNameList.contains(RUNNABLE_CLASS_NAME)) {
                    // find Runnable impl classes
                    runnableImplClassMap.put(className, Boolean.FALSE);
                }
                if (interfaceNameList.contains(CALLABLE_CLASS_NAME)) {
                    // find Callable impl classes
                    callableImplClassMap.put(className, Boolean.FALSE);
                }
            }
        }
    }

    private static void findExtendsClassesInfo(JavaClass javaClass) {
        String className = javaClass.getClassName();
        if (extendsClassMethodInfoMap.get(className) != null) {
            return;
        }

        String superClassName = javaClass.getSuperclassName();
        if (!superClassName.startsWith("java.")) {
            // cache super class and it's children class, ignore super class start with "java."
            List<String> childrenClassInfoList = childrenClassInfoMap.get(superClassName);
            if (childrenClassInfoList == null) {
                List<String> newChildrenClassInfoList = new ArrayList<>();
                newChildrenClassInfoList.add(className);
                childrenClassInfoMap.put(superClassName, newChildrenClassInfoList);
            } else {
                childrenClassInfoList.add(className);
            }
        }

        // cache the method information of current class
        ExtendsClassMethodInfo extendsClassMethodInfo = new ExtendsClassMethodInfo();
        extendsClassMethodInfo.setAbstractClass(javaClass.isAbstract());
        extendsClassMethodInfo.setSuperClassName(superClassName);
        Map<String, MethodAttribute> methodAttributeMap = new HashMap<>();

        Method[] methods = javaClass.getMethods();
        if (methods != null && methods.length > 0) {
            for (Method method : methods) {
                String methodName = method.getName();
                if (!methodName.startsWith("<") && !method.isStatic() && (
                        method.isAbstract() ||
                                (!method.isAbstract() && (method.isPublic() || method.isProtected()))
                )) {
                    MethodAttribute methodAttribute = new MethodAttribute();
                    methodAttribute.setAbstractMethod(method.isAbstract());
                    methodAttribute.setPublicMethod(method.isPublic());
                    methodAttribute.setProtectedMethod(method.isProtected());

                    String methodWithArgs = methodName + CommonUtil.argumentList(method.getArgumentTypes());
                    methodAttributeMap.put(methodWithArgs, methodAttribute);
                }
            }
        }
        extendsClassMethodInfo.setMethodAttributeMap(methodAttributeMap);
        extendsClassMethodInfoMap.put(className, extendsClassMethodInfo);
    }

    // record method annotation information
    private static void recordMethodAnnotationInfo(BufferedWriter out) throws IOException {
        for (Map.Entry<String, Set<String>> entry : methodAnnotationMap.entrySet()) {
            String fullMethod = entry.getKey();
            Set<String> annotationSet = entry.getValue();
            for (String annotation : annotationSet) {
                String methodWithAnnotation = fullMethod + " " + annotation + Constants.NEW_LINE;
                out.write(methodWithAnnotation);
            }
        }
    }

    // write data to result file
    private static void writeResult(BufferedWriter resultWriter, String data) throws IOException {
        resultWriter.write(data);
    }
    // added end
}

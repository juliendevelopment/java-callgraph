package gr.gousiosg.javacg.stat;

import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.BootstrapMethod;
import org.apache.bcel.classfile.BootstrapMethods;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantMethodHandle;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKEDYNAMIC;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;

import gr.gousiosg.javacg.util.CommonUtil;

public class MethodVisitorExtended extends MethodVisitor {
	private final MethodGen mg;
	private final ConstantPoolGen cp;
	private final InstructionList il;
	private final JavaClass visitedClass;

	public MethodVisitorExtended(MethodGen m, JavaClass jc) {
		super(m, jc);
		this.mg = m;
		this.cp = m.getConstantPool();
		this.il = m.getInstructionList();
		this.visitedClass = jc;
	}

	@Override
	public void visitINVOKEVIRTUAL(INVOKEVIRTUAL i) {
		String methodName = i.getMethodName(cp);
		String className = i.getReferenceType(cp).toString();

		// Check if this is a map() call on Optional
		if ("map".equals(methodName) && "java.util.Optional".equals(className)) {
			// Get the instruction handle for current instruction
			InstructionHandle ih = findCurrentInstructionHandle(i);
			if (ih != null && ih.getPrev() != null) {
				// Look at previous instruction to find lambda/method reference creation
				InstructionHandle prevIh = ih.getPrev();
				Instruction prevInst = prevIh.getInstruction();

				if (prevInst instanceof INVOKEDYNAMIC) {
					INVOKEDYNAMIC invokedynamic = (INVOKEDYNAMIC) prevInst;

					// Get the bootstrap method from class attributes
					for (Attribute attr : visitedClass.getAttributes()) {
						if (attr instanceof BootstrapMethods) {
							BootstrapMethods bootstrapMethods = (BootstrapMethods) attr;

							// Get the ConstantInvokeDynamic info
							ConstantInvokeDynamic cid =
									(ConstantInvokeDynamic) cp.getConstant(invokedynamic.getIndex());

							// Get the specific bootstrap method
							BootstrapMethod bm = bootstrapMethods.getBootstrapMethods()[cid.getBootstrapMethodAttrIndex()];

							// Get the method handle from bootstrap arguments
							if (bm.getBootstrapArguments().length >= 2) {
								int methodRefIndex = bm.getBootstrapArguments()[1];
								Constant c = cp.getConstant(methodRefIndex);

								if (c instanceof ConstantMethodHandle) {
									ConstantMethodHandle cmh = (ConstantMethodHandle) c;
									int refIndex = cmh.getReferenceIndex();
									ConstantCP methodRef = (ConstantCP) cp.getConstant(refIndex);

									// Get the actual referenced class and method
									String refClassName = methodRef.getClass(cp.getConstantPool()).replace('/', '.');
									ConstantNameAndType nt = (ConstantNameAndType)
											cp.getConstant(methodRef.getNameAndTypeIndex());
									String refMethodName = ((ConstantUtf8)
																	cp.getConstant(nt.getNameIndex())).getBytes();

									// Add both the map() call and the actual method reference
									addMethodCalls("M", className, methodName, CommonUtil.argumentList(i.getArgumentTypes(cp)));
									addMethodCalls("M", refClassName, refMethodName, "()");
									return;
								}
							}
						}
					}
				}
			}
		}
		// Default handling if not a method reference
		addMethodCalls("M", className, methodName, CommonUtil.argumentList(i.getArgumentTypes(cp)));
	}

	private InstructionHandle findCurrentInstructionHandle(Instruction inst) {
		if (il == null) return null;

		for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
			if (ih.getInstruction() == inst) {
				return ih;
			}
		}
		return null;
	}
}
package edu.washington.cse.instrumentation.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

import edu.columbia.cs.psl.phosphor.TaintUtils;

class MatchPattern<T extends Enum<T>> {
	final List<AbstractInsnNode> pattern = new ArrayList<>();
	final T tag;
	public MatchPattern(T tag, AbstractInsnNode... patt) {
		pattern.addAll(Arrays.asList(patt));
		this.tag = tag;
	}
	
	public boolean filterField(int opcode, String owner, String name, String desc, List<FieldInsnNode> fields) {
		if(fields.size() == 0) {
//			System.out.println("DEBUG: --> " + name);
			return name.endsWith(TaintUtils.TAINT_FIELD);
		}
		assert fields.size() == 1;
		FieldInsnNode taintField = fields.get(0);
		return opcode == taintField.getOpcode()
			&& (name + TaintUtils.TAINT_FIELD).equals(taintField.name)
			&& taintField.desc.equals(TaintUtils.getShadowTaintType(desc))
			&& owner.equals(taintField.owner);
	}
	/*{
		if(fields.size() == 0) {
			return owner.equals(this.volatileFieldWriteMV.className) 
				&& this.volatileFieldWriteMV.volatileFields.contains(name);
		}
		assert fields.size() == 1;
		FieldInsnNode fi = fields.get(0);
		return fi.getOpcode() == opcode &&
			fi.owner.equals(owner) &&
			name.equals(fi.name + TaintUtils.TAINT_FIELD) &&
			desc.equals(TaintUtils.getShadowTaintType(fi.desc));
	}*/
}
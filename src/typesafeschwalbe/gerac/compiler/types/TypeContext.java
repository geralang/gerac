
package typesafeschwalbe.gerac.compiler.types;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.UnionFind;

public class TypeContext {

    private int nextVarId;
    public final UnionFind<DataType<TypeVariable>> substitutes;

    public TypeContext() {
        this.nextVarId = 0;
        this.substitutes = new UnionFind<>();
    }

    @Override
    public String toString() {
        return this.substitutes.toString();
    }

    public int varVount() {
        return this.nextVarId;
    }

    public TypeVariable makeVar() {
        return this.makeVar(new DataType<>(
            DataType.Type.ANY, null, Optional.empty()
        ));
    }

    public TypeVariable makeVar(DataType<TypeVariable> value) {
        int id = this.nextVarId;
        this.nextVarId += 1;
        this.substitutes.add(value);
        return new TypeVariable(id);
    }

    public DataType<TypeVariable> get(TypeVariable var) {
        return this.get(var.id);
    }

    public DataType<TypeVariable> get(int id) {
        return this.substitutes.get(id);
    }

    public void set(TypeVariable var, DataType<TypeVariable> value) {
        this.substitutes.set(var.id, value);
    }

    public TypeVariable copyVar(TypeVariable var) {
        return this.copyVar(var, new HashMap<>());
    }

    private TypeVariable copyVar(
        TypeVariable var, Map<Integer, TypeVariable> copied
    ) {
        int root = this.substitutes.find(var.id);
        TypeVariable existing = copied.get(root);
        if(existing != null) {
            return existing;
        }
        TypeVariable copy = this.makeVar();
        copied.put(root, copy);
        DataType<TypeVariable> value = this.substitutes.get(root)
            .map(v -> this.copyVar(v, copied));
        this.substitutes.set(copy.id, value);
        return copy;
    }

    private static record EqualityEncounter(int rootA, int rootB) {}

    public boolean deepEquals(TypeVariable a, TypeVariable b) {
        return this.deepEquals(a, b, new HashSet<>());
    }

    public boolean deepEquals(int a_id, int b_id) {
        return this.deepEquals(
            new TypeVariable(a_id), new TypeVariable(b_id), new HashSet<>()
        );
    }

    private boolean deepEquals(
        TypeVariable a, TypeVariable b, Set<EqualityEncounter> encountered
    ) {
        int rootA = this.substitutes.find(a.id);
        int rootB = this.substitutes.find(b.id);
        EqualityEncounter encounter = new EqualityEncounter(rootA, rootB);
        if(encountered.contains(encounter)) { return true; }
        encountered.add(encounter);
        DataType<TypeVariable> valA = this.substitutes.get(rootA);
        DataType<TypeVariable> valB = this.substitutes.get(rootB);
        boolean equals = this.deepEquals(valA, valB, encountered);
        encountered.remove(encounter);
        return equals;
    }

    private boolean deepEquals(
        DataType<TypeVariable> a, DataType<TypeVariable> b, 
        Set<EqualityEncounter> encountered
    ) {
        if(a.type != b.type) { return false; }
        switch(a.type) {
            case ANY:
            case NUMERIC:
            case INDEXED:
            case REFERENCED:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return true;
            }
            case ARRAY: {
                DataType.Array<TypeVariable> dataA = a.getValue();
                DataType.Array<TypeVariable> dataB = b.getValue();
                return this.deepEquals(
                    dataA.elementType(), dataB.elementType(), encountered
                );
            }
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject<TypeVariable> dataA = a.getValue();
                DataType.UnorderedObject<TypeVariable> dataB = b.getValue();
                Set<String> members = new HashSet<>();
                members.addAll(dataA.memberTypes().keySet());
                members.addAll(dataB.memberTypes().keySet());
                for(String member: members) {
                    if(!dataA.memberTypes().containsKey(member)) {
                        return false;
                    }
                    if(!dataB.memberTypes().containsKey(member)) {
                        return false;
                    }
                    TypeVariable mA = dataA.memberTypes().get(member);
                    TypeVariable mB = dataB.memberTypes().get(member);
                    if(!this.deepEquals(mA, mB, encountered)) {
                        return false;
                    }
                }
                return true;
            }
            case CLOSURE: {
                DataType.Closure<TypeVariable> dataA = a.getValue();
                DataType.Closure<TypeVariable> dataB = b.getValue();
                int aArgC = dataA.argumentTypes().size();
                int bArgC = dataB.argumentTypes().size();
                if(aArgC != bArgC) {
                    return false;
                }
                for(int argI = 0; argI < aArgC; argI += 1) {
                    TypeVariable argA = dataA.argumentTypes().get(argI);
                    TypeVariable argB = dataB.argumentTypes().get(argI);
                    if(!this.deepEquals(argA, argB, encountered)) {
                        return false;
                    }
                }
                return this.deepEquals(
                    dataA.returnType(), dataB.returnType(), encountered
                );
            }
            case UNION: {
                DataType.Union<TypeVariable> dataA = a.getValue();
                DataType.Union<TypeVariable> dataB = b.getValue();
                Set<String> variants = new HashSet<>();
                variants.addAll(dataA.variantTypes().keySet());
                variants.addAll(dataB.variantTypes().keySet());
                for(String variant: variants) {
                    if(!dataA.variantTypes().containsKey(variant)) {
                        return false;
                    }
                    if(!dataB.variantTypes().containsKey(variant)) {
                        return false;
                    }
                    TypeVariable vA = dataA.variantTypes().get(variant);
                    TypeVariable vB = dataB.variantTypes().get(variant);
                    if(!this.deepEquals(vA, vB, encountered)) {
                        return false;
                    }
                }
                return true;
            }
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
    }

}

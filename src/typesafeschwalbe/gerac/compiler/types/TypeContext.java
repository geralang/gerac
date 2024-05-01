
package typesafeschwalbe.gerac.compiler.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

}


package typesafeschwalbe.gerac.compiler.types;

import java.util.ArrayList;
import java.util.List;

import typesafeschwalbe.gerac.compiler.DisjointSet;

public class TypeContext {

    private int nextVarId;
    final List<List<TypeConstraint>> constraints;
    final DisjointSet<DataType<TypeVariable>> substitutes;

    public TypeContext() {
        this.nextVarId = 0;
        this.constraints = new ArrayList<>();
        this.substitutes = new DisjointSet<>();
    }

    public TypeVariable makeVar() {
        int id = this.nextVarId;
        this.nextVarId += 1;
        this.constraints.add(new ArrayList<>());
        return new TypeVariable(id);
    }

    public void add(TypeConstraint c, TypeVariable v) {
        this.constraints.get(v.id).add(c);
    }

}

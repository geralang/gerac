
package typesafeschwalbe.gerac.compiler.types;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.UnionFind;

public class TypeContext {

    private int nextVarId;
    final List<TypeConstraint> constraints;
    final UnionFind<DataType<TypeVariable>> substitutes;

    public TypeContext() {
        this.nextVarId = 0;
        this.constraints = new LinkedList<>();
        this.substitutes = new UnionFind<>();
    }

    public TypeVariable makeVar() {
        int id = this.nextVarId;
        this.nextVarId += 1;
        this.substitutes.add(new DataType<>(
            DataType.Type.ANY, null, Optional.empty()
        ));
        return new TypeVariable(id);
    }

    public void add(TypeConstraint c) {
        this.constraints.add(c);
    }

}

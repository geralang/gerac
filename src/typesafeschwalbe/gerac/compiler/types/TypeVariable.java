
package typesafeschwalbe.gerac.compiler.types;

public class TypeVariable {
 
    public final int id;

    TypeVariable(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "$" + this.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(this.id);
    }

    @Override
    public boolean equals(Object otherRaw) {
        if(!(otherRaw instanceof TypeVariable)) {
            return false; 
        }
        TypeVariable other = (TypeVariable) otherRaw;
        return this.id == other.id;
    }

}

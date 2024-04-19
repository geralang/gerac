
package typesafeschwalbe.gerac.compiler.types;

public class TypeVariable {
 
    final int id;

    TypeVariable(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "$" + this.id;
    }

}


package typesafeschwalbe.gerac.compiler.types;

public class TypeValue {

    public enum Type {
        ANY,
        PANIC,
        UNIT,
        BOOLEAN,
        NUMERIC,
        INTEGER,
        FLOAT,
        STRING,
        ARRAY,
        UNORDERED_OBJECT,
        ORDERED_OBJECT,
        CLOSURE,
        UNION
    }

}

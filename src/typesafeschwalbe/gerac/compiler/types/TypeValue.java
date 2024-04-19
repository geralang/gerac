
package typesafeschwalbe.gerac.compiler.types;

public class TypeValue {

    public enum Type {
        ANY,
        NUMERIC,
        UNIT,
        BOOLEAN,
        INTEGER,
        FLOAT,
        STRING,
        ARRAY,
        UNORDERED_OBJECT,
        ORDERED_OBJECT,
        CLOSURE,
        UNION;

        @Override
        public String toString() {
            switch(this) {
                case ANY: return "any type";
                case NUMERIC: return "a number";
                case UNIT: return "the unit value";
                case BOOLEAN: return "a boolean";
                case INTEGER: return "an integer";
                case FLOAT: return "a float";
                case STRING: return "a string";
                case ARRAY: return "an array";
                case UNORDERED_OBJECT: return "an object";
                case ORDERED_OBJECT: return "an object";
                case CLOSURE: return "a closure";
                case UNION: return "a union";
                default:
                    throw new RuntimeException("unhandled type!");
            }
        }
    }

}

package typesafeschwalbe.gerac.compiler.types;

import java.util.List;
import java.util.Map;

public class DataType<T> {

    public static record Array<T>(
        T elementType
    ) {}
    public static record UnorderedObject<T>(
        Map<String, T> memberTypes, boolean expandable
    ) {}
    public static record OrderedObject<T>(
        List<String> memberNames, List<T> memberTypes
    ) {}
    public static record Closure<T>(
        List<T> argumentTypes, T returnTypes
    ) {}
    public static record Union<T>(
        Map<String, T> variantTypes, boolean expandable
    ) {}

    public enum Type {
        ANY,              // = null
        NUMERIC,          // = null
        UNIT,             // = null
        BOOLEAN,          // = null
        INTEGER,          // = null
        FLOAT,            // = null
        STRING,           // = null
        ARRAY,            // Array
        UNORDERED_OBJECT, // UnorderedObject
        ORDERED_OBJECT,   // OrderedObject
        CLOSURE,          // Closure
        UNION;            // Union

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

    public final Type type;
    private final Object value;

    public DataType(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public <V> V getValue() {
        return (V) this.value;
    }

}

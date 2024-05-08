package typesafeschwalbe.gerac.compiler.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.Source;

public class DataType<T> {

    public interface DataTypeValue<T> {}

    public static record Array<T>(
        T elementType
    ) implements DataTypeValue<T> {}

    public static record UnorderedObject<T>(
        Map<String, T> memberTypes, boolean expandable, 
        Optional<List<String>> order
    ) implements DataTypeValue<T> {}

    public static record Closure<T>(
        List<T> argumentTypes, T returnType
    ) implements DataTypeValue<T> {}

    public static record Union<T>(
        Map<String, T> variantTypes, boolean expandable
    ) implements DataTypeValue<T> {}

    public enum Type {
        ANY,              // = null
        NUMERIC,          // = null
        INDEXED,          // = null
        REFERENCED,       // = null
        UNIT,             // = null
        BOOLEAN,          // = null
        INTEGER,          // = null
        FLOAT,            // = null
        STRING,           // = null
        ARRAY,            // Array
        UNORDERED_OBJECT, // UnorderedObject
        CLOSURE,          // Closure
        UNION;            // Union

        @Override
        public String toString() {
            switch(this) {
                case ANY: return "any type";
                case NUMERIC: return "an integer or a float";
                case INDEXED: return "a string or an array";
                case REFERENCED: return "an array or an object";
                case UNIT: return "the unit value";
                case BOOLEAN: return "a boolean";
                case INTEGER: return "an integer";
                case FLOAT: return "a float";
                case STRING: return "a string";
                case ARRAY: return "an array";
                case UNORDERED_OBJECT: return "an object";
                case CLOSURE: return "a closure";
                case UNION: return "a union variant";
                default:
                    throw new RuntimeException("unhandled type!");
            }
        }

        // public boolean isOneOf(Type... possibleTypes) {
        //     return this.isOneOf(List.of(possibleTypes));
        // }

        // public boolean isOneOf(List<Type> possibleTypes) {
        //     for(Type t: possibleTypes) {
        //         if(this == t) {
        //             return true;
        //         }
        //     }
        //     return false;
        // }

        public boolean isNumeric() {
            return this == NUMERIC
                || this == INTEGER 
                || this == FLOAT;
        }

        public boolean isIndexed() {
            return this == INDEXED
                || this == ARRAY 
                || this == STRING;
        }

        public boolean isReferenced() {
            return this == REFERENCED
                || this == UNORDERED_OBJECT 
                || this == ARRAY;
        }
    }

    public Type type;
    private Object value;
    public Optional<Source> source;

    public DataType(
        Type type, DataTypeValue<T> value, Optional<Source> source
    ) {
        this.type = type;
        this.value = value;
        this.source = source;
    }

    @SuppressWarnings("unchecked")
    public <V extends DataTypeValue<T>> V getValue() {
        return (V) this.value;
    }

    @Override
    public String toString() {
        if(this.value == null) {
            return this.type.name();
        }
        return this.value.toString();
    }

    void setValue(DataTypeValue<T> value) {
        this.value = value;
    }

    public <R> DataType<R> map(Function<T, R> f) {
        switch(this.type) {
            case ANY:
            case NUMERIC:
            case INDEXED:
            case REFERENCED:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return new DataType<>(this.type, null, this.source);
            }
            case ARRAY: {
                Array<T> data = this.getValue();
                return new DataType<>(
                    this.type, 
                    new Array<>(f.apply(data.elementType)),
                    this.source
                );
            }
            case UNORDERED_OBJECT: {
                UnorderedObject<T> data = this.getValue();
                Map<String, R> memberTypes = new HashMap<>();
                for(String member: data.memberTypes.keySet()) {
                    memberTypes.put(
                        member, f.apply(data.memberTypes.get(member))
                    );
                }
                return new DataType<>(
                    this.type,
                    new UnorderedObject<>(
                        memberTypes, data.expandable, data.order
                    ),
                    this.source
                );
            }
            case CLOSURE: {
                Closure<T> data = this.getValue();
                List<R> argumentTypes = data.argumentTypes
                    .stream().map(t -> f.apply(t)).toList();
                return new DataType<>(
                    this.type,
                    new Closure<>(
                        argumentTypes, f.apply(data.returnType)
                    ),
                    this.source
                );
            }
            case UNION: {
                Union<T> data = this.getValue();
                Map<String, R> variantTypes = new HashMap<>();
                for(String variant: data.variantTypes.keySet()) {
                    variantTypes.put(
                        variant, f.apply(data.variantTypes.get(variant))
                    );
                }
                return new DataType<>(
                    this.type,
                    new Union<>(variantTypes, data.expandable),
                    this.source
                );
            }
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
    }

}

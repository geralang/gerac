package typesafeschwalbe.gerac.compiler.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import typesafeschwalbe.gerac.compiler.Source;

public class DataType<T> {

    public interface DataTypeValue<T> {}

    public static record Array<T>(
        T elementType
    ) implements DataTypeValue<T> {}

    public static record UnorderedObject<T>(
        Map<String, T> memberTypes, boolean expandable
    ) implements DataTypeValue<T> {}

    public static record OrderedObject<T>(
        List<String> memberNames, List<T> memberTypes
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
                case UNION: return "a union variant";
                default:
                    throw new RuntimeException("unhandled type!");
            }
        }

        public boolean isOneOf(Type... possibleTypes) {
            for(Type t: possibleTypes) {
                if(this == t) {
                    return true;
                }
            }
            return false;
        }
    }

    public final Type type;
    private final Object value;
    public final Optional<Source> source;

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

    public <R> DataType<R> map(BiFunction<DataType<T>, T, R> f) {
        switch(this.type) {
            case ANY:
            case NUMERIC:
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
                    new Array<>(f.apply(this, data.elementType)),
                    this.source
                );
            }
            case UNORDERED_OBJECT: {
                UnorderedObject<T> data = this.getValue();
                Map<String, R> memberTypes = new HashMap<>();
                for(String member: data.memberTypes.keySet()) {
                    memberTypes.put(
                        member, f.apply(this, data.memberTypes.get(member))
                    );
                }
                return new DataType<>(
                    this.type,
                    new UnorderedObject<>(memberTypes, data.expandable),
                    this.source
                );
            }
            case ORDERED_OBJECT: {
                OrderedObject<T> data = this.getValue();
                List<R> memberTypes = data.memberTypes
                    .stream().map(t -> f.apply(this, t)).toList();
                return new DataType<>(
                    this.type,
                    new OrderedObject<>(data.memberNames, memberTypes),
                    this.source
                );
            }
            case CLOSURE: {
                Closure<T> data = this.getValue();
                List<R> argumentTypes = data.argumentTypes
                    .stream().map(t -> f.apply(this, t)).toList();
                return new DataType<>(
                    this.type,
                    new Closure<>(
                        argumentTypes, f.apply(this, data.returnType)
                    ),
                    this.source
                );
            }
            case UNION: {
                Union<T> data = this.getValue();
                Map<String, R> variantTypes = new HashMap<>();
                for(String variant: data.variantTypes.keySet()) {
                    variantTypes.put(
                        variant, f.apply(this, data.variantTypes.get(variant))
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

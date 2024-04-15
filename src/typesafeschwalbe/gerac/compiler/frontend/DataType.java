
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.Ref;
import typesafeschwalbe.gerac.compiler.Source;

public final class DataType {

    public static UnorderedObject makeUnordered(OrderedObject data) {
        Map<String, DataType> memberTypes = new HashMap<>();
        for(
            int memberI = 0;
            memberI < data.memberNames().size();
            memberI += 1
        ) {
            memberTypes.put(
                data.memberNames().get(memberI), 
                data.memberTypes().get(memberI)
            );
        }
        return new UnorderedObject(new Ref<>(memberTypes));
    }

    public static class UnknownOriginMarker {}

    public static record ClosureImpl(
        AstNode node,
        List<TypeChecker.CheckedBlock> context
    ) {}

    public static record Array(
        Ref<Ref<DataType>> elementType
    ) {}

    public static record UnorderedObject(
        Ref<Map<String, DataType>> memberTypes
    ) {}

    public static record OrderedObject(
        List<String> memberNames,
        List<DataType> memberTypes
    ) {}

    public static record Closure(
        Optional<List<DataType>> argumentTypes,
        Optional<DataType> returnType,
        List<ClosureImpl> unchecked
    ) {}

    public static record Union(
        Map<String, DataType> variants
    ) {}

    public enum Type {
        UNKNOWN,          // UnknownOriginMarker
        PANIC,            // = null
        UNIT,             // = null
        BOOLEAN,          // = null
        INTEGER,          // = null
        FLOAT,            // = null
        STRING,           // = null
        ARRAY,            // Array
        UNORDERED_OBJECT, // UnorderedObject
        ORDERED_OBJECT,   // OrderedObject
        CLOSURE,          // Closure
        UNION             // Union
    }

    private final Type type;
    private final Object value;
    // The origin of the type used for error reporting.
    // 'source' describes the location in the code where the
    // assumption originates from. Most of the time, this will be a literal.
    public final Source source;
    // 'age' describes the number of unififations that has been done
    // to reach this type. When unifiying the origin source and age from
    // the type with the largest origin age will be chosen. 
    public final long age;

    public DataType(Type type, Source source) {
        this.type = type;
        this.value = null;
        this.source = source;
        this.age = 0;
    }

    public DataType(Type type, Object value, Source source) {
        this.type = type;
        this.value = value;
        this.source = source;
        this.age = 0;
    }

    public DataType(Type type, Object value, Source source, long age) {
        this.type = type;
        this.value = value;
        this.source = source;
        this.age = age;
    }

    public boolean isType(DataType.Type... types) {
        if(this.type == Type.PANIC) {
            return true;
        }
        for(int i = 0; i < types.length; i += 1) {
            if(this.type == types[i]) { return true; }
        }
        return false;
    }

    public DataType.Type exactType() {
        return this.type;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) this.value;
    }

    public boolean isConcrete() {
        switch(this.type) {
            case PANIC:
            case UNKNOWN:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return true;
            }
            case ARRAY: {
                return this
                    .<Array>getValue().elementType.get().get().isConcrete();
            }
            case UNORDERED_OBJECT: {
                UnorderedObject data = this.getValue();
                for(DataType memberType: data.memberTypes.get().values()) {
                    if(!memberType.isConcrete()) { return false; }
                }
                return true;
            }
            case ORDERED_OBJECT: {
                OrderedObject data = this.getValue();
                for(DataType memberType: data.memberTypes) {
                    if(!memberType.isConcrete()) { return false; }
                }
                return true;
            }
            case CLOSURE: {
                Closure data = this.getValue();
                if(data.argumentTypes.isEmpty()) { return false; }
                for(DataType argumentType: data.argumentTypes.get()) {
                    if(!argumentType.isConcrete()) { return false; }
                }
                return data.returnType.get().isConcrete();
            }
            case UNION: {
                return false;
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    @Override
    public String toString() {
        switch(this.type) {
            case PANIC: return "a panic";
            case UNKNOWN: return "not known at this point";
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
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    public DataType replaceUnknown(
        UnknownOriginMarker replacedOrigin, DataType replacement
    ) {
        switch(this.type) {
            case UNKNOWN: {
                UnknownOriginMarker marker = this.getValue();
                if(marker == replacedOrigin) {
                    return replacement;
                }
                return this;
            }
            case PANIC:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return this;
            }
            case ARRAY: {
                Array data = this.getValue();
                DataType elementType = data.elementType().get().get()
                    .replaceUnknown(replacedOrigin, replacement);
                return new DataType(
                    this.type, new Array(new Ref<>(new Ref<>(elementType))),
                    this.source, this.age
                );
            }
            case UNORDERED_OBJECT: {
                UnorderedObject data = this.getValue();
                Map<String, DataType> memberTypes = new HashMap<>();
                for(String memberName: data.memberTypes().get().keySet()) {
                    DataType memberType = data.memberTypes().get()
                        .get(memberName)
                        .replaceUnknown(replacedOrigin, replacement);
                    memberTypes.put(memberName, memberType);
                }
                return new DataType(
                    this.type, 
                    new UnorderedObject(new Ref<>(memberTypes)),
                    this.source,
                    this.age
                );
            }
            // case ORDERED_OBJECT: {
            //    ...
            // }
            case CLOSURE: {
                return this;
            }
            case UNION: {
                Union data = this.getValue();
                Map<String, DataType> variants = new HashMap<>();
                for(String memberName: data.variants().keySet()) {
                    DataType memberType = data.variants().get(memberName)
                        .replaceUnknown(replacedOrigin, replacement);
                    variants.put(memberName, memberType);
                }
                return new DataType(
                    this.type, 
                    new Union(variants),
                    this.source,
                    this.age
                );
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    @Override
    public boolean equals(Object otherRaw) {
        if(otherRaw == this) {
            return true;
        }   
        if (otherRaw == null || !(otherRaw instanceof DataType)) {
            return false;
        }
        DataType other = (DataType) otherRaw;
        if(this.type != other.type) {
            return false;
        }
        if(this.value == null && other.value == null) {
            return true;
        }
        return this.value.equals(other.value);
    }

}


package typesafeschwalbe.gerac.compiler.frontend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.Source;

public final class DataType {

    public static record UntypedClosureContext(
        AstNode node,
        List<TypeChecker.CheckedBlock> context
    ) {}

    public static record Array(
        DataType elementType
    ) {}

    public static record UnorderedObject(
        Map<String, DataType> memberTypes
    ) {}

    public static record Closure(
        Optional<List<DataType>> argumentTypes,
        Optional<DataType> returnType,
        List<UntypedClosureContext> untypedBodies
    ) {}

    public static record Union(
        Map<String, DataType> variants
    ) {}

    public enum Type {
        UNKNOWN,          // = null
        UNIT,             // = null
        BOOLEAN,          // = null
        INTEGER,          // = null
        FLOAT,            // = null
        STRING,           // = null
        ARRAY,            // Array
        UNORDERED_OBJECT, // UnorderedObject
        CLOSURE,          // Closure
        UNION             // Union
    }

    public final Type type;
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

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) this.value;
    }

    public boolean isExpandable() {
        switch(this.type) {
            case UNKNOWN:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return false;
            }
            case ARRAY: {
                return this.<Array>getValue().elementType.isExpandable();
            }
            case UNORDERED_OBJECT: {
                UnorderedObject data = this.getValue();
                for(DataType memberType: data.memberTypes.values()) {
                    if(memberType.isExpandable()) {
                        return true;
                    }
                }
                return false;
            }
            case CLOSURE: {
                Closure data = this.getValue();
                if(data.argumentTypes.isPresent()) {
                    for(DataType argumentType: data.argumentTypes.get()) {
                        if(argumentType.isExpandable()) {
                            return true;
                        }
                    }
                } else { 
                    return true; 
                }
                if(data.returnType.isPresent()) {
                    if(data.returnType.get().isExpandable()) {
                        return true;
                    }
                } else {
                    return true;
                }
                return false;
            }
            case UNION: {
                Union data = this.getValue();
                for(DataType memberType: data.variants.values()) {
                    if(memberType.isExpandable()) {
                        return true;
                    }
                }
                return true;
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    @Override
    public String toString() {
        switch(this.type) {
            case UNKNOWN: return "not known";
            case UNIT: return "the unit value";
            case BOOLEAN: return "a boolean";
            case INTEGER: return "an integer";
            case FLOAT: return "a float";
            case STRING: return "a string";
            case ARRAY: return "an array";
            case UNORDERED_OBJECT: return "an object";
            case CLOSURE: return "a closure";
            case UNION: return "a union";
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    public DataType replace(DataType replaced, DataType replacement) {
        if(this == replaced) {
            return replacement;
        }
        switch(this.type) {
            case UNKNOWN:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return this;
            }
            case ARRAY: {
                Array data = this.getValue();
                DataType elementType = data.elementType()
                    .replace(replaced, replacement);
                return new DataType(
                    this.type, new Array(elementType), this.source, this.age
                );
            }
            case UNORDERED_OBJECT: {
                UnorderedObject data = this.getValue();
                Map<String, DataType> memberTypes = new HashMap<>();
                for(String memberName: data.memberTypes().keySet()) {
                    DataType memberType = data.memberTypes().get(memberName)
                        .replace(replaced, replacement);
                    memberTypes.put(memberName, memberType);
                }
                return new DataType(
                    this.type, 
                    new UnorderedObject(memberTypes),
                    this.source,
                    this.age
                );
            }
            case CLOSURE: {
                Closure data = this.getValue();
                Optional<List<DataType>> argumentTypes = Optional.empty();
                if(data.argumentTypes().isPresent()) {
                    argumentTypes = Optional.of(data.argumentTypes().get()
                        .stream().map(argumentType -> argumentType.replace(
                            replaced, replacement
                        )).toList()
                    );
                }
                Optional<DataType> returnType = Optional.empty();
                if(data.returnType().isPresent()) {
                    returnType = Optional.of(data.returnType().get()
                        .replace(replaced, replacement)
                    );
                }
                return new DataType(
                    this.type,
                    new Closure(argumentTypes, returnType, data.untypedBodies()),
                    this.source,
                    this.age
                );
            }
            case UNION: {
                Union data = this.getValue();
                Map<String, DataType> variants = new HashMap<>();
                for(String memberName: data.variants().keySet()) {
                    DataType memberType = data.variants().get(memberName)
                        .replace(replaced, replacement);
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
        if(!(otherRaw instanceof DataType)) {
            return false;
        }
        DataType other = (DataType) otherRaw;
        return this.type == other.type
            && this.value.equals(other.value);
    }

}


package typesafeschwalbe.gerac.compiler.frontend;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import typesafeschwalbe.gerac.compiler.Source;

public final class DataType {

    public static record Array(
        DataType elementType
    ) {}

    public static record UnorderedObject(
        Map<String, DataType> memberTypes,
        boolean expandable
    ) {}

    public static record ConcreteObject(
        List<String> memberNames,
        List<DataType> memberTypes
    ) {}

    public static record Closure(
        List<DataType> argumentTypes,
        DataType returnType
    ) {}

    public static record Union(
        Map<String, DataType> variants,
        boolean expandable
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
        CONCRETE_OBJECT,  // ConcreteObject
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
    private final long age;

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

    private DataType(Type type, Object value, Source source, long age) {
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
                return data.expandable;
            }
            case CONCRETE_OBJECT: {
                ConcreteObject data = this.getValue();
                for(DataType memberType: data.memberTypes) {
                    if(memberType.isExpandable()) {
                        return true;
                    }
                }
                return false;
            }
            case CLOSURE: {
                Closure data = this.getValue();
                for(DataType argumentType: data.argumentTypes) {
                    if(argumentType.isExpandable()) {
                        return true;
                    }
                }
                return data.returnType.isExpandable();
            }
            case UNION: {
                Union data = this.getValue();
                for(DataType memberType: data.variants.values()) {
                    if(memberType.isExpandable()) {
                        return true;
                    }
                }
                return data.expandable;
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

    public static DataType unify(DataType a, DataType b) {
        // TODO: - COMBINE THE TWO TYPES AND ALL MEMBERS, VARIANTS AND ELEMENTS
        //       - COMBINE VARIANTS AND MEMBERS IF EXPANDABLE
        //       - ERROR IF THEY DON'T MATCH UP
        //       - CHOOSE THE SOURCE AND AGE
        //         OF THE TYPE WITH THE HIGHEST AGE AND INCREMENT THE AGE
        throw new RuntimeException("not yet implemented");
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
                    new UnorderedObject(memberTypes, data.expandable()),
                    this.source,
                    this.age
                );
            }
            case CONCRETE_OBJECT: {
                ConcreteObject data = this.getValue();
                List<DataType> memberTypes = data.memberTypes()
                    .stream().map(memberType -> memberType.replace(
                        replaced, replacement
                    )).toList();
                return new DataType(
                    this.type,
                    new ConcreteObject(data.memberNames(), memberTypes),
                    this.source,
                    this.age
                );
            }
            case CLOSURE: {
                Closure data = this.getValue();
                List<DataType> argumentTypes = data.argumentTypes()
                    .stream().map(argumentType -> argumentType.replace(
                        replaced, replacement
                    )).toList();
                DataType returnType = data.returnType()
                    .replace(replaced, replacement);
                return new DataType(
                    this.type,
                    new Closure(argumentTypes, returnType),
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
                    new Union(variants, data.expandable()),
                    this.source,
                    this.age
                );
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
    }

}

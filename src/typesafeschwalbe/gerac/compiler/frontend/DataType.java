
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Source;

public final class DataType {

    public static record Array(
        DataType elementType
    ) {}

    public static record UnorderedObject(
        Map<String, DataType> memberTypes
    ) {}

    public static record Closure(
        List<DataType> argumentTypes,
        DataType returnType
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

    private static Error makeIncompatibleError(
        Source opSource, Error.Marking aMarking, Error.Marking bMarking 
    ) {
        return new Error(
            "Incompatible types",
            new Error.Marking(
                opSource, "types are expected to be compatible here"
            ),
            aMarking,
            bMarking
        );
    }

    public static DataType unify(
        DataType a, DataType b, Source source
    ) throws TypingException {
        if(a.type != b.type) {
            throw new TypingException(DataType.makeIncompatibleError(
                source, 
                new Error.Marking(
                    a.source, "this is " + a.toString()
                ),
                new Error.Marking(
                    b.source, "but this is " + a.toString()
                )
            ));
        }
        Object value;
        switch(a.type) {
            case UNKNOWN:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                value = null;
            } break;
            case ARRAY: {
                Array dataA = a.getValue();
                Array dataB = b.getValue();
                DataType elementType = DataType.unify(
                    dataA.elementType(), dataB.elementType(), source
                );
                value = new Array(elementType);
            } break;
            case UNORDERED_OBJECT: {
                UnorderedObject dataA = a.getValue();
                UnorderedObject dataB = b.getValue();
                Set<String> memberNames = new HashSet<>(
                    dataA.memberTypes().keySet()
                );
                memberNames.addAll(dataB.memberTypes().keySet());
                Map<String, DataType> memberTypes = new HashMap<>();
                for(String memberName: memberNames) {
                    boolean inA = dataA.memberTypes().containsKey(memberName);
                    boolean inB = dataB.memberTypes().containsKey(memberName);
                    if(!inA || !inB) {
                        String inMsg = "this is an object with a property "
                            + "'" + memberName + "'";
                        String withoutMsg = "but this is an object without it";
                        Error e = DataType.makeIncompatibleError(
                            source, 
                            new Error.Marking(
                                a.source, inA? inMsg : withoutMsg
                            ),
                            new Error.Marking(
                                b.source, inB? inMsg : withoutMsg
                            )
                        );
                        throw new TypingException(e);
                    }
                    DataType memberType = DataType.unify(
                        dataA.memberTypes().get(memberName),
                        dataB.memberTypes().get(memberName), 
                        source
                    );
                    memberTypes.put(memberName, memberType);
                }
                value = new UnorderedObject(memberTypes);
            } break;
            case CLOSURE: {
                Closure dataA = a.getValue();
                Closure dataB = b.getValue();
                int aArgC = dataA.argumentTypes().size();
                int bArgC = dataB.argumentTypes().size();
                if(aArgC != bArgC) {
                    String aMsg = "this is a closure with "
                    + aArgC + " argument" + (aArgC == 1? "" : "s");
                    String bMsg = "this is a closure with "
                    + bArgC + " argument" + (bArgC == 1? "" : "s");
                    throw new TypingException(DataType.makeIncompatibleError(
                        source,
                        new Error.Marking(a.source, aMsg),
                        new Error.Marking(b.source, bMsg)
                    ));
                }
                List<DataType> argumentTypes = new ArrayList<>();
                for(int argI = 0; argI < aArgC; argI += 1) {
                    DataType argumentType = DataType.unify(
                        dataA.argumentTypes().get(argI), 
                        dataB.argumentTypes().get(argI), 
                        source
                    );
                    argumentTypes.add(argumentType);
                }
                DataType returnType = DataType.unify(
                    dataA.returnType(), dataB.returnType(), source
                );
                value = new Closure(argumentTypes, returnType);
            } break;
            case UNION: {
                Union dataA = a.getValue();
                Union dataB = b.getValue();
                Set<String> variantNames = new HashSet<>(
                    dataA.variants().keySet()
                );
                variantNames.addAll(dataB.variants().keySet());
                Map<String, DataType> variantTypes = new HashMap<>();
                for(String variantName: variantNames) {
                    boolean inA = dataA.variants().containsKey(variantName);
                    boolean inB = dataB.variants().containsKey(variantName);
                    if(!inA) {
                        variantTypes.put(
                            variantName, dataB.variants().get(variantName)
                        );
                    }
                    if(!inB) {
                        variantTypes.put(
                            variantName, dataA.variants().get(variantName)
                        );
                    }
                    DataType variantType = DataType.unify(
                        dataA.variants().get(variantName),
                        dataB.variants().get(variantName), 
                        source
                    );
                    variantTypes.put(variantName, variantType);
                }
                value = new Union(variantTypes);
            }
            default: {
                throw new RuntimeException("type not handled!");
            }
        }
        if(a.age > b.age) {
            return new DataType(a.type, value, a.source, a.age + 1);
        } else {
            return new DataType(a.type, value, b.source, b.age + 1);
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

}

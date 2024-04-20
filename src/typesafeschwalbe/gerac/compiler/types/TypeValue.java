
package typesafeschwalbe.gerac.compiler.types;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Source;

public class TypeValue extends DataType<TypeValue> {

    public TypeValue(
        DataType.Type type, DataTypeValue<TypeValue> value, 
        Optional<Source> source
    ) {
        super(type, value, source);
    }

    public static TypeValue upgrade(DataType<TypeValue> t) {
        return new TypeValue(t.type, t.getValue(), t.source);
    }

    private static record EqualityEncounter(TypeValue a, TypeValue b) {}

    public boolean deepEquals(TypeValue other) {
        return this.deepEquals(other, new HashSet<>());
    }

    private boolean deepEquals(
        TypeValue other, Set<EqualityEncounter> encountered
    ) {
        if(this.type != other.type) { return false; }
        EqualityEncounter encounter = new EqualityEncounter(this, other);
        if(encountered.contains(encounter)) {
            return true;
        }
        encountered.add(encounter);
        boolean result;
        switch(this.type) {
            case ANY:
            case NUMERIC:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                result = true;
            } break;
            case ARRAY: {
                DataType.Array<TypeValue> dataA = this.getValue();
                DataType.Array<TypeValue> dataB = other.getValue();
                result = dataA.elementType()
                    .deepEquals(dataB.elementType(), encountered);
            } break;
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject<TypeValue> dataA = this.getValue();
                DataType.UnorderedObject<TypeValue> dataB = other.getValue();
                result = true;
                Set<String> members = new HashSet<>();
                members.addAll(dataA.memberTypes().keySet());
                members.addAll(dataB.memberTypes().keySet());
                for(String member: members) {
                    if(!dataA.memberTypes().containsKey(member)) {
                        result = false;
                        break;
                    }
                    if(!dataB.memberTypes().containsKey(member)) {
                        result = false;
                        break;
                    }
                    TypeValue mA = dataA.memberTypes().get(member);
                    TypeValue mB = dataB.memberTypes().get(member);
                    if(!mA.deepEquals(mB, encountered)) {
                        result = false;
                        break;
                    }
                }
            } break;
            case ORDERED_OBJECT: {
                DataType.OrderedObject<TypeValue> dataA = this.getValue();
                DataType.OrderedObject<TypeValue> dataB = other.getValue();
                result = true;
                if(!dataA.memberNames().equals(dataB.memberNames())) {
                    result = false;
                    break;
                }
                for(
                    int memberI = 0; memberI < dataA.memberTypes().size(); 
                    memberI += 1
                ) {
                    TypeValue mA = dataA.memberTypes().get(memberI);
                    TypeValue mB = dataB.memberTypes().get(memberI);
                    if(!mA.deepEquals(mB, encountered)) {
                        result = false;
                        break;
                    }
                }
            } break;
            case CLOSURE: {
                DataType.Closure<TypeValue> dataA = this.getValue();
                DataType.Closure<TypeValue> dataB = other.getValue();
                result = true;
                int aArgC = dataA.argumentTypes().size();
                int bArgC = dataB.argumentTypes().size();
                if(aArgC != bArgC) {
                    result = false;
                    break;
                }
                for(int argI = 0; argI < aArgC; argI += 1) {
                    TypeValue argA = dataA.argumentTypes().get(argI);
                    TypeValue argB = dataB.argumentTypes().get(argI);
                    if(!argA.deepEquals(argB, encountered)) {
                        result = false;
                        break;
                    }
                }
                TypeValue rA = dataA.returnType();
                TypeValue rB = dataB.returnType();
                if(!rA.deepEquals(rB, encountered)) {
                    result = false;
                    break;
                }
            } break;
            case UNION: {
                DataType.Union<TypeValue> dataA = this.getValue();
                DataType.Union<TypeValue> dataB = other.getValue();
                result = true;
                Set<String> variants = new HashSet<>();
                variants.addAll(dataA.variantTypes().keySet());
                variants.addAll(dataB.variantTypes().keySet());
                for(String variant: variants) {
                    if(!dataA.variantTypes().containsKey(variant)) {
                        result = false;
                        break;
                    }
                    if(!dataB.variantTypes().containsKey(variant)) {
                        result = false;
                        break;
                    }
                    TypeValue vA = dataA.variantTypes().get(variant);
                    TypeValue vB = dataB.variantTypes().get(variant);
                    if(!vA.deepEquals(vB, encountered)) {
                        result = false;
                        break;
                    }
                }
            } break;
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
        encountered.remove(encounter);
        return result;
    }

}

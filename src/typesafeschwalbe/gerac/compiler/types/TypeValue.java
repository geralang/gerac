
package typesafeschwalbe.gerac.compiler.types;

public class TypeValue extends DataType<TypeValue> {

    public TypeValue(DataType.Type type, DataTypeValue<TypeValue> value) {
        super(type, value);
    }

    public static TypeValue upgrade(DataType<TypeValue> t) {
        return new TypeValue(t.type, t.getValue());
    }

}

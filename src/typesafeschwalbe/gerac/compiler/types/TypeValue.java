
package typesafeschwalbe.gerac.compiler.types;

import java.util.Optional;

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

}

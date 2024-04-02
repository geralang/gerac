
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;
import java.util.Map;

import typesafeschwalbe.gerac.compiler.Source;

public final class DataType {

    public static record Array(
        DataType elementType
    ) {}

    public static record Object(
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
        UNKNOWN,         // = null
        UNIT,            // = null
        BOOLEAN,         // = null
        INTEGER,         // = null
        FLOAT,           // = null
        STRING,          // = null
        ARRAY,           // Array
        OBJECT,          // Object
        CONCRETE_OBJECT, // ConcreteObject
        CLOSURE,         // Closure
        UNION            // Union
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

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) this.value;
    }

    public boolean isExpandable() {
        // TODO: DETERMINE IF ALL MEMBERS, VARIANTS AND ELEMENTS
        //       AND THE TYPE ITSELF IS EXPANDABLE
        //       (ONLY RETURN FALSE IF NOTHING CAN BE EXPANDED)
        throw new RuntimeException("not yet implemented");
    }

    public static DataType unify(DataType a, DataType b) {
        // TODO: COMBINE THE TWO TYPES AND ALL MEMBERS, VARIANTS AND ELEMENTS
        //       COMBINE VARIANTS AND MEMBERS IF EXPANDABLE
        //       ERROR IF THEY DON'T MATCH UP, CHOOSE THE SOURCE AND AGE
        //       OF THE TYPE WITH THE HIGHEST AGE AND INCREMENT THE AGE
        throw new RuntimeException("not yet implemented");
    }

}

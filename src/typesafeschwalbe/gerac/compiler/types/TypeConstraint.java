
package typesafeschwalbe.gerac.compiler.types;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Source;

public class TypeConstraint {

    private interface ConstraintValue {}

    public static record IsType(
        TypeValue.Type type, Optional<String> reason
    ) implements ConstraintValue {}
    
    public static record HasElement(
        TypeVariable type
    ) implements ConstraintValue {}

    public static record HasMember(
        String name, TypeVariable type
    ) implements ConstraintValue {}

    public static record LimitMembers(
        Set<String> names
    ) implements ConstraintValue {}

    public static record HasSignature(
        List<TypeVariable> arguments, TypeVariable returned
    ) implements ConstraintValue {}

    public static record HasVariant(
        String name, TypeVariable type
    ) implements ConstraintValue {}

    public static record LimitVariants(
        Set<String> names
    ) implements ConstraintValue {}

    public static record Unify(
        TypeVariable with
    ) implements ConstraintValue {}

    public enum Type {
        IS_NUMERIC,     // = null
        IS_TYPE,        // IsType
        HAS_ELEMENT,    // HasElement
        HAS_MEMBER,     // HasMember
        LIMIT_MEMBERS,  // LimitMembers
        HAS_SIGNATURE,  // HasSignature
        HAS_VARIANT,    // HasVariant
        LIMIT_VARIANTS, // LimitVariants
        UNIFY           // Unify
    }

    public final TypeVariable target;
    public final Type type;
    private final ConstraintValue value;
    public final Source source;

    public TypeConstraint(
        TypeVariable target, Source source,
        Type type, ConstraintValue value
    ) {
        this.target = target;
        this.type = type;
        this.value = value;
        this.source = source;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConstraintValue> T getValue() {
        return (T) this.value;
    }

    @Override
    public String toString() {
        return "<" + this.type + ">" + this.value;
    }

}

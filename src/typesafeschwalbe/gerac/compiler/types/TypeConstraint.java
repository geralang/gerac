
package typesafeschwalbe.gerac.compiler.types;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Source;

public class TypeConstraint {

    public static TypeConstraint isNumeric(Source src) {
        return new TypeConstraint(Type.IS_NUMERIC, null, src);
    }

    public static TypeConstraint isType(
        TypeValue.Type type, Source src, Optional<String> reason
    ) {
        return new TypeConstraint(Type.IS_TYPE, new IsType(type, reason), src);
    }

    public static TypeConstraint hasElement(TypeVariable type, Source src) {
        return new TypeConstraint(Type.HAS_ELEMENT, new HasElement(type), src);
    }

    public static TypeConstraint hasMember(
        String name, TypeVariable type, Source src
    ) {
        return new TypeConstraint(
            Type.HAS_MEMBER, new HasMember(name, type), src
        );
    }

    public static TypeConstraint limitMembers(Set<String> names, Source src) {
        return new TypeConstraint(
            Type.LIMIT_MEMBERS, new LimitMembers(names), src
        );
    }

    public static TypeConstraint hasSignature(
        List<TypeVariable> arguments, TypeVariable returned, Source src
    ) {
        return new TypeConstraint(
            Type.HAS_SIGNATURE, new HasSignature(arguments, returned), src
        );
    }

    public static TypeConstraint hasVariant(
        String name, TypeVariable type, Source src) {
        return new TypeConstraint(
            Type.HAS_VARIANT, new HasVariant(name, type), src
        );
    }

    public static TypeConstraint limitVariants(Set<String> names, Source src) {
        return new TypeConstraint(
            Type.LIMIT_VARIANTS, new LimitVariants(names), src
        );
    }

    public static TypeConstraint unify(TypeVariable with, Source src) {
        return new TypeConstraint(Type.UNIFY, new Unify(with), src);
    }

    private static record IsType(
        TypeValue.Type type, Optional<String> reason
    ) {}
    private static record HasElement(TypeVariable type) {}
    private static record HasMember(String name, TypeVariable type) {}
    private static record LimitMembers(Set<String> names) {}
    private static record HasSignature(
        List<TypeVariable> arguments, TypeVariable returned
    ) {}
    private static record HasVariant(String name, TypeVariable type) {}
    private static record LimitVariants(Set<String> names) {}
    private static record Unify(TypeVariable with) {}

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

    private final Type type;
    private final Object value;
    private final Source source;

    private TypeConstraint(
        Type t, Object v, Source s
    ) {
        this.type = t;
        this.value = v;
        this.source = s;
    }

    @Override
    public String toString() {
        return "<" + this.type + ">" + this.value;
    }

}

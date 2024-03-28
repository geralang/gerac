
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Source;

public class AstNode {

    public static record Procedure(
        boolean isPublic,
        String name,
        List<String> argumentNames,
        List<Source> argumentSources,
        List<AstNode> body
    ) {}

    public static record Closure(
        List<String> argumentNames,
        List<Source> argumentSources,
        Set<String> captured,
        List<AstNode> body
    ) {}

    public static record Variable(
        boolean isPublic,
        boolean isMutable,
        String name,
        Optional<AstNode> value
    ) {}

    public static record CaseBranching(
        AstNode value,
        List<AstNode> branchValues,
        List<List<AstNode>> branchBodies,
        List<AstNode> elseBody
    ) {}

    public static record CaseConditional(
        AstNode condition,
        List<AstNode> ifBody,
        List<AstNode> elseBody
    ) {}

    public static record CaseVariant(
        AstNode value,
        List<String> branchVariants,
        List<Optional<String>> branchVariableNames,
        List<Optional<Source>> branchVariableSources,
        List<List<AstNode>> branchBodies,
        List<AstNode> elseBody
    ) {}

    public static record Call(
        AstNode called,
        List<AstNode> arguments
    ) {}

    public static record MethodCall(
        AstNode called,
        String memberName,
        List<AstNode> arguments
    ) {}

    public static record ObjectLiteral(
        List<String> memberNames,
        List<AstNode> memberValues
    ) {}

    public static record ArrayLiteral(
        List<AstNode> values
    ) {}

    public static record ObjectAccess(
        AstNode accessed,
        String memberName
    ) {}

    public static record VariableAccess(
        String variableName
    ) {}

    public static record SimpleLiteral(
        String value
    ) {}

    public static record NamespacePath(
        Namespace path
    ) {}

    public static record Usages(
        List<Namespace> paths
    ) {}

    public static record VariantLiteral(
        String variantName,
        AstNode value
    ) {}

    public static record Target(
        String targetName,
        List<AstNode> body
    ) {}

    public static record MonoOp(
        AstNode value
    ) {}

    public static record BiOp(
        AstNode left,
        AstNode right
    ) {}

    public enum Type {
        PROCEDURE,          // Procedure
        CLOSURE,            // Closure
        VARIABLE,           // Variable
        CASE_BRANCHING,     // CaseBranching
        CASE_CONDITIONAL,   // CaseConditional
        CASE_VARIANT,       // CaseVariant
        ASSIGNMENT,         // BiOp
        RETURN,             // MonoOp
        CALL,               // Call
        METHOD_CALL,        // MethodCall
        OBJECT_LITERAL,     // ObjectLiteral
        ARRAY_LITERAL,      // ArrayLiteral
        OBJECT_ACCESS,      // ObjectAccess
        ARRAY_ACCESS,       // BiOp
        VARIABLE_ACCESS,    // VariableAccess
        BOOLEAN_LITERAL,    // SimpleLiteral
        INTEGER_LITERAL,    // SimpleLiteral
        FLOAT_LITERAL,      // SimpleLiteral
        STRING_LITERAL,     // SimpleLiteral
        UNIT_LITERAL,       // = null
        ADD,                // BiOp
        SUBTRACT,           // BiOp
        MULTIPLY,           // BiOp
        DIVIDE,             // BiOp
        MODULO,             // BiOp
        NEGATE,             // MonoOp
        LESS_THAN,          // BiOp
        GREATER_THAN,       // BiOp
        LESS_THAN_EQUAL,    // BiOp
        GREATER_THAN_EQUAL, // BiOp
        EQUALS,             // BiOp
        NOT_EQUALS,         // BiOp
        NOT,                // MonoOp
        OR,                 // BiOp
        AND,                // BiOp
        MODULE_DECLARATION, // NamespacePath
        MODULE_ACCESS,      // NamespacePath
        USE,                // Usages
        VARIANT_LITERAL,    // VariantLiteral
        STATIC,             // MonoOp
        TARGET              // Target
    }

    public final Type type;
    private final Object value;
    public final Source source;

    public AstNode(
        Type type, Object value, Source source
    ) {
        this.type = type;
        this.value = value;
        this.source = source;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) this.value;
    }

    public boolean isAssignable() {
        // TODO: return if this node can be assigned to
        throw new RuntimeException("Not yet implemented");
    }

}
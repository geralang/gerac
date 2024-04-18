
package typesafeschwalbe.gerac.compiler.types;

import java.util.ArrayList;
import java.util.List;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;

public class ConstraintGenerator {
    
    private final TypeContext ctx;
    private final List<TypeVariable> arguments;
    private final TypeVariable returnType;

    public ConstraintGenerator(Symbols.Symbol.Procedure p) {
        this.ctx = new TypeContext();
        this.arguments = new ArrayList<>(p.argumentNames().size());
        for(int argI = 0; argI < p.argumentNames().size(); argI += 1) {
            this.arguments.add(this.ctx.makeVar());
        }
        this.returnType = this.ctx.makeVar();
    }

    private void walkBlock(List<AstNode> body) {
        for(AstNode node: body) {
            this.walkNode(node);
        }
    }

    private TypeVariable walkNode(AstNode node) {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case RETURN: {
                AstNode.MonoOp data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case PROCEDURE_CALL: {
                AstNode.ProcedureCall data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                // TODO
                throw new RuntimeException("not yet implemented");
            }
            case OBJECT_LITERAL: {
                
            }
            case ARRAY_LITERAL: {
                
            }
            case REPEATING_ARRAY_LITERAL: {
                
            }
            case OBJECT_ACCESS: {
                
            }
            case ARRAY_ACCESS: {
                
            }
            case VARIABLE_ACCESS: {
                
            }
            case BOOLEAN_LITERAL: {
                
            }
            case INTEGER_LITERAL: {
                
            }
            case FLOAT_LITERAL: {
                
            }
            case STRING_LITERAL: {
                
            }
            case UNIT_LITERAL: {
                
            }
            case ADD: {
                
            }
            case SUBTRACT: {
                
            }
            case MULTIPLY: {
                
            }
            case DIVIDE: {
                
            }
            case MODULO: {
                
            }
            case NEGATE: {
                
            }
            case LESS_THAN: {
                
            }
            case GREATER_THAN: {
                
            }
            case LESS_THAN_EQUAL: {
                
            }
            case GREATER_THAN_EQUAL: {
                
            }
            case EQUALS: {
                
            }
            case NOT_EQUALS: {
                
            }
            case NOT: {
                
            }
            case OR: {
                
            }
            case AND: {
                
            }
            case MODULE_ACCESS: {
                
            }
            case VARIANT_LITERAL: {
                
            }
            case STATIC: {

            }
            case PROCEDURE:
            case TARGET:
            case USE:
            case MODULE_DECLARATION: {
                throw new RuntimeException("should not be encountered!");
            }
            default: {
                throw new RuntimeException("unhandled node type!");
            }
        }
    }



}

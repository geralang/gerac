
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;

public class TypeChecker {
    
    private static Error makeNotAlwaysReturnError(DataType type, Source src) {
        return new Error(
            "Possibly missing return value",
            new Error.Marking(
                src,
                "returns " + type.toString()
                    + ", but only on some branches"
            )
        );
    }

    private static Error makeNonNumericError(DataType type, Source opSource) {
        return new Error(
            "Numeric operation used on non-numeric type",
            new Error.Marking(
                type.source, "this is " + type.toString()
            ),
            new Error.Marking(
                opSource, "but this operation requires a numeric type"
            )
        );
    }

    private static Error makeNonBooleanError(DataType type, Source opSource) {
        return new Error(
            "Logical operation used on non-boolean type",
            new Error.Marking(
                type.source, "this is " + type.toString()
            ),
            new Error.Marking(
                opSource, "but this operation requires a boolean"
            )
        );
    }

    private static Error makeNonBooleanAsCondError(
        DataType type, Source condSource
    ) {
        return new Error(
            "Non-boolean value used as condition",
            new Error.Marking(
                type.source, "this is " + type.toString()
            ),
            new Error.Marking(
                condSource,
                "but it's usage as the condition here"
                    + " requires it to be a boolean"
            )
        );
    }

    private static class CheckedBlock {
        private final Map<String, Optional<DataType>> variableTypes;
        private final Map<String, Boolean> variablesMutable;
        private final Set<String> captured;
        private final Map<String, DataType> initializes;
        private boolean returns;

        private CheckedBlock() {
            this.variableTypes = new HashMap<>();
            this.variablesMutable = new HashMap<>();
            this.captured = new HashSet<>();
            this.initializes = new HashMap<>();
            this.returns = false;
        }
    }

    private static class CheckedSymbol {
        private final Namespace path;
        private final Optional<DataType> returnType;
        private final List<CheckedBlock> blocks;

        private CheckedSymbol(Namespace path) {
            this.path = path;
            this.returnType = Optional.empty();
            this.blocks = new LinkedList<>();
        }
    }


    private final Symbols symbols;
    private final List<CheckedSymbol> checked;

    public TypeChecker(Symbols symbols) {
        this.symbols = symbols;
        this.checked = new LinkedList<>();
    }

    private void enterSymbol(Namespace path) {
        this.checked.add(new CheckedSymbol(path));
    }

    private CheckedSymbol currentSymbol() {
        return this.checked.get(this.checked.size() - 1);
    }

    private CheckedSymbol exitSymbol() {
        return this.checked.remove(this.checked.size() - 1);
    }

    private void enterBlock() {
        CheckedSymbol currentSymbol = this.currentSymbol();
        CheckedBlock block = new CheckedBlock();
        currentSymbol.blocks.add(block);
    }

    private CheckedBlock currentBlock() {
        CheckedSymbol currentSymbol = this.currentSymbol();
        return currentSymbol.blocks.get(currentSymbol.blocks.size() - 1);
    }

    private CheckedBlock exitBlock() {
        CheckedSymbol currentSymbol = this.currentSymbol();
        return currentSymbol.blocks.remove(currentSymbol.blocks.size() - 1);
    }

    private void handleBranches(List<CheckedBlock> branches) {
        CheckedBlock currentBlock = this.currentBlock();
        Map<String, DataType> initializes = null;
        boolean returns = false;
        for(int branchI = 0; branchI < branches.size(); branchI += 1) {
            CheckedBlock branch = branches.get(branchI);
            currentBlock.captured.addAll(branch.captured);
            if(branchI == 0) {
                initializes = new HashMap<>(branch.initializes);
                returns = branch.returns;
            } else {
                initializes.keySet().retainAll(branch.initializes.keySet());
                returns &= branch.returns;
            }
        }
        currentBlock.initializes.putAll(initializes);
        currentBlock.returns |= returns;
    }

    public List<AstNode> typeNodes(
        List<AstNode> nodes
    ) throws TypingException {
        List<AstNode> typedNodes = new ArrayList<>(nodes.size());
        for(AstNode node: nodes) {
            typedNodes.add(this.typeNode(node, READ));
        }
        return typedNodes;
    }

    private static final boolean READ = false;
    private static final boolean WRITE = true;

    public AstNode typeNode(
        AstNode node, boolean assignment
    ) throws TypingException {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                Optional<AstNode> valueTyped;
                if(data.value().isPresent()) {
                    AstNode valueTypedD = this.typeNode(
                        data.value().get(), READ
                    );
                    this.currentBlock().variableTypes.put(
                        data.name(), Optional.of(valueTypedD.resultType.get())
                    );
                    this.currentBlock().variablesMutable.put(
                        data.name(), data.isMutable()
                    );
                    valueTyped = Optional.of(valueTypedD);
                } else {
                    this.currentBlock().variableTypes.put(
                        data.name(), Optional.empty()
                    );
                    this.currentBlock().variablesMutable.put(
                        data.name(), data.isMutable()
                    );
                    valueTyped = Optional.empty();
                }
                return new AstNode(
                    node.type,
                    new AstNode.Variable(
                        data.isPublic(), data.isMutable(), data.name(),
                        valueTyped
                    ), 
                    node.source
                );
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value(), READ);
                List<CheckedBlock> blocks = new ArrayList<>();
                this.enterBlock();
                List<AstNode> elseBodyTyped = this.typeNodes(data.elseBody());
                blocks.add(this.exitBlock());
                List<AstNode> branchValuesTyped = new ArrayList<>();
                List<List<AstNode>> branchBodiesTyped = new ArrayList<>();
                for(
                    int branchI = 0; 
                    branchI < data.branchValues().size(); 
                    branchI += 1
                ) {
                    AstNode branchValue = data.branchValues().get(branchI);
                    AstNode branchValueTyped = this.typeNode(branchValue, READ);
                    DataType.unify(
                        valueTyped.resultType.get(), 
                        branchValueTyped.resultType.get(),
                        node.source
                    );
                    List<AstNode> branchBody = data.branchBodies().get(branchI);
                    this.enterBlock();
                    List<AstNode> branchBodyTyped = this.typeNodes(branchBody);
                    blocks.add(this.exitBlock());
                    branchValuesTyped.add(branchValueTyped);
                    branchBodiesTyped.add(branchBodyTyped);
                }
                this.handleBranches(blocks);
                return new AstNode(
                    node.type,
                    new AstNode.CaseBranching(
                        valueTyped, branchValuesTyped, branchBodiesTyped,
                        elseBodyTyped
                    ),
                    node.source
                );
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                AstNode conditionTyped = this.typeNode(data.condition(), READ);
                DataType condType = conditionTyped.resultType.get();
                if(condType.type != DataType.Type.BOOLEAN) {
                    throw new TypingException(
                        TypeChecker.makeNonBooleanAsCondError(
                            condType, conditionTyped.source
                        )
                    );
                }
                List<CheckedBlock> branches = new ArrayList<>();
                this.enterBlock();
                List<AstNode> ifBodyTyped = this.typeNodes(data.ifBody());
                branches.add(this.exitBlock());
                this.enterBlock();
                List<AstNode> elseBodyTyped = this.typeNodes(data.elseBody());
                branches.add(this.exitBlock());
                this.handleBranches(branches);
                return new AstNode(
                    node.type,
                    new AstNode.CaseConditional(
                        conditionTyped, ifBodyTyped, elseBodyTyped
                    ),
                    node.source
                );
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case RETURN: {
                AstNode.MonoOp data = node.getValue();
                // TODO: - IF CURRENT SYMBOL RETURN TYPE IS EMPTY,
                //         MAKE RETURNED VALUE TYPE
                //       - ELSE MAKE THE SYMBOL RETURN TYPE THE CURRENT RETURN
                //         TYPE UNIFIED WITH RETURNED VALUE TYPE
                //       - SET THE CURRENT BLOCK TO ALWAYS RETURN
                throw new RuntimeException("not yet implemented!");
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case BOOLEAN_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, 
                    new DataType(DataType.Type.BOOLEAN, node.source)
                );
            }
            case INTEGER_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, 
                    new DataType(DataType.Type.INTEGER, node.source)
                );
            }
            case FLOAT_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, 
                    new DataType(DataType.Type.FLOAT, node.source)
                );
            }
            case STRING_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, 
                    new DataType(DataType.Type.STRING, node.source)
                );
            }
            case UNIT_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, 
                    new DataType(DataType.Type.UNIT, node.source)
                );
            }
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO: {
                AstNode.BiOp data = node.getValue();
                AstNode leftTyped = this.typeNode(data.left(), READ);
                AstNode rightTyped = this.typeNode(data.right(), READ);
                DataType resultType = DataType.unify(
                    leftTyped.resultType.get(), rightTyped.resultType.get(),
                    node.source
                );
                boolean isNumberType = resultType.type == DataType.Type.INTEGER
                    || resultType.type == DataType.Type.FLOAT;
                if(!isNumberType) {
                    throw new TypingException(
                        TypeChecker.makeNonNumericError(resultType, node.source)
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(leftTyped, rightTyped),
                    node.source, 
                    resultType
                );
            }
            case NEGATE: {
                AstNode.MonoOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value(), READ);
                DataType resultType = valueTyped.resultType.get();
                boolean isNumberType = resultType.type == DataType.Type.INTEGER
                    || resultType.type == DataType.Type.FLOAT;
                if(!isNumberType) {
                    throw new TypingException(
                        TypeChecker.makeNonNumericError(resultType, node.source)
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.MonoOp(valueTyped),
                    node.source,
                    resultType
                );
            }
            case LESS_THAN:
            case GREATER_THAN:
            case LESS_THAN_EQUAL:
            case GREATER_THAN_EQUAL: {
                AstNode.BiOp data = node.getValue();
                AstNode leftTyped = this.typeNode(data.left(), READ);
                AstNode rightTyped = this.typeNode(data.right(), READ);
                DataType valuesType = DataType.unify(
                    leftTyped.resultType.get(), rightTyped.resultType.get(),
                    node.source
                );
                boolean isNumberType = valuesType.type == DataType.Type.INTEGER
                    || valuesType.type == DataType.Type.FLOAT;
                if(!isNumberType) {
                    throw new TypingException(
                        TypeChecker.makeNonNumericError(valuesType, node.source)
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(leftTyped, rightTyped),
                    node.source, 
                    new DataType(DataType.Type.BOOLEAN, node.source)
                );
            }
            case EQUALS:
            case NOT_EQUALS: {
                AstNode.BiOp data = node.getValue();
                AstNode leftTyped = this.typeNode(data.left(), READ);
                AstNode rightTyped = this.typeNode(data.right(), READ);
                DataType.unify(
                    leftTyped.resultType.get(), rightTyped.resultType.get(),
                    node.source
                );
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(leftTyped, rightTyped),
                    node.source, 
                    new DataType(DataType.Type.BOOLEAN, node.source)
                );
            }
            case NOT: {
                AstNode.MonoOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value(), READ);
                DataType resultType = valueTyped.resultType.get();
                if(resultType.type != DataType.Type.BOOLEAN) {
                    throw new TypingException(
                        TypeChecker.makeNonBooleanError(resultType, node.source)
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.MonoOp(valueTyped),
                    node.source,
                    resultType
                );
            }
            case OR:
            case AND: {
                AstNode.BiOp data = node.getValue();
                AstNode leftTyped = this.typeNode(data.left(), READ);
                AstNode rightTyped = this.typeNode(data.right(), READ);
                DataType resultType = DataType.unify(
                    leftTyped.resultType.get(), rightTyped.resultType.get(),
                    node.source
                );
                if(resultType.type != DataType.Type.BOOLEAN) {
                    throw new TypingException(
                        TypeChecker.makeNonBooleanError(resultType, node.source)
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(leftTyped, rightTyped),
                    node.source, 
                    resultType
                );
            }
            case MODULE_ACCESS: {
                AstNode.NamespacePath data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                throw new RuntimeException("not yet implemented!");
            }
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                throw new RuntimeException("not yet implemented!");
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

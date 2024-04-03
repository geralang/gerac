
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

    static class CheckedBlock {
        private final Map<String, Optional<DataType>> variableTypes;
        private final Map<String, Boolean> variablesMutable;
        private final Map<String, DataType> initializes;
        private final Set<String> captures;
        private boolean returns;

        private CheckedBlock() {
            this.variableTypes = new HashMap<>();
            this.variablesMutable = new HashMap<>();
            this.initializes = new HashMap<>();
            this.captures = new HashSet<>();
            this.returns = false;
        }

        private CheckedBlock(CheckedBlock src) {
            this.variableTypes = new HashMap<>(src.variableTypes);
            this.variablesMutable = new HashMap<>(src.variablesMutable);
            this.initializes = new HashMap<>(src.initializes);
            this.captures = new HashSet<>(src.captures);
            this.returns = src.returns;
        }
    }

    static class CheckedSymbol {
        private final Namespace path;
        private final List<DataType> argumentTypes;
        private final Optional<DataType> returnType;
        private final List<CheckedBlock> blocks;

        private CheckedSymbol(Namespace path, List<DataType> argumentTypes) {
            this.path = path;
            this.argumentTypes = argumentTypes;
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

    private int checkProcedureCall(
        Symbols.Symbol symbol, List<DataType> argumentTypes
    ) {
        if(symbol.node().type != AstNode.Type.PROCEDURE) {
            throw new IllegalArgumentException("must be a procedure symbol!");
        }
        // TODO: - IF THE SAME PATH AND ARGUMENT TYPES ARE ALREADY IN THE
        //         'checked' STACK
        //           - IF THE RETURN TYPE IS EMPTY OR NOT EXPANDABLE RETURN
        //             AN UNKNOWN TYPE
        //           - ELSE RETURN THE RETURN TYPE
        //       - IF THE SYMBOL ALREADY HAS A VARIANT WITH THE SAME ARGUMENT
        //         TYPES AND IF THE ARGUMENT TYPES DON'T CONTAIN ANY UNTYPED
        //         CLOSURES SIMPLY RETURN THE INDEX OF THAT VARIANT 
        //       - CHECK THE SYMBOL AND RETURN THE INDEX OF THE NEW VARIANT
        throw new RuntimeException("not yet implemented!");
    }

    private DataType checkGlobalVariable(
        Symbols.Symbol symbol
    ) {
        if(symbol.node().type != AstNode.Type.VARIABLE) {
            throw new IllegalArgumentException("must be a variable symbol!");
        }
        // TODO: - IF THE VARIANT COUNT IS 0 TYPE THE VALUE NODE AND
        //         ADD THE VARIANT
        //       - RETURN THE VALUE TYPE
        throw new RuntimeException("not yet implemented!");
    }

    private void enterSymbol(Namespace path, List<DataType> argumentTypes) {
        this.checked.add(new CheckedSymbol(path, argumentTypes));
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
            currentBlock.captures.addAll(branch.captures);
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
        // TODO: ACTUALLY INITIALIZE VARIABLES
        throw new RuntimeException("not yet implemented");
    }

    private List<AstNode> typeNodes(
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

    private AstNode typeNode(
        AstNode node, boolean assignment
    ) throws TypingException {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                // TODO: - MAKE A NEW LIST, AND PUSH DEEP COPIES OF THE CURRENT
                //         BLOCKS ON THERE
                //       - CONSTRUCT AN UNTYPED CLOSURE TYPE AS THE RESULT TYPE
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
                    this.unify(
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
                // TODO: <COPY THE ABOVE FOR CASE VARIANT>
                //       - IF THERE IS NO ELSE BRANCH ENFORCE THAT THE MATCHED
                //         VALUE TYPE DOES NOT HAVE MORE VARIANTS THAN THE
                //         BRANCHES HANDLE
                throw new RuntimeException("not yet implemented!");
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                // TODO: - CHECK THE ASSIGNED NODE WITH 'WRITE'
                //       - CHECK THE VALUE NODE WITH 'READ'
                //       - UNIFY TYPES, RESULT IS RESULT TYPE
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
                // TODO: - IF CALLED NODE IS A MODULE ACCESS
                //         LOOK UP THE SYMBOL AND IF THE SYMBOL
                //         IS ALSO A PROCEDURE 
                //           - TYPE CHECK THE ARGUMENTS
                //           - USE THE ARGUMENTS TO CHECK THE CALLED SYMBOL
                //           - USE THE RETURN TYPE
                //       - ELSE
                //           - TYPE CHECK THE CALLED NODE AND THE ARGUMENTS
                //           - IF THE CALLED TYPE IS NOT A CLOSURE, ERROR
                //           - IF THE CALLED TYPE IS AN UNTYPED CLOSURE, TYPE IT
                //             TO GET THE RETURN TYPE
                //           - ELSE GET THE RETURN TYPE FROM THE ALREADY TYPED
                //             CLOSURE
                throw new RuntimeException("not yet implemented!");
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                // TODO: SIMILAR TO ABOVE, BUT ALSO A BIT FROM OBJECT ACCESS
                throw new RuntimeException("not yet implemented!");
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                // TODO: okay come on man you can probably do this
                //       while half asleep
                throw new RuntimeException("not yet implemented!");
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                // TODO: okay come on man you can probably do this
                //       while half asleep, just unify all the values
                throw new RuntimeException("not yet implemented!");
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                // TODO: okay come on man you can probably do this
                //       while half asleep
                throw new RuntimeException("not yet implemented!");
            }
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                // TODO: okay come on man you can probably do this
                //       while half asleep
                throw new RuntimeException("not yet implemented!");
            }
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                // TODO: - FROM TOP (n - 1) TO BOTTOM (0), LOOK FOR A VARIABLE
                //         WITH THE SAME NAME AS THE DATA NAME
                //       - IF IT'S NOT MUTABLE BUT 'assignment' IS TRUE -> ERROR
                //       - IF IT'S NOT AT THE n-1 BLOCK AND 'assignment' IS TRUE
                //         MARK AS INITIALIZED
                //       - IF IT'S NOT AT THE n-1 BLOCK MARK AS CAPTURED
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
                DataType resultType = this.unify(
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
                DataType valuesType = this.unify(
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
                this.unify(
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
                DataType resultType = this.unify(
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
                // TODO: - LOOK UP THE CALLED SYMBOL
                //       - CHECK THE SYMBOL
                //       - IF IT'S A PROCEDURE, REPLACE WITH A CLOSURE LITERAL
                //         THAT CALLS THE PROCEDURE (TYPE MANUALLY)
                //       - IF IT'S A VARIABLE, USE THE VALUE TYPE
                throw new RuntimeException("not yet implemented!");
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                // TODO: pretty ez
                throw new RuntimeException("not yet implemented!");
            }
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                // TODO: pretty ez
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

    private DataType typeClosureBody(
        AstNode node,
        List<CheckedBlock> context,
        List<DataType> argumentTypes,
        Error.Marking butInitMarking
    ) throws TypingException {
        AstNode.Closure data = node.getValue();
        int argC = data.argumentNames().size();
        if(argC != argumentTypes.size()) {
            throw new TypingException(new Error(
                "Invalid argument count",
                new Error.Marking(
                    node.source,
                    "this closure literal accepts " + argC
                        + " argument" + (argC == 1? "" : "s")
                ),
                butInitMarking
            ));
        }
        this.enterSymbol(new Namespace(List.of("<closure>")), argumentTypes);
        this.currentSymbol().blocks.addAll(context);
        this.enterBlock();
        for(int argI = 0; argI < argC; argI += 1) {
            String argName = data.argumentNames().get(argI);
            DataType argType = argumentTypes.get(argI);
            this.currentBlock().variableTypes.put(
                argName, Optional.of(argType)
            );
            this.currentBlock().variablesMutable.put(argName, false);
        }
        List<AstNode> typedBody = this.typeNodes(data.body());
        CheckedBlock block = this.exitBlock();
        CheckedSymbol symbol = this.exitSymbol();
        boolean hasReturnType = symbol.returnType.isPresent()
            && symbol.returnType.get().type != DataType.Type.UNIT;
        if(!block.returns && hasReturnType) {
            throw new TypingException(
                TypeChecker.makeNotAlwaysReturnError(
                    symbol.returnType.get(), node.source
                )
            );
        }
        node.setValue(new AstNode.Closure(
            data.argumentNames(), Optional.of(argumentTypes),
            Optional.of(symbol.returnType.get()),
            Optional.of(block.captures),
            typedBody
        ));
        return symbol.returnType.get();
    }

    private DataType unify(
        DataType a, DataType b, Source source
    ) throws TypingException {
        if(a.type != b.type) {
            throw new TypingException(TypeChecker.makeIncompatibleError(
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
                DataType.Array dataA = a.getValue();
                DataType.Array dataB = b.getValue();
                DataType elementType = this.unify(
                    dataA.elementType(), dataB.elementType(), source
                );
                value = new DataType.Array(elementType);
            } break;
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject dataA = a.getValue();
                DataType.UnorderedObject dataB = b.getValue();
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
                        Error e = TypeChecker.makeIncompatibleError(
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
                    DataType memberType = this.unify(
                        dataA.memberTypes().get(memberName),
                        dataB.memberTypes().get(memberName), 
                        source
                    );
                    memberTypes.put(memberName, memberType);
                }
                value = new DataType.UnorderedObject(memberTypes);
            } break;
            case CLOSURE: {
                DataType.Closure dataA = a.getValue();
                DataType.Closure dataB = b.getValue();
                boolean aIsTyped = dataA.argumentTypes().isPresent();
                boolean bIsTyped = dataB.argumentTypes().isPresent();
                if(!aIsTyped && !bIsTyped) {
                    List<DataType.UntypedClosureContext> untypedBodies
                        = new ArrayList<>(dataA.untypedBodies());
                    untypedBodies.addAll(dataB.untypedBodies());
                    value = new DataType.Closure(
                        Optional.empty(), Optional.empty(), untypedBodies
                    );
                    break;
                }
                if(!aIsTyped || !bIsTyped) {
                    DataType.Closure dataT = (aIsTyped? dataA : dataB);
                    DataType.Closure dataU = (aIsTyped? dataB : dataA);
                    int argC = dataT.argumentTypes().get().size();
                    DataType returnType = dataT.returnType().get();
                    for(
                        int bodyI = 0; 
                        bodyI < dataU.untypedBodies().size(); 
                        bodyI += 1
                    ) {
                        DataType.UntypedClosureContext closure
                            = dataU.untypedBodies().get(bodyI);
                        DataType cReturnType = this.typeClosureBody(
                            closure.node(), closure.context(),
                            dataT.argumentTypes().get(), 
                            new Error.Marking(
                                source,
                                "but here it is assumed to have " + argC
                                    + " argument" + (argC == 1? "" : "s")
                            )
                        );
                        returnType = this.unify(
                            returnType, cReturnType, source
                        );
                    }
                    value = new DataType.Closure(
                        dataT.argumentTypes(), Optional.of(returnType), 
                        List.of()
                    );
                    break;
                }
                int aArgC = dataA.argumentTypes().get().size();
                int bArgC = dataB.argumentTypes().get().size();
                if(aArgC != bArgC) {
                    String aMsg = "this is a closure with "
                    + aArgC + " argument" + (aArgC == 1? "" : "s");
                    String bMsg = "this is a closure with "
                    + bArgC + " argument" + (bArgC == 1? "" : "s");
                    throw new TypingException(TypeChecker.makeIncompatibleError(
                        source,
                        new Error.Marking(a.source, aMsg),
                        new Error.Marking(b.source, bMsg)
                    ));
                }
                List<DataType> argumentTypes = new ArrayList<>();
                for(int argI = 0; argI < aArgC; argI += 1) {
                    DataType argumentType = this.unify(
                        dataA.argumentTypes().get().get(argI), 
                        dataB.argumentTypes().get().get(argI), 
                        source
                    );
                    argumentTypes.add(argumentType);
                }
                DataType returnType = this.unify(
                    dataA.returnType().get(), dataB.returnType().get(), source
                );
                value = new DataType.Closure(
                    Optional.of(argumentTypes), Optional.of(returnType), 
                    List.of()
                );
            } break;
            case UNION: {
                DataType.Union dataA = a.getValue();
                DataType.Union dataB = b.getValue();
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
                    DataType variantType = this.unify(
                        dataA.variants().get(variantName),
                        dataB.variants().get(variantName), 
                        source
                    );
                    variantTypes.put(variantName, variantType);
                }
                value = new DataType.Union(variantTypes);
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

}

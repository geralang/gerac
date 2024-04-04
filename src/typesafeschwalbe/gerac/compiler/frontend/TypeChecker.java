
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

    private static Error makeNonClosureError(DataType type, Source opSource) {
        return new Error(
            "Call of dynamic value of non-closure type",
            new Error.Marking(
                type.source, "this is " + type.toString()
            ),
            new Error.Marking(
                opSource, "but this call requires a closure"
            )
        );
    }

    private static Error makeInvalidArgCError(
        Source acceptsSource, int acceptsArgC, Source usageSource, int usageArgC
    ) {
        return new Error(
            "Invalid argument count",
            new Error.Marking(
                acceptsSource,
                "this closure accepts " + acceptsArgC
                    + " argument" + (acceptsArgC == 1? "" : "s")
            ),
            new Error.Marking(
                usageSource,
                "but here it is assumed to have " + usageArgC
                    + " argument" + (usageArgC == 1? "" : "s")
            )
        );
    }

    private static Error makeNonbjectError(DataType type, Source opSource) {
        return new Error(
            "Member access done on non-object type",
            new Error.Marking(
                type.source, "this is " + type.toString()
            ),
            new Error.Marking(
                opSource, "but this access requires an object"
            )
        );
    }

    private static Error makeMissingMemberError(
        String memberName, Source opSource, DataType type
    ) {
        return new Error(
            "Object member does not exist",
            new Error.Marking(
                opSource, 
                "but this access requires a member '" + memberName + "'"
            ),
            new Error.Marking(
                type.source, "this is object does not have it"
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
        private Optional<DataType> returnType;
        private int variant;
        private final List<CheckedBlock> blocks;

        private CheckedSymbol(
            Namespace path, List<DataType> argumentTypes, int variant
        ) {
            this.path = path;
            this.argumentTypes = argumentTypes;
            this.variant = variant;
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

    private static record CallCheckResult(
        DataType returnType, int variant
    ) {}

    public CallCheckResult checkProcedureCall(
        Namespace path, Symbols.Symbol symbol, List<DataType> argumentTypes,
        Source callSource
    ) throws TypingException {
        if(symbol.node().type != AstNode.Type.PROCEDURE) {
            throw new IllegalArgumentException("must be a procedure symbol!");
        }
        for(CheckedSymbol encountered: this.checked) {
            if(!encountered.path.equals(path)) { continue; }
            if(!argumentTypes.equals(encountered.argumentTypes)) { continue; }
            boolean isDefinite = encountered.returnType.isEmpty()
                || encountered.returnType.get().isExpandable();
            return new CallCheckResult(
                isDefinite
                    ? encountered.returnType.get()
                    : new DataType(DataType.Type.UNKNOWN, callSource),
                encountered.variant
            );
        }
        for(
            int variantI = 0; 
            variantI < symbol.variants.size(); 
            variantI += 1
        ) {
            List<DataType> variantArgTypes = symbol.variants.get(variantI)
                .<AstNode.Procedure>getValue().argumentTypes().get();
            if(!argumentTypes.equals(variantArgTypes)) { continue; }
            return new CallCheckResult(
                symbol.variants.get(variantI)
                    .<AstNode.Procedure>getValue().returnType().get(),
                variantI
            );
        }
        int variant = symbol.variants.size();
        this.enterSymbol(path, argumentTypes);
        this.currentSymbol().variant = variant;
        this.enterBlock();
        AstNode.Procedure data = symbol.node().getValue();
        if(data.argumentNames().size() != argumentTypes.size()) {
            throw new TypingException(TypeChecker.makeInvalidArgCError(
                symbol.node().source, data.argumentNames().size(),
                callSource, argumentTypes.size()
            ));
        }
        for(int argI = 0; argI < argumentTypes.size(); argI += 1) {
            String argumentName = data.argumentNames().get(argI);
            this.currentBlock().variableTypes.put(
                argumentName, Optional.of(argumentTypes.get(argI))
            );
            this.currentBlock().variablesMutable.put(argumentName, false);
        }
        List<AstNode> bodyTyped = this.typeNodes(data.body());
        CheckedBlock checkedBlock = this.exitBlock();
        CheckedSymbol checkedSymbol = this.exitSymbol();
        boolean hasReturnType = checkedSymbol.returnType.isPresent()
            && checkedSymbol.returnType.get().type != DataType.Type.UNIT;
        if(!checkedBlock.returns && hasReturnType) {
            throw new TypingException(
                TypeChecker.makeNotAlwaysReturnError(
                    checkedSymbol.returnType.get(), symbol.node().source
                )
            );
        }
        AstNode nodeTyped = new AstNode(
            AstNode.Type.PROCEDURE,
            new AstNode.Procedure(
                data.isPublic(), data.name(), data.argumentNames(),
                Optional.of(argumentTypes), checkedSymbol.returnType,
                bodyTyped
            ),
            symbol.node().source
        );
        symbol.variants.add(nodeTyped);
        return new CallCheckResult(
            checkedSymbol.returnType.isPresent()
                ? checkedSymbol.returnType.get()
                : new DataType(DataType.Type.UNIT, symbol.node().source),
            variant
        );
    }

    private DataType checkGlobalVariable(
        Namespace path, Symbols.Symbol symbol
    ) throws TypingException {
        if(symbol.node().type != AstNode.Type.VARIABLE) {
            throw new IllegalArgumentException("must be a variable symbol!");
        }
        if(symbol.variants.size() == 0) {
            this.enterSymbol(path, List.of());
            AstNode typedNode = this.typeNode(symbol.node());
            symbol.variants.add(typedNode);
        }
        AstNode typedNode = symbol.variants.get(0);
        AstNode.Variable data = typedNode.getValue();
        return data.value().get().resultType.get();
    }

    private void enterSymbol(Namespace path, List<DataType> argumentTypes) {
        this.checked.add(new CheckedSymbol(path, argumentTypes, 0));
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
        Map<String, DataType> initialized = null;
        Set<String> captures = new HashSet<>();
        boolean returns = false;
        for(int branchI = 0; branchI < branches.size(); branchI += 1) {
            CheckedBlock branch = branches.get(branchI);
            captures.addAll(branch.captures);
            if(branchI == 0) {
                initialized = new HashMap<>(branch.initializes);
                returns = branch.returns;
            } else {
                initialized.keySet().retainAll(branch.initializes.keySet());
                returns &= branch.returns;
            }
        }
        for(String initializedName: initialized.keySet()) {
            DataType initializedType = initialized.get(initializedName);
            if(currentBlock.variableTypes.containsKey(initializedName)) {
                currentBlock.variableTypes.put(
                    initializedName, Optional.of(initializedType)
                );
            } else {
                currentBlock.initializes.put(initializedName, initializedType);
            }
        }
        for(String captureName: captures) {
            if(!currentBlock.variableTypes.containsKey(captureName)) {
                currentBlock.captures.add(captureName);
            }
        }
        currentBlock.returns |= returns;
    }

    private List<AstNode> typeNodes(
        List<AstNode> nodes
    ) throws TypingException {
        List<AstNode> typedNodes = new ArrayList<>(nodes.size());
        for(AstNode node: nodes) {
            typedNodes.add(this.typeNode(node));
        }
        return typedNodes;
    }

    private AstNode typeNode(AstNode node) throws TypingException {
        return this.typeNode(node, Optional.empty());
    }

    private AstNode typeNode(
        AstNode node, Optional<DataType> assignedType
    ) throws TypingException {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                List<CheckedBlock> context = this.currentSymbol().blocks
                    .stream().map(CheckedBlock::new).toList();
                AstNode newNode = new AstNode(
                    node.type, 
                    new AstNode.Closure(
                        data.argumentNames(), 
                        Optional.empty(), Optional.empty(),
                        Optional.empty(),
                        data.body()
                    ),
                    node.source
                );
                newNode.resultType = Optional.of(new DataType(
                    DataType.Type.CLOSURE, 
                    new DataType.Closure(
                        Optional.empty(), Optional.empty(), List.of(
                        new DataType.UntypedClosureContext(newNode, context)
                    )), 
                    node.source
                ));
                return newNode;
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                Optional<AstNode> valueTyped;
                if(data.value().isPresent()) {
                    AstNode valueTypedD = this.typeNode(
                        data.value().get()
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
                AstNode valueTyped = this.typeNode(data.value());
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
                    AstNode branchValueTyped = this.typeNode(branchValue);
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
                AstNode conditionTyped = this.typeNode(data.condition());
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
                AstNode valueTyped = this.typeNode(data.value());
                DataType valueType = valueTyped.resultType.get();
                if(valueType.type != DataType.Type.UNION) {
                    throw new TypingException(new Error(
                        "Variant matching done on non-union type",
                        new Error.Marking(
                            valueType.source, 
                            "this is " + valueType.toString()
                        ),
                        new Error.Marking(
                            node.source, "but this access requires a union"
                        )
                    ));
                }
                Map<String, DataType> variantTypes = valueType
                    .<DataType.Union>getValue().variants();
                List<CheckedBlock> blocks = new ArrayList<>();
                Optional<List<AstNode>> elseBodyTyped = Optional.empty();
                if(data.elseBody().isPresent()) {
                    this.enterBlock();
                    elseBodyTyped = Optional.of(
                        this.typeNodes(data.elseBody().get())
                    );
                    blocks.add(this.exitBlock());
                }
                List<List<AstNode>> branchBodiesTyped = new ArrayList<>();
                for(
                    int branchI = 0; 
                    branchI < data.branchVariants().size(); 
                    branchI += 1
                ) {
                    String branchVariantName = data.branchVariants()
                        .get(branchI);
                    if(!variantTypes.containsKey(branchVariantName)) {
                        continue;
                    }
                    List<AstNode> branchBody = data.branchBodies().get(branchI);
                    this.enterBlock();
                    if(data.branchVariableNames().get(branchI).isPresent()) {
                        String branchVariableName = data.branchVariableNames()
                            .get(branchI).get();
                        DataType branchVariableType = variantTypes
                            .get(branchVariantName);
                        this.currentBlock().variableTypes.put(
                            branchVariableName, Optional.of(branchVariableType)
                        );
                        this.currentBlock().variablesMutable.put(
                            branchVariableName, false
                        );
                    }
                    List<AstNode> branchBodyTyped = this.typeNodes(branchBody);
                    blocks.add(this.exitBlock());
                    branchBodiesTyped.add(branchBodyTyped);
                }
                if(data.elseBody().isEmpty()) {
                    for(String variantName: variantTypes.keySet()) {
                        if(!data.branchVariants().contains(variantName)) {
                            throw new TypingException(new Error(
                                "Unhandled union variant",
                                new Error.Marking(
                                    valueType.source, 
                                    "this union has a variant '"
                                        + variantName + "'"
                                ),
                                new Error.Marking(
                                    node.source, "which is not handled here"
                                )
                            ));
                        }
                    }
                }
                this.handleBranches(blocks);
                return new AstNode(
                    node.type,
                    new AstNode.CaseVariant(
                        valueTyped, data.branchVariants(), 
                        data.branchVariableNames(), branchBodiesTyped,
                        elseBodyTyped
                    ),
                    node.source
                );
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.right());
                AstNode assignedTyped = this.typeNode(
                    data.left(), valueTyped.resultType
                );
                this.unify(
                    assignedTyped.resultType.get(), 
                    valueTyped.resultType.get(), 
                    node.source
                );
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(assignedTyped, valueTyped),
                    node.source
                );
            }
            case RETURN: {
                AstNode.MonoOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value());
                DataType valueType = valueTyped.resultType.get();
                CheckedSymbol currentSymbol = this.currentSymbol();
                if(currentSymbol.returnType.isEmpty()) {
                    currentSymbol.returnType = Optional.of(valueType);
                } else {
                    currentSymbol.returnType = Optional.of(this.unify(
                        currentSymbol.returnType.get(),
                        valueType,
                        node.source
                    ));
                }
                this.currentBlock().returns = true;
                return new AstNode(
                    node.type, new AstNode.MonoOp(valueTyped), node.source
                );
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                List<AstNode> argumentsTyped = new ArrayList<>();
                List<DataType> argumentTypes = new ArrayList<>();
                int argC = data.arguments().size();
                for(AstNode argument: data.arguments()) {
                    AstNode argumentTyped = this.typeNode(argument);
                    argumentsTyped.add(argumentTyped);
                    argumentTypes.add(argumentTyped.resultType.get());
                }
                boolean isProcCall;
                if(data.called().type == AstNode.Type.MODULE_ACCESS) {
                    Optional<Symbols.Symbol> called = this.symbols.get(
                        data.called().<AstNode.ModuleAccess>getValue().path()
                    );
                    if(!called.isPresent()) {
                        throw new RuntimeException(
                            "module management didn't catch this!"
                        );
                    }
                    isProcCall = called.get().node().type
                        == AstNode.Type.PROCEDURE;
                } else {
                    isProcCall = false;
                }
                if(isProcCall) {
                    Namespace calledPath = data.called()
                        .<AstNode.ModuleAccess>getValue().path();
                    Symbols.Symbol symbol = this.symbols.get(calledPath).get();
                    CallCheckResult call = this.checkProcedureCall(
                        calledPath, symbol, argumentTypes, node.source
                    );
                    DataType returnType = symbol.variants.get(call.variant)
                        .<AstNode.Procedure>getValue().returnType().get();
                    return new AstNode(
                        node.type,
                        new AstNode.Call(
                            new AstNode(
                                AstNode.Type.MODULE_ACCESS,
                                new AstNode.ModuleAccess(
                                    calledPath, Optional.of(call.variant)
                                ),
                                data.called().source
                            ), 
                            argumentsTyped
                        ),
                        node.source,
                        returnType
                    );
                } else {
                    AstNode calledTyped = this.typeNode(data.called());
                    DataType calledType = calledTyped.resultType.get();
                    if(calledType.type != DataType.Type.CLOSURE) {
                        throw new TypingException(
                            TypeChecker.makeNonClosureError(
                                calledType, node.source
                            )
                        );
                    }
                    DataType.Closure calledTypeData = calledType.getValue();
                    DataType returnType;
                    if(calledTypeData.argumentTypes().isEmpty()) {
                        if(calledTypeData.untypedBodies().size() == 0) {
                            throw new RuntimeException("must have a body!");
                        }
                        returnType = null;
                        for(
                            int untypedI = 0; 
                            untypedI < calledTypeData.untypedBodies().size(); 
                            untypedI += 1
                        ) {
                            DataType.UntypedClosureContext ccc = calledTypeData
                                .untypedBodies().get(untypedI);
                            DataType cReturnType = this.typeClosureBody(
                                ccc.node(), ccc.context(), argumentTypes,
                                node.source
                            );
                            if(untypedI == 0) {
                                returnType = cReturnType;
                            } else {
                                returnType = this.unify(
                                    returnType, cReturnType, node.source
                                );
                            }
                        }
                    } else {
                        // NOTE: This could lead to a bug later down the road.
                        //       At some point this should be rewritten to still
                        //       check the closure body with the argument types
                        //       and get the return type of that instead.
                        int acceptsArgC = calledTypeData
                            .argumentTypes().get().size();
                        if(argC != acceptsArgC) {
                            throw new TypingException(
                                TypeChecker.makeInvalidArgCError(
                                    calledType.source, acceptsArgC,
                                    node.source, argC
                                )
                            );
                        }
                        for(int argI = 0; argI < argC; argI += 1) {
                            this.unify(
                                calledTypeData.argumentTypes().get().get(argI),
                                argumentTypes.get(argI),
                                node.source
                            );
                        }
                        returnType = calledTypeData.returnType().get();
                    }
                    return new AstNode(
                        node.type,
                        new AstNode.Call(
                            calledTyped,
                            argumentsTyped
                        ),
                        node.source,
                        returnType
                    );
                }
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                // TODO: SIMILAR TO ABOVE, BUT ALSO A BIT FROM OBJECT ACCESS
                throw new RuntimeException("not yet implemented!");
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                Map<String, AstNode> valuesTyped = new HashMap<>();
                Map<String, DataType> memberTypes = new HashMap<>();
                for(String memberName: data.values().keySet()) {
                    AstNode valueTyped = this.typeNode(
                        data.values().get(memberName)
                    );
                    valuesTyped.put(memberName, valueTyped);
                    memberTypes.put(memberName, valueTyped.resultType.get());
                }
                return new AstNode(
                    node.type,
                    new AstNode.ObjectLiteral(valuesTyped),
                    node.source,
                    new DataType(
                        DataType.Type.UNORDERED_OBJECT, 
                        new DataType.UnorderedObject(memberTypes), 
                        node.source
                    )
                );
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                List<AstNode> valuesTyped = new ArrayList<>();
                DataType elementType = data.values().size() == 0
                    ? new DataType(DataType.Type.UNKNOWN, node.source)
                    : null;
                for(
                    int valueI = 0; 
                    valueI < data.values().size(); 
                    valueI += 1
                ) {
                    AstNode valueTyped = this.typeNode(
                        data.values().get(valueI)
                    );
                    valuesTyped.add(valueTyped);
                    DataType valueType = valueTyped.resultType.get();
                    elementType = valueI == 0
                        ? valueType
                        : this.unify(elementType, valueType, node.source);
                }
                return new AstNode(
                    node.type,
                    new AstNode.ArrayLiteral(valuesTyped),
                    node.source,
                    new DataType(
                        DataType.Type.ARRAY, 
                        new DataType.Array(elementType), 
                        node.source
                    )
                );
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                AstNode accessedTyped = this.typeNode(data.accessed());
                DataType accessedType = accessedTyped.resultType.get();
                if(accessedType.type != DataType.Type.UNORDERED_OBJECT) {
                    throw new TypingException(TypeChecker.makeNonbjectError(
                        accessedType, node.source
                    ));
                }
                DataType resultType = accessedType
                    .<DataType.UnorderedObject>getValue()
                    .memberTypes().get(data.memberName());
                if(resultType == null) {
                    throw new TypingException(
                        TypeChecker.makeMissingMemberError(
                            data.memberName(), node.source, accessedType
                        )
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.ObjectAccess(accessedTyped, data.memberName()),
                    node.source,
                    resultType
                );
            }
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                AstNode accessedTyped = this.typeNode(data.left());
                DataType accessedType = accessedTyped.resultType.get();
                if(accessedType.type == DataType.Type.ARRAY) {
                    throw new TypingException(new Error(
                        "Array access done on non-array type",
                        new Error.Marking(
                            accessedType.source, 
                            "this is " + accessedType.toString()
                        ),
                        new Error.Marking(
                            node.source, "but this access requires an array"
                        )
                    ));
                }
                AstNode indexTyped = this.typeNode(data.right());
                DataType indexType = indexTyped.resultType.get();
                if(indexType.type == DataType.Type.INTEGER) {
                    throw new TypingException(new Error(
                        "Array access done with a non-integer type",
                        new Error.Marking(
                            accessedType.source, 
                            "this is " + accessedType.toString()
                        ),
                        new Error.Marking(
                            node.source, "but this index requires an integer"
                        )
                    ));
                }
                DataType resultType = accessedType
                    .<DataType.Array>getValue().elementType();
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(accessedTyped, indexTyped),
                    node.source,
                    resultType
                );
            }
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                String variableName = data.variableName();
                boolean found = false;
                Optional<DataType> variableType = assignedType;
                for(
                    int blockI = this.currentSymbol().blocks.size() - 1;
                    blockI >= 0;
                    blockI -= 1
                ) {
                    CheckedBlock cBlock = this.currentSymbol()
                        .blocks.get(blockI);
                    if(!cBlock.variableTypes.containsKey(variableName)) {
                        if(cBlock.initializes.containsKey(variableName)) {
                            DataType initType = cBlock.initializes
                                .get(variableName);
                            if(variableType.isPresent()) {
                                variableType = Optional.of(this.unify(
                                    variableType.get(), initType, node.source
                                ));
                            } else {
                                variableType = Optional.of(initType);
                            } 
                        }
                        continue;
                    }
                    boolean mutable = cBlock.variablesMutable.get(
                        data.variableName()
                    );
                    Optional<DataType> initialized = cBlock.variableTypes.get(
                        data.variableName()
                    );
                    if(variableType.isPresent() && initialized.isPresent()) {
                        variableType = Optional.of(this.unify(
                            variableType.get(), initialized.get(), node.source
                        ));
                    } else {
                        variableType = variableType.isPresent()
                            ? variableType : initialized;
                    }
                    boolean wasInitialized = initialized.isPresent();
                    if(assignedType.isEmpty() && !wasInitialized) {
                        throw new TypingException(new Error(
                            "Variable is possibly uninitialized",
                            new Error.Marking(
                                node.source,
                                "it is possible for this variable to not be"
                                    + " initialized, but it is accessed here"
                            )
                        ));
                    }
                    boolean isInitializing = assignedType.isPresent() 
                        && !wasInitialized;
                    if(isInitializing && !mutable) {
                        throw new TypingException(new Error(
                            "Mutation of immutable variable",
                            new Error.Marking(
                                node.source,
                                "it is not possible to assign to this variable"
                                    + " as it has been not been marked"
                                    + " as mutable"
                            )
                        ));
                    }
                    if(assignedType.isPresent() && !wasInitialized) {
                        if(cBlock == this.currentBlock()) {
                            this.currentBlock().variableTypes.put(
                                variableName, variableType
                            );
                        } else {
                            this.currentBlock().initializes.put(
                                variableName, variableType.get()
                            );
                        }
                    }
                    if(cBlock != this.currentBlock()) {
                        this.currentBlock().captures.add(variableName);
                    }
                    found = true;
                    break;
                }
                if(!found) {
                    throw new TypingException(new Error(
                        "Variable does not exist",
                        new Error.Marking(
                            node.source,
                            "there is no variable called '" + variableName + "'"
                        )
                    ));
                }
                return new AstNode(
                    node.type,
                    new AstNode.VariableAccess(variableName),
                    node.source,
                    variableType.get()
                );
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
                AstNode leftTyped = this.typeNode(data.left());
                AstNode rightTyped = this.typeNode(data.right());
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
                AstNode valueTyped = this.typeNode(data.value());
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
                AstNode leftTyped = this.typeNode(data.left());
                AstNode rightTyped = this.typeNode(data.right());
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
                AstNode leftTyped = this.typeNode(data.left());
                AstNode rightTyped = this.typeNode(data.right());
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
                AstNode valueTyped = this.typeNode(data.value());
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
                AstNode leftTyped = this.typeNode(data.left());
                AstNode rightTyped = this.typeNode(data.right());
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
                AstNode.ModuleAccess data = node.getValue();
                Optional<Symbols.Symbol> symbol = this.symbols.get(data.path());
                if(symbol.isEmpty()) {
                    throw new RuntimeException(
                        "module management didn't catch this!"
                    );
                }
                switch(symbol.get().node().type) {
                    case PROCEDURE: {
                        // std::io::println
                        // => |thing| { return std::io::println(thing) }
                        AstNode.Procedure symbolData = symbol.get()
                            .node().getValue();
                        AstNode closureValue = new AstNode(
                            AstNode.Type.CALL,
                            new AstNode.Call(
                                new AstNode(
                                    AstNode.Type.MODULE_ACCESS,
                                    new AstNode.ModuleAccess(
                                        data.path(), Optional.empty()
                                    ),
                                    node.source
                                ),
                                symbolData.argumentNames().stream()
                                    .map(argumentName -> new AstNode(
                                        AstNode.Type.VARIABLE_ACCESS,
                                        new AstNode.VariableAccess(
                                            argumentName
                                        ),
                                        node.source
                                    )).toList()
                            ),
                            node.source
                        );
                        AstNode newNode = new AstNode(
                            AstNode.Type.CLOSURE,
                            new AstNode.Closure(
                                symbolData.argumentNames(),
                                Optional.empty(), Optional.empty(),
                                Optional.empty(),
                                List.of(new AstNode(
                                    AstNode.Type.RETURN,
                                    new AstNode.MonoOp(closureValue),
                                    node.source
                                ))    
                            ),
                            node.source
                        );
                        newNode.resultType = Optional.of(new DataType(
                            DataType.Type.CLOSURE,
                            new DataType.Closure(
                                Optional.empty(), Optional.empty(),
                                List.of(
                                    new DataType.UntypedClosureContext(
                                        newNode, List.of()
                                    )
                                )
                            ),
                            node.source
                        ));
                        return newNode;
                    }
                    case VARIABLE: {
                        DataType variableType = this.checkGlobalVariable(
                            data.path(), symbol.get()
                        );
                        return new AstNode(
                            node.type,
                            new AstNode.ModuleAccess(
                                data.path(), Optional.of(0)
                            ), 
                            node.source, 
                            variableType
                        );
                    }
                    default: {
                        throw new RuntimeException("unhandled symbol type!");
                    }
                }
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value());
                Map<String, DataType> variants = new HashMap<>();
                variants.put(data.variantName(), valueTyped.resultType.get());
                return new AstNode(
                    node.type,
                    new AstNode.VariantLiteral(data.variantName(), valueTyped),
                    node.source,
                    new DataType(
                        DataType.Type.UNION, 
                        new DataType.Union(variants), 
                        node.source
                    )
                );
            }
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.value());
                return new AstNode(
                    node.type,
                    new AstNode.MonoOp(valueTyped),
                    node.source,
                    valueTyped.resultType.get()
                );
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
        Source usageSource
    ) throws TypingException {
        AstNode.Closure data = node.getValue();
        int argC = data.argumentNames().size();
        if(argC != argumentTypes.size()) {
            throw new TypingException(TypeChecker.makeInvalidArgCError(
                node.source, argC, usageSource, argumentTypes.size()
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
                    b.source, "but this is " + b.toString()
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
                            source
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

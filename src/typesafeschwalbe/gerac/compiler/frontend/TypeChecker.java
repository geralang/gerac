
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
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.DataType.ClosureContext;

public class TypeChecker {
    
    private static Error makeNotAlwaysReturnError(DataType type, Source src) {
        return new Error(
            "Possibly missing return value",
            Error.Marking.error(
                src,
                "returns " + type.toString()
                    + ", but only on some branches"
            )
        );
    }

    private static Error makeNonNumericError(DataType type, Source opSource) {
        return new Error(
            "Numeric operation used on non-numeric type",
            Error.Marking.error(
                type.source, "this is " + type.toString()
            ),
            Error.Marking.error(
                opSource, "but this operation requires a numeric type"
            )
        );
    }

    private static Error makeNonBooleanError(DataType type, Source opSource) {
        return new Error(
            "Logical operation used on non-boolean type",
            Error.Marking.error(
                type.source, "this is " + type.toString()
            ),
            Error.Marking.error(
                opSource, "but this operation requires a boolean"
            )
        );
    }

    private static Error makeNonClosureError(DataType type, Source opSource) {
        return new Error(
            "Call of dynamic value of non-closure type",
            Error.Marking.error(
                type.source, "this is " + type.toString()
            ),
            Error.Marking.error(
                opSource, "but this call requires a closure"
            )
        );
    }

    private static Error makeInvalidArgCError(
        Source acceptsSource, int acceptsArgC, Source usageSource, int usageArgC
    ) {
        return new Error(
            "Invalid argument count",
            Error.Marking.error(
                acceptsSource,
                "this closure accepts " + acceptsArgC
                    + " argument" + (acceptsArgC == 1? "" : "s")
            ),
            Error.Marking.error(
                usageSource,
                "but here it is assumed to have " + usageArgC
                    + " argument" + (usageArgC == 1? "" : "s")
            )
        );
    }

    private static Error makeNonbjectError(DataType type, Source opSource) {
        return new Error(
            "Member access done on non-object type",
            Error.Marking.error(
                type.source, "this is " + type.toString()
            ),
            Error.Marking.error(
                opSource, "but this access requires an object"
            )
        );
    }

    private static Error makeMissingMemberError(
        String memberName, Source opSource, DataType type
    ) {
        return new Error(
            "Object member does not exist",
            Error.Marking.error(
                opSource, 
                "this access requires a member '" + memberName + "'"
            ),
            Error.Marking.error(
                type.source, "this is object does not have it"
            )
        );
    }

    private static Error makeNonBooleanAsCondError(
        DataType type, Source condSource
    ) {
        return new Error(
            "Non-boolean value used as condition",
            Error.Marking.error(
                type.source, "this is " + type.toString()
            ),
            Error.Marking.error(
                condSource,
                "but it's usage as the condition here"
                    + " requires it to be a boolean"
            )
        );
    }

    private static Error makeIncompatibleError(
        Source opSource,
        Source aSource, String aType, 
        Source bSource, String bType
    ) {
        return new Error(
            "Incompatible types",
            Error.Marking.info(
                aSource, "this is " + aType
            ),
            Error.Marking.info(
                bSource, "this is " + bType
            ),
            Error.Marking.error(
                opSource, "this expects the two to be compatible"
            )
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
        private final DataType.UnknownOriginMarker unknownOriginMarker;
        private Optional<DataType> returnType;
        private int variant;
        private final List<CheckedBlock> blocks;

        private CheckedSymbol(
            Namespace path, List<DataType> argumentTypes, int variant
        ) {
            this.path = path;
            this.argumentTypes = argumentTypes;
            this.unknownOriginMarker = new DataType.UnknownOriginMarker();
            this.returnType = Optional.empty();
            this.variant = variant;
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
    ) throws ErrorException {
        if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
            throw new IllegalArgumentException("must be a procedure symbol!");
        }
        for(CheckedSymbol encountered: this.checked) {
            // skip closures
            if(encountered.path.elements().size() == 0) { continue; }
            if(!encountered.path.equals(path)) { continue; }
            if(!argumentTypes.equals(encountered.argumentTypes)) { continue; }
            boolean isDefinite = encountered.returnType.isPresent()
                && encountered.returnType.get().isConcrete();
            return new CallCheckResult(
                isDefinite
                    ? encountered.returnType.get()
                    : new DataType(
                        DataType.Type.UNKNOWN, encountered.unknownOriginMarker, 
                        callSource
                    ),
                encountered.variant
            );
        }
        for(
            int variantI = 0; 
            variantI < symbol.variantCount(); 
            variantI += 1
        ) {
            Symbols.Symbol.Procedure variant = symbol
                .<Symbols.Symbol.Procedure>getVariant(variantI);
            if(!argumentTypes.equals(variant.argumentTypes().get())) { 
                continue;
            }
            return new CallCheckResult(
                variant.returnType().get().apply(callSource),
                variantI
            );
        }
        Symbols.Symbol.Procedure data = symbol.getValue();
        if(data.argumentNames().size() != argumentTypes.size()) {
            throw new ErrorException(TypeChecker.makeInvalidArgCError(
                symbol.source, data.argumentNames().size(),
                callSource, argumentTypes.size()
            ));
        }
        if(data.body().isEmpty()) {
            List<DataType> argTypes = new ArrayList<>(argumentTypes);
            if(data.allowedArgumentTypes().isEmpty()) {
                if(data.argumentTypes().isPresent()) {
                    for(int argI = 0; argI < argumentTypes.size(); argI += 1) {
                        this.unify(
                            data.argumentTypes().get().get(argI), 
                            argumentTypes.get(argI), 
                            callSource
                        );
                    }
                }
            } else {
                boolean argumentsValid = data.allowedArgumentTypes().get()
                    .isValid(argumentTypes, this);
                if(!argumentsValid) {
                    throw new ErrorException(new Error(
                        "Invalid arguments for built-in procedure",
                        Error.Marking.error(
                            callSource, "argument types are invalid"
                        ) 
                    ));
                }
            }
            if(symbol.variantCount() == 0) {
                symbol.addVariant(
                    new Symbols.Symbol.Procedure(
                        data.argumentNames(),
                        Optional.empty(), Optional.of(argTypes),
                        data.returnType(), Optional.empty()
                    )
                );
            }
            return new CallCheckResult(
                symbol.<Symbols.Symbol.Procedure>getVariant(0)
                    .returnType().get().apply(callSource),
                0
            );
        }
        int variantIdx = symbol.variantCount();
        this.enterSymbol(path, argumentTypes);
        this.currentSymbol().variant = variantIdx;
        this.enterBlock();
        for(int argI = 0; argI < argumentTypes.size(); argI += 1) {
            String argumentName = data.argumentNames().get(argI);
            this.currentBlock().variableTypes.put(
                argumentName, Optional.of(argumentTypes.get(argI))
            );
            this.currentBlock().variablesMutable.put(argumentName, false);
        }
        List<AstNode> bodyTyped = this.typeNodes(data.body().get());
        CheckedBlock checkedBlock = this.exitBlock();
        CheckedSymbol checkedSymbol = this.exitSymbol();
        boolean hasReturnType = checkedSymbol.returnType.isPresent()
            && checkedSymbol.returnType.get().type != DataType.Type.UNIT;
        if(!checkedBlock.returns && hasReturnType) {
            throw new ErrorException(
                TypeChecker.makeNotAlwaysReturnError(
                    checkedSymbol.returnType.get(), symbol.source
                )
            );
        }
        symbol.addVariant(new Symbols.Symbol.Procedure(
            data.argumentNames(),
            Optional.empty(),
            Optional.of(argumentTypes),
            checkedSymbol.returnType.map(t -> src -> t),
            Optional.of(bodyTyped)
        ));
        DataType returnType = checkedSymbol.returnType.isPresent()
            ? checkedSymbol.returnType.get()
            : new DataType(DataType.Type.UNIT, symbol.source);
        returnType = returnType.replaceUnknown(
            checkedSymbol.unknownOriginMarker, returnType
        );
        return new CallCheckResult(returnType, variantIdx);
    }

    private DataType checkGlobalVariable(
        Namespace path, Symbols.Symbol symbol, Source accessSource
    ) throws ErrorException {
        if(symbol.type != Symbols.Symbol.Type.VARIABLE) {
            throw new IllegalArgumentException("must be a variable symbol!");
        }
        for(CheckedSymbol encountered: this.checked) {
            if(encountered.path.elements().size() == 0) { continue; }
            if(!encountered.path.equals(path)) { continue; }
            return new DataType(
                DataType.Type.UNKNOWN, encountered.unknownOriginMarker, 
                accessSource
            );
        }
        if(symbol.variantCount() != 0) {
            return symbol.<Symbols.Symbol.Variable>getVariant(0)
                .valueType().get();
        }
        Symbols.Symbol.Variable data = symbol.getValue();
        if(data.valueNode().isEmpty()) {
            if(symbol.variantCount() == 0) {
                symbol.addVariant(symbol.<Symbols.Symbol.Variable>getValue());
            }
            return symbol.<Symbols.Symbol.Variable>getVariant(0)
                .valueType().get();
        }
        this.enterSymbol(path, List.of());
        AstNode typedValue = this.typeNode(data.valueNode().get());
        symbol.addVariant(new Symbols.Symbol.Variable(
            typedValue.resultType, Optional.of(typedValue),
            Optional.empty()
        ));
        return typedValue.resultType.get();
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
    ) throws ErrorException {
        List<AstNode> typedNodes = new ArrayList<>(nodes.size());
        for(AstNode node: nodes) {
            typedNodes.add(this.typeNode(node));
        }
        return typedNodes;
    }

    private AstNode typeNode(AstNode node) throws ErrorException {
        return this.typeNode(node, Optional.empty());
    }

    private AstNode typeNode(
        AstNode node, Optional<DataType> assignedType
    ) throws ErrorException {
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
                    new DataType.Closure(List.of(
                        new DataType.ClosureContext(newNode, context)
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
                    throw new ErrorException(
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
                    throw new ErrorException(new Error(
                        "Variant matching done on non-union type",
                        Error.Marking.error(
                            valueType.source, 
                            "this is " + valueType.toString()
                        ),
                        Error.Marking.error(
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
                            throw new ErrorException(new Error(
                                "Unhandled union variant",
                                Error.Marking.error(
                                    valueType.source, 
                                    "this union has a variant '"
                                        + variantName + "'"
                                ),
                                Error.Marking.error(
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
                    isProcCall = called.get().type
                        == Symbols.Symbol.Type.PROCEDURE;
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
                    DataType returnType = call.returnType;
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
                        throw new ErrorException(
                            TypeChecker.makeNonClosureError(
                                calledType, node.source
                            )
                        );
                    }
                    DataType returnType = this.checkClosure(
                        calledType, argumentTypes, node.source
                    );
                    return new AstNode(
                        node.type,
                        new AstNode.Call(
                            calledTyped, argumentsTyped
                        ),
                        node.source,
                        returnType
                    );
                }
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                AstNode accessedTyped = this.typeNode(data.called());
                DataType accessedType = accessedTyped.resultType.get();
                DataType calledType;
                if(accessedType.type == DataType.Type.UNORDERED_OBJECT) {
                    calledType = accessedType
                        .<DataType.UnorderedObject>getValue()
                        .memberTypes().get(data.memberName());
                } else if(accessedType.type == DataType.Type.ORDERED_OBJECT) {
                    calledType = null;
                    DataType.OrderedObject accessedData
                        = accessedType.getValue();
                    for(
                        int memberI = 0; 
                        memberI < accessedData.memberNames().size(); 
                        memberI += 1
                    ) {
                        boolean nameMatches = accessedData.memberNames()
                            .get(memberI).equals(data.memberName());
                        if(!nameMatches) { continue; }
                        accessedType = accessedData.memberTypes().get(memberI);
                        break;
                    }
                } else {
                    throw new ErrorException(TypeChecker.makeNonbjectError(
                        accessedType, node.source
                    ));
                }
                if(calledType == null) {
                    throw new ErrorException(
                        TypeChecker.makeMissingMemberError(
                            data.memberName(), node.source, accessedType
                        )
                    );
                }
                List<AstNode> argumentsTyped = new ArrayList<>();
                List<DataType> argumentTypes = new ArrayList<>();
                argumentTypes.add(accessedType);
                for(AstNode argument: data.arguments()) {
                    AstNode argumentTyped = this.typeNode(argument);
                    argumentsTyped.add(argumentTyped);
                    argumentTypes.add(argumentTyped.resultType.get());
                }
                if(calledType.type != DataType.Type.CLOSURE) {
                    throw new ErrorException(
                        TypeChecker.makeNonClosureError(
                            calledType, node.source
                        )
                    );
                }
                DataType returnType = this.checkClosure(
                    calledType, argumentTypes, node.source
                );
                return new AstNode(
                    node.type,
                    new AstNode.MethodCall(
                        accessedTyped, data.memberName(), argumentsTyped
                    ),
                    node.source,
                    returnType
                );
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
                    ? new DataType(
                        DataType.Type.UNKNOWN, 
                        new DataType.UnknownOriginMarker(), 
                        node.source
                    )
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
            case REPEATING_ARRAY_LITERAL: {
                AstNode.BiOp data = node.getValue();
                AstNode valueTyped = this.typeNode(data.left());
                DataType valueType = valueTyped.resultType.get();
                AstNode sizeTyped = this.typeNode(data.right());
                DataType sizeType = sizeTyped.resultType.get();
                if(sizeType.type != DataType.Type.INTEGER) {
                    throw new ErrorException(new Error(
                        "Array size is a non-integer type",
                        Error.Marking.error(
                            sizeType.source, 
                            "this is " + sizeType.toString()
                        ),
                        Error.Marking.error(
                            sizeTyped.source, 
                            "but the size value is required to be an integer"
                        )
                    ));
                }
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(valueTyped, sizeTyped),
                    node.source,
                    new DataType(
                        DataType.Type.ARRAY, 
                        new DataType.Array(valueType), 
                        node.source
                    )
                );
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                AstNode accessedTyped = this.typeNode(data.accessed());
                DataType accessedType = accessedTyped.resultType.get();
                DataType resultType;
                if(accessedType.type == DataType.Type.UNORDERED_OBJECT) {
                    resultType = accessedType
                        .<DataType.UnorderedObject>getValue()
                        .memberTypes().get(data.memberName());
                } else if(accessedType.type == DataType.Type.ORDERED_OBJECT) {
                    resultType = null;
                    DataType.OrderedObject accessedData
                        = accessedType.getValue();
                    for(
                        int memberI = 0; 
                        memberI < accessedData.memberNames().size(); 
                        memberI += 1
                    ) {
                        boolean nameMatches = accessedData.memberNames()
                            .get(memberI).equals(data.memberName());
                        if(!nameMatches) { continue; }
                        resultType = accessedData.memberTypes().get(memberI);
                        break;
                    }
                } else {
                    throw new ErrorException(TypeChecker.makeNonbjectError(
                        accessedType, node.source
                    ));
                }
                if(resultType == null) {
                    throw new ErrorException(
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
                if(accessedType.type != DataType.Type.ARRAY) {
                    throw new ErrorException(new Error(
                        "Array access done on non-array type",
                        Error.Marking.error(
                            accessedType.source, 
                            "this is " + accessedType.toString()
                        ),
                        Error.Marking.error(
                            node.source, "but this access requires an array"
                        )
                    ));
                }
                AstNode indexTyped = this.typeNode(data.right());
                DataType indexType = indexTyped.resultType.get();
                if(indexType.type != DataType.Type.INTEGER) {
                    throw new ErrorException(new Error(
                        "Array indexed with a non-integer type",
                        Error.Marking.error(
                            indexType.source, 
                            "this is " + indexType.toString()
                        ),
                        Error.Marking.error(
                            indexTyped.source, 
                            "but the index is required to be an integer"
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
                        throw new ErrorException(new Error(
                            "Variable is possibly uninitialized",
                            Error.Marking.error(
                                node.source,
                                "it is possible for this variable to not be"
                                    + " initialized, but it is accessed here"
                            )
                        ));
                    }
                    boolean isInitializing = assignedType.isPresent() 
                        && !wasInitialized;
                    if(isInitializing && !mutable) {
                        throw new ErrorException(new Error(
                            "Mutation of immutable variable",
                            Error.Marking.error(
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
                    if(assignedType.isPresent()) {
                        cBlock.variableTypes.put(variableName, variableType);
                    }
                    found = true;
                    break;
                }
                if(!found) {
                    throw new ErrorException(new Error(
                        "Variable does not exist",
                        Error.Marking.error(
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
                    throw new ErrorException(
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
                    throw new ErrorException(
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
                    throw new ErrorException(
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
                    throw new ErrorException(
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
                    throw new ErrorException(
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
                switch(symbol.get().type) {
                    case PROCEDURE: {
                        // std::io::println
                        // => |thing| { return std::io::println(thing) }
                        Symbols.Symbol.Procedure symbolData = symbol.get()
                            .getValue();
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
                                Optional.of(List.of(new AstNode(
                                    AstNode.Type.RETURN,
                                    new AstNode.MonoOp(closureValue),
                                    node.source
                                )))
                            ),
                            node.source
                        );
                        newNode.resultType = Optional.of(new DataType(
                            DataType.Type.CLOSURE,
                            new DataType.Closure(List.of(
                                new DataType.ClosureContext(
                                    newNode, List.of()
                                )
                            )),
                            node.source
                        ));
                        return newNode;
                    }
                    case VARIABLE: {
                        DataType variableType = this.checkGlobalVariable(
                            data.path(), symbol.get(), node.source
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

    public DataType checkClosure(
        DataType closureType,
        List<DataType> argumentTypes,
        Source usageSource
    ) throws ErrorException {
        if(closureType.type != DataType.Type.CLOSURE) {
            throw new RuntimeException("must be a closure!");
        }
        List<DataType> argTypes = new ArrayList<>(argumentTypes);
        DataType.Closure typeData = closureType.getValue();
        DataType returnType = null;
        for(int bodyI = 0; bodyI < typeData.bodies().size(); bodyI += 1) {
            ClosureContext context = typeData.bodies().get(bodyI);
            AstNode.Closure nodeData = context.node().getValue();
            if(argTypes.size() != nodeData.argumentNames().size()) {
                throw new ErrorException(TypeChecker.makeInvalidArgCError(
                    context.node().source, nodeData.argumentNames().size(),
                    usageSource, argTypes.size()
                ));
            }
            if(nodeData.argumentTypes().isPresent()) {
                if(nodeData.argumentTypes().get().equals(argTypes)) {
                    if(bodyI == 0) {
                        returnType = nodeData.returnType().get();
                    } else {
                        returnType = this.unify(
                            returnType, nodeData.returnType().get(), usageSource
                        );
                    }
                }
                for(int argI = 0; argI < argTypes.size(); argI += 1) {
                    DataType argType = this.unify(
                        argTypes.get(argI),
                        nodeData.argumentTypes().get().get(argI),
                        usageSource
                    );
                    argTypes.set(argI, argType);
                }
            }
            DataType contextReturnType;
            Optional<List<AstNode>> contextTypedBody;
            Optional<Set<String>> contextCaptures;
            if(nodeData.body().isPresent()) {
                this.enterSymbol(new Namespace(List.of()), argTypes);
                this.currentSymbol().blocks.addAll(context.context());
                this.enterBlock();
                for(int argI = 0; argI < argTypes.size(); argI += 1) {
                    String argName = nodeData.argumentNames().get(argI);
                    DataType argType = argTypes.get(argI);
                    this.currentBlock().variableTypes.put(
                        argName, Optional.of(argType)
                    );
                    this.currentBlock().variablesMutable.put(argName, false);
                }
                List<AstNode> typedBody = this.typeNodes(nodeData.body().get());
                CheckedBlock block = this.exitBlock();
                CheckedSymbol symbol = this.exitSymbol();
                boolean hasReturnType = symbol.returnType.isPresent()
                && symbol.returnType.get().type != DataType.Type.UNIT;
                if(!block.returns && hasReturnType) {
                    throw new ErrorException(
                        TypeChecker.makeNotAlwaysReturnError(
                            symbol.returnType.get(), context.node().source
                        )
                    );
                }
                contextReturnType = hasReturnType
                    ? symbol.returnType.get()
                    : new DataType(DataType.Type.UNIT, context.node().source);
                contextTypedBody = Optional.of(typedBody);
                contextCaptures = Optional.of(block.captures);
                if(nodeData.returnType().isPresent()) {
                    contextReturnType = this.unify(
                        contextReturnType, nodeData.returnType().get(), usageSource
                    );
                } 
            } else {
                if(nodeData.returnType().isEmpty()) {
                    throw new RuntimeException(
                        "closures without bodies must specify types!"
                    );
                }
                contextReturnType = nodeData.returnType().get();
                contextTypedBody = Optional.empty();
                contextCaptures = Optional.empty();
            }
            context.node().setValue(new AstNode.Closure(
                nodeData.argumentNames(), Optional.of(argTypes),
                Optional.of(contextReturnType),
                contextCaptures,
                contextTypedBody
            ));
            if(bodyI == 0) {
                returnType = contextReturnType;
            } else {
                returnType = this.unify(
                    returnType, contextReturnType, usageSource
                );
            }
        }
        return returnType;
    }

    private DataType unify(
        DataType a, DataType b, Source source
    ) throws ErrorException {
        boolean isValid = a.type == b.type
            || (a.type == DataType.Type.ORDERED_OBJECT
                && b.type == DataType.Type.UNORDERED_OBJECT
            ) || (b.type == DataType.Type.ORDERED_OBJECT
                && a.type == DataType.Type.UNORDERED_OBJECT
            );
        if(!isValid) {
            throw new ErrorException(TypeChecker.makeIncompatibleError(
                source, 
                a.source, a.toString(),
                b.source, b.toString()
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
                        String inMsg = "an object with a property "
                            + "'" + memberName + "'";
                        String withoutMsg = "an object without it";
                        Error e = TypeChecker.makeIncompatibleError(
                            source, 
                            (inA? a : b).source, inMsg,
                            (inA? b : a).source, withoutMsg 
                        );
                        throw new ErrorException(e);
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
            case ORDERED_OBJECT: {
                DataType.OrderedObject aData = a.getValue();
                Map<String, DataType> aMemberTypes = new HashMap<>();
                for(
                    int memberI = 0;
                    memberI < aData.memberNames().size();
                    memberI += 1
                ) {
                    aMemberTypes.put(
                        aData.memberNames().get(memberI), 
                        aData.memberTypes().get(memberI)
                    );
                }
                return this.unify(
                    b,
                    new DataType(
                        DataType.Type.UNORDERED_OBJECT,
                        new DataType.UnorderedObject(aMemberTypes),
                        a.source
                    ),
                    source
                );
            }
            case CLOSURE: {
                DataType.Closure dataA = a.getValue();
                DataType.Closure dataB = b.getValue();
                List<DataType.ClosureContext> contexts
                    = new ArrayList<>(dataA.bodies());
                contexts.addAll(dataB.bodies());
                value = new DataType.Closure(contexts);
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
                        continue;
                    }
                    if(!inB) {
                        variantTypes.put(
                            variantName, dataA.variants().get(variantName)
                        );
                        continue;
                    }
                    DataType variantType = this.unify(
                        dataA.variants().get(variantName),
                        dataB.variants().get(variantName), 
                        source
                    );
                    variantTypes.put(variantName, variantType);
                }
                value = new DataType.Union(variantTypes);
            } break;
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

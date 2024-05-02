package typesafeschwalbe.gerac.compiler.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.DataType;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

public class Lowerer {
    
    private static record BlockVariables(
        Map<String, Ir.Variable> variables,
        Map<String, Integer> lastUpdates
    ) {
        @Override
        public BlockVariables clone() {
            return new BlockVariables(
                new HashMap<>(this.variables), 
                new HashMap<>(this.lastUpdates)
            );
        }
    }

    private final Symbols symbols;
    private final TypeContext typeContext;
    private final Interpreter interpreter;

    public final Ir.StaticValues staticValues;
    private Ir.Context context;
    private final List<List<Ir.Instr>> blockStack;
    private List<BlockVariables> variableStack;

    public Lowerer(
        Map<String, String> sourceFiles,
        Symbols symbols, TypeContext typeContext
    ) {
        this.symbols = symbols;
        this.typeContext = typeContext;
        this.interpreter = new Interpreter(sourceFiles, symbols);
        this.staticValues = new Ir.StaticValues(this);
        this.blockStack = new LinkedList<>();
        this.variableStack = new LinkedList<>();
    }

    public Optional<Error> lowerProcedures() {
        for(Namespace symbolPath: this.symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = this.symbols.get(symbolPath).get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) { continue; }
            Symbols.Symbol.Procedure symbolData = symbol.getValue();
            if(symbolData.body().isEmpty()) { continue; }
            for(
                int variantI = 0;
                variantI < symbol.variantCount();
                variantI += 1
            ) {
                Symbols.Symbol.Procedure variant = symbol.getVariant(variantI);
                boolean mapped = false;
                for(int cVarI = 0; cVarI < variantI; cVarI += 1) {
                    Symbols.Symbol.Procedure cVariant = symbol
                        .getVariant(cVarI);
                    boolean equals = true;
                    for(
                        int argI = 0; 
                        argI < variant.argumentTypes().get().size(); argI += 1
                    ) {
                        if(this.typeContext.deepEquals(
                            variant.argumentTypes().get().get(argI),
                            cVariant.argumentTypes().get().get(argI)
                        )) { continue; }
                        equals = false;
                        break;
                    }
                    equals &= this.typeContext.deepEquals(
                        variant.returnType().get(), cVariant.returnType().get()
                    );
                    if(!equals) { continue; }
                    symbol.setVariant(variantI, null);
                    symbol.mapVariantIdx(variantI, cVarI);
                    mapped = true;
                    break;
                }
                if(mapped) { continue; }
                this.context = new Ir.Context();
                this.enterBlock();
                for(
                    int argI = 0; 
                    argI < variant.argumentNames().size(); 
                    argI += 1
                ) {
                    String argName = variant.argumentNames().get(argI);
                    TypeVariable argType = variant.argumentTypes().get()
                        .get(argI);
                    Ir.Variable variable = this.context
                        .allocateArgument(argType);
                    this.variables().variables.put(argName, variable);
                    this.variables().lastUpdates.put(argName, variable.version);
                }
                List<Ir.Instr> body;
                try {
                    this.lowerNodes(
                        variant.body().get()
                    );
                    body = this.exitBlock();
                } catch(ErrorException e) {
                    this.exitBlock();
                    return Optional.of(e.error);
                }
                symbol.setVariant(
                    variantI,
                    new Symbols.Symbol.Procedure(
                        variant.argumentNames(),
                        Optional.empty(),
                        variant.argumentTypes(),
                        variant.returnType(),
                        variant.body(),
                        Optional.of(context), Optional.of(body)
                    )
                );
            }
        }
        return Optional.empty();
    }

    private void enterBlock() {
        this.blockStack.add(new ArrayList<>());
        this.variableStack.add(
            this.variableStack.size() > 0
                ? this.variables().clone()
                : new BlockVariables(new HashMap<>(), new HashMap<>())
        );
    }

    private List<Ir.Instr> block() {
        return this.blockStack.get(this.blockStack.size() - 1);
    } 

    private BlockVariables variables() {
        return this.variableStack.get(this.variableStack.size() - 1);
    }

    private List<Ir.Instr> exitBlock() {
        List<Ir.Instr> block = this.block();
        this.blockStack.remove(this.blockStack.size() - 1);
        this.variableStack.remove(this.variableStack.size() - 1);
        return block;
    }

    Ir.StaticValue.Closure lowerClosureValue(
        Value.Closure v
    ) throws ErrorException {
        boolean isEmpty = v.captureTypes.isEmpty();
        Map<String, Ir.StaticValue> captureValues = null;
        List<TypeVariable> argumentTypes = null;
        Ir.Context context = null;
        List<Ir.Instr> body = null;
        if(!isEmpty) {
            captureValues = new HashMap<>();
            for(String capture: v.captureTypes.keySet()) {
                Value captureValue = null;
                for(
                    int frameI = v.environment.size() - 1; 
                    frameI >= 0; 
                    frameI -= 1
                ) {
                    Map<String, Optional<Value>> frame = v.environment
                        .get(frameI);
                    if(!frame.containsKey(capture)) { continue; }
                    captureValue = frame.get(capture).get();
                    break;
                }
                if(captureValue == null) {
                    throw new RuntimeException("capture value should exist!");
                }
                captureValues.put(capture, this.staticValues.add(captureValue));
            }
            argumentTypes = v.argumentTypes;
            Ir.Context prevContext = this.context;
            List<BlockVariables> prevVars = this.variableStack;
            this.context = new Ir.Context();
            this.variableStack = new LinkedList<>();
            this.enterBlock();
            for(
                int argI = 0; 
                argI < v.argumentNames.size(); 
                argI += 1
            ) {
                String argName = v.argumentNames.get(argI);
                TypeVariable argType = v.argumentTypes.get(argI);
                Ir.Variable variable = this.context
                    .allocateArgument(argType);
                this.variables().variables.put(argName, variable);
                this.variables().lastUpdates.put(argName, variable.version);
            }
            this.lowerNodes(v.body);
            body = this.exitBlock();
            context = this.context;
            this.context = prevContext;
            this.variableStack = prevVars;
        }
        return new Ir.StaticValue.Closure(
            v, isEmpty, captureValues, argumentTypes, context, body
        );
    }

    private void addPhi(
        List<BlockVariables> branchVariables
    ) {
        for(String name: this.variables().variables.keySet()) {
            Ir.Variable variable = this.variables().variables.get(name);
            int ogVersion = this.variables().lastUpdates.get(name);
            Set<Integer> versions = new HashSet<>();
            for(
                int branchI = 0; branchI < branchVariables.size(); branchI += 1
            ) {
                BlockVariables branch = branchVariables.get(branchI);
                Integer bVersion = branch.lastUpdates.get(name);
                versions.add(bVersion != null? bVersion : ogVersion);
            }
            List<Ir.Variable> options = versions.stream()
                .map(v -> new Ir.Variable(variable.index, v)).toList();
            variable.version += 1;
            this.block().add(new Ir.Instr(
                Ir.Instr.Type.PHI,
                options,
                null,
                Optional.of(variable.clone())
            ));
            this.variables().lastUpdates.put(name, variable.version);
        }
    }

    private void lowerNodes(
        List<AstNode> nodes
    ) throws ErrorException {
        for(AstNode node: nodes) {
            this.lowerNode(node);
        }
    }

    private Optional<Ir.Variable> lowerNode(
        AstNode node
    ) throws ErrorException {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                List<String> captureNames = new ArrayList<>();
                List<Ir.Variable> captureValues = new ArrayList<>();
                for(String captureName: data.captures().get().get().keySet()) {
                    captureNames.add(captureName);
                    if(this.variables().variables.containsKey(captureName)) {
                        Ir.Variable var = this.variables().variables
                            .get(captureName).clone();
                        captureValues.add(var);
                        this.context.markCaptured(var, captureName);
                    } else {
                        Ir.Variable value = this.context.allocate(
                            data.captures().get().get().get(captureName)
                        );
                        this.block().add(new Ir.Instr(
                            Ir.Instr.Type.READ_CAPTURE,
                            List.of(), 
                            new Ir.Instr.CaptureAccess(captureName),
                            Optional.of(value)
                        ));
                        captureValues.add(value);
                    }
                }
                Ir.Context prevContext = this.context;
                List<BlockVariables> prevVars = this.variableStack;
                this.context = new Ir.Context();
                this.variableStack = new LinkedList<>();
                this.enterBlock();
                for(
                    int argI = 0; 
                    argI < data.argumentNames().size(); 
                    argI += 1
                ) {
                    String argName = data.argumentNames().get(argI);
                    TypeVariable argType = data.argumentTypes()
                        .get().get().get(argI);
                    Ir.Variable variable = this.context
                        .allocateArgument(argType);
                    this.variables().variables.put(argName, variable);
                    this.variables().lastUpdates.put(argName, variable.version);
                }
                this.lowerNodes(data.body());
                List<Ir.Instr> body = this.exitBlock();
                Ir.Context context = this.context;
                this.context = prevContext;
                this.variableStack = prevVars;
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_CLOSURE,
                    captureValues,
                    new Ir.Instr.LoadClosure(
                        data.argumentTypes().get().get(), 
                        data.returnType().get().get(), 
                        captureNames, context, body
                    ), 
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                Ir.Variable value;
                if(data.value().isPresent()) {
                    value = this.lowerNode(data.value().get()).get();
                } else {
                    value = this.context.allocate(data.valueType().get().get());
                }
                this.variables().variables.put(data.name(), value);
                this.variables().lastUpdates.put(data.name(), value.version);
                return Optional.empty();
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                Ir.Variable value = this.lowerNode(data.value()).get();
                List<BlockVariables> branches = new ArrayList<>();
                List<Ir.StaticValue> branchValues = new ArrayList<>();
                List<List<Ir.Instr>> branchBodies = new ArrayList<>();
                for(
                    int branchI = 0; 
                    branchI < data.branchBodies().size(); 
                    branchI += 1
                ) {
                    Value branchValue = this.interpreter.evaluateNode(
                        data.branchValues().get(branchI)
                    );
                    branchValues.add(this.staticValues.add(branchValue));
                    this.enterBlock();
                    this.lowerNodes(data.branchBodies().get(branchI));
                    branches.add(this.variables());
                    branchBodies.add(this.exitBlock());
                }
                this.enterBlock();
                this.lowerNodes(data.elseBody());
                branches.add(this.variables());
                List<Ir.Instr> elseBody = this.exitBlock();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.BRANCH_ON_VALUE,
                    List.of(value),
                    new Ir.Instr.BranchOnValue(
                        branchValues, 
                        branchBodies, 
                        elseBody
                    ),
                    Optional.empty()
                ));
                this.addPhi(branches);
                return Optional.empty();
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                Ir.Variable condition = this.lowerNode(data.condition()).get();
                this.enterBlock();
                this.lowerNodes(data.ifBody());
                BlockVariables ifVars = this.variables();
                List<Ir.Instr> ifBody = this.exitBlock();
                this.enterBlock();;
                this.lowerNodes(data.elseBody());
                BlockVariables elseVars = this.variables();
                List<Ir.Instr> elseBody = this.exitBlock();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.BRANCH_ON_VALUE,
                    List.of(condition),
                    new Ir.Instr.BranchOnValue(
                        List.of(this.staticValues.add(new Value.Bool(true))), 
                        List.of(ifBody), 
                        elseBody
                    ),
                    Optional.empty()
                ));
                this.addPhi(List.of(ifVars, elseVars));
                return Optional.empty();
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                Ir.Variable value = this.lowerNode(data.value()).get();
                DataType.Union<TypeVariable> valueVariants = this.typeContext
                    .get(data.value().resultType.get()).getValue();
                List<BlockVariables> branches = new ArrayList<>();
                List<Optional<Ir.Variable>> branchVariables = new ArrayList<>();
                List<List<Ir.Instr>> branchBodies = new ArrayList<>();
                for(
                    int branchI = 0;
                    branchI < data.branchBodies().size();
                    branchI += 1
                ) {
                    String variantName = data.branchVariants().get(branchI);
                    TypeVariable variantType = valueVariants.variantTypes()
                        .get(variantName);
                    this.enterBlock();
                    if(data.branchVariableNames().get(branchI).isPresent()) {
                        String bVarName = data.branchVariableNames()
                            .get(branchI).get();
                        Ir.Variable bVar = this.context.allocate(variantType);
                        this.variables().variables.put(bVarName, bVar);
                        this.variables().lastUpdates.put(
                            bVarName, bVar.version
                        );
                        branchVariables.add(Optional.of(bVar.clone()));
                    } else {
                        branchVariables.add(Optional.empty());
                    }
                    this.lowerNodes(data.branchBodies().get(branchI));
                    branches.add(this.variables());
                    branchBodies.add(this.exitBlock());
                }
                this.enterBlock();
                if(data.elseBody().isPresent()) {
                    this.lowerNodes(data.elseBody().get());
                }
                branches.add(this.variables());
                List<Ir.Instr> elseBody = this.exitBlock();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.BRANCH_ON_VARIANT,
                    List.of(value),
                    new Ir.Instr.BranchOnVariant(
                        data.branchVariants(), branchVariables,
                        branchBodies, elseBody
                    ),
                    Optional.empty()
                ));
                this.addPhi(branches);
                return Optional.empty();
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable value = this.lowerNode(data.right()).get();
                switch(data.left().type) {
                    case OBJECT_ACCESS: {
                        AstNode.ObjectAccess accessData = data.left()
                            .getValue();
                        Ir.Variable accessed = this
                            .lowerNode(accessData.accessed()).get();
                        this.block().add(new Ir.Instr(
                            Ir.Instr.Type.WRITE_OBJECT,
                            List.of(accessed, value),
                            new Ir.Instr.ObjectAccess(accessData.memberName()), 
                            Optional.empty()
                        ));
                    } break;
                    case ARRAY_ACCESS: {
                        AstNode.BiOp accessData = data.left()
                            .getValue();
                        Ir.Variable accessed = this
                            .lowerNode(accessData.left()).get();
                        Ir.Variable index = this
                            .lowerNode(accessData.right()).get();
                        this.block().add(new Ir.Instr(
                            Ir.Instr.Type.WRITE_ARRAY,
                            List.of(accessed, index, value),
                            new Ir.Instr.ArrayAccess(data.left().source), 
                            Optional.empty()
                        ));
                    } break;
                    case VARIABLE_ACCESS: {
                        AstNode.VariableAccess accessData = data.left()
                            .getValue();
                        boolean isLocal = this.variables().variables
                            .containsKey(accessData.variableName());
                        if(isLocal) {
                            Ir.Variable accessed = this.variables()
                                .variables.get(accessData.variableName());
                            accessed.version += 1;
                            this.variables().lastUpdates.put(
                                accessData.variableName(), accessed.version
                            );
                            this.block().add(new Ir.Instr(
                                Ir.Instr.Type.COPY,
                                List.of(value),
                                null, 
                                Optional.of(accessed.clone())
                            ));
                        } else {
                            this.block().add(new Ir.Instr(
                                Ir.Instr.Type.WRITE_CAPTURE,
                                List.of(value),
                                new Ir.Instr.CaptureAccess(
                                    accessData.variableName()
                                ),
                                Optional.empty()
                            ));
                        }
                    } break;
                    default: {
                        throw new RuntimeException("unhandled node type!");
                    }
                }
                return Optional.empty();
            }
            case RETURN: {
                AstNode.MonoOp data = node.getValue();
                Ir.Variable value = this.lowerNode(data.value()).get();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.RETURN,
                    List.of(value),
                    null,
                    Optional.empty()
                ));
                return Optional.empty();
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                Ir.Variable called = this.lowerNode(data.called()).get();
                List<Ir.Variable> arguments = new ArrayList<>();
                arguments.add(called);
                for(AstNode argument: data.arguments()) {
                    arguments.add(this.lowerNode(argument).get());
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.CALL_CLOSURE,
                    arguments,
                    new Ir.Instr.CallClosure(node.source),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case PROCEDURE_CALL: {
                AstNode.ProcedureCall data = node.getValue();
                List<Ir.Variable> arguments = new ArrayList<>();
                for(AstNode argument: data.arguments()) {
                    arguments.add(this.lowerNode(argument).get());
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.CALL_PROCEDURE,
                    arguments,
                    new Ir.Instr.CallProcedure(
                        data.path(), data.variant(), node.source
                    ),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                Ir.Variable accessed = this.lowerNode(data.called()).get();
                DataType.UnorderedObject<TypeVariable> accessedObject
                    = this.typeContext.get(data.called().resultType.get())
                        .getValue(); 
                TypeVariable calledType = accessedObject.memberTypes()
                    .get(data.memberName());
                Ir.Variable called = this.context.allocate(calledType);
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.READ_OBJECT,
                    List.of(accessed),
                    new Ir.Instr.ObjectAccess(data.memberName()),
                    Optional.of(called)
                ));
                List<Ir.Variable> arguments = new ArrayList<>();
                arguments.add(called);
                arguments.add(accessed);
                for(AstNode argument: data.arguments()) {
                    arguments.add(this.lowerNode(argument).get());
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.CALL_CLOSURE,
                    arguments,
                    new Ir.Instr.CallClosure(node.source),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                List<String> names = new ArrayList<>();
                List<Ir.Variable> values = new ArrayList<>();
                for(String member: data.values().keySet()) {
                    names.add(member);
                    values.add(this.lowerNode(data.values().get(member)).get());
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_OBJECT,
                    values,
                    new Ir.Instr.LoadObject(names),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                List<Ir.Variable> values = new ArrayList<>();
                for(AstNode value: data.values()) {
                    values.add(this.lowerNode(value).get());
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_FIXED_ARRAY,
                    values,
                    null,
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case REPEATING_ARRAY_LITERAL: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable value = this.lowerNode(data.left()).get();
                Ir.Variable size = this.lowerNode(data.right()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_REPEAT_ARRAY,
                    List.of(value, size),
                    new Ir.Instr.LoadRepeatArray(node.source),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                Ir.Variable accessed = this.lowerNode(data.accessed()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.READ_OBJECT,
                    List.of(accessed), 
                    new Ir.Instr.ObjectAccess(data.memberName()), 
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable accessed = this.lowerNode(data.left()).get();
                Ir.Variable index = this.lowerNode(data.right()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.READ_ARRAY,
                    List.of(accessed, index), 
                    new Ir.Instr.ArrayAccess(node.source),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                if(this.variables().variables.containsKey(data.variableName())) {
                    return Optional.of(
                        this.variables().variables.get(data.variableName())
                            .clone()
                    );
                }
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.READ_CAPTURE,
                    List.of(), 
                    new Ir.Instr.CaptureAccess(data.variableName()),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case BOOLEAN_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                boolean value = Boolean.valueOf(data.value());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_BOOLEAN,
                    List.of(), new Ir.Instr.LoadBoolean(value),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case INTEGER_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                long value = Long.valueOf(data.value());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_INTEGER,
                    List.of(), new Ir.Instr.LoadInteger(value),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case FLOAT_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                double value = Double.valueOf(data.value());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_FLOAT,
                    List.of(), new Ir.Instr.LoadFloat(value),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case STRING_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_STRING,
                    List.of(), new Ir.Instr.LoadString(data.value()),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case UNIT_LITERAL: {
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_UNIT,
                    List.of(), null, Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
            case LESS_THAN:
            case GREATER_THAN:
            case LESS_THAN_EQUAL:
            case GREATER_THAN_EQUAL:
            case EQUALS:
            case NOT_EQUALS: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable left = this.lowerNode(data.left()).get();
                Ir.Variable right = this.lowerNode(data.right()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                Ir.Instr.Type t;
                Object v = null;
                switch(node.type) {
                    case ADD: t = Ir.Instr.Type.ADD; break;
                    case SUBTRACT: t = Ir.Instr.Type.SUBTRACT; break;
                    case MULTIPLY: t = Ir.Instr.Type.MULTIPLY; break;
                    case DIVIDE:
                        t = Ir.Instr.Type.DIVIDE;
                        v = new Ir.Instr.Division(node.source);
                        break;
                    case MODULO: 
                        t = Ir.Instr.Type.MODULO;
                        v = new Ir.Instr.Division(node.source);
                        break;
                    case LESS_THAN: t = Ir.Instr.Type.LESS_THAN; break;
                    case GREATER_THAN: t = Ir.Instr.Type.GREATER_THAN; break;
                    case LESS_THAN_EQUAL:
                        t = Ir.Instr.Type.LESS_THAN_EQUAL; break;
                    case GREATER_THAN_EQUAL:
                        t = Ir.Instr.Type.GREATER_THAN_EQUAL; break;
                    case EQUALS: t = Ir.Instr.Type.EQUALS; break;
                    case NOT_EQUALS: t = Ir.Instr.Type.NOT_EQUALS; break;
                    default: throw new RuntimeException("unhandled type!");
                }
                this.block().add(new Ir.Instr(
                    t, List.of(left, right), v, Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case NEGATE:
            case NOT: {
                AstNode.MonoOp data = node.getValue();
                Ir.Variable value = this.lowerNode(data.value()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                Ir.Instr.Type t;
                switch(node.type) {
                    case NEGATE: t = Ir.Instr.Type.NEGATE; break;
                    case NOT: t = Ir.Instr.Type.NOT; break;
                    default: throw new RuntimeException("unhandled type!");
                }
                this.block().add(new Ir.Instr(
                    t, List.of(value), null, Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case OR: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable left = this.lowerNode(data.left()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                Ir.StaticValue trueValue = this.staticValues.add(
                    new Value.Bool(true)
                );
                this.enterBlock();
                Ir.Variable right = this.lowerNode(data.right()).get();
                List<Ir.Instr> rightInstr = this.exitBlock();
                Ir.Variable destRight = dest.clone();
                rightInstr.add(new Ir.Instr(
                    Ir.Instr.Type.COPY,
                    List.of(right),
                    null,
                    Optional.of(destRight)
                ));
                dest.version += 1;
                Ir.Variable destLeft = dest.clone();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.BRANCH_ON_VALUE,
                    List.of(left),
                    new Ir.Instr.BranchOnValue(
                        List.of(trueValue),
                        List.of(List.of(
                            new Ir.Instr(
                                Ir.Instr.Type.COPY,
                                List.of(left),
                                null,
                                Optional.of(destLeft)
                            )
                        )),
                        rightInstr
                    ),
                    Optional.empty()
                ));
                dest.version += 1;
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.PHI,
                    List.of(destLeft, destRight),
                    null,
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case AND: {
                AstNode.BiOp data = node.getValue();
                Ir.Variable left = this.lowerNode(data.left()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                Ir.StaticValue falseValue = this.staticValues.add(
                    new Value.Bool(false)
                );
                this.enterBlock();
                Ir.Variable right = this.lowerNode(data.right()).get();
                List<Ir.Instr> rightInstr = this.exitBlock();
                Ir.Variable destRight = dest.clone();
                rightInstr.add(new Ir.Instr(
                    Ir.Instr.Type.COPY,
                    List.of(right),
                    null, 
                    Optional.of(destRight)
                ));
                dest.version += 1;
                Ir.Variable destLeft = dest.clone();
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.BRANCH_ON_VALUE,
                    List.of(left),
                    new Ir.Instr.BranchOnValue(
                        List.of(falseValue),
                        List.of(List.of(
                            new Ir.Instr(
                                Ir.Instr.Type.COPY,
                                List.of(left),
                                null,
                                Optional.of(destLeft)
                            )
                        )),
                        rightInstr
                    ),
                    Optional.empty()
                ));
                dest.version += 1;
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.PHI,
                    List.of(destLeft, destRight),
                    null,
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case MODULE_ACCESS: {
                // this can only be a global variable access
                AstNode.ModuleAccess data = node.getValue();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                Symbols.Symbol symbol = this.symbols.get(data.path()).get();
                Symbols.Symbol.Variable variant = symbol
                    .getVariant(data.variant().get());
                if(variant.valueNode().isPresent()) {
                    if(variant.value().isEmpty()) {
                        Value value = this.interpreter.evaluateNode(
                            variant.valueNode().get()
                        );
                        variant = new Symbols.Symbol.Variable(
                            variant.valueType(), variant.valueNode(), 
                            Optional.of(value)
                        );
                        symbol.setVariant(data.variant().get(), variant);
                    }
                    Value value = variant.value().get();
                    Ir.StaticValue staticValue = this.staticValues.add(value);
                    this.block().add(new Ir.Instr(
                        Ir.Instr.Type.LOAD_STATIC_VALUE,
                        List.of(),
                        new Ir.Instr.LoadStaticValue(staticValue),
                        Optional.of(dest)
                    ));
                } else {
                    this.block().add(new Ir.Instr(
                        Ir.Instr.Type.LOAD_EXT_VARIABLE,
                        List.of(),
                        new Ir.Instr.LoadExtVariable(data.path()),
                        Optional.of(dest)
                    ));
                }
                return Optional.of(dest);
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                Ir.Variable value = this.lowerNode(data.value()).get();
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_VARIANT,
                    List.of(value),
                    new Ir.Instr.LoadVariant(data.variantName()),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                Value value = this.interpreter.evaluateNode(data.value());
                Ir.StaticValue staticValue = this.staticValues.add(value);
                Ir.Variable dest = this.context.allocate(node.resultType.get());
                this.block().add(new Ir.Instr(
                    Ir.Instr.Type.LOAD_STATIC_VALUE,
                    List.of(),
                    new Ir.Instr.LoadStaticValue(staticValue),
                    Optional.of(dest)
                ));
                return Optional.of(dest);
            }
            case PROCEDURE:
            case MODULE_DECLARATION:
            case USE:
            case TARGET: {
                throw new RuntimeException("should not be encountered!");
            }
            default: {
                throw new RuntimeException("unhandled node type!");
            }
        }
    }   

}
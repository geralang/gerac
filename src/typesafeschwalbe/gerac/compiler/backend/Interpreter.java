
package typesafeschwalbe.gerac.compiler.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.BuiltIns;
import typesafeschwalbe.gerac.compiler.Color;
import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class Interpreter {

    private static Error makeExternalUsageError(Namespace path, Source source) {
        return new Error(
            "Usage of an external symbol in static context",
            Error.Marking.error(
                source,
                "the external symbol '" + path.toString()
                    + "' may not be used in a static context"
            )
        );
    }

    private static record CallTraceEntry(Namespace path, Source source) {}

    @FunctionalInterface
    private static interface BuiltInProcedure {
        Value eval(List<Value> args, Source src) throws ErrorException;
    }

    private final Map<String, String> sourceFiles;
    private final Symbols symbols;
    private List<Map<String, Optional<Value>>> stack;
    private final List<CallTraceEntry> callTrace;
    private Optional<Value> returnedValue;
    private final Map<Namespace, BuiltInProcedure> builtIns;

    public Interpreter(Map<String, String> sourceFiles, Symbols symbols) {
        this.sourceFiles = sourceFiles;
        this.symbols = symbols;
        this.stack = new LinkedList<>();
        this.callTrace = new LinkedList<>();
        this.returnedValue = Optional.empty();
        this.builtIns = new HashMap<>();
        this.builtIns.put(
            new Namespace(List.of("core", "addr_eq")),
            (args, src) -> {
                if(args.get(0) instanceof Value.Arr) {
                    return new Value.Bool(
                        args.get(0).<Value.Arr>getValue().value
                            == args.get(1).<Value.Arr>getValue().value
                    );
                } else {
                    return new Value.Bool(
                        args.get(0).<Value.Obj>getValue().value
                            == args.get(1).<Value.Obj>getValue().value
                    );
                }
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "tag_eq")),
            (args, src) -> new Value.Bool(
                args.get(0).<Value.Union>getValue().variant
                    .equals(args.get(1).<Value.Union>getValue().variant)
            )
        );
        this.builtIns.put(
            new Namespace(List.of("core", "length")),
            (args, src) -> {
                if(args.get(0) instanceof Value.Arr) {
                    return new Value.Int(
                        args.get(0).<Value.Arr>getValue().value.size()
                    );
                } else {
                    return new Value.Int(
                        args.get(0).<Value.Str>getValue().value.length()
                    );
                }
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "exhaust")),
            (args, src) -> {
                String variant;
                do {
                    variant = this.callClosure(
                        args.get(0).getValue(),
                        List.of(), 
                        new Source(BuiltIns.BUILTIN_FILE_NAME, 0, 0)
                    ).<Value.Union>getValue().variant;
                } while(variant.equals("next"));
                return Value.UNIT;
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "panic")),
            (args, src) -> {
                this.panic(args.get(0).<Value.Str>getValue().value, src);
                return Value.UNIT;
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_str")),
            (args, src) -> {
                if(args.get(0) instanceof Value.Unit) {
                    return new Value.Str("unit");
                } else if(args.get(0) instanceof Value.Bool) {
                    return new Value.Str(
                        args.get(0).<Value.Bool>getValue().value
                            ? "true"
                            : "false"
                    );
                } else if(args.get(0) instanceof Value.Int) {
                    return new Value.Str(String.valueOf(
                        args.get(0).<Value.Int>getValue().value
                    ));
                } else if(args.get(0) instanceof Value.Float) {
                    return new Value.Str(String.valueOf(
                        args.get(0).<Value.Float>getValue().value
                    ));
                } else if(args.get(0) instanceof Value.Str) {
                    return args.get(0);
                } else if(args.get(0) instanceof Value.Arr) {
                    return new Value.Str("<array>");
                } else if(args.get(0) instanceof Value.Obj) {
                    return new Value.Str("<object>");
                } else if(args.get(0) instanceof Value.Closure) {
                    return new Value.Str("<closure>");
                } else if(args.get(0) instanceof Value.Union) {
                    return new Value.Str(
                        "#" + args.get(0).<Value.Union>getValue().variant
                            + " <...>"
                    );
                }
                throw new RuntimeException("unhandled value type!");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_int")),
            (args, src) -> {
                if(args.get(0) instanceof Value.Float) {
                    return new Value.Int(
                        (long) args.get(0).<Value.Float>getValue().value
                    );
                } else {
                    return args.get(0);
                }
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_flt")),
            (args, src) -> {
                if(args.get(0) instanceof Value.Int) {
                    return new Value.Float(
                        args.get(0).<Value.Int>getValue().value
                    );
                } else {
                    return args.get(0);
                }
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "substring")),
            (args, callSource) -> {
                String src = args.get(0).<Value.Str>getValue().value;
                long srcLength = src.codePoints().count();
                long start = args.get(1).<Value.Int>getValue().value;
                long start_idx = start;
                if(start < 0) {
                    start_idx = start + srcLength;
                }
                if(start_idx < 0 || start_idx >= srcLength) {
                    this.panic(
                        "the start index " + start + " is out of bounds"
                            + " for a string of length " + srcLength,
                        callSource
                    );
                }
                long end = args.get(2).<Value.Int>getValue().value;
                long end_idx = end;
                if(end < 0) {
                    end_idx = end + srcLength;
                }
                if(end_idx < 0 || end_idx > srcLength) {
                    this.panic(
                        "the end index " + end + " is out of bounds"
                            + " for a string of length " + srcLength,
                        callSource
                    );
                }
                if(start_idx > end_idx) {
                    this.panic(
                        "the start index " + start
                            + " is larger than the end index " + end
                            + " (length of string is " + srcLength + ")",
                        callSource
                    );
                }
                StringBuilder result = new StringBuilder();
                src.codePoints()
                    .skip(start_idx)
                    .limit(end_idx - start_idx)
                    .forEach(result::appendCodePoint);
                return new Value.Str(result.toString());
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "concat")),
            (args, src) -> new Value.Str(
                args.get(0).<Value.Str>getValue().value
                    + args.get(1).<Value.Str>getValue().value
            )
        );
        this.builtIns.put(
            new Namespace(List.of("core", "hash")),
            (args, src) -> new Value.Int(
                args.get(0).hashCode()
            )
        );
    }

    private void enterFrame() {
        this.stack.add(new HashMap<>());
    }

    private Map<String, Optional<Value>> currentFrame() {
        return this.stack.get(this.stack.size() - 1);
    }

    private void exitFrame() {
        this.stack.remove(this.stack.size() - 1);
    }

    private void enterCall(Namespace path, Source source) {
        this.callTrace.add(new CallTraceEntry(path, source));
    }

    private Value exitCall() {
        this.callTrace.remove(this.callTrace.size() - 1);
        Value returnedValue = Value.UNIT;
        if(this.returnedValue.isPresent()) {
            returnedValue = this.returnedValue.get();
            this.returnedValue = Optional.empty();
        }
        return returnedValue;
    }

    private void panic(String reason, Source source) throws ErrorException {
        throw new ErrorException(new Error(
            reason,
            colored -> {
                String errorNoteColor = colored
                    ? Color.from(Color.GRAY) : "";
                String errorIndexColor = colored
                    ? Color.from(Color.GRAY) : "";
                String errorProcedureColor = colored
                    ? Color.from(Color.GREEN, Color.BOLD) : "";
                String errorFileNameColor = colored
                    ? Color.from(Color.WHITE) : "";
                StringBuilder trace = new StringBuilder();
                trace.append(errorNoteColor);
                trace.append("Stack trace (latest call first):\n");
                for(
                    int callI = this.callTrace.size() - 1; 
                    callI >= 0; 
                    callI -= 1
                ) {
                    CallTraceEntry entry = this.callTrace.get(callI);
                    trace.append(errorIndexColor);
                    trace.append(" ");
                    trace.append(callI);
                    trace.append(" ");
                    trace.append(errorProcedureColor);
                    trace.append(entry.path);
                    trace.append(errorNoteColor);
                    trace.append(" at ");
                    trace.append(errorFileNameColor);
                    trace.append(entry.source.file);
                    trace.append(":");
                    trace.append(entry.source.computeLine(this.sourceFiles));
                    trace.append("\n");
                }
                return trace.toString();
            },
            Error.Marking.error(source, "this caused the panic")
        ));
    }

    private void panicArrayIdxOutOfBounds(
        long index, int size, Source source
    ) throws ErrorException {
        this.enterCall(
            new Namespace(List.of("<index>")), source
        );
        this.panic(
            "the index " + index + " is out of bounds"
                + " for an array of length " + size,
            source
        );
    }

    private Value callClosure(
        Value.Closure called, List<Value> arguments, Source source
    ) throws ErrorException {
        this.enterCall(
            new Namespace(List.of("<closure>")), source
        );
        List<Map<String, Optional<Value>>> prevStack = this.stack;
        this.stack = new ArrayList<>(called.environment);
        this.enterFrame();
        for(
            int argI = 0; 
            argI < called.argumentNames.size(); 
            argI += 1
        ) {
            this.currentFrame().put(
                called.argumentNames.get(argI),
                Optional.of(arguments.get(argI))
            );
        }
        this.evaluateBlock(called.body);
        this.exitFrame();
        this.stack = prevStack;
        return this.exitCall();
    }

    private void evaluateBlock(List<AstNode> body) throws ErrorException {
        for(AstNode node: body) {
            if(this.returnedValue.isPresent()) { return; }
            this.evaluateNode(node);
        }
    }

    public Value evaluateNode(AstNode node) throws ErrorException {
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                return new Value.Closure(
                    new ArrayList<>(this.stack),
                    data.argumentNames(),
                    data.body().get()
                );
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                if(data.value().isPresent()) {
                    Value value = this.evaluateNode(data.value().get());
                    this.currentFrame().put(data.name(), Optional.of(value));
                } else {
                    this.currentFrame().put(data.name(), Optional.empty());
                }
                return Value.UNIT;
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                Value value = this.evaluateNode(data.value());
                boolean evalElseBody = true;
                for(
                    int branchI = 0; 
                    branchI < data.branchBodies().size(); 
                    branchI += 1
                ) {
                    Value branchValue = this.evaluateNode(
                        data.branchValues().get(branchI)
                    );
                    if(!branchValue.equals(value)) { continue; }
                    this.enterFrame();
                    this.evaluateBlock(data.branchBodies().get(branchI));
                    this.exitFrame();
                    evalElseBody = false;
                    break;
                }
                if(evalElseBody) {
                    this.enterFrame();
                    this.evaluateBlock(data.elseBody());
                    this.exitFrame();
                }
                return Value.UNIT;
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                Value.Bool condition = this.evaluateNode(data.condition())
                    .getValue();
                if(condition.value) {
                    this.enterFrame();
                    this.evaluateBlock(data.ifBody());
                    this.exitFrame();
                } else {
                    this.enterFrame();
                    this.evaluateBlock(data.elseBody());
                    this.exitFrame();
                }
                return Value.UNIT;
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                Value.Union value = this.evaluateNode(data.value()).getValue();
                boolean evalElseBody = true;
                for(
                    int branchI = 0;
                    branchI < data.branchBodies().size();
                    branchI += 1
                ) {
                    String branchVariant = data.branchVariants().get(branchI);
                    if(!value.variant.equals(branchVariant)) { continue; }
                    Optional<String> variableName = data.branchVariableNames()
                        .get(branchI);
                    this.enterFrame();
                    if(variableName.isPresent()) {
                        this.currentFrame().put(
                            variableName.get(), Optional.of(value)
                        );
                    }
                    this.evaluateBlock(data.branchBodies().get(branchI));
                    this.exitFrame();
                    evalElseBody = false;
                    break;
                }
                if(evalElseBody && data.elseBody().isPresent()) {
                    this.enterFrame();
                    this.evaluateBlock(data.elseBody().get());
                    this.exitFrame();
                }
                return Value.UNIT;
            }
            case ASSIGNMENT: {
                AstNode.BiOp data = node.getValue();
                Value value = this.evaluateNode(data.right());
                this.evaluateAssignment(data.left(), value);
                return Value.UNIT;
            }
            case RETURN: {
                AstNode.MonoOp data = node.getValue();
                Value value = this.evaluateNode(data.value());
                this.returnedValue = Optional.of(value);
                return Value.UNIT;
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                if(data.called().type == AstNode.Type.MODULE_ACCESS) {
                    AstNode.ModuleAccess calledData = data.called().getValue();
                    Optional<Symbols.Symbol> symbol = this.symbols.get(
                        calledData.path()
                    );
                    if(symbol.isEmpty()) {
                        throw new RuntimeException("symbol should exist!");
                    }
                    Symbols.Symbol.Procedure symbolData = symbol.get()
                        .getVariant(calledData.variant().get());
                    if(symbolData.body().isEmpty()) {
                        if(!this.builtIns.containsKey(calledData.path())) {
                            throw new ErrorException(
                                Interpreter.makeExternalUsageError(
                                    calledData.path(), data.called().source
                                )
                            );
                        }
                        List<Value> args = new ArrayList<>(
                            data.arguments().size()
                        );
                        for(AstNode argument: data.arguments()) {
                            args.add(this.evaluateNode(argument));
                        }
                        this.enterCall(calledData.path(), node.source);
                        Value returned = this.builtIns.get(calledData.path())
                            .eval(args, node.source);
                        this.exitCall();
                        return returned;
                    }
                    this.enterCall(calledData.path(), node.source);
                    this.enterFrame();
                    for(
                        int argI = 0; 
                        argI < symbolData.argumentNames().size(); 
                        argI += 1
                    ) {
                        String argName = symbolData.argumentNames().get(argI);
                        Value argValue = this.evaluateNode(
                            data.arguments().get(argI)
                        );
                        this.currentFrame().put(argName, Optional.of(argValue));
                    }
                    this.evaluateBlock(symbolData.body().get());
                    this.exitFrame();
                    return this.exitCall();
                }
                Value.Closure called = this.evaluateNode(data.called())
                    .getValue();
                List<Value> argumentValues = new ArrayList<>(
                    data.arguments().size()
                );
                for(AstNode argument: data.arguments()) {
                    argumentValues.add(this.evaluateNode(argument));
                }
                return this.callClosure(called, argumentValues, node.source);
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                Value.Obj accessed = this.evaluateNode(data.called())
                    .getValue();
                Value.Closure called = accessed.value.get(data.memberName())
                    .getValue();
                List<Value> argumentValues = new ArrayList<>(
                    data.arguments().size()
                );
                argumentValues.add(accessed);
                for(AstNode argument: data.arguments()) {
                    argumentValues.add(this.evaluateNode(argument));
                }
                return this.callClosure(called, argumentValues, node.source);
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                Map<String, Value> values = new HashMap<>();
                for(String memberName: data.values().keySet()) {
                    Value memberValue = this.evaluateNode(
                        data.values().get(memberName)
                    );
                    values.put(memberName, memberValue);
                }
                return new Value.Obj(values);
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                List<Value> values = new ArrayList<>(data.values().size());
                for(AstNode valueNode: data.values()) {
                    Value value = this.evaluateNode(valueNode);
                    values.add(value);
                }
                return new Value.Arr(values);
            }
            case REPEATING_ARRAY_LITERAL: {
                AstNode.BiOp data = node.getValue();
                Value value = this.evaluateNode(data.left());
                Value.Int size = this.evaluateNode(data.right()).getValue();
                if(size.value < 0) {
                    this.panic(
                        "the value " + size.value
                            + " is not a valid array size", 
                        node.source
                    );
                }
                List<Value> values = Collections.nCopies(
                    (int) size.value, value
                );
                return new Value.Arr(values);
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                Value.Obj accessed = this.evaluateNode(data.accessed())
                    .getValue();
                return accessed.value.get(data.memberName());
            }
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                Value.Arr accessed = this.evaluateNode(data.left()).getValue();
                Value.Int index = this.evaluateNode(data.right()).getValue();
                long idx = index.value;
                if(idx < accessed.value.size()) {
                    idx += accessed.value.size();
                }
                if(idx < 0 || idx >= accessed.value.size()) {
                    this.panicArrayIdxOutOfBounds(
                        index.value, accessed.value.size(), node.source
                    );
                }
                return accessed.value.get((int) idx);
            }
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                for(
                    int frameI = this.stack.size() - 1; 
                    frameI >= 0; 
                    frameI -= 1
                ) {
                    Map<String, Optional<Value>> frame = this.stack.get(frameI);
                    if(!frame.containsKey(data.variableName())) { continue; }
                    return frame.get(data.variableName()).get();
                }
                throw new RuntimeException("variable doesn't exist!");
            }
            case BOOLEAN_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                boolean value = data.value().equals("true");
                return new Value.Bool(value);
            }
            case INTEGER_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                long value = Long.parseLong(data.value());
                return new Value.Int(value);
            }
            case FLOAT_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                double value = Double.parseDouble(data.value());
                return new Value.Float(value);
            }
            case STRING_LITERAL: {
                AstNode.SimpleLiteral data = node.getValue();
                return new Value.Str(data.value());
            }
            case UNIT_LITERAL: {
                return Value.UNIT;
            }
            case ADD: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Int(
                        left.<Value.Int>getValue().value
                            + right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        left.<Value.Float>getValue().value
                            + right.<Value.Float>getValue().value
                    );
                }
            }
            case SUBTRACT: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Int(
                        left.<Value.Int>getValue().value
                            - right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        left.<Value.Float>getValue().value
                            - right.<Value.Float>getValue().value
                    );
                }
            }
            case MULTIPLY: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Int(
                        left.<Value.Int>getValue().value
                            * right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        left.<Value.Float>getValue().value
                            * right.<Value.Float>getValue().value
                    );
                }
            }
            case DIVIDE: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Int(
                        left.<Value.Int>getValue().value
                            / right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        left.<Value.Float>getValue().value
                            / right.<Value.Float>getValue().value
                    );
                }
            }
            case MODULO: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Int(
                        left.<Value.Int>getValue().value
                            % right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        left.<Value.Float>getValue().value
                            % right.<Value.Float>getValue().value
                    );
                }
            }
            case NEGATE: {
                AstNode.MonoOp data = node.getValue();
                Value value = this.evaluateNode(data.value());
                if(value instanceof Value.Int) {
                    return new Value.Int(
                        -value.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Float(
                        -value.<Value.Float>getValue().value
                    );
                }
            }
            case LESS_THAN: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Bool(
                        left.<Value.Int>getValue().value
                            < right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Bool(
                        left.<Value.Float>getValue().value
                            < right.<Value.Float>getValue().value
                    );
                }
            }
            case GREATER_THAN: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Bool(
                        left.<Value.Int>getValue().value
                            > right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Bool(
                        left.<Value.Float>getValue().value
                            > right.<Value.Float>getValue().value
                    );
                }
            }
            case LESS_THAN_EQUAL: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Bool(
                        left.<Value.Int>getValue().value
                            <= right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Bool(
                        left.<Value.Float>getValue().value
                            <= right.<Value.Float>getValue().value
                    );
                }
            }
            case GREATER_THAN_EQUAL: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                if(left instanceof Value.Int) {
                    return new Value.Bool(
                        left.<Value.Int>getValue().value
                            >= right.<Value.Int>getValue().value
                    );
                } else {
                    return new Value.Bool(
                        left.<Value.Float>getValue().value
                            >= right.<Value.Float>getValue().value
                    );
                }
            }
            case EQUALS: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                return new Value.Bool(left.equals(right));
            }
            case NOT_EQUALS: {
                AstNode.BiOp data = node.getValue();
                Value left = this.evaluateNode(data.left());
                Value right = this.evaluateNode(data.right());
                return new Value.Bool(!left.equals(right));
            }
            case NOT: {
                AstNode.MonoOp data = node.getValue();
                Value.Bool value = this.evaluateNode(data.value()).getValue();
                return new Value.Bool(!value.value);
            }
            case OR: {
                AstNode.BiOp data = node.getValue();
                Value.Bool left = this.evaluateNode(data.left()).getValue();
                if(left.value) {
                    return left;
                }
                return this.evaluateNode(data.right());
            }
            case AND: {
                AstNode.BiOp data = node.getValue();
                Value.Bool left = this.evaluateNode(data.left()).getValue();
                if(!left.value) {
                    return left;
                }
                return this.evaluateNode(data.right());
            }
            case MODULE_ACCESS: {
                AstNode.ModuleAccess data = node.getValue();
                Optional<Symbols.Symbol> symbol = this.symbols.get(data.path());
                if(symbol.isEmpty()) {
                    throw new RuntimeException("symbol should exist!");
                }
                Symbols.Symbol.Variable symbolData = symbol.get().getVariant(
                    data.variant().get()
                );
                if(symbolData.valueNode().isEmpty()) {
                    throw new ErrorException(
                        Interpreter.makeExternalUsageError(
                            data.path(), node.source
                        )
                    );
                }
                Value value;
                if(symbolData.value().isPresent()) {
                    value = symbolData.value().get();
                } else {
                    value = this.evaluateNode(symbolData.valueNode().get());
                    symbol.get().setValue(new Symbols.Symbol.Variable(
                        symbolData.valueType(),
                        symbolData.valueNode(),
                        Optional.of(value)
                    ));
                }
                return value;
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                Value value = this.evaluateNode(data.value());
                return new Value.Union(data.variantName(), value);
            }
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                return this.evaluateNode(data.value());
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

    private void evaluateAssignment(
        AstNode node, Value value
    ) throws ErrorException {
        switch(node.type) {
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                Value.Obj accessed = this.evaluateNode(data.accessed())
                    .getValue();
                accessed.value.put(data.memberName(), value);
            } break;
            case ARRAY_ACCESS: {
                AstNode.BiOp data = node.getValue();
                Value.Arr accessed = this.evaluateNode(data.left()).getValue();
                Value.Int index = this.evaluateNode(data.right()).getValue();
                long idx = index.value;
                if(idx < accessed.value.size()) {
                    idx += accessed.value.size();
                }
                if(idx < 0 || idx >= accessed.value.size()) {
                    this.panicArrayIdxOutOfBounds(
                        index.value, accessed.value.size(), node.source
                    );
                }
                accessed.value.set((int) idx, value);
            } break;
            case VARIABLE_ACCESS: {
                AstNode.VariableAccess data = node.getValue();
                for(
                    int frameI = this.stack.size() - 1; 
                    frameI >= 0; 
                    frameI -= 1
                ) {
                    Map<String, Optional<Value>> frame = this.stack.get(frameI);
                    if(!frame.containsKey(data.variableName())) { continue; }
                    frame.put(data.variableName(), Optional.of(value));
                    break;
                }
            } break;
            default: {
                throw new RuntimeException("should not be encountered!");
            }
        }
    }
    
}


package typesafeschwalbe.gerac.compiler.backend;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.BuiltIns;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class JsCodeGen implements CodeGen {

    private static final String CORE_LIB = """
        function gera___eq(a, b) {
            if(typeof a === "object") {
                if(a instanceof Array) {
                    if(a.length !== b.length) { return false; }
                    for(let i = 0; i < a.length; i += 1) {
                        if(!gera___eq(a[i], b[i])) { return false; }
                    }
                    return true;
                }
                for(const key of Object.keys(a)) {
                    if(!gera___eq(a[key], b[key])) { return false; }
                }
                return true;
            }
            return a === b;
        }
        
        const gera___hash = (function() {
            const random_int = () => {
                const upper = BigInt.asIntN(
                    32, BigInt(Math.floor(Math.random() * (2 ** 32)))
                ) << 32n;
                const lower = BigInt.asIntN(
                    32, BigInt(Math.floor(Math.random() * (2 ** 32)))
                );
                return BigInt.asIntN(64, upper | lower);
            };
            const object_hashes = new WeakMap();
            return (data) => {
                if(typeof data === "object") {
                    if(!object_hashes.has(data)) {
                        const h = random_int();
                        object_hashes.set(data, h);
                        return h;
                    }
                    return object_hashes.get(data);
                }
                const d = typeof data === "string"? data : data.toString();
                let h = 0n;
                for(let i = 0; i < d.length; i += 1) {
                    h = BigInt.asIntN(64, BigInt(d.charCodeAt(i))
                        + (h << 6n) + (h << 16n) - h);
                }
                return h;
            };
        })();
        
        const gera___stack = {
            trace: [],
            push: function(name, file, line) {
                this.trace.push({ name, file, line });
                if(this.trace.length > GERA_MAX_CALL_DEPTH) {
                    gera___panic("Maximum call depth exceeded!");
                }
            },
            pop: function() { this.trace.pop(); }
        };
        
        function gera___panic(message) {
            let err = "";
            err += `The program panicked: ${message}` + '\\n';
            err += `Stack trace (latest call first):` + '\\n';
            let i = gera___stack.trace.length - 1;
            while(true) {
                const si = gera___stack.trace[i];
                if(i < 0) { break; }
                err += `${i} ${si.name} at ${si.file}:${si.line}` + '\\n';
                if(i === 0) { break; }
                i -= 1;
            }
            console.error(err);
            class GeraPanic extends Error {
                constructor() {
                    super("The program panicked.");
                    this.name = "error";
                    this.stack = null;
                }
            }
            throw new GeraPanic();
        }
        
        function gera___verify_size(size, file, line) {
            if(size >= 0) {
                return size;
            }
            gera___stack.push("<array-init>", file, line);
            gera___panic(`the value ${size} is not a valid array size`);
            return -1;
        }

        function gera___verify_index(index, length, file, line) {
            const final_index = index < 0n
                ? BigInt(length) + index : index;
            if(final_index >= 0 && final_index < BigInt(length)) {
                return final_index;
            }
            gera___stack.push("<index>", file, line);
            gera___panic(
                `the index ${index} is out of bounds`
                    + ` for an array of length ${length}`
            );
            return -1;
        }
        
        function gera___verify_integer_divisor(d, file, line) {
            if(d != 0n) { return; }
            gera___stack.push("<division>", file, line);
            gera___panic("integer division by zero");
        }
        
        function gera___substring(s, start, end) {
            let start_offset = 0;
            for(let i = 0; i < start; i += 1) {
                start_offset += s.codePointAt(start_offset) > 0xFFFF? 2 : 1;
            }
            let end_offset = start_offset;
            for(let c = start; c < end; c += 1) {
                end_offset += s.codePointAt(end_offset) > 0xFFFF? 2 : 1;
            }
            return s.substring(start_offset, end_offset);
        }
        
        function gera___strlen(s) {
            let length = 0;
            for(let i = 0; i < s.length; length += 1) {
                const code = s.codePointAt(i);
                i += code > 0xFFFF? 2 : 1;
            }
            return length;
        }    
        """;

    private final Map<String, String> sourceFiles;
    private final Symbols symbols;
    private final Ir.StaticValues staticValues;
    private final long maxCallDepth;

    private final List<Ir.Context> contextStack;

    public JsCodeGen(
        Map<String, String> sourceFiles, Symbols symbols, 
        Ir.StaticValues staticValues, long maxCallDepth
    ) {
        this.sourceFiles = sourceFiles;
        this.symbols = symbols;
        this.staticValues = staticValues;
        this.maxCallDepth = maxCallDepth;
        this.contextStack = new LinkedList<>();
    }

    private void enterContext(Ir.Context context) {
        this.contextStack.add(context);
    }

    private void exitContext() {
        this.contextStack.remove(this.contextStack.size() - 1);
    }

    @Override
    public String generate(Namespace mainPath) {
        StringBuilder out = new StringBuilder();
        out.append("\n");
        out.append("""
            //
            // Generated from Gera source code by the Gera compiler.
            // See: https://github.com/geralang
            //
            """);
        out.append("\n");
        out.append("(function() {\n");
        out.append("\"use strict\";\n");
        out.append("const GERA_MAX_CALL_DEPTH = ");
        out.append(this.maxCallDepth);
        out.append(";\n");
        out.append("\n");
        out.append(CORE_LIB);
        out.append("\n");
        this.emitStaticValues(out);
        out.append("\n");
        this.emitSymbols(out);
        out.append("\n");
        this.emitStackTracePush(
            mainPath, new Source(BuiltIns.BUILTIN_FILE_NAME, 0, 0), out
        );
        this.emitVariant(mainPath, 0, out);
        out.append("();\n");
        this.emitStackTracePop(out);
        out.append("})();\n");
        return out.toString();
    }

    private void emitStaticValues(StringBuilder out) {
        int valueC = this.staticValues.values.size();
        for(int valueI = 0; valueI < valueC; valueI += 1) {
            out.append("const GERA_STATIC_VALUE_");
            out.append(valueI);
            out.append(" = ");
            this.emitValue(this.staticValues.values.get(valueI), out);
            out.append(";\n");
        }
    }

    private void emitStaticValue(Ir.StaticValue v, StringBuilder out) {
        out.append("GERA_STATIC_VALUE_");
        out.append(v.index);
    }

    private void emitValue(Value v, StringBuilder out) {
        // TODO
        out.append("undefined /* TODO: implement 'emitValue' */");
    }
    
    private void emitSymbols(StringBuilder out) {
        for(Namespace path: this.symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = this.symbols.get(path).get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
                continue;
            }
            Symbols.Symbol.Procedure symbolData = symbol.getValue();
            if(symbolData.body().isEmpty()) {
                continue;
            }
            for(
                int variantI = 0; 
                variantI < symbol.variantCount(); 
                variantI += 1
            ) {
                Symbols.Symbol.LoweredProcedure variantData = symbol
                    .getVariant(variantI);
                out.append("function ");
                this.emitVariant(path, variantI, out);
                this.emitArgListDef(variantData.argumentTypes().size(), out);
                out.append(" {\n");
                this.enterContext(variantData.context());
                this.emitContextInit(out);
                this.emitInstructions(variantData.body(), out);
                this.exitContext();
                out.append("}\n");
                out.append("\n");
            }
        }
    }

    private void emitArgListDef(int argC, StringBuilder out) {
        out.append("(");
        for(int argI = 0; argI < argC; argI += 1) {
            if(argI > 0) {
                out.append(", ");
            }
            out.append("arg");
            out.append(argI);
        }
        out.append(")");
    }

    private void emitContextInit(StringBuilder out) {
        int ctxI = this.contextStack.size() - 1;
        Ir.Context ctx = this.contextStack.get(ctxI);
        out.append("const captured");
        out.append(ctxI);
        out.append(" = {};\n");
        for(int varI = 0; varI < ctx.variableTypes.size(); varI += 1) {
            if(ctx.capturedNames.containsKey(varI)) { continue; }
            out.append("let local");
            out.append(varI);
            out.append(";\n");
        }
        for(int argI = 0; argI < ctx.argumentVars.size(); argI += 1) {
            this.emitVariable(ctx.argumentVars.get(argI), out);
            out.append(" = arg");
            out.append(argI);
            out.append(";\n");
        }
    }

    private void emitVariable(Ir.Variable v, StringBuilder out) {
        int ctxI = this.contextStack.size() - 1;
        Ir.Context ctx = this.contextStack.get(ctxI);
        String capturedName = ctx.capturedNames.get(v.index);
        if(capturedName == null) {
            out.append("local");
            out.append(v.index);
        } else {
            out.append("captured");
            out.append(ctxI);
            out.append(".");
            out.append(capturedName);
        }
    }

    private void emitStackTracePush(
        Namespace path, Source source, StringBuilder out
    ) {
        out.append("gera___stack.push(");
        this.emitStringLiteral(path.toString(), out);
        out.append(", ");
        this.emitStringLiteral(source.file, out);
        out.append(", ");
        out.append(source.computeLine(this.sourceFiles));
        out.append(");\n");
    }

    private void emitStackTracePop(StringBuilder out) {
        out.append("gera___stack.pop();\n");
    }

    private void emitVariant(Namespace path, int variant, StringBuilder out) {
        this.emitPath(path, out);
        out.append("_");
        out.append(variant);
    }

    private void emitPath(Namespace path, StringBuilder out) {
        for(
            int elementI = 0; elementI < path.elements().size(); elementI += 1
        ) {
            if(elementI > 0) {
                out.append("_");
            }
            String element = path.elements().get(elementI);
            for(int charI = 0; charI < element.length(); charI += 1) {
                if(element.charAt(charI) == '_') {
                    out.append("__");
                } else {
                    out.append(element.charAt(charI));
                }
            }
        }
    }

    private void emitStringLiteral(String content, StringBuilder out) {
        out.append("\"");
        for(int charI = 0; charI < content.length(); charI += 1) {
            char c = content.charAt(charI);
            switch(c) {
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\"': out.append("\\\""); break;
                default: out.append(c);
            }
        }
        out.append("\"");
    }

    private void emitInstructions(List<Ir.Instr> instr, StringBuilder out) {
        for(Ir.Instr i: instr) {
            this.emitInstruction(i, out);
        }
    }

    private void emitInstruction(Ir.Instr instr, StringBuilder out) {
        switch(instr.type) {
            case LOAD_UNIT: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = undefined;\n");
            } break;
            case LOAD_BOOLEAN: {
                Ir.Instr.LoadBoolean data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value()? "true" : "false");
                out.append(";\n");
            } break;
            case LOAD_INTEGER: {
                Ir.Instr.LoadInteger data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append("n;\n");
            } break;
            case LOAD_FLOAT: {
                Ir.Instr.LoadFloat data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append(";\n");
            } break;
            case LOAD_STRING: {
                Ir.Instr.LoadString data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitStringLiteral(data.value(), out);
                out.append(";\n");
            } break;
            case LOAD_OBJECT: {
                Ir.Instr.LoadObject data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = { ");
                for(
                    int memberI = 0; 
                    memberI < data.memberNames().size(); 
                    memberI += 1
                ) {
                    if(memberI > 0) {
                        out.append(", ");
                    }
                    out.append(data.memberNames().get(memberI));
                    out.append(": ");
                    this.emitVariable(instr.arguments.get(memberI), out);
                }
                out.append(" };\n");
            } break;
            case LOAD_FIXED_ARRAY: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = [");
                for(
                    int valueI = 0; valueI < instr.arguments.size(); valueI += 1
                ) {
                    if(valueI > 0) {
                        out.append(", ");
                    }
                    this.emitVariable(instr.arguments.get(valueI), out);
                }
                out.append("];\n");
            } break;
            case LOAD_REPEAT_ARRAY: {
                Ir.Instr.LoadRepeatArray data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = new Array(");
                this.emitArraySizeVerify(
                    instr.arguments.get(1), data.source(), out
                );
                out.append(").fill(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(");\n");
            } break;
            case LOAD_VARIANT: {
                Ir.Instr.LoadVariant data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = { tag: ");
                out.append(data.variantName().hashCode());
                out.append(", value: ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" };\n");
            } break;
            case LOAD_CLOSURE: {
                Ir.Instr.LoadClosure data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitArgListDef(data.captureNames().size(), out);
                out.append(" => {\n");
                this.enterContext(data.context());
                this.emitContextInit(out);
                this.emitInstructions(data.body(), out);
                this.exitContext();
                out.append("};\n");
            } break;
            case LOAD_EMPTY_CLOSURE: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = () => {};\n");
            } break;
            case LOAD_STATIC_VALUE: {
                Ir.Instr.LoadStaticValue data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitStaticValue(data.value(), out);
                out.append(";\n");
            } break;
            case LOAD_EXT_VARIABLE: {
                Ir.Instr.LoadExtVariable data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                Symbols.Symbol symbol = this.symbols.get(data.path()).get();
                out.append(symbol.externalName.get());
                out.append(";\n");
            } break;

            case READ_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".");
                out.append(data.memberName());
                out.append(";\n");
            } break;
            case WRITE_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".");
                out.append(data.memberName());
                out.append(" = ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case READ_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append("[");
                this.emitArrayIndexVerify(
                    instr.arguments.get(1), 
                    instr.arguments.get(0),
                    data.source(),
                    out
                );
                out.append("];\n");
            } break;
            case WRITE_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                this.emitVariable(instr.arguments.get(0), out);
                out.append("[");
                this.emitArrayIndexVerify(
                    instr.arguments.get(1), 
                    instr.arguments.get(0),
                    data.source(),
                    out
                );
                out.append("] = ");
                this.emitVariable(instr.arguments.get(2), out);
                out.append(";\n");
            } break;
            case READ_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = captured");
                out.append(this.contextStack.size() - 2);
                out.append(".");
                out.append(data.captureName());
                out.append(";\n");
            } break;
            case WRITE_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                out.append("captured");
                out.append(this.contextStack.size() - 2);
                out.append(".");
                out.append(data.captureName());
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            } break;

            case COPY: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            } break;

            case ADD: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" + ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case SUBTRACT: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" - ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case MULTIPLY: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" * ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case DIVIDE: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" / ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case MODULO: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" % ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case NEGATE: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(" -");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            } break;
            case LESS_THAN: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" < ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case GREATER_THAN: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" > ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case LESS_THAN_EQUAL: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" <= ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case GREATER_THAN_EQUAL: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" >= ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case EQUALS: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" === ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case NOT_EQUALS: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" != ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case NOT: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(" !");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            } break;

            case BRANCH_ON_VALUE: {
                Ir.Instr.BranchOnValue data = instr.getValue();
                out.append("switch(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(") {\n");
                for(
                    int branchI = 0; 
                    branchI < data.branchBodies().size(); 
                    branchI += 1
                ) {
                    out.append("case ");
                    this.emitStaticValue(
                        data.branchValues().get(branchI), out
                    );
                    out.append(":\n");
                    this.emitInstructions(
                        data.branchBodies().get(branchI), out
                    );
                    out.append("break;\n");
                }
                out.append("default:\n");
                this.emitInstructions(data.elseBody(), out);
                out.append("}\n");
            } break;
            case BRANCH_ON_VARIANT: {
                Ir.Instr.BranchOnVariant data = instr.getValue();
                out.append("switch(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".tag) {\n");
                for(
                    int branchI = 0; 
                    branchI < data.branchBodies().size(); 
                    branchI += 1
                ) {
                    out.append("case ");
                    out.append(data.branchVariants().get(branchI).hashCode());
                    out.append(":\n");
                    Optional<Ir.Variable> bVar = data.branchVariables()
                        .get(branchI);
                    if(bVar.isPresent()) {
                        this.emitVariable(bVar.get(), out);
                        out.append(" = ");
                        this.emitVariable(instr.arguments.get(0), out);
                        out.append(".value;\n");
                    }
                    this.emitInstructions(
                        data.branchBodies().get(branchI), out
                    );
                    out.append("break;\n");
                }
                out.append("default:\n");
                this.emitInstructions(data.elseBody(), out);
                out.append("}\n");
            } break;

            case CALL_PROCEDURE: {
                Ir.Instr.CallProcedure data = instr.getValue();
                this.emitStackTracePush(data.path(), data.source(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                Symbols.Symbol symbol = this.symbols.get(data.path()).get();
                if(symbol.externalName.isPresent()) {
                    out.append(symbol.externalName.get());
                } else {
                    this.emitVariant(data.path(), data.variant(), out);
                }
                out.append("(");
                for(int argI = 0; argI < instr.arguments.size(); argI += 1) {
                    if(argI > 0) {
                        out.append(", ");
                    }
                    this.emitVariable(instr.arguments.get(argI), out);
                }
                out.append(");\n");
                this.emitStackTracePop(out);
            } break;
            case CALL_CLOSURE: {
                Ir.Instr.CallClosure data = instr.getValue();
                this.emitStackTracePush(
                    new Namespace(List.of("<closure>")), data.source(), out
                );
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append("(");
                for(int argI = 1; argI < instr.arguments.size(); argI += 1) {
                    if(argI > 1) {
                        out.append(", ");
                    }
                    this.emitVariable(instr.arguments.get(argI), out);
                }
                out.append(");\n");
                this.emitStackTracePop(out);
            } break;
            case RETURN: {
                out.append("return ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            }
            case PHI: break;
            default: {
                throw new RuntimeException("unhandled instruction type!");
            }
        }
    }

    private void emitArraySizeVerify(
        Ir.Variable size, Source source, StringBuilder out
    ) {
        out.append("gera___verify_size(");
        this.emitVariable(size, out);
        out.append(", ");
        this.emitStringLiteral(source.file, out);
        out.append(", ");
        out.append(source.computeLine(this.sourceFiles));
        out.append(")");
    }

    private void emitArrayIndexVerify(
        Ir.Variable index, Ir.Variable accessed,
        Source source, StringBuilder out
    ) {
        out.append("gera___verify_index(");
        this.emitVariable(index, out);
        out.append(", ");
        this.emitVariable(accessed, out);
        out.append(".length, ");
        this.emitStringLiteral(source.file, out);
        out.append(", ");
        out.append(source.computeLine(this.sourceFiles));
        out.append(")");
    }

}

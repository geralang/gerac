
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.DataType;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

public class SymbolInfoGen {
    
    private final Symbols symbols;
    private final TypeContext types;

    private final Set<Namespace> modules;

    public SymbolInfoGen(Symbols symbols, TypeContext types) {
        this.symbols = symbols;
        this.types = types;
        this.modules = new HashSet<>();
        this.collectModules();
    }

    private void collectModules() {
        for(Namespace symbolPath: this.symbols.allSymbolPaths()) {
            List<String> modulePath = new ArrayList<>(symbolPath.elements());
            modulePath.remove(modulePath.size() - 1);
            modules.add(new Namespace(modulePath));
        }
    }

    public String generate() {
        StringBuilder out = new StringBuilder();
        out.append("{\"types\":");
        this.emitTypes(out);
        out.append(",\"modules\":");
        this.emitModules(out);
        out.append("}");
        return out.toString();
    }

    private void emitString(String content, StringBuilder out) {
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

    private void emitTypes(StringBuilder out) {
        out.append("{");
        boolean hadType = false;
        for(int tid = 0; tid < this.types.varVount(); tid += 1) {
            if(!this.types.substitutes.isRoot(tid)) { continue; }
            if(hadType) { out.append(","); }
            hadType = true;
            DataType<TypeVariable> tval = this.types.get(tid);
            out.append("\"");
            out.append(tid);
            out.append("\":{\"type\":\"");
            switch(tval.type) {
                case ANY: out.append("any"); break;
                case NUMERIC: out.append("numeric"); break;
                case INDEXED: out.append("indexed"); break;
                case REFERENCED: out.append("referenced"); break;
                case UNIT: out.append("unit"); break;
                case BOOLEAN: out.append("boolean"); break;
                case INTEGER: out.append("integer"); break;
                case FLOAT: out.append("float"); break;
                case STRING: out.append("string"); break;
                case ARRAY: out.append("array"); break;
                case UNORDERED_OBJECT: out.append("object"); break;
                case CLOSURE: out.append("closure"); break;
                case UNION: out.append("union"); break;
            }
            out.append("\"");
            switch(tval.type) {
                case ANY: case NUMERIC: case INDEXED: case REFERENCED:
                case UNIT: case BOOLEAN: case INTEGER: case FLOAT:
                case STRING:
                    break;
                case ARRAY: {
                    DataType.Array<TypeVariable> td = tval.getValue();
                    out.append(",\"elements\":");
                    this.emitType(td.elementType(), out);
                } break;
                case UNORDERED_OBJECT: {
                    DataType.UnorderedObject<TypeVariable> td = tval.getValue();
                    out.append(",\"members\":{");
                    boolean hadMember = false;
                    for(String member: td.memberTypes().keySet()) {
                        if(hadMember) { out.append(","); }
                        hadMember = true;
                        out.append("\"");
                        out.append(member);
                        out.append("\":");
                        this.emitType(td.memberTypes().get(member), out);
                    }
                    out.append("},\"expandable\":");
                    out.append(td.expandable()? "true" : "false");
                } break;
                case CLOSURE: {
                    DataType.Closure<TypeVariable> td = tval.getValue();
                    out.append(",\"arguments\":[");
                    boolean hadArgument = false;
                    for(TypeVariable argument: td.argumentTypes()) {
                        if(hadArgument) { out.append(","); }
                        hadArgument = true;
                        this.emitType(argument, out);
                    }
                    out.append("],\"returns\":");
                    this.emitType(td.returnType(), out);
                } break;
                case UNION: {
                    DataType.Union<TypeVariable> td = tval.getValue();
                    out.append(",\"variants\":{");
                    boolean hadVariant = false;
                    for(String variant: td.variantTypes().keySet()) {
                        if(hadVariant) { out.append(","); }
                        hadVariant = true;
                        out.append("\"");
                        out.append(variant);
                        out.append("\":");
                        this.emitType(td.variantTypes().get(variant), out);
                    }
                    out.append("},\"expandable\":");
                    out.append(td.expandable()? "true" : "false");
                } break;
            }
            out.append("}");
        }
        out.append("}");
    }

    private void emitType(TypeVariable t, StringBuilder out) {
        int tid = this.types.substitutes.find(t.id);
        out.append(tid);
    }

    private static boolean isParentModule(Namespace parent, Namespace child) {
        if(parent.elements().size() >= child.elements().size()) {
            return false;
        }
        for(int e = 0; e < parent.elements().size(); e += 1) {
            if(!parent.elements().get(e).equals(child.elements().get(e))) {
                return false;
            }
        }
        return true;
    }

    private void emitModules(StringBuilder out) {
        out.append("[");
        boolean hadModule = false;
        for(Namespace module: this.modules) {
            boolean isRootModule = this.modules.stream()
                .noneMatch(m -> SymbolInfoGen.isParentModule(m, module));
            if(!isRootModule) { continue; }
            if(hadModule) { out.append(","); }
            hadModule = true;
            this.emitModule(module, out);
        }
        out.append("]");
    }

    private static boolean symbolInModule(Namespace symbol, Namespace module) {
        if(symbol.elements().size() != module.elements().size() + 1) {
            return false;
        }
        for(int e = 0; e < module.elements().size(); e += 1) {
            if(!symbol.elements().get(e).equals(module.elements().get(e))) {
                return false;
            }
        }
        return true;
    }

    private void emitModule(Namespace module, StringBuilder out) {
        out.append("{\"path\":\"");
        out.append(module);
        out.append("\"");
        Optional<Symbols.Module> declaredModule = this.symbols
            .getDeclaredModule(module);
        boolean hasInformation = declaredModule.isPresent()
            && declaredModule.get().docComment().isPresent();
        if(hasInformation) {
            out.append(",\"information\":");
            this.emitString(declaredModule.get().docComment().get(), out);
        }
        out.append(",\"modules\":[");
        List<Namespace> childModules = this.modules
            .stream()
            .filter(cm -> SymbolInfoGen.isParentModule(module, cm))
            .toList();
        boolean hadModule = false;
        for(Namespace childModule: childModules) {
            boolean isRootModule = childModules.stream()
                .noneMatch(m -> SymbolInfoGen.isParentModule(m, childModule));
            if(!isRootModule) { continue; }
            if(hadModule) { out.append(","); }
            hadModule = true;
            this.emitModule(childModule, out);
        }
        out.append("],\"constants\":[");
        boolean hadConstant = false;
        for(Namespace symbolPath: this.symbols.allSymbolPaths()) {
            if(!SymbolInfoGen.symbolInModule(symbolPath, module)) {
                continue;
            }
            Symbols.Symbol symbol = this.symbols.get(symbolPath).get();
            if(symbol.type != Symbols.Symbol.Type.VARIABLE) {
                continue;
            }
            if(hadConstant) { out.append(","); }
            hadConstant = true;
            this.emitConstant(symbolPath, symbol, symbol.getValue(), out);
        }
        out.append("],\"procedures\":[");
        boolean hadProcedure = false;
        for(Namespace symbolPath: this.symbols.allSymbolPaths()) {
            if(!SymbolInfoGen.symbolInModule(symbolPath, module)) {
                continue;
            }
            Symbols.Symbol symbol = this.symbols.get(symbolPath).get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
                continue;
            }
            if(hadProcedure) { out.append(","); }
            hadProcedure = true;
            this.emitProcedure(symbolPath, symbol, symbol.getValue(), out);
        }
        out.append("]}");
    }

    private void emitConstant(
        Namespace path, Symbols.Symbol symbol, Symbols.Symbol.Variable data,
        StringBuilder out
    ) {
        out.append("{\"path\":\"");
        out.append(path);
        out.append("\",\"public\":");
        out.append(symbol.isPublic? "true" : "false");
        if(symbol.docComment.isPresent()) {
            out.append(",\"information\":");
            this.emitString(symbol.docComment.get(), out);
        }
        if(symbol.externalName.isPresent()) {
            out.append(",\"external\":\"");
            out.append(symbol.externalName.get());
            out.append("\"");
        }
        out.append(",\"type\":");
        this.emitType(data.valueType().get(), out);
        out.append("}");
    }

    private void emitProcedure(
        Namespace path, Symbols.Symbol symbol, Symbols.Symbol.Procedure data,
        StringBuilder out
    ) {
        out.append("{\"path\":\"");
        out.append(path);
        out.append("\",\"public\":");
        out.append(symbol.isPublic? "true" : "false");
        if(symbol.docComment.isPresent()) {
            out.append(",\"information\":");
            this.emitString(symbol.docComment.get(), out);
        }
        if(symbol.externalName.isPresent()) {
            out.append(",\"external\":\"");
            out.append(symbol.externalName.get());
            out.append("\"");
        }
        out.append(",\"arguments\":[");
        for(int argI = 0; argI < data.argumentNames().size(); argI += 1) {
            if(argI > 0) { out.append(","); }
            out.append("{\"name\":\"");
            out.append(data.argumentNames().get(argI));
            out.append("\",\"type\":");
            this.emitType(data.argumentTypes().get().get(argI), out);
            out.append("}");
        }
        out.append("],\"returns\":");
        this.emitType(data.returnType().get(), out);
        out.append("}");
    }

}

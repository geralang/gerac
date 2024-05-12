
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashMap;
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
        this.modules.addAll(this.symbols.allDeclaredModulePaths());
        for(Namespace symbolPath: this.symbols.allSymbolPaths()) {
            List<String> modulePath = new ArrayList<>(symbolPath.elements());
            modulePath.remove(modulePath.size() - 1);
            modules.add(new Namespace(modulePath));
        }
    }

    public String generate() {
        StringBuilder out = new StringBuilder();
        out.append("{\"modules\":");
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

    private void emitType(TypeVariable t, StringBuilder out) {
        out.append("\"");
        this.emitType(t, out, new HashSet<>(), new HashMap<>());
        out.append("\"");
    }

    private void emitType(
        TypeVariable t, StringBuilder out, 
        HashSet<Integer> e_twice,
        HashMap<Integer, Optional<Integer>> e_ids
    ) {
        int root = this.types.substitutes.find(t.id);
        if(e_ids.containsKey(root)) {
            e_ids.put(root, Optional.of(e_twice.size()));
            e_twice.add(root);
            out.append("<circular *");
            out.append(e_twice.size());
            out.append(">");
            return;
        } else {
            e_ids.put(root, Optional.empty());
        }
        DataType<TypeVariable> tv = this.types.get(root);
        StringBuilder type = new StringBuilder();
        switch(tv.type) {
            case ANY: type.append("any"); break;
            case NUMERIC: type.append("(int | float)"); break;
            case INDEXED: type.append("([any] | str)"); break;
            case REFERENCED: type.append("([any] | { ... })"); break;
            case UNIT: type.append("unit"); break;
            case BOOLEAN: type.append("bool"); break;
            case INTEGER: type.append("int"); break;
            case FLOAT: type.append("float"); break;
            case STRING: type.append("str"); break;
            case ARRAY: {
                DataType.Array<TypeVariable> data = tv.getValue();
                type.append("[");
                this.emitType(data.elementType(), type, e_twice, e_ids);
                type.append("]");
            } break;
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject<TypeVariable> data = tv.getValue();
                type.append("{");
                boolean hadMember = false;
                for(String member: data.memberTypes().keySet()) {
                    if(hadMember) { type.append(", "); }
                    else { type.append(" "); }
                    hadMember = true;
                    type.append(member);
                    type.append(" = ");
                    this.emitType(
                        data.memberTypes().get(member), type, e_twice, e_ids
                    );
                }
                if(data.expandable()) {
                    if(hadMember) { type.append(", "); }
                    else { type.append(" "); }
                    hadMember = true;
                    type.append("...");
                }
                if(hadMember) { type.append(" "); }
                type.append("}");
            } break;
            case CLOSURE: {
                DataType.Closure<TypeVariable> data = tv.getValue();
                type.append("|");
                for(
                    int argI = 0; argI < data.argumentTypes().size(); argI += 1
                ) {
                    if(argI > 0) { type.append(", "); }
                    this.emitType(
                        data.argumentTypes().get(argI), type, e_twice, e_ids
                    );
                }
                type.append("| -> ");
                this.emitType(
                    data.returnType(), type, e_twice, e_ids
                );
            } break;
            case UNION: {
                DataType.Union<TypeVariable> data = tv.getValue();
                type.append("(");
                boolean hadMember = false;
                for(String variant: data.variantTypes().keySet()) {
                    if(hadMember) { type.append(" | "); }
                    else type.append(" ");
                    hadMember = true;
                    type.append("#");
                    type.append(variant);
                    type.append(" ");
                    this.emitType(
                        data.variantTypes().get(variant), type, e_twice, e_ids
                    );
                }
                if(data.expandable()) {
                    if(hadMember) { type.append(" | "); }
                    else { type.append(" "); }
                    hadMember = true;
                    type.append("...");
                }
                if(hadMember) { type.append(" "); }
                type.append(")");
            } break;
        }
        Optional<Integer> e_id = e_ids.get(root);
        if(e_id.isPresent()) {
            out.append("<ref *");
            out.append(e_id.get());
            out.append("> ");
        }
        out.append(type);
        e_ids.remove(root);
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

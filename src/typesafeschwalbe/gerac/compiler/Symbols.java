
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.backend.Ir;
import typesafeschwalbe.gerac.compiler.backend.Value;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.TypeConstraint;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

public class Symbols {

    public static Error duplicateSymbolError(
        Namespace path, Source duplicateSource, Symbol ogSymbol
    ) {
        return new Error(
            "Duplicate symbol",
            Error.Marking.error(
                duplicateSource, 
                "a symbol with the path '"
                    + path.toString() + "' was already declared"
            ),
            Error.Marking.info(
                ogSymbol.source, "previously declared here"
            )
        );
    }

    public static record BuiltinContext(
        List<TypeConstraint> constraints,
        List<TypeVariable> arguments,
        TypeVariable returned
    ) {}

    public static class Symbol {

        public static record Procedure(
            List<String> argumentNames,
            Optional<Function<Source, BuiltinContext>> builtinContext,
            Optional<List<TypeVariable>> argumentTypes,
            Optional<TypeVariable> returnType,
            Optional<List<AstNode>> body,
            Optional<Ir.Context> ir_context,
            Optional<List<Ir.Instr>> ir_body
        ) {}

        public static record Variable(
            Optional<TypeVariable> valueType,
            Optional<AstNode> valueNode,
            Optional<Value> value
        ) {}

        public enum Type {
            PROCEDURE, // Procedure
            VARIABLE   // Variable
        }

        public final Type type;
        public final boolean isPublic;
        public final Source source;
        public final Namespace[] usages;
        private Object value;
        private final List<Object> variants;
        private final Map<Integer, Integer> mappedVariants;
        public final Optional<String> externalName;
        public final Optional<String> docComment;

        public Symbol(
            Type type, boolean isPublic, Source source,
            Namespace[] usages,
            Object value, 
            Optional<String> externalName,
            Optional<String> docComment
        ) {
            this.type = type;
            this.isPublic = isPublic;
            this.source = source;
            this.usages = usages;
            this.value = value;
            this.variants = new ArrayList<>();
            this.mappedVariants = new HashMap<>();
            this.externalName = externalName;
            this.docComment = docComment;
        }

        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) this.value;
        }
        public void setValue(Object value) {
            this.value = value;
        }

        public int variantCount() {
            return this.variants.size();
        }
        @SuppressWarnings("unchecked")
        public <T> T getVariant(int variantIdx) {
            return (T) this.variants.get(this.mappedVariantIdx(variantIdx));
        }
        public void addVariant(Object value) {
            this.variants.add(value);
        }
        public void setVariant(int variantIdx, Object value) {
            this.variants.set(this.mappedVariantIdx(variantIdx), value);
        }
        public void mapVariantIdx(int oldIdx, int newIdx) {
            this.mappedVariants.put(oldIdx, newIdx);
        }
        public int mappedVariantIdx(int idx) {
            Integer mapped = this.mappedVariants.get(idx);
            return mapped == null? idx : mapped;
        }

    }

    public static record Module(Source source, Optional<String> docComment) {}

    private final Map<Namespace, Symbol> symbols;
    private final Map<Namespace, Module> modules;

    public Symbols() {
        this.symbols = new HashMap<>();
        this.modules = new HashMap<>();
    }

    public void add(Namespace path, Symbol symbol) {
        this.symbols.put(path, symbol);
    }
    
    public Optional<Error> addAll(List<AstNode> nodes) {
        if(nodes.size() == 0) { return Optional.empty(); }
        if(nodes.get(0).type != AstNode.Type.MODULE_DECLARATION) {
            return Optional.of(new Error(
                "File does not start with a module declaration",
                Error.Marking.error(nodes.get(0).source, "")
            ));
        }
        Namespace currentModule = null; // first node will overwrite this 
        List<Namespace> usages = new ArrayList<>();
        usages.add(new Namespace(List.of("core", "*")));
        for(AstNode node: nodes) {
            switch(node.type) {
                case MODULE_DECLARATION: {
                    AstNode.ModuleDeclaration data = node.getValue();
                    if(this.modules.containsKey(data.path())) {
                        return Optional.of(new Error(
                            "Duplicate module",
                            Error.Marking.error(
                                node.source,
                                "a module with the same path"
                                    + " was already declared"
                            ),
                            Error.Marking.info(
                                this.modules.get(data.path()).source,
                                "previously declared here"
                            )
                        ));
                    }
                    this.modules.put(data.path(), new Module(
                        node.source, data.docComment()
                    ));
                    currentModule = data.path();
                    List<String> usageSegments = new ArrayList<>(
                        data.path().elements()
                    );
                    usageSegments.add("*");
                    usages.add(new Namespace(usageSegments));
                } break;
                case PROCEDURE: {
                    AstNode.Procedure data = node.getValue();
                    List<String> fullPath = new ArrayList<>(
                        currentModule.elements()
                    );
                    fullPath.add(data.name());
                    Namespace finalPath = new Namespace(fullPath);
                    if(this.symbols.containsKey(finalPath)) {
                        return Optional.of(Symbols.duplicateSymbolError(
                            finalPath, node.source, this.symbols.get(finalPath)
                        ));
                    }
                    this.symbols.put(
                        finalPath,
                        new Symbol(
                            Symbol.Type.PROCEDURE, data.isPublic(), 
                            node.source, usages.toArray(Namespace[]::new),
                            new Symbol.Procedure(
                                data.argumentNames(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(data.body()),
                                Optional.empty(), Optional.empty()
                            ),
                            Optional.empty(),
                            data.docComment()
                        )
                    );
                    System.out.println(data.docComment());
                } break;
                case VARIABLE: {
                    AstNode.Variable data = node.getValue();
                    List<String> fullPath = new ArrayList<>(
                        currentModule.elements()
                    );
                    fullPath.add(data.name());
                    Namespace finalPath = new Namespace(fullPath);
                    if(this.symbols.containsKey(finalPath)) {
                        return Optional.of(Symbols.duplicateSymbolError(
                            finalPath, node.source, this.symbols.get(finalPath)
                        ));
                    }
                    this.symbols.put(
                        finalPath,
                        new Symbol(
                            Symbol.Type.VARIABLE, data.isPublic(), 
                            node.source, usages.toArray(Namespace[]::new),
                            new Symbol.Variable(
                                Optional.empty(),
                                data.value(),
                                Optional.empty()
                            ),
                            Optional.empty(),
                            data.docComment()
                        )
                    );
                } break;
                case USE: {
                    AstNode.Usages data = node.getValue();
                    usages.addAll(data.paths());
                } break;
                default:
                    throw new RuntimeException("invalid top-level statement!");
            }
        }
        return Optional.empty();
    }

    public boolean accessAllowed(
        Symbol accessedSymbol, Source accessSource
    ) {
        boolean fileMatches = accessedSymbol.source.file()
            .equals(accessSource.file());
        return accessedSymbol.isPublic || fileMatches;
    }   

    public List<Namespace> allowedPathExpansions(
        Namespace path, Symbol inSymbol, Source accessSource
    ) {
        String firstPathElement = path.elements().get(0);
        Set<Namespace> validExpansions = new HashSet<>();
        for(Namespace usage: inSymbol.usages) {
            String lastUsageElement = usage.elements()
                .get(usage.elements().size() - 1);
            boolean isWildcard = lastUsageElement.equals("*");
            boolean qualifies = isWildcard 
                || lastUsageElement.equals(firstPathElement);
            if(!qualifies) { continue; }
            List<String> fullPathElements = new ArrayList<>();
            fullPathElements.addAll(usage.elements());
            fullPathElements.remove(fullPathElements.size() - 1);
            fullPathElements.addAll(path.elements());
            Namespace fullPath = new Namespace(fullPathElements);
            Optional<Symbols.Symbol> accessedSymbol = this.get(fullPath);
            if(accessedSymbol.isEmpty()) { continue; }
            if(!this.accessAllowed(accessedSymbol.get(), accessSource)) {
                continue;
            }
            validExpansions.add(fullPath);
        }
        return new ArrayList<>(validExpansions);
    }

    public Optional<Symbol> get(Namespace path) {
        return Optional.ofNullable(this.symbols.get(path));
    }

    public Set<Namespace> allSymbolPaths() {
        return this.symbols.keySet();
    }

    public Optional<Module> getDeclaredModule(Namespace path) {
        return Optional.ofNullable(this.modules.get(path));
    }

}
 
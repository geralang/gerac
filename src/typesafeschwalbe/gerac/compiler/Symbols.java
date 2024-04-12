
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.backend.Ir;
import typesafeschwalbe.gerac.compiler.backend.Value;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.TypeChecker;

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

    @FunctionalInterface
    public interface ArgTypeChecker {
        public boolean isValid(
            List<DataType> argTypes, TypeChecker typeChecker
        ) throws ErrorException;
    }

    public static class Symbol {
        
        public static record LoweredProcedure(
            List<DataType> argumentTypes,
            Ir.Context context,
            List<Ir.Instr> body
        ) {}

        public static record Procedure(
            List<String> argumentNames,
            Optional<ArgTypeChecker> allowedArgumentTypes,
            Optional<List<DataType>> argumentTypes,
            Optional<Function<Source, DataType>> returnType,
            Optional<List<AstNode>> body
        ) {}

        public static record Variable(
            Optional<DataType> valueType,
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
        private final Namespace[] usages;
        private Object value;
        private final List<Object> variants;
        public final Optional<String> externalName;

        public Symbol(
            Type type, boolean isPublic, Source source,
            Namespace[] usages,
            Object value, 
            Optional<String> externalName
        ) {
            this.type = type;
            this.isPublic = isPublic;
            this.source = source;
            this.usages = usages;
            this.value = value;
            this.variants = new ArrayList<>();
            this.externalName = externalName;
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
            return (T) this.variants.get(variantIdx);
        }
        public void addVariant(Object value) {
            this.variants.add(value);
        }
        public void setVariant(int variantIdx, Object value) {
            this.variants.set(variantIdx, value);
        }

    }

    private final Map<Namespace, Symbol> symbols;
    private final Map<Namespace, Source> declaredModules;

    public Symbols() {
        this.symbols = new HashMap<>();
        this.declaredModules = new HashMap<>();
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
                    AstNode.NamespacePath data = node.getValue();
                    if(this.declaredModules.containsKey(data.path())) {
                        return Optional.of(new Error(
                            "Duplicate module",
                            Error.Marking.error(
                                node.source,
                                "a module with the same path"
                                    + " was already declared"
                            ),
                            Error.Marking.info(
                                this.declaredModules.get(data.path()),
                                "previously declared here"
                            )
                        ));
                    }
                    this.declaredModules.put(data.path(), node.source);
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
                                Optional.empty(), Optional.empty(),
                                Optional.of(data.body())
                            ),
                            Optional.empty()
                        )
                    );
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
                                Optional.empty(), data.value(),
                                Optional.empty()
                            ),
                            Optional.empty()
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
        boolean fileMatches = accessedSymbol.source.file
            .equals(accessSource.file);
        return accessedSymbol.isPublic || fileMatches;
    }   

    public List<Namespace> allowedPathExpansions(
        Namespace path, Symbol inSymbol, Source accessSource
    ) {
        String firstPathElement = path.elements().get(0);
        List<Namespace> validExpansions = new ArrayList<>();
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
        return validExpansions;
    }

    public Optional<Symbol> get(Namespace path) {
        return Optional.ofNullable(this.symbols.get(path));
    }

    public Set<Namespace> allSymbolPaths() {
        return this.symbols.keySet();
    }

}
 
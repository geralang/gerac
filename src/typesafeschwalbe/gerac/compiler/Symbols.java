
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.TypeChecker;
import typesafeschwalbe.gerac.compiler.frontend.TypingException;

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
        ) throws TypingException;
    }

    public static class Symbol {
        
        public static record Procedure(
            List<String> argumentNames,
            Optional<ArgTypeChecker> allowedArgumentTypes,
            Optional<List<DataType>> argumentTypes,
            Optional<Function<Source, DataType>> returnType,
            Optional<List<AstNode>> body
        ) {}

        public static record Variable(
            Optional<DataType> valueType,
            Optional<AstNode> value
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

        public Symbol(
            Type type, boolean isPublic, Source source,
            Namespace[] usages,
            Object value
        ) {
            this.type = type;
            this.isPublic = isPublic;
            this.source = source;
            this.usages = usages;
            this.value = value;
            this.variants = new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) this.value;
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
                            )
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
                                Optional.empty(), data.value()
                            )
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

    public List<Error> canonicalize() {
        List<Error> errors = new ArrayList<>();
        for(Symbol symbol: this.symbols.values()) {
            switch(symbol.type) {
                case VARIABLE: {
                    Symbol.Variable data = symbol.getValue();
                    if(data.value.isPresent()) {
                        AstNode value = this.canonicalizeNode(
                            data.value.get(), symbol, new HashSet<>(), errors
                        );
                        symbol.value = new Symbol.Variable(
                            Optional.empty(), Optional.of(value)
                        );
                    }
                } break;
                case PROCEDURE: {
                    Symbol.Procedure data = symbol.getValue();
                    if(data.body.isPresent()) {
                        Set<String> variables = new HashSet<>();
                        for(String argument: data.argumentNames()) {
                            variables.add(argument);
                        }
                        List<AstNode> body = data.body.get()
                            .stream().map(node -> this.canonicalizeNode(
                                node, symbol, variables, errors
                            )).toList();
                        symbol.value = new Symbol.Procedure(
                            data.argumentNames(), 
                            Optional.empty(),
                            Optional.empty(), Optional.empty(),
                            Optional.of(body)
                        );
                    }
                } break;
            }
        }
        return errors;
    }
    
    private static Error makeInvalidSymbolError(Source src, Namespace path) {
        return new Error(
            "Invalid access",
            Error.Marking.error(
                src,
                "'" + path.toString() + "'"
                    + " is not a known and accessible symbol"
            )
        );
    }

    private AstNode canonicalizeNode(
        AstNode node, Symbol symbol, Set<String> variables, List<Error> errors
    ) {
        switch(node.type) {
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                Optional<AstNode> value = Optional.empty();
                if(data.value().isPresent()) {
                    value = Optional.of(this.canonicalizeNode(
                        data.value().get(), symbol, variables, errors
                    ));
                }
                variables.add(data.name());
                return new AstNode(
                    node.type,
                    new AstNode.Variable(
                        data.isPublic(), data.isMutable(), data.name(), value
                    ),
                    node.source
                );
            }
            case MODULE_ACCESS: {
                AstNode.ModuleAccess data = node.getValue();
                boolean isLocalVariable = data.path().elements().size() == 1
                    && variables.contains(data.path().elements().get(0));
                if(isLocalVariable) {
                    return new AstNode(
                        AstNode.Type.VARIABLE_ACCESS,
                        new AstNode.VariableAccess(
                            data.path().elements().get(0)
                        ),
                        node.source
                    );
                }
                String firstPathElement = data.path().elements().get(0);
                Namespace expanded = data.path();
                for(Namespace usage: symbol.usages) {
                    String lastUsageElement = usage.elements()
                        .get(usage.elements().size() - 1);
                    boolean isWildcard = lastUsageElement.equals("*");
                    boolean qualifies = isWildcard
                        || lastUsageElement.equals(firstPathElement);
                    if(qualifies) {
                        List<String> fullPathElements = new ArrayList<>();
                        fullPathElements.addAll(usage.elements());
                        fullPathElements.remove(fullPathElements.size() - 1);
                        fullPathElements.addAll(data.path().elements());
                        Namespace fullPath = new Namespace(fullPathElements);
                        Optional<Symbol> accessedSymbol = this.get(fullPath);
                        if(accessedSymbol.isEmpty()) { continue; }
                        boolean fileMatches = accessedSymbol.get()
                            .source.file.equals(node.source.file);
                        boolean accessAllowed = accessedSymbol.get().isPublic
                            || fileMatches;
                        if(!accessAllowed) { continue; }
                        expanded = fullPath;
                    }
                }
                Optional<Symbol> accessed = this.get(expanded);
                if(accessed.isEmpty()) {
                    errors.add(Symbols.makeInvalidSymbolError(
                        node.source, expanded
                    ));
                    return node;
                }
                boolean accessAllowed = accessed.get().isPublic
                    || accessed.get().source.file.equals(node.source.file);
                if(!accessAllowed) {
                    errors.add(Symbols.makeInvalidSymbolError(
                        node.source, expanded
                    ));
                    return node;
                }
                return new AstNode(
                    node.type, 
                    new AstNode.ModuleAccess(expanded, Optional.empty()),
                    node.source
                );
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                List<AstNode> values = data.values()
                    .stream().map(value -> this.canonicalizeNode(
                        value, symbol, variables, errors
                    )).toList();
                return new AstNode(
                    node.type,
                    new AstNode.ArrayLiteral(values),
                    node.source
                );
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                AstNode value = this.canonicalizeNode(
                    data.value(), symbol, variables, errors
                );
                List<AstNode> branchValues = data.branchValues()
                    .stream().map(branchValue -> this.canonicalizeNode(
                        branchValue, symbol, new HashSet<String>(), errors
                    )).toList();
                List<List<AstNode>> branchBodies = new ArrayList<>();
                for(List<AstNode> oldBranchBody: data.branchBodies()) {
                    Set<String> branchVariables = new HashSet<>(variables);
                    List<AstNode> branchBody = oldBranchBody
                        .stream().map(statement -> this.canonicalizeNode(
                            statement, symbol, branchVariables, errors
                        )).toList();
                    branchBodies.add(branchBody);
                }
                Set<String> elseVariables = new HashSet<>(variables);
                List<AstNode> elseBody = data.elseBody()
                    .stream().map(statement -> this.canonicalizeNode(
                        statement, symbol, elseVariables, errors
                    )).toList();
                return new AstNode(
                    node.type,
                    new AstNode.CaseBranching(
                        value, branchValues, branchBodies, elseBody
                    ),
                    node.source
                );
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                AstNode condition = this.canonicalizeNode(
                    data.condition(), symbol, variables, errors
                );
                Set<String> ifVariables = new HashSet<>(variables);
                List<AstNode> ifBody = data.ifBody()
                    .stream().map(statement -> this.canonicalizeNode(
                        statement, symbol, ifVariables, errors
                    )).toList();
                Set<String> elseVariables = new HashSet<>(variables);
                List<AstNode> elseBody = data.elseBody()
                    .stream().map(statement -> this.canonicalizeNode(
                        statement, symbol, elseVariables, errors
                    )).toList();
                return new AstNode(
                    node.type,
                    new AstNode.CaseConditional(condition, ifBody, elseBody),
                    node.source
                );
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                AstNode value = this.canonicalizeNode(
                    data.value(), symbol, variables, errors
                );
                List<List<AstNode>> branchBodies = new ArrayList<>();
                for(
                    int branchI = 0; 
                    branchI < data.branchBodies().size(); 
                    branchI += 1
                ) {
                    List<AstNode> oldBranchBody = data.branchBodies()
                        .get(branchI);
                    Set<String> branchVariables = new HashSet<>(variables);
                    Optional<String> branchVariableName = data.branchVariableNames()
                        .get(branchI);
                    if(branchVariableName.isPresent()) {
                        branchVariables.add(branchVariableName.get());
                    }
                    List<AstNode> branchBody = oldBranchBody
                        .stream().map(statement -> this.canonicalizeNode(
                            statement, symbol, branchVariables, errors
                        )).toList();
                    branchBodies.add(branchBody);
                }
                Set<String> elseVariables = new HashSet<>(variables);
                Optional<List<AstNode>> elseBody = Optional.empty();
                if(data.elseBody().isPresent()) {
                    elseBody = Optional.of(data.elseBody().get()
                        .stream().map(statement -> this.canonicalizeNode(
                            statement, symbol, elseVariables, errors
                        )).toList()
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.CaseVariant(
                        value,
                        data.branchVariants(),
                        data.branchVariableNames(),
                        branchBodies, elseBody
                    ),
                    node.source
                );
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                AstNode called = this.canonicalizeNode(
                    data.called(), symbol, variables, errors
                );
                List<AstNode> arguments = data.arguments()
                    .stream().map(argument -> this.canonicalizeNode(
                        argument, symbol, variables, errors
                    )).toList();
                return new AstNode(
                    node.type, new AstNode.Call(called, arguments), node.source
                );
            }
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                Set<String> bodyVariables = new HashSet<>(variables);
                for(String argument: data.argumentNames()) {
                    bodyVariables.add(argument);
                }
                List<AstNode> body = data.body()
                    .stream().map(statement -> this.canonicalizeNode(
                        statement, symbol, bodyVariables, errors
                    )).toList();
                return new AstNode(
                    node.type,
                    new AstNode.Closure(
                        data.argumentNames(), Optional.empty(),
                        Optional.empty(), Optional.empty(),
                        body
                    ),
                    node.source
                );
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                AstNode called = this.canonicalizeNode(
                    data.called(), symbol, variables, errors
                );
                List<AstNode> arguments = data.arguments()
                    .stream().map(argument -> this.canonicalizeNode(
                        argument, symbol, variables, errors
                    )).toList();
                return new AstNode(
                    node.type,
                    new AstNode.MethodCall(
                        called, data.memberName(), arguments
                    ), 
                    node.source
                );
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                AstNode accessed = this.canonicalizeNode(
                    data.accessed(), symbol, variables, errors
                );
                return new AstNode(
                    node.type, 
                    new AstNode.ObjectAccess(accessed, data.memberName()), 
                    node.source
                );
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                Map<String, AstNode> values = new HashMap<>();
                for(String memberName: data.values().keySet()) {
                    AstNode memberValue = this.canonicalizeNode(
                        data.values().get(memberName), symbol, variables, errors
                    );
                    values.put(memberName, memberValue);
                }
                return new AstNode(
                    node.type, new AstNode.ObjectLiteral(values), node.source
                );
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                AstNode value = this.canonicalizeNode(
                    data.value(), symbol, variables, errors
                );
                return new AstNode(
                    node.type, 
                    new AstNode.VariantLiteral(data.variantName(), value), 
                    node.source
                ); 
            }
            case REPEATING_ARRAY_LITERAL:
            case ADD:
            case AND:
            case ARRAY_ACCESS:
            case ASSIGNMENT:
            case DIVIDE:
            case EQUALS:
            case GREATER_THAN:
            case GREATER_THAN_EQUAL:
            case LESS_THAN:
            case LESS_THAN_EQUAL:
            case MODULO:
            case MULTIPLY:
            case NOT_EQUALS:
            case OR:
            case SUBTRACT: {
                AstNode.BiOp data = node.getValue();
                AstNode left = this.canonicalizeNode(
                    data.left(), symbol, variables, errors
                );
                AstNode right = this.canonicalizeNode(
                    data.right(), symbol, variables, errors
                );
                return new AstNode(
                    node.type, new AstNode.BiOp(left, right), node.source
                );
            }
            case NEGATE:
            case NOT:
            case RETURN:
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                AstNode value = this.canonicalizeNode(
                    data.value(), symbol, variables, errors
                );
                return new AstNode(
                    node.type, new AstNode.MonoOp(value), node.source
                );
            }
            case BOOLEAN_LITERAL:
            case FLOAT_LITERAL:
            case INTEGER_LITERAL:
            case STRING_LITERAL:
            case UNIT_LITERAL:
            case TARGET:
            case USE:
            case MODULE_DECLARATION:
            case PROCEDURE: {
                return node;
            }
            default: {
                throw new RuntimeException("unhandled node type");
            }
        }
    }

    public Optional<Symbol> get(Namespace path) {
        return Optional.ofNullable(this.symbols.get(path));
    }

}
 
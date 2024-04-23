
package typesafeschwalbe.gerac.compiler.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Color;
import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.UnionFind;
import typesafeschwalbe.gerac.compiler.Symbols.BuiltinContext;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.ConstraintGenerator.ProcedureUsage;
import typesafeschwalbe.gerac.compiler.types.DataType.DataTypeValue;

public class ConstraintSolver {

    private static Error makeInvalidTypeError(
        Source deducedSrc, String deducedStr,
        Source requiredSrc, String requiredStr
    ) {
        return new Error(
            "Invalid type",
            Error.Marking.info(
                deducedSrc,
                "this is " + deducedStr + " here"
            ),
            Error.Marking.error(
                requiredSrc,
                "but needs to be " + requiredStr + " here"
            )
        );
    }

    private static Error makeIncompatibleTypesError(
        Source aSource, String aStr, 
        Source bSource, String bStr,
        Source src, String pathDescription
    ) {
        return new Error(
            "Incompatible types",
            Error.Marking.info(
                aSource,
                "this is " + aStr
            ),
            Error.Marking.info(
                bSource,
                "this is " + bStr
            ),
            Error.Marking.error(
                src, 
                (pathDescription.length() > 0
                    ? pathDescription 
                    : "they"
                ) + " need to be compatible because of this"
            )
        );
    }

    private static String displayOrdinal(int index) {
        int displayed = index + 1;
        switch(displayed % 10) {
            case 1: return displayed + "st";
            case 2: return displayed + "nd";
            case 3: return displayed + "rd";
            default: return displayed + "th";
        }
    }

    private static record Scope(
        Symbols.Symbol symbol,
        int variant,
        TypeContext ctx,
        Optional<List<TypeVariable>> arguments,
        TypeVariable returned,
        List<ConstraintGenerator.VariableUsage> varUsages,
        List<ConstraintGenerator.ProcedureUsage> procUsages
    ) {}

    private Symbols symbols;
    private ConstraintGenerator cGen;
    private List<Scope> scopeStack;

    public ConstraintSolver() {}

    private Scope scope() {
        return this.scopeStack.get(this.scopeStack.size() - 1);
    }

    public List<Error> checkSymbols(Symbols symbols) {
        this.symbols = symbols;
        List<Error> errors = new ArrayList<>();
        for(Namespace path: symbols.allSymbolPaths()) {
            this.cGen = new ConstraintGenerator(symbols);
            this.scopeStack = new LinkedList<>();
            Symbols.Symbol symbol = symbols.get(path).get();
            try {
                switch(symbol.type) {
                    case PROCEDURE: {
                        Symbols.Symbol.Procedure data = symbol.getValue();
                        this.solveProcedure(
                            symbol, data,
                            Optional.empty(), Optional.empty(), Optional.empty()
                        );
                    } break;
                    case VARIABLE: {
                        Symbols.Symbol.Variable data = symbol.getValue();
                        this.solveVariable(symbol, data);
                    } break;
                    default: {
                        throw new RuntimeException("unhandled symbol type!");
                    }
                }
            } catch(ErrorException e) {
                errors.add(e.error);
            }
        }
        return errors;
    }

    private static record SolvedProcedure(
        int variant,
        boolean recursive,
        List<TypeValue> arguments, TypeValue returned,
        List<TypeVariable> argumentV, TypeVariable returnedV
    ) {
        private void unify(
            Source accessSource,
            List<TypeVariable> arguments, List<Source> argSources,
            TypeVariable returned,
            ConstraintSolver solver
        ) throws ErrorException {
            for(int argI = 0; argI < arguments.size(); argI += 1) {
                TypeVariable argV = this.recursive
                    ? this.argumentV.get(argI)
                    : solver.asTypeVariable(this.arguments.get(argI));
                solver.unifyVars(
                    argV, arguments.get(argI), argSources.get(argI)
                );
            }
            TypeVariable returnedV = this.recursive
                ? this.returnedV
                : solver.asTypeVariable(this.returned);
            solver.unifyVars(returned, returnedV, accessSource);
        }
    }

    private SolvedProcedure solveProcedure(
        Symbols.Symbol symbol, Symbols.Symbol.Procedure data,
        Optional<List<TypeValue>> argumentTypes,
        Optional<Source> usageSource,
        Optional<List<Source>> argumentSources
    ) throws ErrorException {
        for(Scope scope: this.scopeStack) {
            if(scope.symbol != symbol) { continue; }
            return new SolvedProcedure(
                scope.variant,
                true,
                null, null,
                scope.arguments.get(), scope.returned
            );
        }
        if(argumentTypes.isPresent()) {
            for(int varI = 0; varI < symbol.variantCount(); varI += 1) {
                Symbols.Symbol.Procedure variant = symbol.getVariant(varI);
                if(variant == null) { continue; }
                boolean argsMatch = true;
                for(
                    int argI = 0; argI < variant.argumentTypes().get().size(); 
                    argI += 1
                ) {
                    TypeValue argA = argumentTypes.get().get(argI);
                    TypeValue varA = variant.argumentTypes().get().get(argI);
                    if(!argA.deepEquals(varA)) {
                        argsMatch = false;
                        break;
                    }
                }
                if(!argsMatch) { continue; }
                return new SolvedProcedure(
                    varI,
                    false,
                    variant.argumentTypes().get(), variant.returnType().get(),
                    null, null
                );
            }
        }
        int variant = symbol.variantCount();
        Scope scope;
        if(data.builtinContext().isPresent()) {
            BuiltinContext builtin = data.builtinContext().get()
                .apply(usageSource.orElse(symbol.source));
            scope = new Scope(
                symbol, variant,
                builtin.ctx(),
                Optional.of(builtin.arguments()), builtin.returned(),
                List.of(), List.of()
            );
        } else {
            ConstraintGenerator.ProcOutput cOutput = this.cGen
                .generateProc(symbol, data);
            scope = new Scope(
                symbol, variant,
                cOutput.ctx(),
                Optional.of(cOutput.arguments()), cOutput.returned(),
                cOutput.varUsages(), cOutput.procUsages()
            );
        }
        this.scopeStack.add(scope);
        if(data.argumentTypes().isPresent()) {
            for(int argI = 0; argI < data.argumentNames().size(); argI += 1) {
                TypeVariable argV = this
                    .asTypeVariable(data.argumentTypes().get().get(argI));
                this.unifyVars(
                    argV, scope.arguments().get().get(argI), 
                    data.argumentTypes().get().get(argI).source.get()
                );
            }
        }
        if(argumentTypes.isPresent()) {
            for(int argI = 0; argI < data.argumentNames().size(); argI += 1) {
                TypeVariable argV = this
                    .asTypeVariable(argumentTypes.get().get(argI));
                this.unifyVars(
                    argV, scope.arguments().get().get(argI), 
                    argumentSources.get().get(argI)
                );
            }
        }
        this.solveConstraints(scope.ctx().constraints);
        Optional<List<AstNode>> processedBody = Optional.empty();
        if(data.body().isPresent()) {
            processedBody = Optional.of(
                this.processNodes(data.body().get())
            );
        }
        List<TypeValue> rArguments = new ArrayList<>();
        for(int argI = 0; argI < data.argumentNames().size(); argI += 1) {
            rArguments.add(this.asTypeValue(scope.arguments().get().get(argI)));
        }
        TypeValue rReturned = this.asTypeValue(scope.returned());
        this.scopeStack.remove(this.scopeStack.size() - 1);
        symbol.addVariant(new Symbols.Symbol.Procedure(
            data.argumentNames(), data.builtinContext(),
            Optional.of(rArguments), Optional.of(rReturned), 
            processedBody,
            Optional.empty(), Optional.empty()
        ));
        return new SolvedProcedure(
            variant, false,
            rArguments, rReturned,
            null, null
        );
    }

    private TypeValue solveVariable(
        Symbols.Symbol symbol, Symbols.Symbol.Variable data
    ) throws ErrorException {
        for(Scope scope: this.scopeStack) {
            if(scope.symbol != symbol) { continue; }
            throw new ErrorException(new Error(
                "Self-referencing global variable",
                Error.Marking.error(
                    data.valueNode().get().source,
                    "references itself"
                )
            ));
        }
        if(symbol.variantCount() > 0) {
            System.out.println(this.scopeStack);
            return symbol.<Symbols.Symbol.Variable>getVariant(0)
                .valueType().get();
        }
        ConstraintGenerator.VarOutput cOutput = this.cGen
            .generateVar(symbol, data);
        this.scopeStack.add(new Scope(
            symbol, 0,
            cOutput.ctx(),
            Optional.empty(), cOutput.value(),
            cOutput.varUsages(), cOutput.procUsages()
        ));
        this.solveConstraints(cOutput.ctx().constraints);
        Optional<AstNode> processedNode = Optional.empty();
        TypeValue value;
        if(data.valueNode().isPresent()) {
            processedNode = Optional.of(
                this.processNode(data.valueNode().get())
            );
            value = this.asTypeValue(cOutput.value());
        } else {
            value = data.valueType().get();
        }
        this.scopeStack.remove(this.scopeStack.size() - 1);
        symbol.addVariant(new Symbols.Symbol.Variable(
            Optional.of(value),
            processedNode,
            Optional.empty()
        ));
        return value;
    }

    private void solveConstraints(
        List<TypeConstraint> constraints
    ) throws ErrorException {
        for(TypeConstraint c: constraints) {
            this.solveConstraint(c);
        }
    }

    private void solveConstraint(
        TypeConstraint c
    ) throws ErrorException {
        DataType<TypeVariable> t = this.scope().ctx
            .substitutes.get(c.target.id);
        switch(c.type) {
            case IS_NUMERIC: {
                DataType<TypeVariable> r = t;
                if(r.type.isOneOf(DataType.Type.ANY)) {
                    r = new DataType<>(
                        DataType.Type.NUMERIC, null, Optional.of(c.source)
                    );
                }
                if(!r.type.isOneOf(
                    DataType.Type.NUMERIC,
                    DataType.Type.INTEGER, DataType.Type.FLOAT
                )) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, "a number"
                        )
                    );
                }
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case IS_TYPE: {
                TypeConstraint.IsType data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    DataType.Type chosenT = data.oneOfTypes().get(0);
                    DataTypeValue<TypeVariable> value;
                    switch(chosenT) {
                        case ANY: case NUMERIC: case UNIT:
                        case BOOLEAN: case INTEGER: case FLOAT:
                        case STRING: {
                            value = null;
                        } break;
                        case ARRAY: {
                            value = new DataType.Array<>(
                                this.scope().ctx.makeVar()
                            );
                        } break;
                        case UNORDERED_OBJECT: {
                            value = new DataType.UnorderedObject<>(
                                new HashMap<>(), true
                            );
                        } break;
                        case ORDERED_OBJECT: {
                            value = new DataType.OrderedObject<>(
                                List.of(), List.of()
                            );
                        } break;
                        case CLOSURE: {
                            throw new RuntimeException(
                                "PLEASE USE 'HAS_SIGNATURE' INSTEAD DUMBASS"
                            );
                        }
                        case UNION: {
                            value = new DataType.Union<>(
                                new HashMap<>(), true
                            );
                        } break;
                        default: {
                            throw new RuntimeException("unhandled type!");
                        }
                    }
                    r = new DataType<>(
                        chosenT, value, Optional.of(c.source)
                    );
                }
                if(r.type == DataType.Type.NUMERIC) {
                    if(data.oneOfTypes().contains(DataType.Type.INTEGER)) {
                        r = new DataType<>(
                            DataType.Type.INTEGER, null, Optional.of(c.source)
                        );
                    } else if(data.oneOfTypes().contains(DataType.Type.FLOAT)) {
                        r = new DataType<>(
                            DataType.Type.FLOAT, null, Optional.of(c.source)
                        );
                    }
                }
                if(!r.type.isOneOf(data.oneOfTypes())) {
                    StringBuilder options = new StringBuilder();
                    for(int ti = 0; ti < data.oneOfTypes().size(); ti += 1) {
                        if(ti > 0) {
                            if(ti == data.oneOfTypes().size() - 1) {
                                options.append(" or ");
                            } else {
                                options.append(", ");
                            }        
                        }
                        options.append(data.oneOfTypes().get(ti));
                    }
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(),
                            r.type.toString(),
                            c.source,
                            options
                                + data.reason().map(rs -> " " + rs).orElse("")
                        )
                    );
                }
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case HAS_ELEMENT: {
                TypeConstraint.HasElement data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    r = new DataType<>(
                        DataType.Type.ARRAY, 
                        new DataType.Array<>(data.type()), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                if(r.type != DataType.Type.ARRAY) {
                    throw new ErrorException(ConstraintSolver.makeInvalidTypeError(
                        r.source.get(), r.type.toString(),
                        c.source, "an array"
                    ));
                }
                DataType.Array<TypeVariable> arrayData = r.getValue();
                this.unifyVars(arrayData.elementType(), data.type(), c.source);
            } break;
            case HAS_MEMBER: {
                TypeConstraint.HasMember data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    Map<String, TypeVariable> members = new HashMap<>();
                    members.put(data.name(), data.type());
                    r = new DataType<>(
                        DataType.Type.UNORDERED_OBJECT, 
                        new DataType.UnorderedObject<>(members, true), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                if(r.type != DataType.Type.UNORDERED_OBJECT) {
                    throw new ErrorException(ConstraintSolver.makeInvalidTypeError(
                        r.source.get(), r.type.toString(),
                        c.source, "an object"
                    ));
                }
                DataType.UnorderedObject<TypeVariable> objectData
                    = r.getValue();
                if(!objectData.memberTypes().containsKey(data.name())) {
                    if(!objectData.expandable()) {
                        throw new ErrorException(
                            ConstraintSolver.makeInvalidTypeError(
                                r.source.get(), 
                                "an object without a property '"
                                    + data.name() + "'",
                                c.source,
                                "an object with the mentioned property"
                            )
                        );
                    }
                    objectData.memberTypes().put(data.name(), data.type());
                }
                this.unifyVars(
                    objectData.memberTypes().get(data.name()), data.type(), 
                    c.source
                );
            } break;
            case LIMIT_MEMBERS: {
                // Because of how the constraint generator uses
                // this constraint, there is no need for some
                // of the following logic.
                TypeConstraint.LimitMembers data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    Map<String, TypeVariable> members = new HashMap<>();
                    for(String member: data.names()) {
                        members.put(member, this.scope().ctx.makeVar());
                    }
                    r = new DataType<>(
                        DataType.Type.UNORDERED_OBJECT, 
                        new DataType.UnorderedObject<>(members, false), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                // if(r.type != DataType.Type.UNORDERED_OBJECT) {
                //     throw new ErrorException(TypeSolver.makeInvalidTypeError(
                //         r.source.get(), r.type.toString(),
                //         c.source, "an object"
                //     ));
                // }
                DataType.UnorderedObject<TypeVariable> objectData
                    = r.getValue();
                // for(String name: objectData.memberTypes().keySet()) {
                //     if(!data.names().contains(name)) {
                //         // todo: produce an error here
                //         throw new RuntimeException("todo");
                //     }
                // }
                r = new DataType<>(
                    r.type,
                    new DataType.UnorderedObject<>(
                        objectData.memberTypes(), false
                    ),
                    r.source
                );
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case HAS_SIGNATURE: {
                TypeConstraint.HasSignature data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    r = new DataType<>(
                        DataType.Type.CLOSURE, 
                        new DataType.Closure<>(
                            data.arguments(), data.returned()
                        ), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                if(r.type != DataType.Type.CLOSURE) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, "a closure"
                        )
                    );
                }
                DataType.Closure<TypeVariable> closureData = r.getValue();
                int targetArgC = closureData.argumentTypes().size();
                int cArgC = data.arguments().size();
                if(targetArgC != cArgC) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(),
                            "a closure with " + targetArgC + " argument"
                                + (targetArgC == 1? "" : "s"),
                            c.source,
                            "a closure with " + cArgC + " argument"
                                + (cArgC == 1? "" : "s")
                        )
                    );
                }
                for(int argI = 0; argI < cArgC; argI += 1) {
                    this.unifyVars(
                        closureData.argumentTypes().get(argI),
                        data.arguments().get(argI),
                        c.source
                    );
                }
                this.unifyVars(
                    closureData.returnType(), data.returned(), c.source
                );
            } break;
            case HAS_VARIANT: {
                TypeConstraint.HasVariant data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    Map<String, TypeVariable> variants = new HashMap<>();
                    variants.put(data.name(), data.type());
                    r = new DataType<>(
                        DataType.Type.UNION, 
                        new DataType.Union<>(variants, true), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                if(r.type != DataType.Type.UNION) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, "a union variant"
                        )
                    );
                }
                DataType.Union<TypeVariable> unionData = r.getValue();
                if(!unionData.variantTypes().containsKey(data.name())) {
                    if(!unionData.expandable()) {
                        throw new ErrorException(
                            ConstraintSolver.makeInvalidTypeError(
                                r.source.get(), 
                                "a union without a variant '"
                                    + data.name() + "'",
                                c.source,
                                "a union with the mentioned variant"
                            )
                        );
                    }
                    unionData.variantTypes().put(data.name(), data.type());
                }
                this.unifyVars(
                    unionData.variantTypes().get(data.name()), data.type(), 
                    c.source
                );
            } break;
            case LIMIT_VARIANTS: {
                TypeConstraint.LimitVariants data = c.getValue();
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    Map<String, TypeVariable> variants = new HashMap<>();
                    for(String variant: data.names()) {
                        variants.put(variant, this.scope().ctx.makeVar());
                    }
                    r = new DataType<>(
                        DataType.Type.UNION, 
                        new DataType.Union<>(variants, false), 
                        Optional.of(c.source)
                    );
                    this.scope().ctx.substitutes.set(c.target.id, r);
                }
                if(r.type != DataType.Type.UNION) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, "a union"
                        )
                    );
                }
                DataType.Union<TypeVariable> unionData = r.getValue();
                for(String name: unionData.variantTypes().keySet()) {
                    if(!data.names().contains(name)) {
                        throw new ErrorException(
                            ConstraintSolver.makeInvalidTypeError(
                                r.source.get(), 
                                "a union with a variant '" + name + "'",
                                c.source,
                                "a union without the mentioned variant"
                            )
                        );
                    }
                }
                r = new DataType<>(
                    r.type,
                    new DataType.Union<>(
                        unionData.variantTypes(), false
                    ),
                    r.source
                );
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case UNIFY: {
                TypeConstraint.Unify data = c.getValue();
                this.unifyVars(c.target, data.with(), c.source);
            } break;
        }
    }

    private static record Unification(TypeVariable a, TypeVariable b) {}

    private TypeVariable unifyVars(
        TypeVariable varA, TypeVariable varB, Source src
    ) throws ErrorException {
        return this.unifyVars(varA, varB, src, "", new LinkedList<>());
    }
    private TypeVariable unifyVars(
        TypeVariable varA, TypeVariable varB, Source src,
        String pathDescription, List<Unification> encountered
    ) throws ErrorException {
        UnionFind<DataType<TypeVariable>> types = this.scope().ctx.substitutes;
        int rootA = types.find(varA.id);
        int rootB = types.find(varB.id);
        if(rootA == rootB) {
            return varA;   
        }
        Unification encounter = new Unification(varA, varB);
        for(Unification u: encountered) {
            if(types.find(u.a.id) == rootA && types.find(u.b.id) == rootB) {
                return varA;
            }
        }
        encountered.add(encounter);
        DataType<TypeVariable> union = this.unifyTypes(
            types.get(varA.id), types.get(varB.id), src, 
            pathDescription, encountered
        );
        types.union(varA.id, varB.id);
        types.set(rootA, union);
        encountered.remove(encountered.size() - 1);
        return varA;
    }

    private DataType<TypeVariable> unifyTypes(
        DataType<TypeVariable> a, DataType<TypeVariable> b, Source src,
        String pathDescription, List<Unification> encountered
    ) throws ErrorException {
        String pathD = (pathDescription.length() > 0? " of " : "")
            + pathDescription;
        if(a.type == DataType.Type.ANY) {
            return b;
        }
        if(b.type == DataType.Type.ANY) {
            return a;
        }
        if(a.type == DataType.Type.NUMERIC && b.type.isOneOf(
            DataType.Type.FLOAT, DataType.Type.INTEGER
        )) {
            return b;
        }
        if(b.type == DataType.Type.NUMERIC && a.type.isOneOf(
            DataType.Type.FLOAT, DataType.Type.INTEGER
        )) {
            return a;
        }
        if(a.type != b.type) {
            throw new ErrorException(
                ConstraintSolver.makeIncompatibleTypesError(
                    a.source.get(), a.type.toString(), 
                    b.source.get(), b.type.toString(), 
                    src, pathDescription
                )
            );
        }
        switch(a.type) {
            case NUMERIC:
            case UNIT:
            case BOOLEAN:
            case INTEGER:
            case FLOAT:
            case STRING: {
                return a;
            }
            case ARRAY: {
                DataType.Array<TypeVariable> dataA = a.getValue();
                DataType.Array<TypeVariable> dataB = b.getValue();
                return new DataType<>(
                    a.type,
                    new DataType.Array<>(this.unifyVars(
                        dataA.elementType(), dataB.elementType(), 
                        src,
                        "the array element types" + pathD,
                        encountered
                    )),
                    a.source
                );
            }
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject<TypeVariable> dataA = a.getValue();
                DataType.UnorderedObject<TypeVariable> dataB = b.getValue();
                Set<String> memberNames = new HashSet<>();
                memberNames.addAll(dataA.memberTypes().keySet());
                memberNames.addAll(dataB.memberTypes().keySet());
                Map<String, TypeVariable> members = new HashMap<>();
                for(String member: memberNames) {
                    boolean inA = dataA.memberTypes().containsKey(member);
                    boolean inB = dataB.memberTypes().containsKey(member);
                    boolean invalidExpansion = (!inA && !dataA.expandable())
                        || (!inB && !dataB.expandable());
                    if(invalidExpansion) {
                        throw new ErrorException(
                            ConstraintSolver.makeIncompatibleTypesError(
                                (inA? a : b).source.get(), 
                                "an object with a property"
                                    + " '" + member + "'", 
                                (inA? b : a).source.get(), 
                                "an object without that property",
                                src, pathDescription
                            )
                        );
                    }
                    if(!inA) {
                        members.put(member, dataB.memberTypes().get(member));
                    } else if(!inB) {
                        members.put(member, dataA.memberTypes().get(member));
                    } else {
                        members.put(member, this.unifyVars(
                            dataA.memberTypes().get(member), 
                            dataB.memberTypes().get(member), 
                            src, 
                            "the object properties '" + member + "'" + pathD,
                            encountered
                        ));
                    }
                }
                return new DataType<>(
                    a.type,
                    new DataType.UnorderedObject<>(
                        members, dataA.expandable() && dataB.expandable()
                    ),
                    a.source
                );
            }
            case CLOSURE: {
                DataType.Closure<TypeVariable> dataA = a.getValue();
                DataType.Closure<TypeVariable> dataB = b.getValue();
                int aArgC = dataA.argumentTypes().size();
                int bArgC = dataB.argumentTypes().size();
                if(aArgC != bArgC) {
                    throw new ErrorException(
                        ConstraintSolver.makeIncompatibleTypesError(
                            a.source.get(), 
                            "a closure with " + aArgC + "argument"
                                + (aArgC == 1? "" : "s"), 
                            b.source.get(), 
                            "a closure with " + bArgC + "argument"
                                + (bArgC == 1? "" : "s"), 
                            src, pathDescription
                        )
                    );
                }
                List<TypeVariable> arguments = new ArrayList<>(aArgC);
                for(int argI = 0; argI < aArgC; argI += 1) {
                    arguments.add(this.unifyVars(
                        dataA.argumentTypes().get(argI), 
                        dataB.argumentTypes().get(argI),
                        src,
                        "the " + ConstraintSolver.displayOrdinal(argI)
                            + " closure arguments" + pathD,
                        encountered
                    ));
                }
                return new DataType<>(
                    a.type,
                    new DataType.Closure<>(
                        arguments, 
                        this.unifyVars(
                            dataA.returnType(), 
                            dataB.returnType(), 
                            src,
                            "the closure return value" + pathD,
                            encountered
                        )
                    ),
                    a.source
                );
            }
            case UNION: {
                DataType.Union<TypeVariable> dataA = a.getValue();
                DataType.Union<TypeVariable> dataB = b.getValue();
                Set<String> variantNames = new HashSet<>();
                variantNames.addAll(dataA.variantTypes().keySet());
                variantNames.addAll(dataB.variantTypes().keySet());
                Map<String, TypeVariable> variants = new HashMap<>();
                for(String variant: variantNames) {
                    boolean inA = dataA.variantTypes().containsKey(variant);
                    boolean inB = dataB.variantTypes().containsKey(variant);
                    boolean invalidExpansion = (!inA && !dataA.expandable())
                        || (!inB && !dataB.expandable());
                    if(invalidExpansion) {
                        throw new ErrorException(
                            ConstraintSolver.makeIncompatibleTypesError(
                                (inA? a : b).source.get(), 
                                "a union with a variant"
                                    + " '" + variant + "'", 
                                (inA? b : a).source.get(), 
                                "a union without that variant",
                                src, pathDescription
                            )
                        );
                    }
                    if(!inA) {
                        variants.put(
                            variant, dataB.variantTypes().get(variant)
                        );
                    } else if(!inB) {
                        variants.put(
                            variant, dataA.variantTypes().get(variant)
                        );
                    } else {
                        variants.put(variant, this.unifyVars(
                            dataA.variantTypes().get(variant), 
                            dataB.variantTypes().get(variant), 
                            src, 
                            "the union variants '" + variant + "'" + pathD,
                            encountered
                        ));
                    }
                }
                return new DataType<>(
                    a.type,
                    new DataType.Union<>(
                        variants, dataA.expandable() && dataB.expandable()
                    ),
                    a.source
                );
            }
            case ANY:
            case ORDERED_OBJECT: {
                throw new RuntimeException("should not be encountered!");
            }
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
    }

    private TypeValue asTypeValue(TypeVariable tvar) {
        return this.asTypeValue(tvar, new HashMap<>());
    }
    private TypeValue asTypeValue(
        TypeVariable tvar, Map<Integer, TypeValue> done
    ) {
        int tvarr = this.scope().ctx.substitutes.find(tvar.id);
        TypeValue mapped = done.get(tvarr);
        if(mapped != null) {
            return mapped; 
        }
        TypeValue t = new TypeValue(
            DataType.Type.ANY, null, null
        );
        done.put(tvarr, t);
        DataType<TypeValue> tv = this.scope().ctx.substitutes.get(tvarr)
            .map(ctvar -> this.asTypeValue(ctvar, done));
        t.type = tv.type;
        t.setValue(tv.getValue());
        t.source = tv.source;
        return t;
    }

    private TypeVariable asTypeVariable(DataType<TypeValue> tval) {
        return this.asTypeVariable(tval, new HashMap<>());
    }
    private TypeVariable asTypeVariable(
        DataType<TypeValue> tval, 
        Map<DataType<TypeValue>, TypeVariable> done
    ) {
        TypeVariable mapped = done.get(tval);
        if(mapped != null) {
            return mapped;
        }
        TypeVariable r = this.scope().ctx.makeVar();
        done.put(tval, r);
        DataType<TypeVariable> rt = tval.map(
            ctval -> this.asTypeVariable(ctval, done)
        );
        this.scope().ctx.substitutes.set(r.id, rt);
        return r;
    }

    private List<AstNode> processNodes(
        List<AstNode> nodes
    ) throws ErrorException {
        List<AstNode> r = new ArrayList<>(nodes.size());
        for(AstNode node: nodes) {
            r.add(this.processNode(node));
        }
        return r;
    } 

    private static record ProcCall(Namespace path, int variant) {}

    private ProcCall resolveProcCall(
        ProcedureUsage p, List<Source> argSources
    ) throws ErrorException {
        List<Namespace> fullPaths = this.symbols.allowedPathExpansions(
            p.shortPath(), this.scope().symbol, p.node().source
        );
        if(fullPaths.size() == 0) {
            fullPaths.add(p.shortPath());
        }
        List<TypeValue> arguments = p.arguments()
            .stream().map(a -> this.asTypeValue(a)).toList();
        List<Error> errors = new ArrayList<>();
        for(int pathI = fullPaths.size() - 1; pathI >= 0; pathI -= 1) {
            Namespace fullPath = fullPaths.get(pathI);
            Optional<Symbols.Symbol> foundSymbol = this.symbols.get(fullPath);
            if(foundSymbol.isEmpty()) {
                continue; 
            }
            Symbols.Symbol symbol = foundSymbol.get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
                continue;
            }
            Symbols.Symbol.Procedure symbolData = symbol.getValue();
            if(arguments.size() != symbolData.argumentNames().size()) {
                int eArgC = symbolData.argumentNames().size();
                int gArgC = arguments.size();
                errors.add(new Error(
                    "Invalid argument count",
                    Error.Marking.info(
                        symbol.source,
                        "'" + fullPath + "' accepts "
                            + eArgC + " argument"
                            + (eArgC == 1? "" : "s")
                    ),
                    Error.Marking.error(
                        p.node().source,
                        "here " + gArgC + " argument"
                            + (gArgC == 1? " is" : "s are")
                            + " provided"
                    )
                ));
                continue;
            }
            SolvedProcedure solved;
            List<Scope> prevScopeStack = new LinkedList<>(this.scopeStack);
            try {
                solved = this.solveProcedure(
                    symbol, symbolData,
                    Optional.of(arguments),
                    Optional.of(p.node().source), Optional.of(argSources)
                );
            } catch(ErrorException e) {
                this.scopeStack = prevScopeStack;
                errors.add(e.error);
                continue;
            }
            solved.unify(
                p.node().source, p.arguments(), argSources, p.returned(), this
            );
            return new ProcCall(fullPath, solved.variant);
        }
        if(errors.size() == 0) {
            throw new ErrorException(new Error(
                "Access to unknown symbol",
                Error.Marking.error(
                    p.node().source,
                    "'" + p.shortPath() + "' is not a known symbol"
                )
            ));
        }
        if(errors.size() == 1) {
            throw new ErrorException(errors.get(0));
        }
        throw new ErrorException(new Error(
            "No valid candidates for procedure call",
            colored -> {
                String errorNoteColor = colored
                    ? Color.from(Color.GRAY) : "";
                String errorProcedureColor = colored
                    ? Color.from(Color.GREEN, Color.BOLD) : "";
                StringBuilder info = new StringBuilder();
                info.append(errorNoteColor);
                info.append("considered candidates (specify the full path");
                info.append(" to get a specific error)\n");
                for(Namespace candidate: fullPaths) {
                    info.append(errorNoteColor);
                    info.append(" - ");
                    info.append(errorProcedureColor);
                    info.append(candidate);
                    info.append("\n");
                }
                return info.toString();
            },
            Error.Marking.error(
                p.node().source, "path could not be expanded"
            )
        ));
    }

    private AstNode processNode(AstNode node) throws ErrorException {
        TypeValue rType;
        if(node.resultTypeVar.isPresent()) {
            rType = this.asTypeValue(node.resultTypeVar.get());
        } else {
            rType = new TypeValue(
                DataType.Type.UNIT, null, Optional.of(node.source)
            );
        }
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                Map<String, TypeValue> captureTypes = new HashMap<>();
                for(String capture: data.captureTypeVars().keySet()) {
                    captureTypes.put(
                        capture,
                        this.asTypeValue(
                            data.captureTypeVars().get(capture)
                        )
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.Closure(
                        data.argumentNames(),
                        data.argumentTypeVars(),
                        Optional.of(data.argumentTypeVars().stream().map(
                            a -> this.asTypeValue(a)
                        ).toList()),
                        data.returnTypeVar(),
                        Optional.of(this.asTypeValue(data.returnTypeVar().get().get())),
                        data.captureTypeVars(),
                        Optional.of(captureTypes),
                        data.capturedNames(),
                        this.processNodes(data.body())
                    ),
                    node.source, rType
                );
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.Variable(
                        data.isPublic(), data.isMutable(), data.name(),
                        data.valueTypeVar(),
                        Optional.of(this.asTypeValue(
                            data.valueTypeVar().get().get()
                        )),
                        data.value().isPresent()
                            ? Optional.of(this.processNode(data.value().get()))
                            : Optional.empty()
                    ),
                    node.source, rType
                );
            }
            case CASE_BRANCHING: {
                AstNode.CaseBranching data = node.getValue();
                List<List<AstNode>> branchBodies = new ArrayList<>();
                for(List<AstNode> branchBody: data.branchBodies()) {
                    branchBodies.add(this.processNodes(branchBody));
                }
                return new AstNode(
                    node.type,
                    new AstNode.CaseBranching(
                        this.processNode(data.value()),
                        this.processNodes(data.branchValues()),
                        branchBodies, 
                        this.processNodes(data.elseBody())
                    ),
                    node.source, rType
                );
            }
            case CASE_CONDITIONAL: {
                AstNode.CaseConditional data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.CaseConditional(
                        this.processNode(data.condition()),
                        this.processNodes(data.ifBody()),
                        this.processNodes(data.elseBody())
                    ),
                    node.source, rType
                );
            }
            case CASE_VARIANT: {
                AstNode.CaseVariant data = node.getValue();
                List<List<AstNode>> branchBodies = new ArrayList<>();
                for(List<AstNode> branchBody: data.branchBodies()) {
                    branchBodies.add(this.processNodes(branchBody));
                }
                return new AstNode(
                    node.type,
                    new AstNode.CaseVariant(
                        this.processNode(data.value()),
                        data.branchVariants(), data.branchVariableNames(),
                        branchBodies,
                        data.elseBody().isPresent()
                            ? Optional.of(
                                this.processNodes(data.elseBody().get())
                            )
                            : Optional.empty()
                    ),
                    node.source, rType
                );
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                for(
                    ConstraintGenerator.ProcedureUsage procUsage
                        : this.scope().procUsages
                ) {
                    if(procUsage.node() != node) { continue; }
                    ProcCall call = this.resolveProcCall(
                        procUsage, 
                        data.arguments().stream().map(a -> a.source).toList()
                    );
                    return new AstNode(
                        AstNode.Type.PROCEDURE_CALL,
                        new AstNode.ProcedureCall(
                            call.path, call.variant,
                            this.processNodes(data.arguments())
                        ),
                        node.source, rType
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.Call(
                        this.processNode(data.called()),
                        this.processNodes(data.arguments())
                    ),
                    node.source, rType
                );
            }
            case METHOD_CALL: {
                AstNode.MethodCall data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.MethodCall(
                        this.processNode(data.called()), data.memberName(), 
                        this.processNodes(data.arguments())
                    ),
                    node.source, rType
                );
            }
            case OBJECT_LITERAL: {
                AstNode.ObjectLiteral data = node.getValue();
                Map<String, AstNode> values = new HashMap<>();
                for(String member: data.values().keySet()) {
                    values.put(
                        member, this.processNode(data.values().get(member))
                    );
                }
                return new AstNode(
                    node.type, new AstNode.ObjectLiteral(values),
                    node.source, rType
                );
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                return new AstNode(
                    node.type, 
                    new AstNode.ArrayLiteral(this.processNodes(data.values())), 
                    node.source, rType
                );
            }
            case OBJECT_ACCESS: {
                AstNode.ObjectAccess data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.ObjectAccess(
                        this.processNode(data.accessed()),
                        data.memberName()
                    ),
                    node.source, rType
                );
            }
            case BOOLEAN_LITERAL:
            case INTEGER_LITERAL:
            case FLOAT_LITERAL:
            case STRING_LITERAL:
            case UNIT_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, rType
                );
            }
            case ASSIGNMENT:
            case REPEATING_ARRAY_LITERAL:
            case ARRAY_ACCESS:
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
            case NOT_EQUALS:
            case OR:
            case AND: {
                AstNode.BiOp data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.BiOp(
                        this.processNode(data.left()),
                        this.processNode(data.right())
                    ),
                    node.source, rType
                );
            }
            case RETURN: 
            case NEGATE:
            case NOT:
            case STATIC: {
                AstNode.MonoOp data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.MonoOp(
                        this.processNode(data.value())
                    ),
                    node.source, rType
                );
            }
            case MODULE_ACCESS: {
                AstNode.ModuleAccess data = node.getValue();
                for(
                    ConstraintGenerator.ProcedureUsage procUsage
                        : this.scope().procUsages
                ) {
                    if(procUsage.node() != node) { continue; }
                    // TODO! make procedure refs work again
                    throw new RuntimeException("not yet implemented!");
                    /* 
                    Symbols.Symbol symbol = this.symbols
                        .get(procUsage.shortPath()).get();
                    Symbols.Symbol.Procedure symbolData = symbol.getValue();
                    List<TypeValue> arguments = procUsage.arguments()
                        .stream().map(a -> this.asTypeValue(a)).toList();
                    List<Source> argSources = symbolData.argumentNames()
                        .stream().map(a -> node.source).toList();
                    SolvedProcedure solved = this.solveProcedure(
                        symbol, symbolData, 
                        Optional.of(arguments),
                        Optional.of(node.source),
                        Optional.of(argSources)
                    );
                    solved.unify(
                        node.source, procUsage.arguments(), argSources, 
                        procUsage.returned(), this
                    );
                    // 'std::math::pow' -> '|x, n| std::math::pow(x, n)'
                    AstNode closureValue = new AstNode(
                        AstNode.Type.PROCEDURE_CALL,
                        new AstNode.ProcedureCall(
                            procUsage.shortPath(), solved.variant,
                            symbolData.argumentNames().stream().map(a -> 
                                new AstNode(
                                    AstNode.Type.VARIABLE_ACCESS,
                                    new AstNode.VariableAccess(a),
                                    node.source
                                )
                            ).toList()
                        ),
                        node.source
                    );
                    return new AstNode(
                        AstNode.Type.CLOSURE,
                        new AstNode.Closure(
                            symbolData.argumentNames(), new HashSet<>(),
                            List.of(new AstNode(
                                AstNode.Type.RETURN,
                                new AstNode.MonoOp(closureValue),
                                node.source
                            ))    
                        ),
                        node.source, rType
                    );
                    */
                }
                for(
                    ConstraintGenerator.VariableUsage varUsage
                        : this.scope().varUsages()
                ) {
                    if(varUsage.node() != node) { continue; }
                    Symbols.Symbol symbol = this.symbols
                        .get(varUsage.fullPath()).get();
                    Symbols.Symbol.Variable symbolData = symbol.getValue();
                    TypeValue valueType = this
                        .solveVariable(symbol, symbolData);
                    TypeVariable valueTypeVar = this.asTypeVariable(valueType);
                    this.unifyVars(
                        varUsage.value(), valueTypeVar, node.source
                    );
                    return new AstNode(
                        AstNode.Type.MODULE_ACCESS,
                        new AstNode.ModuleAccess(
                            varUsage.fullPath(), Optional.of(0)
                        ),
                        node.source, rType
                    );
                }
                String varName = data.path().elements()
                    .get(data.path().elements().size() - 1);
                return new AstNode(
                    AstNode.Type.VARIABLE_ACCESS,
                    new AstNode.VariableAccess(varName),
                    node.source, rType
                );
            }
            case VARIANT_LITERAL: {
                AstNode.VariantLiteral data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.VariantLiteral(
                        data.variantName(),
                        this.processNode(data.value())
                    ),
                    node.source, rType
                );
            }
            case PROCEDURE:
            case PROCEDURE_CALL:
            case VARIABLE_ACCESS:
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

}

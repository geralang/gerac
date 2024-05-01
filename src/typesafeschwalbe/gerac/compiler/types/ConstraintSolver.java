
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
import typesafeschwalbe.gerac.compiler.Ref;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.UnionFind;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

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
        Optional<List<TypeVariable>> arguments,
        TypeVariable returned,
        List<ConstraintGenerator.VariableUsage> varUsages,
        List<ConstraintGenerator.ProcedureUsage> procUsages
    ) {}

    private Symbols symbols;
    private TypeContext ctx;
    private ConstraintGenerator cGen;
    private List<Scope> scopeStack;

    public ConstraintSolver() {}

    private Scope scope() {
        return this.scopeStack.get(this.scopeStack.size() - 1);
    }

    public List<Error> checkSymbols(Symbols symbols, TypeContext ctx) {
        this.symbols = symbols;
        this.ctx = ctx;
        Set<Error> errors = new HashSet<>();
        for(Namespace path: symbols.allSymbolPaths()) {
            this.cGen = new ConstraintGenerator(symbols, ctx);
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
        return new ArrayList<>(errors);
    }

    private static record SolvedProcedure(int variant, TypeVariable returned) {}
    
    private SolvedProcedure solveProcedure(
        Symbols.Symbol symbol, Symbols.Symbol.Procedure data,
        Optional<List<TypeVariable>> argumentTypes,
        Optional<Source> usageSource,
        Optional<List<Source>> argumentSources
    ) throws ErrorException {
        for(Scope scope: this.scopeStack) {
            if(scope.symbol != symbol) { continue; }
            return new SolvedProcedure(scope.variant, scope.returned);
        }
        int variant = symbol.variantCount();
        Scope scope;
        List<TypeConstraint> constraints;
        if(data.builtinContext().isPresent()) {
            Symbols.BuiltinContext builtin = data.builtinContext().get()
                .apply(usageSource.orElse(symbol.source));
            scope = new Scope(
                symbol, variant,
                Optional.of(builtin.arguments()), builtin.returned(),
                List.of(), List.of()
            );
            constraints = builtin.constraints();
        } else {
            ConstraintGenerator.ProcOutput cOutput = this.cGen
                .generateProc(symbol, data);
            scope = new Scope(
                symbol, variant,
                Optional.of(cOutput.arguments()), cOutput.returned(),
                cOutput.varUsages(), cOutput.procUsages()
            );
            constraints = cOutput.constraints();
        }
        this.scopeStack.add(scope);
        if(argumentTypes.isPresent()) {
            for(int argI = 0; argI < data.argumentNames().size(); argI += 1) {
                TypeVariable argV = argumentTypes.get().get(argI);
                this.unifyVars(
                    argV, scope.arguments().get().get(argI), 
                    argumentSources.get().get(argI)
                );
            }
        }
        this.solveConstraints(constraints);
        Optional<List<AstNode>> processedBody = Optional.empty();
        if(data.body().isPresent()) {
            processedBody = Optional.of(
                this.processNodes(data.body().get())
            );
        }
        this.scopeStack.remove(this.scopeStack.size() - 1);
        symbol.addVariant(new Symbols.Symbol.Procedure(
            data.argumentNames(), data.builtinContext(),
            Optional.of(scope.arguments.get()), Optional.of(scope.returned), 
            processedBody,
            Optional.empty(), Optional.empty()
        ));
        return new SolvedProcedure(variant, scope.returned);
    }

    private TypeVariable solveVariable(
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
            return symbol.<Symbols.Symbol.Variable>getVariant(0)
                .valueType().get();
        }
        ConstraintGenerator.VarOutput cOutput = this.cGen
            .generateVar(symbol, data);
        this.scopeStack.add(new Scope(
            symbol, 0,
            Optional.empty(), cOutput.value(),
            cOutput.varUsages(), cOutput.procUsages()
        ));
        this.solveConstraints(cOutput.constraints());
        Optional<AstNode> processedNode = Optional.empty();
        TypeVariable valueType;
        if(data.valueNode().isPresent()) {
            processedNode = Optional.of(
                this.processNode(data.valueNode().get())
            );
            valueType = cOutput.value();
        } else {
            valueType = data.valueType().get();
        }
        this.scopeStack.remove(this.scopeStack.size() - 1);
        symbol.addVariant(new Symbols.Symbol.Variable(
            Optional.of(valueType),
            processedNode,
            Optional.empty()
        ));
        return valueType;
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
        DataType<TypeVariable> t = this.ctx.get(c.target);
        switch(c.type) {
            case IS_NUMERIC: {
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    r = new DataType<>(
                        DataType.Type.NUMERIC, null, Optional.of(c.source)
                    );
                }
                if(!r.type.isNumeric()) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, DataType.Type.NUMERIC.toString()
                        )
                    );
                }
                this.ctx.set(c.target, r);
            } break;
            case IS_INDEXED: {
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    r = new DataType<>(
                        DataType.Type.INDEXED, null, Optional.of(c.source)
                    );
                }
                if(!r.type.isIndexed()) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, DataType.Type.INDEXED.toString()
                        )
                    );
                }
                this.ctx.set(c.target, r);
            } break;
            case IS_REFERENCED: {
                DataType<TypeVariable> r = t;
                if(r.type == DataType.Type.ANY) {
                    r = new DataType<>(
                        DataType.Type.REFERENCED, null, Optional.of(c.source)
                    );
                }
                if(!r.type.isReferenced()) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(), r.type.toString(),
                            c.source, DataType.Type.REFERENCED.toString()
                        )
                    );
                }
                this.ctx.set(c.target, r);
            } break;
            case IS_TYPE: {
                TypeConstraint.IsType data = c.getValue();
                DataType<TypeVariable> r = t;
                boolean replace = r.type == DataType.Type.ANY
                    || (r.type == DataType.Type.NUMERIC
                        && data.type().isNumeric())
                    || (r.type == DataType.Type.INDEXED
                        && data.type().isIndexed())
                    || (r.type == DataType.Type.REFERENCED
                        && data.type().isReferenced());
                if(replace) {
                    DataType.DataTypeValue<TypeVariable> tval;
                    switch(data.type()) {
                        case ANY:
                        case UNIT:
                        case BOOLEAN:
                        case INTEGER:
                        case FLOAT:
                        case STRING: {
                            tval = null;
                        } break;
                        case UNORDERED_OBJECT: {
                            tval = new DataType.UnorderedObject<>(
                                new HashMap<>(), true
                            );
                        } break;
                        case UNION: {
                            tval = new DataType.Union<>(
                                new HashMap<>(), true
                            );
                        } break;
                        case NUMERIC:
                        case INDEXED:
                        case REFERENCED:
                        case ARRAY:
                        case CLOSURE:
                            throw new RuntimeException(
                                "should not be used with 'IS_TYPE'!"
                            );
                        default:
                            throw new RuntimeException("unhandled type!");
                    }
                    r = new DataType<>(
                        data.type(), tval, Optional.of(c.source)
                    );
                }
                if(r.type != data.type()) {
                    throw new ErrorException(
                        ConstraintSolver.makeInvalidTypeError(
                            r.source.get(),
                            r.type.toString(),
                            c.source,
                            data.type().toString()
                        )
                    );
                }
                this.ctx.set(c.target, r);
            } break;
            case HAS_ELEMENT: {
                TypeConstraint.HasElement data = c.getValue();
                DataType<TypeVariable> r = t;
                boolean replace = r.type == DataType.Type.ANY
                    || r.type == DataType.Type.INDEXED
                    || r.type == DataType.Type.REFERENCED;
                if(replace) {
                    r = new DataType<>(
                        DataType.Type.ARRAY, 
                        new DataType.Array<>(data.type()), 
                        Optional.of(c.source)
                    );
                    this.ctx.set(c.target, r);
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
                boolean replace = r.type == DataType.Type.ANY
                    || r.type == DataType.Type.REFERENCED;
                if(replace) {
                    Map<String, TypeVariable> members = new HashMap<>();
                    members.put(data.name(), data.type());
                    r = new DataType<>(
                        DataType.Type.UNORDERED_OBJECT, 
                        new DataType.UnorderedObject<>(members, true), 
                        Optional.of(c.source)
                    );
                    this.ctx.set(c.target, r);
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
                boolean replace = r.type == DataType.Type.ANY
                    || r.type == DataType.Type.REFERENCED;
                if(replace) {
                    Map<String, TypeVariable> members = new HashMap<>();
                    for(String member: data.names()) {
                        members.put(member, this.ctx.makeVar());
                    }
                    r = new DataType<>(
                        DataType.Type.UNORDERED_OBJECT, 
                        new DataType.UnorderedObject<>(members, false), 
                        Optional.of(c.source)
                    );
                    this.ctx.set(c.target, r);
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
                this.ctx.set(c.target, r);
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
                    this.ctx.set(c.target, r);
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
                    this.ctx.set(c.target, r);
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
                        variants.put(variant, this.ctx.makeVar());
                    }
                    r = new DataType<>(
                        DataType.Type.UNION, 
                        new DataType.Union<>(variants, false), 
                        Optional.of(c.source)
                    );
                    this.ctx.set(c.target, r);
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
                this.ctx.set(c.target, r);
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
        UnionFind<DataType<TypeVariable>> types = this.ctx.substitutes;
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
        if(a.type == DataType.Type.ANY) { return b; }
        if(b.type == DataType.Type.ANY) { return a; }
        if(a.type == DataType.Type.NUMERIC && b.type.isNumeric()) { return b; }
        if(b.type == DataType.Type.NUMERIC && a.type.isNumeric()) { return a; }
        if(a.type == DataType.Type.INDEXED && b.type.isIndexed()) { return b; }
        if(b.type == DataType.Type.INDEXED && a.type.isIndexed()) { return a; }
        if(a.type == DataType.Type.REFERENCED && b.type.isReferenced()) {
            return b;
        }
        if(b.type == DataType.Type.REFERENCED && a.type.isReferenced()) { 
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
            case INDEXED:
            case REFERENCED:
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
                            "the closure return values" + pathD,
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
            case ANY: {
                throw new RuntimeException("should not be encountered!");
            }
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
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
        ConstraintGenerator.ProcedureUsage p, List<Source> argSources
    ) throws ErrorException {
        List<Namespace> fullPaths = this.symbols.allowedPathExpansions(
            p.shortPath(), this.scope().symbol, p.node().source
        );
        if(fullPaths.size() == 0) {
            fullPaths.add(p.shortPath());
        }
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
            if(p.arguments().size() != symbolData.argumentNames().size()) {
                int eArgC = symbolData.argumentNames().size();
                int gArgC = p.arguments().size();
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
            List<TypeVariable> attemptArgs = p.arguments()
                .stream().map(this.ctx::copyVar).toList();
            SolvedProcedure solved;
            List<Scope> prevScopeStack = new LinkedList<>(this.scopeStack);
            try {
                solved = this.solveProcedure(
                    symbol, symbolData,
                    Optional.of(attemptArgs),
                    Optional.of(p.node().source), Optional.of(argSources)
                );
            } catch(ErrorException e) {
                this.scopeStack = prevScopeStack;
                errors.add(e.error);
                continue;
            }
            for(int argI = 0; argI < attemptArgs.size(); argI += 1) {
                this.unifyVars(
                    attemptArgs.get(argI), 
                    p.arguments().get(argI), 
                    p.node().source
                );
            }
            this.unifyVars(solved.returned, p.returned(), p.node().source);
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
        switch(node.type) {
            case CLOSURE: {
                AstNode.Closure data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.Closure(
                        data.argumentNames(),
                        new Ref<>(data.argumentTypes().get()),
                        new Ref<>(data.returnType().get()),
                        new Ref<>(data.captures().get()),
                        this.processNodes(data.body())
                    ),
                    node.source, node.resultType
                );
            }
            case VARIABLE: {
                AstNode.Variable data = node.getValue();
                return new AstNode(
                    node.type,
                    new AstNode.Variable(
                        data.isPublic(), data.isMutable(), data.name(),
                        new Ref<>(data.valueType().get()),
                        data.value().isPresent()
                            ? Optional.of(this.processNode(data.value().get()))
                            : Optional.empty()
                    ),
                    node.source, node.resultType
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
                    node.source, node.resultType
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
                    node.source, node.resultType
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
                    node.source, node.resultType
                );
            }
            case CALL: {
                AstNode.Call data = node.getValue();
                List<AstNode> arguments = this.processNodes(data.arguments());
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
                            arguments
                        ),
                        node.source, node.resultType
                    );
                }
                return new AstNode(
                    node.type,
                    new AstNode.Call(
                        this.processNode(data.called()),
                        arguments
                    ),
                    node.source, node.resultType
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
                    node.source, node.resultType
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
                    node.source, node.resultType
                );
            }
            case ARRAY_LITERAL: {
                AstNode.ArrayLiteral data = node.getValue();
                return new AstNode(
                    node.type, 
                    new AstNode.ArrayLiteral(this.processNodes(data.values())), 
                    node.source, node.resultType
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
                    node.source, node.resultType
                );
            }
            case BOOLEAN_LITERAL:
            case INTEGER_LITERAL:
            case FLOAT_LITERAL:
            case STRING_LITERAL:
            case UNIT_LITERAL: {
                return new AstNode(
                    node.type, node.getValue(), node.source, node.resultType
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
                    node.source, node.resultType
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
                    node.source, node.resultType
                );
            }
            case MODULE_ACCESS: {
                AstNode.ModuleAccess data = node.getValue();
                for(
                    ConstraintGenerator.ProcedureUsage procUsage
                        : this.scope().procUsages
                ) {
                    if(procUsage.node() != node) { continue; }
                    Symbols.Symbol symbol = this.symbols
                        .get(procUsage.shortPath()).get();
                    Symbols.Symbol.Procedure symbolData = symbol.getValue();
                    List<Source> argSources = symbolData.argumentNames()
                        .stream().map(a -> node.source).toList();
                    SolvedProcedure solved = this.solveProcedure(
                        symbol, symbolData, 
                        Optional.of(procUsage.arguments()),
                        Optional.of(node.source),
                        Optional.of(argSources)
                    );
                    this.unifyVars(
                        solved.returned, procUsage.returned(),
                        procUsage.node().source
                    );
                    // 'std::math::pow' -> '|x, n| std::math::pow(x, n)'
                    List<AstNode> argNodes = new ArrayList<>();
                    int argC = procUsage.arguments().size();
                    for(int argI = 0; argI < argC; argI += 1) {
                        argNodes.add(new AstNode(
                            AstNode.Type.VARIABLE_ACCESS,
                            new AstNode.VariableAccess(
                                symbolData.argumentNames().get(argI)
                            ),
                            node.source,
                            procUsage.arguments().get(argI)
                        ));
                    }
                    AstNode closureValue = new AstNode(
                        AstNode.Type.PROCEDURE_CALL,
                        new AstNode.ProcedureCall(
                            procUsage.shortPath(), solved.variant,
                            argNodes
                        ),
                        node.source,
                        solved.returned
                    );
                    return new AstNode(
                        AstNode.Type.CLOSURE,
                        new AstNode.Closure(
                            symbolData.argumentNames(),
                            new Ref<>(Optional.of(procUsage.arguments())),
                            new Ref<>(Optional.of(solved.returned)),
                            new Ref<>(Optional.of(new HashMap<>())),
                            List.of(new AstNode(
                                AstNode.Type.RETURN,
                                new AstNode.MonoOp(closureValue),
                                node.source,
                                this.ctx.makeVar(new DataType<>(
                                    DataType.Type.UNIT, null,
                                    Optional.of(node.source)
                                ))
                            ))
                        ),
                        node.source, node.resultType
                    );
                }
                for(
                    ConstraintGenerator.VariableUsage varUsage
                        : this.scope().varUsages()
                ) {
                    if(varUsage.node() != node) { continue; }
                    Symbols.Symbol symbol = this.symbols
                        .get(varUsage.fullPath()).get();
                    Symbols.Symbol.Variable symbolData = symbol.getValue();
                    TypeVariable valueType = this
                        .solveVariable(symbol, symbolData);
                    this.unifyVars(
                        varUsage.value(), valueType, node.source
                    );
                    return new AstNode(
                        AstNode.Type.MODULE_ACCESS,
                        new AstNode.ModuleAccess(
                            varUsage.fullPath(), Optional.of(0)
                        ),
                        node.source, node.resultType
                    );
                }
                String varName = data.path().elements()
                    .get(data.path().elements().size() - 1);
                return new AstNode(
                    AstNode.Type.VARIABLE_ACCESS,
                    new AstNode.VariableAccess(varName),
                    node.source, node.resultType
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
                    node.source, node.resultType
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

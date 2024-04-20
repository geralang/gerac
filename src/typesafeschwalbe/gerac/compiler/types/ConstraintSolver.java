
package typesafeschwalbe.gerac.compiler.types;

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
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.UnionFind;
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
                "this has been deduced to be " + deducedStr + " here"
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
                "this has been deduced to be " + aStr + " here"
            ),
            Error.Marking.info(
                bSource,
                "this has been deduced to be " + bStr + " here"
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
        TypeContext ctx,
        TypeVariable returned
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
        this.cGen = new ConstraintGenerator(symbols);
        this.scopeStack = new LinkedList<>();
        List<Error> errors = new ArrayList<>();
        for(Namespace path: symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = symbols.get(path).get();
            try {
                switch(symbol.type) {
                    case PROCEDURE: {
                        Symbols.Symbol.Procedure data = symbol.getValue();
                        this.solveProcedure(symbol, data, Optional.empty());
                    } break;
                    case VARIABLE: {
                        // TODO: solve variable
                        if(true) {
                            throw new RuntimeException("not yet implemented!");
                        }
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

    private static record SolvedProcedure(int variant, TypeValue returned) {}

    private SolvedProcedure solveProcedure(
        Symbols.Symbol symbol, Symbols.Symbol.Procedure data,
        Optional<TypeValue> argumentTypes
    ) throws ErrorException {
        // TODO: stop recursive calls (return existing context return type)
        // TODO: stop generation of duplicates
        ConstraintGenerator.Output cOutput = this.cGen
            .generateProc(symbol, data);
        this.scopeStack.add(new Scope(
            symbol,
            cOutput.ctx(),
            cOutput.returned()
        ));
        // TODO: insert argument types if present (get argument type vars
        //       from cOutput, insert into context with 'asTypeVariable'
        //       and unify)
        this.solveConstraints(cOutput.ctx().constraints);
        // TODO: make a copy of the body, doing the already noted
        //       transformations and looking up usages.
        // TODO: pop the scope stack
        // TODO: add the monomorphized body as a new variant to the symbol
        // TODO: convert the return type to a value and return it together
        //       with the variant
        return null;
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
                    throw new ErrorException(ConstraintSolver.makeInvalidTypeError(
                        r.source.get(), r.type.toString(),
                        c.source, "a number"
                    ));
                }
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case IS_TYPE: {
                TypeConstraint.IsType data = c.getValue();
                DataType<TypeVariable> r = t;
                boolean isAny = r.type == DataType.Type.ANY;
                boolean convertNumeric = r.type == DataType.Type.NUMERIC
                    && data.type().isOneOf(
                        DataType.Type.INTEGER, DataType.Type.FLOAT
                    );
                if(isAny || convertNumeric) {
                    r = new DataType<>(
                        data.type(), null, Optional.of(c.source)
                    );
                }
                if(!r.type.isOneOf(data.type())) {
                    throw new ErrorException(ConstraintSolver.makeInvalidTypeError(
                        r.source.get(),
                        r.type.toString(),
                        c.source,
                        data.type()
                            + data.reason().map(rs -> " " + rs).orElse("")
                    ));
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
                // // Because of how the constraint generator uses
                // // this constraint, there is no need for some
                // // of the following logic.
                // TypeConstraint.LimitMembers data = c.getValue();
                DataType<TypeVariable> r = t;
                // if(r.type == DataType.Type.ANY) {
                //     Map<String, TypeVariable> members = new HashMap<>();
                //     for(String member: data.names()) {
                //         members.put(member, this.scope().ctx.makeVar());
                //     }
                //     r = new DataType<>(
                //         DataType.Type.UNORDERED_OBJECT, 
                //         new DataType.UnorderedObject<>(members, false), 
                //         Optional.of(c.source)
                //     );
                //     this.scope().ctx.substitutes.set(c.target.id, r);
                // }
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
            throw new ErrorException(ConstraintSolver.makeIncompatibleTypesError(
                a.source.get(), a.type.toString(), 
                b.source.get(), b.type.toString(), 
                src, pathDescription
            ));
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
                        ConstraintSolver.displayOrdinal(argI)
                            + "the closure arguments " + pathD,
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
                            "the closure return types" + pathD,
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
        DataType<TypeVariable> t = this.scope().ctx.substitutes.get(tvarr);
        TypeValue r = TypeValue.upgrade(t.map(
            (ct, ctvar) -> this.asTypeValue(ctvar, done)
        ));
        done.put(tvarr, r);
        return r;
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
        DataType<TypeVariable> rt = tval.map(
            (ct, ctval) -> this.asTypeVariable(ct, done)
        );
        TypeVariable r = this.scope().ctx.makeVar();
        this.scope().ctx.substitutes.set(r.id, rt);
        done.put(tval, r);
        return r;
    }
}

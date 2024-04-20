
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

public class TypeSolver {

    private static Error makeIncompatibleTypesError(
        Source aSource, String aStr, Source bSource, String bStr, Source src
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
                src, "they need to be compatible here"
            )
        );
    }

    private static record Scope(
        Symbols.Symbol symbol,
        TypeContext ctx,
        TypeVariable returned
    ) {}

    private Symbols symbols;
    private ConstraintGenerator cGen;
    private List<Scope> scopeStack;

    public TypeSolver() {}

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
                    throw new ErrorException(new Error(
                        "Non-numeric type used in a numeric context",
                        Error.Marking.info(
                            r.source.get(),
                            "this has been deduced to be " + r.type + " here"
                        ),
                        Error.Marking.error(
                            c.source,
                            "but needs to have a numeric type here"
                        )
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
                    throw new ErrorException(new Error(
                        "Invalid type",
                        Error.Marking.info(
                            r.source.get(),
                            "this has been deduced to be " + r.type + " here"
                        ),
                        Error.Marking.error(
                            c.source,
                            "but needs to be " + data.type() + " here"
                                + data.reason().map(rs -> " " + rs).orElse("")
                        )
                    ));
                }
                this.scope().ctx.substitutes.set(c.target.id, r);
            } break;
            case HAS_ELEMENT: {
                TypeConstraint.HasElement data = c.getValue();
                // TODO
            } break;
            case HAS_MEMBER: {
                TypeConstraint.HasMember data = c.getValue();
                // TODO
            } break;
            case LIMIT_MEMBERS: {
                TypeConstraint.LimitMembers data = c.getValue();
                // TODO
            } break;
            case HAS_SIGNATURE: {
                TypeConstraint.HasSignature data = c.getValue();
                // TODO
            } break;
            case HAS_VARIANT: {
                TypeConstraint.HasVariant data = c.getValue();
                // TODO
            } break;
            case LIMIT_VARIANTS: {
                TypeConstraint.LimitVariants data = c.getValue();
                // TODO
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
        return this.unifyVars(varA, varB, src, new LinkedList<>());
    }
    private TypeVariable unifyVars(
        TypeVariable varA, TypeVariable varB, Source src,
        List<Unification> encountered
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
            types.get(varA.id), types.get(varB.id), src, encountered
        );
        types.union(varA.id, varB.id);
        types.set(rootA, union);
        encountered.remove(encountered.size() - 1);
        return varA;
    }

    private DataType<TypeVariable> unifyTypes(
        DataType<TypeVariable> a, DataType<TypeVariable> b, Source src,
        List<Unification> encountered
    ) throws ErrorException {
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
            // TODO: use 'TypeSolver.makeIncompatibleTypesError'
            throw new ErrorException(new Error(
                "Incompatible types",
                Error.Marking.info(
                    a.source.get(),
                    "this has been deduced to be " + a.type + " here"
                ),
                Error.Marking.info(
                    b.source.get(),
                    "this has been deduced to be " + b.type + " here"
                ),
                Error.Marking.error(
                    src, "they need to be compatible here"
                )
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
                        src, encountered
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
                        // TODO: use 'TypeSolver.makeIncompatibleTypesError'
                        // throw new ErrorException();
                        throw new RuntimeException("not yet implemented!");
                    }
                }
                throw new RuntimeException("not yet implemented!");
            }
            case CLOSURE: {
                DataType.Closure<TypeVariable> dataA = a.getValue();
                DataType.Closure<TypeVariable> dataB = b.getValue();
                // TODO
                throw new RuntimeException("not yet implemented!");
            }
            case UNION: {
                DataType.Union<TypeVariable> dataA = a.getValue();
                DataType.Union<TypeVariable> dataB = b.getValue();
                // TODO
                throw new RuntimeException("not yet implemented!");
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

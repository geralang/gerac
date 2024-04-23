
package typesafeschwalbe.gerac.compiler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.DataType;
import typesafeschwalbe.gerac.compiler.types.TypeConstraint;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

// import typesafeschwalbe.gerac.compiler.types.TypeValue;
// import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class BuiltIns {

    private BuiltIns() {}

    public static final String BUILTIN_FILE_NAME = "<built-in>";
    public static final String BUILTIN_CORE_FILE_NAME = "<built-in>/core.gera";

    public static void addParsedFiles(Map<String, String> files) {
        files.put(BUILTIN_CORE_FILE_NAME, """
            mod core

            pub proc range(start, end) {
                mut var i = start
                return || {
                    case i >= end -> return #end unit
                    i = i + 1
                    return #next i - 1
                }
            }

            pub proc range_incl(start, end) {
                mut var i = start
                return || {
                    case i > end -> return #end unit
                    i = i + 1
                    return #next i - 1
                }
            }
            """
        );
    }

    public static void addUnparsedFiles(Map<String, String> files) {
        files.put(BUILTIN_FILE_NAME, "<built-in>");
    }

    public static void addSymbols(Symbols symbols) {
        // TODO: REIMPLEMENT ADDING CORE PROCEDURES
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.ARRAY, DataType.Type.UNORDERED_OBJECT
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.BOOLEAN
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(a, b), r);
            };
            symbols.add(
                new Namespace(List.of("core", "addr_eq")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("a", "b"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.UNION
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.BOOLEAN
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(a, b), r);
            };
            symbols.add(
                new Namespace(List.of("core", "tag_eq")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("a", "b"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable thing = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    thing, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.ARRAY, DataType.Type.STRING
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(thing), r);
            };
            symbols.add(
                new Namespace(List.of("core", "length")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("thing"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable iterR = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.HAS_VARIANT,
                    new TypeConstraint.HasVariant("next", ctx.makeVar())
                ));
                ctx.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.HAS_VARIANT,
                    new TypeConstraint.HasVariant("end", ctx.makeVar())
                ));
                ctx.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.LIMIT_VARIANTS,
                    new TypeConstraint.LimitVariants(Set.of("next", "end"))
                ));
                TypeVariable iter = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    iter, src,
                    TypeConstraint.Type.HAS_SIGNATURE,
                    new TypeConstraint.HasSignature(List.of(), iterR)
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.UNIT
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(iter), r);
            };
            symbols.add(
                new Namespace(List.of("core", "exhaust")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("iter"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable reason = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    reason, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        List.of(DataType.Type.STRING), Optional.empty()
                    )
                ));
                TypeVariable r = ctx.makeVar();
                return new Symbols.BuiltinContext(ctx, List.of(reason), r);
            };
            symbols.add(
                new Namespace(List.of("core", "panic")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("reason"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable thing = ctx.makeVar();
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.STRING
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(thing), r);
            };
            symbols.add(
                new Namespace(List.of("core", "as_str")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("thing"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable thing = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    thing, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER, DataType.Type.FLOAT
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(thing), r);
            };
            symbols.add(
                new Namespace(List.of("core", "as_int")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("thing"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable thing = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    thing, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER, DataType.Type.FLOAT
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.FLOAT
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(thing), r);
            };
            symbols.add(
                new Namespace(List.of("core", "as_flt")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("thing"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable source = ctx.makeVar();
                TypeVariable start = ctx.makeVar();
                TypeVariable end = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    source, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.STRING
                    ), Optional.empty())
                ));
                ctx.add(new TypeConstraint(
                    start, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER
                    ), Optional.empty())
                ));
                ctx.add(new TypeConstraint(
                    end, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.STRING
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(
                    ctx, List.of(source, start, end), r
                );
            };
            symbols.add(
                new Namespace(List.of("core", "substring")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("source", "start", "end"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                ctx.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.STRING
                    ), Optional.empty())
                ));
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.STRING
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(
                    ctx, List.of(a, b), r
                );
            };
            symbols.add(
                new Namespace(List.of("core", "concat")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("a", "b"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                TypeContext ctx = new TypeContext();
                TypeVariable thing = ctx.makeVar();
                TypeVariable r = ctx.makeVar();
                ctx.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(List.of(
                        DataType.Type.INTEGER
                    ), Optional.empty())
                ));
                return new Symbols.BuiltinContext(ctx, List.of(thing), r);
            };
            symbols.add(
                new Namespace(List.of("core", "hash")),
                new Symbols.Symbol(
                    Symbols.Symbol.Type.PROCEDURE, true,
                    new Source(BUILTIN_FILE_NAME, 0, 0),
                    new Namespace[0],
                    new Symbols.Symbol.Procedure(
                        List.of("thing"),
                        Optional.of(builtin),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                    ),
                    Optional.empty()
                )
            );
        }
    }

}

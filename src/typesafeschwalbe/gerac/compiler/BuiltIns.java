
package typesafeschwalbe.gerac.compiler;

import java.util.LinkedList;
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

public class BuiltIns {

    private BuiltIns() {}

    public static final String BUILTIN_FILE_NAME = "<built-in>";
    public static final String BUILTIN_CORE_FILE_NAME = "<built-in>/core.gera";

    public static void addParsedFiles(Map<String, String> files) {
        files.put(BUILTIN_CORE_FILE_NAME, """
            /// A module containing special procedures implemented
            /// by the compiler. 
            /// Every module implicitly does `use core::*`.
            mod core

            /// Returns an iterator over all integers that are greater than or 
            /// equal to `start` less than `end` in ascending order.
            /// The range syntax `s..e` is syntax sugar for a call to this
            /// procedure. 
            pub proc range(start, end) {
                mut i = start
                return || {
                    case i >= end -> return #end unit
                    i = i + 1
                    return #next i - 1
                }
            }

            /// Returns an iterator over all integers that are greater than or 
            /// equal to `start` less than or equal to `end` in ascending order.
            /// The range syntax `s..=e` is syntax sugar for a call to this
            /// procedure. 
            pub proc range_incl(start, end) {
                mut i = start
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

    public static void addSymbols(TypeContext ctx, Symbols symbols) {
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                constraints.add(new TypeConstraint(
                    a, src, TypeConstraint.Type.IS_REFERENCED, null
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.BOOLEAN, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(a, b), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Returns `true` if modifying the object or array"
                            + " behind the reference `a` will also modify"
                            + " the object or array behind the reference `b`"
                            + " (if they refer to the same object or array)."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                constraints.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.UNION, Optional.empty()
                    )
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.BOOLEAN, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(a, b), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Returns `true` if `a` and `b` are the same variant"
                            + " of a shared union type, without regard to the"
                            + " value they hold."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable thing = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    thing, src, TypeConstraint.Type.IS_INDEXED, null
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.INTEGER, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(thing), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Returns the length of the array or string `thing`"
                            + " in elements or unicode code points."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable iterR = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.HAS_VARIANT,
                    new TypeConstraint.HasVariant("next", ctx.makeVar())
                ));
                constraints.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.HAS_VARIANT,
                    new TypeConstraint.HasVariant("end", ctx.makeVar())
                ));
                constraints.add(new TypeConstraint(
                    iterR, src,
                    TypeConstraint.Type.LIMIT_VARIANTS,
                    new TypeConstraint.LimitVariants(Set.of("next", "end"))
                ));
                TypeVariable iter = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    iter, src,
                    TypeConstraint.Type.HAS_SIGNATURE,
                    new TypeConstraint.HasSignature(List.of(), iterR)
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.UNIT, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(iter), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Calls `iter` until it returns the union variant `end`."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable reason = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    reason, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                TypeVariable r = ctx.makeVar();
                return new Symbols.BuiltinContext(constraints, List.of(reason), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Immediately causes the program to abort, logging"
                            + " the message `reason` to"
                            + " the standard error output."
                            + " Panics are only supposed to be used for invalid"
                            + " program states, such as an array index that is"
                            + " out of bounds."
                            + " This is also why a panic cannot be caught."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable thing = ctx.makeVar();
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(thing), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Converts `thing` to its string representation."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable thing = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    thing, src, TypeConstraint.Type.IS_NUMERIC, null
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.INTEGER, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(thing), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Converts `thing` to an integer,"
                            + " rounding floats towards zero."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable thing = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    thing, src, TypeConstraint.Type.IS_NUMERIC, null
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.FLOAT, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(thing), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Converts `thing` to a float."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable source = ctx.makeVar();
                TypeVariable start = ctx.makeVar();
                TypeVariable end = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    source, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                constraints.add(new TypeConstraint(
                    start, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.INTEGER, Optional.empty()
                    )
                ));
                constraints.add(new TypeConstraint(
                    end, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.INTEGER, Optional.empty()
                    )
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(
                    constraints, List.of(source, start, end), r
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Returns a copy of the part of `source`"
                            + " starting at the code point at index `start`"
                            + " up to (not including) the code point at index"
                            + " `end`. `start` and `end` will have the length"
                            + " of `source` in code points added to them"
                            + " if they are negative."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable a = ctx.makeVar();
                TypeVariable b = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.UNIFY, new TypeConstraint.Unify(b)
                ));
                constraints.add(new TypeConstraint(
                    a, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.STRING, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(
                    constraints, List.of(a, b), r
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Returns a new string, being the result of appending"
                            + " `b` to the end of `a`."
                    )
                )
            );
        }
        {
            Function<Source, Symbols.BuiltinContext> builtin = src -> {
                List<TypeConstraint> constraints = new LinkedList<>();
                TypeVariable thing = ctx.makeVar();
                TypeVariable r = ctx.makeVar();
                constraints.add(new TypeConstraint(
                    r, src,
                    TypeConstraint.Type.IS_TYPE,
                    new TypeConstraint.IsType(
                        DataType.Type.INTEGER, Optional.empty()
                    )
                ));
                return new Symbols.BuiltinContext(constraints, List.of(thing), r);
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
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()
                    ),
                    Optional.empty(),
                    Optional.of(
                        "Creates a hash of `thing`. Note that objects, arrays"
                            + " and closures will have their references hashed,"
                            + " not the contents of what the references"
                            + " reference."
                    )
                )
            );
        }
    }

}

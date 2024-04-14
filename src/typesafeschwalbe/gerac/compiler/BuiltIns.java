
package typesafeschwalbe.gerac.compiler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

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
        symbols.add(
            new Namespace(List.of("core", "addr_eq")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("a", "b"),
                    Optional.of((args, tc) -> {
                        boolean argsMatch = args.get(0).equals(args.get(1));
                        return argsMatch && args.get(0).isType(
                            DataType.Type.ARRAY, DataType.Type.UNORDERED_OBJECT
                        );
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.BOOLEAN, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "tag_eq")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("a", "b"),
                    Optional.of((args, tc) -> {
                        boolean aIsUnion = args.get(0)
                            .isType(DataType.Type.UNION);
                        boolean bIsUnion = args.get(1)
                            .isType(DataType.Type.UNION);
                        return aIsUnion && bIsUnion;
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.BOOLEAN, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "length")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("thing"),
                    Optional.of((args, tc) -> {
                        return args.get(0).isType(
                            DataType.Type.STRING, DataType.Type.ARRAY
                        );
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.INTEGER, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "exhaust")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("iter"),
                    Optional.of((args, tc) -> {
                        boolean isClosure = args.get(0)
                            .isType(DataType.Type.CLOSURE);
                        if(!isClosure) { return false; }
                        DataType.Closure argData = args.get(0).getValue();
                        for(DataType.ClosureContext cc: argData.bodies()) {
                            int argC = cc.node().<AstNode.Closure>getValue()
                                .argumentNames().size();
                            if(argC != 0) { return false; }
                        }
                        DataType returnType = tc.checkClosure(
                            args.get(0), List.of(), 
                            new Source(BUILTIN_FILE_NAME, 0, 0)    
                        );
                        boolean returnsUnion = returnType
                            .isType(DataType.Type.UNION);
                        if(!returnsUnion) { return false; }
                        DataType.Union returnData = returnType.getValue();
                        for(String variant: returnData.variants().keySet()) {
                            boolean isValid = variant == "next"
                                || variant == "end";
                            if(!isValid) { return false; }
                        }
                        return true;
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.UNIT, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "panic")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("reason"),
                    Optional.of((args, tc) -> {
                        return args.get(0).isType(DataType.Type.STRING);
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.PANIC, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "as_str")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("converted"),
                    Optional.of((args, tc) -> true),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.STRING, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "as_int")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("converted"),
                    Optional.of((args, tc) -> {
                        return args.get(0).isType(
                            DataType.Type.FLOAT, DataType.Type.INTEGER
                        );
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.INTEGER, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "as_flt")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("converted"),
                    Optional.of((args, tc) -> {
                        return args.get(0).isType(
                            DataType.Type.FLOAT, DataType.Type.INTEGER
                        );
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.FLOAT, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "substring")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("source", "start", "end"),
                    Optional.of((args, tc) -> {
                        boolean sourceIsString = args.get(0)
                            .isType(DataType.Type.STRING);
                        boolean startIsInt = args.get(1)
                            .isType(DataType.Type.INTEGER);
                        boolean endIsInt = args.get(2)
                            .isType(DataType.Type.INTEGER);
                        return sourceIsString && startIsInt && endIsInt;
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.STRING, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "concat")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("a", "b"),
                    Optional.of((args, tc) -> {
                        boolean aIsString = args.get(0)
                            .isType(DataType.Type.STRING);
                        boolean bIsString = args.get(1)
                            .isType(DataType.Type.STRING);
                        return aIsString && bIsString;
                    }),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.STRING, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
        symbols.add(
            new Namespace(List.of("core", "hash")),
            new Symbols.Symbol(
                Symbols.Symbol.Type.PROCEDURE, true,
                new Source(BUILTIN_FILE_NAME, 0, 0),
                new Namespace[0],
                new Symbols.Symbol.Procedure(
                    List.of("thing"),
                    Optional.of((args, tc) -> true),
                    Optional.empty(),
                    Optional.of(
                        src -> new DataType(DataType.Type.INTEGER, src)
                    ),
                    Optional.empty(), Optional.empty(), Optional.empty()
                ),
                Optional.empty()
            )
        );
    }

}


package typesafeschwalbe.gerac.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.types.DataType;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

public class ExternalMappingsParser extends Parser {

    private final Symbols symbols;
    private final TypeContext ctx;
    private final Map<String, Supplier<TypeVariable>> declaredTypes;

    public ExternalMappingsParser(
        Lexer lexer, Symbols symbols, TypeContext ctx
    ) throws ErrorException {
        super(lexer);
        this.symbols = symbols;
        this.ctx = ctx;
        this.declaredTypes = new HashMap<>();
    }

    public void parseStatements() throws ErrorException {
        while(this.current.type != Token.Type.FILE_END) {
            this.parseStatement(); 
        }
    }

    private void parseStatement() throws ErrorException {
        Token start = this.current;
        switch(start.content) {
            case "type": {
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String name = this.current.content;
                this.next();
                this.expect(Token.Type.EQUALS);
                this.next();
                Supplier<TypeVariable> type = this.parseType();
                this.declaredTypes.put(name, type);
            } break;
            case "proc": {
                this.next();
                List<String> pathElements = new ArrayList<>();
                this.expect(Token.Type.IDENTIFIER);
                pathElements.add(this.current.content);
                this.next();
                this.expect(Token.Type.DOUBLE_COLON);
                while(this.current.type == Token.Type.DOUBLE_COLON) {
                    this.next();
                    this.expect(Token.Type.IDENTIFIER);
                    pathElements.add(this.current.content);
                    this.next();
                }
                this.expect(Token.Type.PAREN_OPEN);
                this.next();
                List<Supplier<TypeVariable>> argumentTypes = new ArrayList<>();
                List<String> argumentNames = new ArrayList<>();
                while(this.current.type != Token.Type.PAREN_CLOSE) {
                    Supplier<TypeVariable> argumentType = this.parseType();
                    argumentTypes.add(argumentType);
                    argumentNames.add("arg" + argumentNames.size());
                    this.expect(Token.Type.PAREN_CLOSE, Token.Type.COMMA);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                this.next();
                this.expect(Token.Type.ARROW, Token.Type.EQUALS);
                Supplier<TypeVariable> returnType;
                if(this.current.type == Token.Type.ARROW) {
                    this.next();
                    returnType = this.parseType();
                } else {
                    returnType = () -> this.ctx.makeVar(new DataType<>(
                        DataType.Type.UNIT, null,
                        Optional.of(this.current.source)
                    ));
                }
                this.expect(Token.Type.EQUALS);
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String externalName = this.current.content;
                Token end = this.current;
                this.next();
                this.symbols.add(
                    new Namespace(pathElements),
                    new Symbols.Symbol(
                        Symbols.Symbol.Type.PROCEDURE, 
                        true, new Source(start.source, end.source), 
                        new Namespace[0], 
                        new Symbols.Symbol.Procedure(
                            argumentNames,
                            Optional.of(src -> new Symbols.BuiltinContext(
                                List.of(), 
                                argumentTypes.stream()
                                    .map(Supplier::get).toList(), 
                                returnType.get()
                            )),
                            Optional.empty(), Optional.empty(), 
                            Optional.empty(), Optional.empty(), 
                            Optional.empty()
                        ),
                        Optional.of(externalName)
                    )
                );
            } break;
            case "val": {
                this.next();
                List<String> pathElements = new ArrayList<>();
                this.expect(Token.Type.IDENTIFIER);
                pathElements.add(this.current.content);
                this.next();
                this.expect(Token.Type.DOUBLE_COLON);
                while(this.current.type == Token.Type.DOUBLE_COLON) {
                    this.next();
                    this.expect(Token.Type.IDENTIFIER);
                    pathElements.add(this.current.content);
                    this.next();
                }
                TypeVariable valueType = this.parseType().get();
                this.expect(Token.Type.EQUALS);
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String externalName = this.current.content;
                Token end = this.current;
                this.next();
                this.symbols.add(
                    new Namespace(pathElements),
                    new Symbols.Symbol(
                        Symbols.Symbol.Type.VARIABLE,
                        true, new Source(start.source, end.source),
                        new Namespace[0],
                        new Symbols.Symbol.Variable(
                            Optional.of(valueType), Optional.empty(),
                            Optional.empty()
                        ),
                        Optional.of(externalName)
                    )
                );
            } break;
            default: {
                this.throwUnexpected("'type', 'proc' or 'val'");
            }
        }
    }

    private Supplier<TypeVariable> parseType() throws ErrorException {
        Token start = this.current;
        switch(start.type) {
            case PIPE:
            case DOUBLE_PIPE: {
                List<Supplier<TypeVariable>> argTypes = new ArrayList<>();
                List<String> argNames = new ArrayList<>();
                if(this.current.type == Token.Type.PIPE) {
                    this.next();
                    while(this.current.type != Token.Type.PIPE) {
                        Supplier<TypeVariable> argType = this.parseType();
                        argTypes.add(argType);
                        argNames.add("arg" + argNames.size());
                        this.expect(Token.Type.COMMA, Token.Type.PIPE);
                        if(this.current.type == Token.Type.COMMA) {
                            this.next();
                        }
                    }
                }
                this.next();
                this.expect(Token.Type.ARROW);
                this.next();
                Supplier<TypeVariable> returnType = this.parseType();
                return () -> this.ctx.makeVar(new DataType<>(
                    DataType.Type.CLOSURE,
                    new DataType.Closure<>(
                        argTypes.stream().map(Supplier::get).toList(),
                        returnType.get()
                    ),
                    Optional.of(new Source(
                        start.source, 
                        this.ctx.get(returnType.get()).source.get()
                    ))
                ));
            }
            case IDENTIFIER:
            case KEYWORD_UNIT: {
                switch(start.content) {
                    case "unit": {
                        this.next();
                        return () -> this.ctx.makeVar(new DataType<>(
                            DataType.Type.UNIT, null,
                            Optional.of(start.source)
                        ));
                    }
                    case "bool": {
                        this.next();
                        return () -> this.ctx.makeVar(new DataType<>(
                            DataType.Type.BOOLEAN, null,
                            Optional.of(start.source)
                        ));
                    }
                    case "int": {
                        this.next();
                        return () -> this.ctx.makeVar(new DataType<>(
                            DataType.Type.INTEGER, null,
                            Optional.of(start.source)
                        ));
                    }
                    case "float": {
                        this.next();
                        return () -> this.ctx.makeVar(new DataType<>(
                            DataType.Type.FLOAT, null,
                            Optional.of(start.source)
                        ));
                    }
                    case "str": {
                        this.next();
                        return () -> this.ctx.makeVar(new DataType<>(
                            DataType.Type.STRING, null,
                            Optional.of(start.source)
                        ));
                    }
                    default: {
                        this.next();
                        if(this.declaredTypes.containsKey(start.content)) {
                            return this.declaredTypes.get(start.content);
                        }
                        throw new ErrorException(new Error(
                            "Unknown type name",
                            Error.Marking.error(
                                start.source, 
                                "there is no known type with the name '"
                                    + start.content + "'"
                            )
                        ));
                    }
                }
            }
            case BRACE_OPEN: {
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                Map<String, Supplier<TypeVariable>> members = new HashMap<>();
                while(this.current.type != Token.Type.BRACE_CLOSE) {
                    this.expect(Token.Type.IDENTIFIER);
                    String memberName = this.current.content;
                    this.next();
                    this.expect(Token.Type.EQUALS);
                    this.next();
                    Supplier<TypeVariable> memberType = this.parseType();
                    members.put(memberName, memberType);
                    this.expect(Token.Type.COMMA, Token.Type.BRACE_CLOSE);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                Token end = this.current;
                this.next();
                return () -> {
                    Map<String, TypeVariable> vMembers = new HashMap<>();
                    for(String member: members.keySet()) {
                        vMembers.put(member, members.get(member).get());
                    }
                    return this.ctx.makeVar(new DataType<>(
                        DataType.Type.UNORDERED_OBJECT,
                        new DataType.UnorderedObject<>(vMembers, false),
                        Optional.of(new Source(start.source, end.source))
                    ));
                };
            }
            case BRACKET_OPEN: {
                this.next();
                Supplier<TypeVariable> elementType = this.parseType();
                this.expect(Token.Type.BRACKET_CLOSE);
                Token end = this.current;
                this.next();
                return () -> this.ctx.makeVar(new DataType<>(
                    DataType.Type.ARRAY,
                    new DataType.Array<>(elementType.get()),
                    Optional.of(new Source(start.source, end.source))
                ));
            }
            default: {
                this.throwUnexpected("a type");
                return null;
            }
        }
    }

}

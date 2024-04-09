
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;

public class ExternalMappingsParser extends Parser {

    private Symbols symbols;
    private Map<String, DataType> declaredTypes;

    public ExternalMappingsParser(
        Lexer lexer, Symbols symbols
    ) throws ParsingException {
        super(lexer);
        this.symbols = symbols;
        this.declaredTypes = new HashMap<>();
    }

    public void parseStatements() throws ParsingException {
        while(this.current.type != Token.Type.FILE_END) {
            this.parseStatement(); 
        }
    }

    private void parseStatement() throws ParsingException {
        Token start = this.current;
        switch(start.content) {
            case "type": {
                this.next();
                this.expect(Token.Type.IDENTIFIER);
                String name = this.current.content;
                this.next();
                this.expect(Token.Type.EQUALS);
                this.next();
                DataType type = this.parseType();
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
                List<DataType> argumentTypes = new ArrayList<>();
                List<String> argumentNames = new ArrayList<>();
                while(this.current.type != Token.Type.PAREN_CLOSE) {
                    DataType argumentType = this.parseType();
                    argumentTypes.add(argumentType);
                    argumentNames.add("arg" + argumentNames.size());
                    this.expect(Token.Type.PAREN_CLOSE, Token.Type.COMMA);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                this.next();
                this.expect(Token.Type.ARROW, Token.Type.EQUALS);
                DataType returnType;
                if(this.current.type == Token.Type.ARROW) {
                    this.next();
                    returnType = this.parseType();
                } else {
                    returnType = new DataType(
                        DataType.Type.UNIT, this.current.source
                    );
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
                            Optional.empty(),
                            Optional.of(argumentTypes), 
                            Optional.of(src -> new DataType(
                                    returnType.type, returnType.getValue(), src
                            )),
                            Optional.empty()
                        ),
                        Optional.of(externalName)
                    )
                );
            } break;
            case "var": {
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
                DataType valueType = this.parseType();
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
                            Optional.of(valueType), Optional.empty()
                        ),
                        Optional.of(externalName)
                    )
                );
            } break;
            default: {
                this.throwUnexpected("'type', 'proc' or 'var'");
            }
        }
    }

    private DataType parseType() throws ParsingException {
        Token start = this.current;
        switch(start.type) {
            case PIPE:
            case DOUBLE_PIPE: {
                List<DataType> argTypes = new ArrayList<>();
                List<String> argNames = new ArrayList<>();
                if(this.current.type == Token.Type.PIPE) {
                    this.next();
                    while(this.current.type != Token.Type.PIPE) {
                        DataType argType = this.parseType();
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
                DataType returnType = this.parseType();
                return new DataType(
                    DataType.Type.CLOSURE,
                    new DataType.Closure(List.of(new DataType.ClosureContext(
                        new AstNode(
                            AstNode.Type.CLOSURE,
                            new AstNode.Closure(
                                argNames, 
                                Optional.of(argTypes), Optional.of(returnType), 
                                Optional.of(new HashSet<>()), Optional.empty()
                            ),
                            new Source(start.source, returnType.source)
                        ),
                        List.of()
                    ))),
                    new Source(start.source, returnType.source)
                );
            }
            case IDENTIFIER:
            case KEYWORD_UNIT: {
                switch(start.content) {
                    case "unit": {
                        this.next();
                        return new DataType(DataType.Type.UNIT, start.source);
                    }
                    case "bool": {
                        this.next();
                        return new DataType(
                            DataType.Type.BOOLEAN, start.source
                        );
                    }
                    case "int": {
                        this.next();
                        return new DataType(
                            DataType.Type.INTEGER, start.source
                        );
                    }
                    case "float": {
                        this.next();
                        return new DataType(DataType.Type.FLOAT, start.source);
                    }
                    case "str": {
                        this.next();
                        return new DataType(DataType.Type.STRING, start.source);
                    }
                    default: {
                        this.next();
                        if(this.declaredTypes.containsKey(start.content)) {
                            return this.declaredTypes.get(start.content);
                        }
                        throw new ParsingException(new Error(
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
                List<String> memberNames = new ArrayList<>();
                List<DataType> memberTypes = new ArrayList<>();
                while(this.current.type != Token.Type.BRACE_CLOSE) {
                    this.expect(Token.Type.IDENTIFIER);
                    String memberName = this.current.content;
                    memberNames.add(memberName);
                    this.next();
                    this.expect(Token.Type.EQUALS);
                    this.next();
                    DataType memberType = this.parseType();
                    memberTypes.add(memberType);
                    this.expect(Token.Type.COMMA, Token.Type.BRACE_CLOSE);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                Token end = this.current;
                this.next();
                return new DataType(
                    DataType.Type.ORDERED_OBJECT,
                    new DataType.OrderedObject(memberNames, memberTypes),
                    new Source(start.source, end.source)
                );
            }
            case BRACKET_OPEN: {
                this.next();
                DataType elementType = this.parseType();
                this.expect(Token.Type.BRACKET_CLOSE);
                Token end = this.current;
                this.next();
                return new DataType(
                    DataType.Type.ARRAY,
                    new DataType.Array(elementType),
                    new Source(start.source, end.source)
                );
            }
            default: {
                this.throwUnexpected("a type");
                return null;
            }
        }
    }

}

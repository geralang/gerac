
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.types.TypeValue;


public class ExternalMappingsParser extends Parser {

    private Symbols symbols;
    private Map<String, TypeValue> declaredTypes;

    public ExternalMappingsParser(
        Lexer lexer, Symbols symbols
    ) throws ErrorException {
        super(lexer);
        this.symbols = symbols;
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
                TypeValue type = this.parseType();
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
                List<TypeValue> argumentTypes = new ArrayList<>();
                List<String> argumentNames = new ArrayList<>();
                while(this.current.type != Token.Type.PAREN_CLOSE) {
                    TypeValue argumentType = this.parseType();
                    argumentTypes.add(argumentType);
                    argumentNames.add("arg" + argumentNames.size());
                    this.expect(Token.Type.PAREN_CLOSE, Token.Type.COMMA);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                this.next();
                this.expect(Token.Type.ARROW, Token.Type.EQUALS);
                TypeValue returnType;
                if(this.current.type == Token.Type.ARROW) {
                    this.next();
                    returnType = this.parseType();
                } else {
                    returnType = new TypeValue(
                        TypeValue.Type.UNIT, null,
                        Optional.of(this.current.source)
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
                            Optional.of(returnType),
                            Optional.empty(), Optional.empty(), Optional.empty()
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
                TypeValue valueType = this.parseType();
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
                this.throwUnexpected("'type', 'proc' or 'var'");
            }
        }
    }

    private TypeValue parseType() throws ErrorException {
        Token start = this.current;
        switch(start.type) {
            case PIPE:
            case DOUBLE_PIPE: {
                List<TypeValue> argTypes = new ArrayList<>();
                List<String> argNames = new ArrayList<>();
                if(this.current.type == Token.Type.PIPE) {
                    this.next();
                    while(this.current.type != Token.Type.PIPE) {
                        TypeValue argType = this.parseType();
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
                TypeValue returnType = this.parseType();
                return new TypeValue(
                    TypeValue.Type.CLOSURE,
                    new TypeValue.Closure<>(
                        argTypes,
                        returnType
                    ),
                    Optional.of(new Source(
                        start.source, returnType.source.get()
                    ))
                );
            }
            case IDENTIFIER:
            case KEYWORD_UNIT: {
                switch(start.content) {
                    case "unit": {
                        this.next();
                        return new TypeValue(
                            TypeValue.Type.UNIT, null,
                            Optional.of(start.source)
                        );
                    }
                    case "bool": {
                        this.next();
                        return new TypeValue(
                            TypeValue.Type.BOOLEAN, null,
                            Optional.of(start.source)
                        );
                    }
                    case "int": {
                        this.next();
                        return new TypeValue(
                            TypeValue.Type.INTEGER, null,
                            Optional.of(start.source)
                        );
                    }
                    case "float": {
                        this.next();
                        return new TypeValue(
                            TypeValue.Type.FLOAT, null,
                            Optional.of(start.source)
                        );
                    }
                    case "str": {
                        this.next();
                        return new TypeValue(
                            TypeValue.Type.STRING, null,
                            Optional.of(start.source)
                        );
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
                List<String> memberNames = new ArrayList<>();
                List<TypeValue> memberTypes = new ArrayList<>();
                while(this.current.type != Token.Type.BRACE_CLOSE) {
                    this.expect(Token.Type.IDENTIFIER);
                    String memberName = this.current.content;
                    memberNames.add(memberName);
                    this.next();
                    this.expect(Token.Type.EQUALS);
                    this.next();
                    TypeValue memberType = this.parseType();
                    memberTypes.add(memberType);
                    this.expect(Token.Type.COMMA, Token.Type.BRACE_CLOSE);
                    if(this.current.type == Token.Type.COMMA) {
                        this.next();
                    }
                }
                Token end = this.current;
                this.next();
                return new TypeValue(
                    TypeValue.Type.ORDERED_OBJECT,
                    new TypeValue.OrderedObject<>(memberNames, memberTypes),
                    Optional.of(new Source(start.source, end.source))
                );
            }
            case BRACKET_OPEN: {
                this.next();
                TypeValue elementType = this.parseType();
                this.expect(Token.Type.BRACKET_CLOSE);
                Token end = this.current;
                this.next();
                return new TypeValue(
                    TypeValue.Type.ARRAY,
                    new TypeValue.Array<>(elementType),
                    Optional.of(new Source(start.source, end.source))
                );
            }
            default: {
                this.throwUnexpected("a type");
                return null;
            }
        }
    }

}

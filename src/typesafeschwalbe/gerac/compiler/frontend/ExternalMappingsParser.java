
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

    private Map<String, DataType> declaredTypes;

    public ExternalMappingsParser(
        Lexer lexer, Symbols symbols
    ) throws ParsingException {
        super(lexer);
        this.declaredTypes = new HashMap<>();
    }

    public void parseMappings() throws ParsingException {
        Token start = this.current;
        switch(start.content) {
            case "type": {
                // TODO: PARSE TYPE DECLARATION
                throw new RuntimeException("not yet implemented");
            }
            case "proc": {
                // TODO: PARSE EXTERNAL PROCEDURE DECLARATION
                throw new RuntimeException("not yet implemented");
            }
            case "var": {
                // TODO: PARSE EXTERNAL VARIABLE DECLARATION
                throw new RuntimeException("not yet implemented");
            }
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

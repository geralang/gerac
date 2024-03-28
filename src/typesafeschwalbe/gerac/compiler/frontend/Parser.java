package typesafeschwalbe.gerac.compiler.frontend;

import java.util.LinkedList;
import java.util.List;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Source;

public class Parser {

    private final Lexer lexer;
    private Token current;

    public Parser(Lexer lexer) throws ParsingException {
        this.lexer = lexer;
        this.current = lexer.nextFilteredToken();
    }
    
    private void throwUnexpected() throws ParsingException {
        throw new ParsingException(new Error(
            "Unexpected syntax",
            new Error.Marking(
                this.current.source,
                this.current.type == Token.Type.FILE_END
                ? "the file ends here unexpectedly"
                : "'" + this.current.content + "' is unexpected here"
            )
        ));
    }

    private void next() throws ParsingException {
        this.current = lexer.nextFilteredToken();
    }
        
    private void expect(Token.Type... allowedTypes) throws ParsingException {
        if(!List.of(allowedTypes).contains(this.current.type)) {
            this.throwUnexpected();
        }
    }

    public List<AstNode> parseGlobalStatements() throws ParsingException {
        return this.parseStatements(true);
    }

    private List<AstNode> parseStatements(
        boolean inGlobalScope
    ) throws ParsingException {
        LinkedList<AstNode> nodes = new LinkedList<>();
        while(this.current.type != Token.Type.BRACE_CLOSE) {
            nodes.add(this.parseStatement(inGlobalScope));
        }
        return nodes;
    }

    private AstNode parseStatement(
        boolean inGlobalScope
    ) throws ParsingException {
        switch(this.current.type) {
            case KEYWORD_PUBLIC:
            case KEYWORD_MUTABLE:
            case KEYWORD_PROCEDURE:
            case KEYWORD_VARIABLE:
                // TODO: parse procedures and variables
                // TODO: error if public but not in global scope
                // TODO: error if mutable and procedure
                throw new RuntimeException("Not yet implemented");
            case KEYWORD_CASE:
                // TODO: parse branching, conditional and variant case
                throw new RuntimeException("Not yet implemented");
            case KEYWORD_RETURN:
                // TODO: parse return syntax
                throw new RuntimeException("Not yet implemented");
            case KEYWORD_MODULE:
                // TODO: parse module declaration syntax
                throw new RuntimeException("Not yet implemented");
            case KEYWORD_USE:
                // TODO: parse usage syntax
                throw new RuntimeException("Not yet implemented");
            case KEYWORD_TARGET:
                // TODO: parse target blocks
                throw new RuntimeException("Not yet implemented");
            default:                
                AstNode expr = this.parseExpression();
                boolean isAssignment = expr.isAssignable()
                    && this.current.type == Token.Type.EQUALS;
                if(!isAssignment) { return expr; }
                this.next();
                AstNode value = this.parseExpression();
                return new AstNode(
                    AstNode.Type.ASSIGNMENT,
                    new AstNode.BiOp(expr, value),
                    new Source(expr.source, value.source)
                );
        }
    }

    private AstNode parseExpression() throws ParsingException {
        // TODO: parse all kinds of expressions
        throw new RuntimeException("Not yet implemented");
    }

    private Namespace parseNamespace() throws ParsingException {
        // TODO: parse namespace path
        throw new RuntimeException("Not yet implemented");
    }

}

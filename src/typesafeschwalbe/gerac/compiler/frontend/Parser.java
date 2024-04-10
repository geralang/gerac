
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;

public abstract class Parser {

    private final Lexer lexer;
    protected Token current;

    public Parser(Lexer lexer) throws ErrorException {
        this.lexer = lexer;
        this.current = lexer.nextFilteredToken();
    }
    
    protected void throwUnexpected(String expected) throws ErrorException {
        throw new ErrorException(new Error(
            "Unexpected syntax",
            Error.Marking.error(
                this.current.source,
                "expected " + expected + ", but " + (
                    this.current.type == Token.Type.FILE_END
                        ? "reached the end of the file"
                        : "got '" + this.current.content + "' instead"
                )
            )
        ));
    }

    protected void next() throws ErrorException {
        this.current = lexer.nextFilteredToken();
    }
        
    protected void expect(Token.Type... allowedTypes) throws ErrorException {
        if(!List.of(allowedTypes).contains(this.current.type)) {
            StringBuilder expected = new StringBuilder();
            for(int expIdx = 0; expIdx < allowedTypes.length; expIdx += 1) {
                if(expIdx > 0) { expected.append(
                    expIdx < allowedTypes.length - 1? ", " : " or "
                ); }
                expected.append(allowedTypes[expIdx].description);
            }
            this.throwUnexpected(expected.toString());
        }
    }

}
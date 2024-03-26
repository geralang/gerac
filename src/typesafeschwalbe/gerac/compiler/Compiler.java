
package typesafeschwalbe.gerac.compiler;

import java.util.HashMap;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Token;
import typesafeschwalbe.gerac.compiler.frontend.ParsingException;

public class Compiler {

    public static Result<String> compile(
        HashMap<String, String> files, Target target, String main,
        long maxCallDepth
    ) {
        for(String fileName: files.keySet()) {
            String fileContent = files.get(fileName);
            Lexer fileLexer = new Lexer(fileName, fileContent);
            try {
                while(true) {
                    Token currentToken = fileLexer.nextFilteredToken();
                    System.out.println(currentToken);
                    if(currentToken.type == Token.Type.FILE_END) { break; }
                }
            } catch(ParsingException e) {
                return Result.ofError(e.error);
            }
        }
        return Result.ofValue("<no codegen, no ouput>");
    }

}
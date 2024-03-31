
package typesafeschwalbe.gerac.compiler;

import java.util.Map;
import java.util.List;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Parser;
import typesafeschwalbe.gerac.compiler.frontend.ParsingException;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;

public class Compiler {

    public static Result<String> compile(
        Map<String, String> files, Target target, String main,
        long maxCallDepth
    ) {
        for(String fileName: files.keySet()) {
            String fileContent = files.get(fileName);
            Lexer fileLexer = new Lexer(fileName, fileContent);
            try {
                Parser fileParser = new Parser(fileLexer, target);
                List<AstNode> nodes = fileParser.parseGlobalStatements();
            } catch(ParsingException e) {
                return Result.ofError(e.error);
            }
        }
        return Result.ofValue("<no codegen, no output>");
    }

}
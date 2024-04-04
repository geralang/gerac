
package typesafeschwalbe.gerac.compiler;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.Parser;
import typesafeschwalbe.gerac.compiler.frontend.ParsingException;
import typesafeschwalbe.gerac.compiler.frontend.TypeChecker;
import typesafeschwalbe.gerac.compiler.frontend.TypingException;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;

public class Compiler {

    public static Result<String> compile(
        Map<String, String> files, Target target, String mainRaw,
        long maxCallDepth
    ) {
        Symbols symbols = new Symbols();
        for(String fileName: files.keySet()) {
            String fileContent = files.get(fileName);
            Lexer fileLexer = new Lexer(fileName, fileContent);
            List<AstNode> nodes;
            try {
                Parser fileParser = new Parser(fileLexer, target);
                nodes = fileParser.parseGlobalStatements();
            } catch(ParsingException e) {
                return Result.ofError(e.error);
            }
            Optional<Error> symbolAddError = symbols.add(nodes);
            if(symbolAddError.isPresent()) {
                return Result.ofError(symbolAddError.get());
            }
        }
        List<Error> canonicalizationErrors = symbols.canonicalize();
        if(canonicalizationErrors.size() > 0) {
            return Result.ofError(canonicalizationErrors);
        }
        Namespace mainPath = new Namespace(List.of(mainRaw.split("::")));
        Optional<Symbols.Symbol> main = symbols.get(mainPath);
        if(main.isEmpty() || main.get().node().type != AstNode.Type.PROCEDURE) {
            return Result.ofError(new Error(
                "The main procedure '" + mainRaw + "' does not exist"
            ));
        }
        int mainArgC = main.get().node().<AstNode.Procedure>getValue()
            .argumentNames().size();
        if(mainArgC != 0) {
            return Result.ofError(new Error(
                "The main procedure '" + mainRaw + "' has more than 0 arguments"
            ));
        }
        TypeChecker typeChecker = new TypeChecker(symbols);
        try {
            typeChecker.checkProcedureCall(main.get(), List.of());
        } catch(TypingException e) {
            return Result.ofError(e.error);
        }
        return Result.ofValue("");
    }

}
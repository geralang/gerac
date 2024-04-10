
package typesafeschwalbe.gerac.compiler;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.SourceParser;
import typesafeschwalbe.gerac.compiler.frontend.TypeChecker;
import typesafeschwalbe.gerac.compiler.backend.Interpreter;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.ExternalMappingsParser;

public class Compiler {

    public static Result<String> compile(
        Map<String, String> files, Target target, String mainRaw,
        long maxCallDepth
    ) {
        Symbols symbols = new Symbols();
        BuiltIns.addParsedFiles(files);
        BuiltIns.addSymbols(symbols);
        for(String fileName: files.keySet()) {
            String fileContent = files.get(fileName);
            Lexer fileLexer = new Lexer(fileName, fileContent);
            if(fileName.endsWith(".gera")) {
                List<AstNode> nodes;
                try {
                    SourceParser fileParser = new SourceParser(fileLexer, target);
                    nodes = fileParser.parseGlobalStatements();
                } catch(ErrorException e) {
                    BuiltIns.addUnparsedFiles(files);
                    return Result.ofError(e.error);
                }
                Optional<Error> symbolAddError = symbols.addAll(nodes);
                if(symbolAddError.isPresent()) {
                    BuiltIns.addUnparsedFiles(files);
                    return Result.ofError(symbolAddError.get());
                }
            } else if(fileName.endsWith(".gem")) {
                try {
                    ExternalMappingsParser fileParser
                        = new ExternalMappingsParser(fileLexer, symbols);
                    fileParser.parseStatements();
                } catch(ErrorException e) {
                    BuiltIns.addUnparsedFiles(files);
                    return Result.ofError(e.error);
                }
            } else {
                BuiltIns.addUnparsedFiles(files);
                return Result.ofError(new Error(
                    "Unsupported file extension for file '" + fileName + "'"
                ));
            }
        }
        BuiltIns.addUnparsedFiles(files);
        List<Error> canonicalizationErrors = symbols.canonicalize();
        if(canonicalizationErrors.size() > 0) {
            return Result.ofError(canonicalizationErrors);
        }
        Namespace mainPath = new Namespace(List.of(mainRaw.split("::")));
        Optional<Symbols.Symbol> main = symbols.get(mainPath);
        if(main.isEmpty() || main.get().type != Symbols.Symbol.Type.PROCEDURE) {
            return Result.ofError(new Error(
                "The main procedure '" + mainRaw + "' does not exist"
            ));
        }
        int mainArgC = main.get().<Symbols.Symbol.Procedure>getValue()
            .argumentNames().size();
        if(mainArgC != 0) {
            return Result.ofError(new Error(
                "The main procedure '" + mainRaw + "' has more than 0 arguments"
            ));
        }
        TypeChecker typeChecker = new TypeChecker(symbols);
        try {
            typeChecker.checkProcedureCall(
                mainPath, main.get(), List.of(), null
            );
        } catch(ErrorException e) {
            return Result.ofError(e.error);
        }
        // FOR DEBUGGING THE INTERPRETER ///////////////////////////////////////
        Interpreter interpreter = new Interpreter(files, symbols);
        try {
            interpreter.evaluateNode(new AstNode(
                AstNode.Type.CALL,
                new AstNode.Call(
                    new AstNode(
                        AstNode.Type.MODULE_ACCESS,
                        new AstNode.ModuleAccess(mainPath, Optional.of(0)),
                        new Source(BuiltIns.BUILTIN_FILE_NAME, 0, 0)
                    ),
                    List.of()
                ),
                new Source(BuiltIns.BUILTIN_FILE_NAME, 0, 0)
            ));
        } catch(ErrorException e) {
            return Result.ofError(e.error);
        }
        ////////////////////////////////////////////////////////////////////////
        return Result.ofValue("");
    }

}
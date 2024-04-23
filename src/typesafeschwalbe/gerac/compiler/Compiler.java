
package typesafeschwalbe.gerac.compiler;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.SourceParser;
import typesafeschwalbe.gerac.compiler.types.ConstraintSolver;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.ExternalMappingsParser;
// import typesafeschwalbe.gerac.compiler.backend.CodeGen;
// import typesafeschwalbe.gerac.compiler.backend.Lowerer;

public class Compiler {

    public static Map<String, String> DEBUG_FILES = null;

    public static Result<String> compile(
        Map<String, String> files, Target target, String mainRaw,
        long maxCallDepth
    ) {
        // DEBUG
        DEBUG_FILES = files;

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
        ConstraintSolver solver = new ConstraintSolver();
        List<Error> typeErrors = solver.checkSymbols(symbols);
        if(typeErrors.size() > 0) {
            return Result.ofError(typeErrors);
        }
        return Result.ofValue("<todo>");
        // TypeChecker typeChecker = new TypeChecker(symbols);
        // Optional<Error> typeCheckError = typeChecker.checkMain(mainPath);
        // if(typeCheckError.isPresent()) {
        //     return Result.ofError(typeCheckError.get());
        // }
        // Lowerer lowerer = new Lowerer(files, symbols);
        // Optional<Error> loweringError = lowerer.lowerProcedures();
        // if(loweringError.isPresent()) {
        //     return Result.ofError(loweringError.get());
        // }
        // CodeGen codeGen = target.codeGen
        //     .create(files, symbols, lowerer.staticValues, maxCallDepth);
        // String output = codeGen.generate(mainPath);
        // return Result.ofValue(output);
    }

}
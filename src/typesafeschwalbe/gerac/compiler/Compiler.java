
package typesafeschwalbe.gerac.compiler;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.frontend.SourceParser;
import typesafeschwalbe.gerac.compiler.types.ConstraintSolver;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.frontend.AstNode;
import typesafeschwalbe.gerac.compiler.frontend.ExternalMappingsParser;
import typesafeschwalbe.gerac.compiler.backend.CodeGen;
import typesafeschwalbe.gerac.compiler.backend.Lowerer;

public class Compiler {

    public static Result<String> compile(
        Map<String, String> files, Target target, String mainRaw
    ) {
        Symbols symbols = new Symbols();
        TypeContext typeContext = new TypeContext();
        BuiltIns.addParsedFiles(files);
        BuiltIns.addSymbols(typeContext, symbols);
        for(String fileName: files.keySet()) {
            String fileContent = files.get(fileName);
            Lexer fileLexer = new Lexer(fileName, fileContent);
            if(fileName.endsWith(".gera")) {
                List<AstNode> nodes;
                try {
                    SourceParser fileParser
                        = new SourceParser(fileLexer, target);
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
                        = new ExternalMappingsParser(
                            fileLexer, symbols, typeContext
                        );
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
        List<Error> typeErrors = solver.checkSymbols(
            symbols, typeContext, mainPath
        );
        if(typeErrors.size() > 0) {
            return Result.ofError(typeErrors);
        }
        Lowerer lowerer = new Lowerer(files, symbols, typeContext);
        Optional<Error> loweringError = lowerer.lowerProcedures();
        if(loweringError.isPresent()) {
            return Result.ofError(loweringError.get());
        }
        CodeGen codeGen = target.codeGen.create(
            files, symbols, typeContext, lowerer.staticValues
        );
        String output = codeGen.generate(mainPath);
        return Result.ofValue(output);
    }

}
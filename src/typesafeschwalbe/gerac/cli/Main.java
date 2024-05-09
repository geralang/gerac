
package typesafeschwalbe.gerac.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import typesafeschwalbe.gerac.compiler.Compiler;
import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Result;
import typesafeschwalbe.gerac.compiler.Target;
import typesafeschwalbe.gerac.compiler.frontend.Lexer;

public class Main {

    private static final Cli.RequiredArgument MAIN = new Cli.RequiredArgument(
        'm', "main",
        "specifies the path of the main procedure",
        "full procedure path"
    );
    private static final Cli.RequiredArgument TARGET = new Cli.RequiredArgument(
        't', "target", "specifies the target language",
        String.join(
            " / ",
            Arrays.stream(Target.values())
                .map(t -> "'" + t.targetName + "'")
                .toArray(String[]::new)
        )
    );
    private static final Cli.RequiredArgument OUTPUT = new Cli.RequiredArgument(
        'o', "output", "specifies the output file path",
        "output file path"
    );
    private static final Cli.OptionalArgument SYMBOLS = new Cli.OptionalArgument(
        's', "symbols", 
        "specifies the output file path for the symbol info file",
        "output file path"
    );
    private static final Cli.Flag NO_COLOR = new Cli.Flag(
        'c', "nocolor", "disables colored output"
    );

    public static void main(String[] args) {
        // color is always disabled if we think we are on Windows
        boolean onWindows = System.getProperty("os.name")
            .toLowerCase().contains("win");
        // parse CLI arguments
        Cli cli = new Cli()
            .add(MAIN).add(TARGET).add(OUTPUT).add(SYMBOLS)
            .add(NO_COLOR);
        Result<Cli.Values> cliParseResult = cli.parse(args);
        if(cliParseResult.isError()) {
            Main.exitWithErrors(
                cliParseResult.getError(), new HashMap<>(), !onWindows
            );
        }
        Cli.Values cliValues = cliParseResult.getValue();
        boolean colored = !cliValues.get(NO_COLOR) && !onWindows;
        // read all files
        Map<String, String> files = new HashMap<>();
        for(String fileName: cliValues.free()) {
            byte[] fileBytes;
            try {
                fileBytes = Files.readAllBytes(Paths.get(fileName));
            } catch(IOException e) {
                Main.exitWithErrors(
                    List.of(new Error(
                        "Unable to read file '" + fileName + "': "
                            + "'" + e.getMessage() + "'"
                    )),
                    files,
                    colored
                );
                return;
            }
            String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
            files.put(fileName, fileContent);
        }
        // make sure main path is remotely valid
        String main = cliValues.get(MAIN);
        for(String segment: main.split("::")) {
            for(int charIdx = 0; charIdx < segment.length(); charIdx += 1) {
                if(Lexer.isAlphanumeral(segment.charAt(charIdx))) {
                    continue;
                }
                Main.exitWithErrors(
                    List.of(new Error(
                        "'" + main + "' is not a valid main path"
                    )),
                    files,
                    colored
                );
            }
        }
        // get the given target as an enum instance and ensure it's valid
        String strTarget = cliValues.get(TARGET);
        Target target = Target.C;
        boolean foundTarget = false;
        for(Target possibleTarget: Target.values()) {
            if(!possibleTarget.targetName.equals(strTarget)) {
                continue;
            }
            target = possibleTarget;
            foundTarget = true;
            break;
        }
        if(!foundTarget) {
            Main.exitWithErrors(
                List.of(new Error(
                    "'" + strTarget + "' is not a valid target language"
                )),
                files,
                colored
            );
        }
        // compile
        Result<Compiler.Output> compilationResult = Compiler.compile(
            files, target, main, 
            cliValues.get(SYMBOLS).isPresent()
        );
        if(compilationResult.isError()) {
            Main.exitWithErrors(
                compilationResult.getError(), files, colored
            );
        }
        // write symbols to file
        if(compilationResult.getValue().symbolInfo().isPresent()) {
            Main.writeFile(
                compilationResult.getValue().symbolInfo().get(), 
                cliValues.get(SYMBOLS).get(), 
                colored
            );
        }
        // write output to file
        Main.writeFile(
            compilationResult.getValue().code(),
            cliValues.get(OUTPUT), 
            colored
        );
    }

    private static void writeFile(
        String content, String path, boolean errorColored
    ) {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(Paths.get(path), contentBytes);
        } catch(IOException e) {
            Main.exitWithErrors(
                List.of(new Error(
                    "Unable to write to file '" + path + "': "
                        + "'" + e.getMessage() + "'"
                )),
                new HashMap<>(),
                errorColored
            );
        }
    }

    private static void exitWithErrors(
        List<Error> errors, Map<String, String> files, boolean colored
    ) {
        for(Error error: errors) {
            System.err.print(error.render(files, colored));
        }
        System.exit(1);
    }

}

package typesafeschwalbe.gerac.cli;

import java.util.HashMap;
import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Source;

public class Main {
    
    public static void main(String[] args) {
        HashMap<String, String> files = new HashMap<>();
        files.put("test.gera",
            "mod example\n" +
            "\n" +
            "proc add(x, y) {\n" +
            "    return x + y\n" +
            "}"
        );
        Error e = new Error(
            "you fucked up big time",
            new Error.Marking(new Source("test.gera", 13, 17), "right here"),
            new Error.Marking(new Source("test.gera", 41, 46), "but also here")
        );
        System.out.println(e.render(files, true));
    }

}
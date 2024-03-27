
package typesafeschwalbe.gerac.cli;

import java.util.HashMap;
import typesafeschwalbe.gerac.compiler.Compiler;
import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.Result;
import typesafeschwalbe.gerac.compiler.Target;

public class Main {
    
    public static void main(String[] args) {
        HashMap<String, String> files = new HashMap<>();
        files.put("test.gera",
            "mod example\n" +
            "\n" +
            "proc add(x, y) {\n" +
            "    return x + y // this is funny lol\n" +
            "}\n" + 
            "\n" +
            "\"balls\\0\""
        );
        Result<String> r = Compiler.compile(
            files, Target.C, "example::main", 1024
        );
        if(r.isError()) {
            for(Error e: r.getError()) {
                System.out.print(e.render(files, true));
            }
        }
        if(r.isValue()) {
            System.out.println(r.getValue());
        }
    }

}
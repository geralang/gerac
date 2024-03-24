
package typesafeschwalbe.gerac.compiler;

import java.util.HashMap;

public class Compiler {

    public static Result<String> compile(
        HashMap<String, String> files, Target target, String main,
        long maxCallDepth
    ) {
        return Result.ofValue("balls");
    }

}
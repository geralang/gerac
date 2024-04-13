
package typesafeschwalbe.gerac.compiler.backend;

import java.util.Map;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public interface CodeGen {
    
    @FunctionalInterface
    public static interface Constructor {
        CodeGen create(
            Map<String, String> sourceFiles, Symbols symbols, 
            Ir.StaticValues staticValues, long maxCallDepth
        );
    }

    String generate(Namespace mainPath);

}

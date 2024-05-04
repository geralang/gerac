
package typesafeschwalbe.gerac.compiler.backend;

import java.util.Map;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.TypeContext;

public interface CodeGen {
    
    @FunctionalInterface
    public static interface Constructor {
        CodeGen create(
            Map<String, String> sourceFiles, Symbols symbols, 
            TypeContext typeContext, Ir.StaticValues staticValues
        );
    }

    String generate(Namespace mainPath);

}

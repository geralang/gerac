
package typesafeschwalbe.gerac.compiler.backend;

import java.util.Map;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class CCodeGen implements CodeGen {

    public CCodeGen(
        Map<String, String> sourceFiles, Symbols symbols, 
        Ir.StaticValues staticValues, long maxCallDepth
    ) {
        // TODO
        throw new RuntimeException("not yet implemented!");
    }

    @Override
    public String generate(Namespace mainPath) {
        // TODO
        throw new RuntimeException("not yet implemented!");
    }
    
}
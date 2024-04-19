
package typesafeschwalbe.gerac.compiler.types;

import java.util.ArrayList;
import java.util.List;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class TypeSolver {

    private Symbols symbols;

    public TypeSolver() {}

    public List<Error> checkSymbols(Symbols symbols) {
        this.symbols = symbols;
        List<Error> errors = new ArrayList<>();
        for(Namespace path: symbols.allSymbolPaths()) {
            try {
                this.checkSymbol(symbols.get(path).get());
            } catch(ErrorException e) {
                errors.add(e.error);
            }
        }
        return errors;
    }

    private void checkSymbol(Symbols.Symbol symbol) throws ErrorException {
        
    }

}

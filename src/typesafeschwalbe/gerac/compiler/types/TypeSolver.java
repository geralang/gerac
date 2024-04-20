
package typesafeschwalbe.gerac.compiler.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import typesafeschwalbe.gerac.compiler.Error;
import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class TypeSolver {

    private static record Scope(
        TypeContext ctx
    ) {}

    private Symbols symbols;
    private List<Scope> scopeStack;

    public TypeSolver() {}

    private Scope scope() {
        return this.scopeStack.get(this.scopeStack.size() - 1);
    }

    public List<Error> checkSymbols(Symbols symbols) {
        this.symbols = symbols;
        this.scopeStack = new LinkedList<>();
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

    private TypeValue asTypeValue(TypeVariable tvar) {
        return this.asTypeValue(tvar, new HashMap<>());
    }
    private TypeValue asTypeValue(
        TypeVariable tvar, Map<Integer, TypeValue> done
    ) {
        int tvarr = this.scope().ctx.substitutes.find(tvar.id);
        TypeValue mapped = done.get(tvarr);
        if(mapped != null) {
            return mapped; 
        }
        DataType<TypeVariable> t = this.scope().ctx.substitutes.get(tvarr);
        TypeValue r = TypeValue.upgrade(t.map(
            (ct, ctvar) -> this.asTypeValue(ctvar, done)
        ));
        done.put(tvarr, r);
        return r;
    }

    private TypeVariable asTypeVariable(DataType<TypeValue> tval) {
        return this.asTypeVariable(tval, new HashMap<>());
    }
    private TypeVariable asTypeVariable(
        DataType<TypeValue> tval, 
        Map<DataType<TypeValue>, TypeVariable> done
    ) {
        TypeVariable mapped = done.get(tval);
        if(mapped != null) {
            return mapped;
        }
        DataType<TypeVariable> rt = tval.map(
            (ct, ctval) -> this.asTypeVariable(ct, done)
        );
        TypeVariable r = this.scope().ctx.makeVar();
        this.scope().ctx.substitutes.set(r.id, rt);
        done.put(tval, r);
        return r;
    }
}

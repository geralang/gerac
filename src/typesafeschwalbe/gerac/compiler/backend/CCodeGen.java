
package typesafeschwalbe.gerac.compiler.backend;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.TypeContext;

public class CCodeGen implements CodeGen {

    private static final String CORE_LIB = """
        #include <geracoredeps.h>
        #include <gera.h>

        void gera___panic(const char* reason);

        GeraAllocation* gera___alloc(size_t size, GeraFreeHandler fh) {
            if(size == 0) { return NULL; }
            GeraAllocation* a = geracoredeps_malloc(
                sizeof(GeraAllocation) + size
            );
            if(a == NULL) { gera___panic("memory allocation failed"); }
            a->rc_mutex = geracoredeps_create_mutex();
            a->data_mutex = geracoredeps_create_mutex();
            a->rc = 1;
            a->size = size;
            a->fh = fh;
            return a;
        }

        void gera___free(GeraAllocation* a) {
            if(a == NULL) { return; }
            (a->fh)(a->data, a->size);
            geracoredeps_free(a);
        }

        double gera___float_mod(double x, double div) {
            if (div != div || x != x) { return x; }
            if (div == 0) { return (0.0f / 0.0f); }
            return x - (int) (x / div) * div;
        }

        char gera___string_eq(GeraString a, GeraString b) {
            if(a.length_bytes != b.length_bytes) { return 0; }
            for(size_t i = 0; i < a.length_bytes; i += 1) {
                if(a.data[i] != b.data[i]) { return 0; }
            }
            return 1;
        }

        void gera___free_nothing(char* data, size_t size) {}

        // TODO: more functions

        """;

    private final Map<String, String> sourceFiles;
    private final Symbols symbols;
    private final TypeContext typeContext;
    private final Ir.StaticValues staticValues;

    private final List<Ir.Context> contextStack;

    public CCodeGen(
        Map<String, String> sourceFiles, Symbols symbols, 
        TypeContext typeContext, Ir.StaticValues staticValues
    ) {
        this.sourceFiles = sourceFiles;
        this.symbols = symbols;
        this.typeContext = typeContext;
        this.staticValues = staticValues;
        this.contextStack = new LinkedList<>();
    }

    private void enterContext(Ir.Context context) {
        this.contextStack.add(context);
    }

    private void exitContext() {
        this.contextStack.remove(this.contextStack.size() - 1);
    }

    @Override
    public String generate(Namespace mainPath) {
        StringBuilder out = new StringBuilder();
        out.append("\n");
        out.append("""
            //
            // Generated from Gera source code by the Gera compiler.
            // See: https://github.com/geralang
            //
            """);
        out.append("\n");
        out.append(CORE_LIB);
        out.append("\n");
        // this.emitStaticValues(out);
        out.append("\n");
        // this.emitSymbols(out);
        out.append("\n");
        out.append("int main(int argc, char** argv) {\n");
        // out.append("    ");
        // this.emitVariant(mainPath, 0, out);
        // out.append("();\n");
        out.append("    return 0;\n");
        out.append("}\n");
        return out.toString();
    }
    
}
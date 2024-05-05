
package typesafeschwalbe.gerac.compiler.backend;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.Symbols;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;
import typesafeschwalbe.gerac.compiler.types.DataType;
import typesafeschwalbe.gerac.compiler.types.TypeContext;
import typesafeschwalbe.gerac.compiler.types.TypeVariable;

public class CCodeGen implements CodeGen {

    private static final String CORE_LIB = """
        #include <geracoredeps.h>
        #include <gera.h>

        void gera___panic(const char* reason);

        #define GC_USED 1
        #define GC_REACHABLE 2

        typedef struct GeraGC {
            GERACORE_MUTEX alloc_add_mutex;
            GERACORE_MUTEX alloc_resize_mutex;
            size_t alloc_count;
            size_t allocs_buffer_size;
            GeraAllocation* allocs;
            GERACORE_MUTEX free_mutex;
            size_t free_count;
            size_t free_buffer_size;
            GeraAllocation** free_allocs;
        } GeraGC;

        static GeraGC GERA___GC_STATE;

        static void gera___gc_state_init(void) {
            GeraGC* gc = &GERA___GC_STATE;
            gc->alloc_add_mutex = geracoredeps_create_mutex();
            gc->alloc_resize_mutex = geracoredeps_create_mutex();
            gc->alloc_count = 0;
            gc->allocs_buffer_size = 1;
            gc->allocs = geracoredeps_malloc(
                sizeof(GeraAllocation) * gc->allocs_buffer_size
            );
            gc->free_mutex = geracoredeps_create_mutex();
            gc->free_count = 0;
            gc->free_buffer_size = 1;
            gc->free_allocs = geracoredeps_malloc(
                sizeof(GeraAllocation*) * gc->free_buffer_size
            );
        }

        GeraAllocation* gera___alloc(size_t size, GeraAllocHandler mark_h) {
            if(size == 0) { return NULL; }
            GeraGC* gc = &GERA___GC_STATE;
            geracoredeps_lock_mutex(&gc->free_mutex);
            if(gc->free_count > 0) {
                gc->free_count -= 1;
                GeraAllocation* a = gc->free_allocs[gc->free_count];
                geracoredeps_unlock_mutex(&gc->free_mutex);
                geracoredeps_lock_mutex(&a->header_mutex);
                a->rc = 1;
                a->size = size;
                a->fh = NULL;
                a->mh = mark_h;
                a->gc_flags = GC_USED;
                a->data = geracoredeps_malloc(size);
                geracoredeps_unlock_mutex(&a->header_mutex);
                return a;
            }
            geracoredeps_unlock_mutex(&gc->free_mutex);
            geracoredeps_lock_mutex(&gc->alloc_add_mutex);
            geracoredeps_lock_mutex(&gc->alloc_resize_mutex);
            if(gc->alloc_count >= gc->allocs_buffer_size) {
                gc->allocs_buffer_size *= 2;
                gc->allocs = geracoredeps_realloc(
                    gc->allocs, 
                    sizeof(GeraAllocation) * gc->allocs_buffer_size
                );
            }
            GeraAllocation* a = gc->allocs + gc->alloc_count;
            gc->alloc_count += 1;
            geracoredeps_unlock_mutex(&gc->alloc_add_mutex);
            a->header_mutex = geracoredeps_create_mutex();
            a->rc = 1;
            a->size = size;
            a->fh = NULL;
            a->mh = mark_h;
            a->gc_flags = GC_USED;
            a->data_mutex = geracoredeps_create_mutex();
            a->data = geracoredeps_malloc(size);
            geracoredeps_unlock_mutex(&gc->alloc_resize_mutex);
            return a;
        }

        void gera___stack_copied(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->header_mutex);
            a->rc += 1;
            geracoredeps_unlock_mutex(&a->header_mutex);
        }

        void gera___stack_deleted(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->header_mutex);
            a->rc -= 1;
            geracoredeps_unlock_mutex(&a->header_mutex);
        }

        void gera___mark_ref(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->header_mutex);
            if(a->gc_flags & GC_REACHABLE) {
                geracoredeps_unlock_mutex(&a->header_mutex);
                return;
            }
            a->gc_flags |= GC_REACHABLE;
            GeraAllocHandler mh = a->mh;
            geracoredeps_unlock_mutex(&a->header_mutex);
            if(mh != NULL) { (mh)(a->data, a->size); }
        }

        void gera___free(GeraAllocation* a) {
            if(a == NULL) { return; }
            GeraGC* gc = &GERA___GC_STATE;
            geracoredeps_lock_mutex(&a->header_mutex);
            if(a->fh != NULL) { (a->fh)(a->data, a->size); }
            a->gc_flags = 0;
            geracoredeps_free(a->data);
            geracoredeps_unlock_mutex(&a->header_mutex);
            geracoredeps_lock_mutex(&gc->free_mutex);
            if(gc->free_count >= gc->free_buffer_size) {
                gc->free_buffer_size *= 2;
                gc->free_allocs = geracoredeps_realloc(
                    gc->free_allocs, 
                    sizeof(GeraAllocation*) * gc->free_buffer_size
                );
            }
            gc->free_allocs[gc->free_count] = a;
            geracoredeps_unlock_mutex(&gc->free_mutex);
        }

        void gera___gc_cycle() {
            GeraGC* gc = &GERA___GC_STATE;
            geracoredeps_lock_mutex(&gc->alloc_add_mutex);
            size_t alloc_c = gc->alloc_count;
            geracoredeps_unlock_mutex(&gc->alloc_add_mutex);
            geracoredeps_lock_mutex(&gc->alloc_resize_mutex);
            for(size_t alloc_i = 0; alloc_i < alloc_c; alloc_i += 1) {
                GeraAllocation* a = gc->allocs + alloc_i;
                geracoredeps_lock_mutex(&a->header_mutex);
                gbool used = a->gc_flags & GC_USED;
                gbool stack_reachable = a->rc > 0;
                geracoredeps_unlock_mutex(&a->header_mutex);
                if(used && stack_reachable) {
                    gera___mark_ref(a);
                }
            }
            for(size_t alloc_i = 0; alloc_i < alloc_c; alloc_i += 1) {
                GeraAllocation* a = gc->allocs + alloc_i;
                geracoredeps_lock_mutex(&a->header_mutex);
                gbool used = a->gc_flags & GC_USED;
                gbool reachable = a->gc_flags & GC_REACHABLE;
                a->gc_flags &= ~GC_REACHABLE;
                geracoredeps_unlock_mutex(&a->header_mutex);
                if(used && !reachable) {
                    gera___free(a);
                }
            }
            geracoredeps_unlock_mutex(&gc->alloc_resize_mutex);
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

        #if defined(__unix__) || defined(__unix) || (defined(__APPLE__) && defined(__MACH__))
            #define PANIC_REASON_COLOR "\033[0;91;1m"
            #define PANIC_NOTE_COLOR "\033[0;90m"
            #define PANIC_FILE_NAME_COLOR "\033[0;37m"
            #define PANIC_RESET_COLOR "\033[0m"
        #else
            #define PANIC_START_COLOR ""
            #define PANIC_REASON_COLOR ""
            #define PANIC_NOTE_COLOR ""
            #define PANIC_FILE_NAME_COLOR ""
            #define PANIC_RESET_COLOR ""
        #endif

        void gera___panic_pre_at(const char* file, size_t line) {
            size_t line_str_len = geracoredeps_display_uint_length(line);
            char line_str[line_str_len + 1];
            geracoredeps_display_uint(line, line_str);
            line_str[line_str_len] = '\\0';
            geracoredeps_eprint(
                PANIC_NOTE_COLOR \"The program panicked at \"
                PANIC_FILE_NAME_COLOR \"\\"\"
            );
            geracoredeps_eprint(file);
            geracoredeps_eprint(\"\\":\");
            geracoredeps_eprint(line_str);
            geracoredeps_eprint(
                PANIC_NOTE_COLOR \": \"
                PANIC_REASON_COLOR
            );
        }

        void gera___panic_pre() {
            geracoredeps_eprint(
                PANIC_NOTE_COLOR \"The program panicked: \"
                PANIC_REASON_COLOR
            );
        }

        void gera___panic_post() {
            geracoredeps_eprint(\"\\n\" PANIC_RESET_COLOR);
            geracoredeps_exit(1);
        }

        void gera___panic(const char* reason) {
            gera___panic_pre();
            geracoredeps_eprint(reason);
            gera___panic_post();
        }

        size_t gera___verify_index(
            gint index, size_t size, const char* file, size_t line
        ) {
            size_t final_index;
            if(index < 0) { final_index = (size_t) (((gint) size) + index); }
            else { final_index = (size_t) index; }
            if(final_index < size) { return final_index; }
            size_t index_str_len = geracoredeps_display_sint_length(index);
            char index_str[index_str_len + 1];
            geracoredeps_display_sint(index, index_str);
            index_str[index_str_len] = '\\0';
            size_t length_str_len = geracoredeps_display_uint_length(size);
            char length_str[length_str_len + 1];
            geracoredeps_display_uint(size, length_str);
            length_str[length_str_len] = '\\0';
            gera___panic_pre_at(file, line);
            geracoredeps_eprint("the index ");
            geracoredeps_eprint(index_str);
            geracoredeps_eprint(" is out of bounds for an array of length ");
            geracoredeps_eprint(length_str);
            gera___panic_post();
            return -1;
        }

        size_t gera___verify_size(
            gint size, const char* file, size_t line
        ) {
            if(size >= 0) { return (size_t) size; }
            size_t size_str_len = geracoredeps_display_sint_length(size);
            char size_str[size_str_len + 1];
            geracoredeps_display_sint(size, size_str);
            size_str[size_str_len] = '\\0';
            gera___panic_pre_at(file, line);
            geracoredeps_eprint("the value ");
            geracoredeps_eprint(size_str);
            geracoredeps_eprint(" is not a valid array size");
            gera___panic_post();
            return -1;
        }

        gint gera___verify_integer_divisor(
            gint d, const char* file, size_t line
        ) {
            if(d != 0) { return d; }
            gera___panic_pre_at(file, line);
            geracoredeps_eprint("integer division by zero");
            gera___panic_post();
            return -1;
        }

        size_t gera___codepoint_size(char fb) {
            if((fb & 0b10000000) == 0b00000000) { return 1; }
            if((fb & 0b11100000) == 0b11000000) { return 2; }
            if((fb & 0b11110000) == 0b11100000) { return 3; }
            if((fb & 0b11111000) == 0b11110000) { return 4; }
            return 0;
        }

        GeraString gera___alloc_string(const char* data) {
            size_t length = 0;
            size_t length_bytes = 0;
            for(; data[length_bytes] != '\\0'; length += 1) {
                length_bytes += gera___codepoint_size(data[length_bytes]);
            }
            GeraAllocation* allocation = gera___alloc(length_bytes, NULL);
            for(size_t c = 0; c < length_bytes; c += 1) {
                allocation->data[c] = data[c];
            }
            return (GeraString) {
                .allocation = allocation,
                .data = allocation->data,
                .length_bytes = length_bytes,
                .length = length
            };
        }

        GeraString gera___wrap_static_string(const char* data) {
            size_t length = 0;
            size_t length_bytes = 0;
            for(; data[length_bytes] != '\\0'; length += 1) {
                length_bytes += gera___codepoint_size(data[length_bytes]);
            }
            return (GeraString) {
                .allocation = NULL,
                .data = data,
                .length = length,
                .length_bytes = length_bytes
            };
        }

        GeraString gera___substring(GeraString src, gint start, gint end) {
            size_t start_idx = (size_t) start;
            size_t end_idx = (size_t) end;
            size_t start_offset = 0;
            for(size_t i = 0; i < start_idx; i += 1) {
                start_offset += gera___codepoint_size(src.data[start_offset]);
            }
            size_t length_bytes = 0;
            for(size_t c = start_idx; c < end_idx; c += 1) {
                length_bytes += gera___codepoint_size(
                    src.data[start_offset + length_bytes]
                );
            }
            return (GeraString) {
                .allocation = src.allocation,
                .length = end_idx - start_idx,
                .length_bytes = length_bytes,
                .data = src.data + start_offset
            };
        }

        GeraString gera___concat(GeraString a, GeraString b) {
            GeraAllocation* allocation = gera___alloc(
                a.length_bytes + b.length_bytes, NULL
            );
            GeraString result = (GeraString) {
                .allocation = allocation,
                .length = a.length + b.length,
                .length_bytes = a.length_bytes + b.length_bytes,
                .data = allocation->data
            };
            for(size_t i = 0; i < a.length_bytes; i += 1) {
                result.allocation->data[i] = a.data[i];
            }
            for(size_t i = 0; i < b.length_bytes; i += 1) {
                result.allocation->data[a.length_bytes + i] = b.data[i];
            }
            return result;
        }

        gint gera___hash(unsigned char* data, size_t data_len) {
            size_t hash = 0;
            for(size_t i = 0; i < data_len; i += 1) {
                hash = data[i] + (hash << 6) + (hash << 16) - hash;
            }
            return *((gint*) &hash);
        }
        
        GeraArray GERA_ARGS;
        void gera___set_args(int argc, char** argv) {
            GeraAllocation* allocation = gera___alloc(
                sizeof(GeraString) * argc, NULL
            );
            GeraString* data = (GeraString*) allocation->data;
            for(size_t i = 0; i < argc; i += 1) {
                data[i] = gera___wrap_static_string(argv[i]);
            }
            GERA_ARGS = (GeraArray) {
                .allocation = allocation,
                .data = allocation->data,
                .length = argc
            };
        }
        """;


    private final Map<String, String> sourceFiles;
    private final Symbols symbols;
    private final TypeContext typeContext;
    private final Ir.StaticValues staticValues;

    private final List<Ir.Context> contextStack;

    private long nextUnionTagNumber;
    private Map<String, Long> unionVariantTagNumbers;
    private Set<Integer> usedTypes;
    private StringBuilder closureBodies;
    private long closureBodyCount;

    public CCodeGen(
        Map<String, String> sourceFiles, Symbols symbols, 
        TypeContext typeContext, Ir.StaticValues staticValues
    ) {
        this.sourceFiles = sourceFiles;
        this.symbols = symbols;
        this.typeContext = typeContext;
        this.staticValues = staticValues;
        this.contextStack = new LinkedList<>();
        this.collapseDuplicateTypes();
    }


    private void collapseDuplicateTypes() {
        for(int tid = 0; tid < this.typeContext.varVount(); tid += 1) {
            if(!this.typeContext.substitutes.isRoot(tid)) { continue; }
            for(int mid = 0; mid < tid; mid += 1) {
                if(!this.typeContext.substitutes.isRoot(mid)) { continue; }
                if(!this.typeContext.deepEquals(tid, mid)) { continue; }
                this.typeContext.substitutes.union(tid, mid);
            }
        }
    }


    private void enterContext(Ir.Context context) {
        this.contextStack.add(context);
    }


    private Ir.Context context() {
        return this.contextStack.get(this.contextStack.size() - 1);
    }


    private void exitContext() {
        this.contextStack.remove(this.contextStack.size() - 1);
    }


    private long getVariantTagNumber(String variantName) {
        Long existingNumber = this.unionVariantTagNumbers.get(variantName);
        if(existingNumber != null) { return existingNumber; }
        long number = this.nextUnionTagNumber;
        this.unionVariantTagNumbers.put(variantName, number);
        this.nextUnionTagNumber += 1;
        return number;
    }


    @Override
    public String generate(Namespace mainPath) {
        this.nextUnionTagNumber = 0;
        this.unionVariantTagNumbers = new HashMap<>();
        this.usedTypes = new HashSet<>();
        this.closureBodies = new StringBuilder();
        this.closureBodyCount = 0;
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
        StringBuilder typed = new StringBuilder();
        this.emitValueDeclarations(typed);
        this.emitValueInitializer(typed);
        typed.append("\n");
        this.emitSymbols(typed);
        StringBuilder types = new StringBuilder();
        out.append("\n");
        this.emitTypeDeclarations(types, out);
        out.append("\n");
        out.append(types);
        out.append("\n");
        out.append(this.closureBodies);
        out.append("\n");
        out.append(typed);
        out.append("\n");
        out.append("int main(int argc, char** argv) {\n");
        out.append("    gera___gc_state_init();\n");
        out.append("    geracoredeps_start_gc();\n");
        out.append("    gera___set_args(argc, argv);\n");
        out.append("    gera_init_svals();\n");
        out.append("    ");
        this.emitVariant(mainPath, 0, out);
        out.append("();\n");
        out.append("    return 0;\n");
        out.append("}\n");
        return out.toString();
    }


    private void emitValueDeclarations(StringBuilder out) {
        int valC = this.staticValues.values.size();
        for(int valI = 0; valI < valC; valI += 1) {
            Ir.StaticValue val = this.staticValues.values.get(valI);
            if(val instanceof Ir.StaticValue.Unit) { continue; }
            out.append("static ");
            this.emitValueType(val, out);
            out.append(" ");
            this.emitValueRef(val, out);
            out.append(";\n");
        }
    }


    private void emitValueType(Ir.StaticValue v, StringBuilder out) {
        if(v instanceof Ir.StaticValue.Unit) out.append("void");
        else if(v instanceof Ir.StaticValue.Bool) out.append("gbool");
        else if(v instanceof Ir.StaticValue.Int) out.append("gint");
        else if(v instanceof Ir.StaticValue.Float) out.append("gfloat");
        else if(v instanceof Ir.StaticValue.Str) out.append("GeraString");
        else if(v instanceof Ir.StaticValue.Arr) out.append("GeraArray");
        else if(v instanceof Ir.StaticValue.Obj) out.append("GeraObject");
        else if(v instanceof Ir.StaticValue.Closure) out.append("GeraClosure");
        else if(v instanceof Ir.StaticValue.Union) out.append("GeraUnion");
        else throw new RuntimeException("unhandled value type!");
    }


    private void emitValueInitializer(StringBuilder out) {
        out.append("void gera_init_svals(void) {\n");
        // TODO!
        out.append("}\n");
    }


    private void emitValueRef(Ir.StaticValue v, StringBuilder out) {
        out.append("gera_sval_");
        out.append(this.staticValues.getIndexOf(v));
    }


    private void emitTypeDeclarations(StringBuilder out, StringBuilder pre) {
        Set<Integer> emitted = new HashSet<>();
        while(true) {
            int tid = -1;
            for(int utid: this.usedTypes) {
                if(emitted.contains(utid)) { continue; }
                tid = utid;
                break;
            }
            if(tid == -1) { break; }
            emitted.add(tid);
            DataType<TypeVariable> t = this.typeContext.get(tid);
            switch(t.type) {
                case ANY: case NUMERIC: case INDEXED: case REFERENCED:
                case UNIT: case BOOLEAN: case INTEGER: case FLOAT: case STRING: 
                case ARRAY: case UNION: case CLOSURE:
                    break;
                case UNORDERED_OBJECT: {                    
                    DataType.UnorderedObject<TypeVariable> data = t.getValue();
                    out.append("typedef struct ");
                    this.emitObjectLayoutName(tid, out);
                    out.append(" {\n");
                    for(String member: data.memberTypes().keySet()) {
                        out.append("    ");
                        this.emitType(data.memberTypes().get(member), out);
                        out.append(" ");
                        out.append(member);
                        out.append(";\n");
                    }
                    out.append("} ");
                    this.emitObjectLayoutName(tid, out);
                    out.append(";\n");
                } break;
            }
            this.emitTypeFunctions(tid, out, pre);
        }
    }


    private void emitObjectLayoutName(int tid, StringBuilder out) {
        out.append("GeraObjectLayout");
        out.append(this.typeContext.substitutes.find(tid));
    }


    private void emitTypeFunctions(
        int tid, StringBuilder out, StringBuilder pre
    ) {
        DataType<TypeVariable> tval = this.typeContext.get(tid);
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: 
            case REFERENCED: 
            case BOOLEAN: case INTEGER: case FLOAT: case STRING:
                break;
            case ARRAY: case UNORDERED_OBJECT: case UNION: {
                pre.append("gbool gera_");
                pre.append(tid);
                pre.append("_eq(");
                this.emitType(tid, pre);
                pre.append(" a, ");
                this.emitType(tid, pre);
                pre.append(" b);\n");
            } break;
            case CLOSURE: {
                // nothing to do yet
            } break;
        }
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: case REFERENCED: 
            case BOOLEAN: case INTEGER: case FLOAT: case STRING:
                break;
            case ARRAY: {
                DataType.Array<TypeVariable> td = tval.getValue();
                out.append("gbool gera_");
                out.append(tid);
                out.append("_eq(");
                this.emitType(tid, out);
                out.append(" a, ");
                this.emitType(tid, out);
                out.append(" b) {\n");
                out.append("    if(a.length != b.length) { return 0; }\n");
                out.append("    ");
                this.emitType(td.elementType(), out);
                out.append("* dataA = (");
                this.emitType(td.elementType(), out);
                out.append("*) a.data;\n");
                out.append("    ");
                this.emitType(td.elementType(), out);
                out.append("* dataB = (");
                this.emitType(td.elementType(), out);
                out.append("*) b.data;\n");
                out.append("    for(size_t i = 0; i < a.length; i += 1) {\n");
                out.append("    if(!(");
                this.emitEquality(
                    "dataA[i]", "dataB[i]", td.elementType(), out
                );
                out.append(")) { return 0; }\n");
                out.append("    }\n");
                out.append("    return 1;\n");
                out.append("}\n");
            } break;
            case UNORDERED_OBJECT: {
                DataType.UnorderedObject<TypeVariable> td = tval.getValue();
                out.append("gbool gera_");
                out.append(tid);
                out.append("_eq(");
                this.emitType(tid, out);
                out.append(" a, ");
                this.emitType(tid, out);
                out.append(" b) {\n");
                out.append("    ");
                this.emitObjectLayoutName(tid, out);
                out.append("* dataA = (");
                this.emitObjectLayoutName(tid, out);
                out.append("*) a.allocation->data;\n");
                out.append("    ");
                this.emitObjectLayoutName(tid, out);
                out.append("* dataB = (");
                this.emitObjectLayoutName(tid, out);
                out.append("*) b.allocation->data;\n");
                for(String memberName: td.memberTypes().keySet()) {
                    TypeVariable memberType = td.memberTypes().get(memberName);
                    out.append("    if(!(");
                    this.emitEquality(
                        "dataA->" + memberName, "dataB->" + memberName, 
                        memberType, out
                    );
                    out.append(")) { return 0; }\n");
                }
                out.append("    return 1;\n");
                out.append("}\n");
            } break;
            case UNION: {
                DataType.Union<TypeVariable> td = tval.getValue();
                out.append("gbool gera_");
                out.append(tid);
                out.append("_eq(");
                this.emitType(tid, out);
                out.append(" a, ");
                this.emitType(tid, out);
                out.append(" b) {\n");
                out.append("    if(a.tag != b.tag) { return 0; }\n");
                out.append("    switch(a.tag) {\n");
                for(String variantName: td.variantTypes().keySet()) {
                    TypeVariable variantType = td.variantTypes()
                        .get(variantName);
                    out.append("        case ");
                    out.append(this.getVariantTagNumber(variantName));
                    out.append(": {\n");
                    out.append("            ");
                    this.emitType(variantType, out);
                    out.append("* dataA = (");
                    this.emitType(variantType, out);
                    out.append("*) a.allocation->data;\n");
                    out.append("            ");
                    this.emitType(variantType, out);
                    out.append("* dataB = (");
                    this.emitType(variantType, out);
                    out.append("*) b.allocation->data;\n");
                    out.append("            return ");
                    this.emitEquality("*dataA", "*dataB", variantType, out);
                    out.append(";\n");
                    out.append("        }\n");
                }
                out.append("    }\n");
                out.append("}\n");
            } break;
            case CLOSURE: {
                // nothing to do yet
            } break;
        }
    }


    private void emitType(TypeVariable t , StringBuilder out) {
        this.emitType(t.id, out);
    }


    private void emitType(int tid, StringBuilder out) {
        this.usedTypes.add(this.typeContext.substitutes.find(tid));
        DataType<TypeVariable> tval = this.typeContext.get(tid);
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: case REFERENCED:
                out.append("void"); 
                return;
            case BOOLEAN: out.append("gbool"); return;
            case INTEGER: out.append("gint"); return;
            case FLOAT: out.append("gfloat"); return;
            case STRING: out.append("GeraString"); return;
            case ARRAY: out.append("GeraArray"); return;
            case UNORDERED_OBJECT: out.append("GeraObject"); return;
            case CLOSURE: out.append("GeraClosure"); return;
            case UNION: out.append("GeraUnion"); return;
        }
    }


    private boolean shouldEmitType(TypeVariable t) {
        switch(this.typeContext.get(t).type) {
            case ANY: case NUMERIC: case INDEXED: case REFERENCED: case UNIT:
                return false;
            case BOOLEAN: case INTEGER: case FLOAT: case STRING: case ARRAY:
            case UNORDERED_OBJECT: case CLOSURE: case UNION:
                return true;
        }
        return true;
    }


    private void emitDefaultValue(TypeVariable t, StringBuilder out) {
        DataType<TypeVariable> tval = this.typeContext.get(t);
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: case REFERENCED:
                throw new RuntimeException("type does not have a value!");
            case BOOLEAN: out.append("0"); return;
            case INTEGER: out.append("0"); return;
            case FLOAT: out.append("0.0"); return;
            case STRING: case ARRAY: case UNORDERED_OBJECT: case UNION:
                out.append("(");
                this.emitType(t, out);
                out.append(") { .allocation = NULL }");
                return;
            case CLOSURE:
                out.append("(GeraClosure) { .captures = NULL }"); 
                return;
        }
    }


    private void emitSymbols(StringBuilder out) {
        for(Namespace path: this.symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = this.symbols.get(path).get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
                continue;
            }
            Symbols.Symbol.Procedure symbolData = symbol.getValue();
            if(symbolData.body().isEmpty()) {
                continue;
            }
            for(
                int variantI = 0; 
                variantI < symbol.variantCount(); 
                variantI += 1
            ) {
                if(symbol.mappedVariantIdx(variantI) != variantI) { continue; }
                Symbols.Symbol.Procedure variantData = symbol
                    .getVariant(variantI);
                this.emitType(variantData.returnType().get(), out);
                out.append(" ");
                this.emitVariant(path, variantI, out);
                out.append("(");
                int argC = variantData.argumentTypes().get().size();
                if(argC == 0) { 
                    out.append("void"); 
                }
                boolean hadArg = false;
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = variantData.argumentTypes()
                        .get().get(argI);
                    if(!this.shouldEmitType(argT)) { continue; }
                    if(hadArg) { 
                        out.append(", "); 
                    }
                    hadArg = true;
                    this.emitType(argT, out);
                    out.append(" arg_");
                    out.append(argI);
                }
                out.append(");\n");
            }
        }
        for(Namespace path: this.symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = this.symbols.get(path).get();
            if(symbol.type != Symbols.Symbol.Type.PROCEDURE) {
                continue;
            }
            Symbols.Symbol.Procedure symbolData = symbol.getValue();
            if(symbolData.body().isEmpty()) {
                continue;
            }
            for(
                int variantI = 0; 
                variantI < symbol.variantCount(); 
                variantI += 1
            ) {
                if(symbol.mappedVariantIdx(variantI) != variantI) { continue; }
                Symbols.Symbol.Procedure variantData = symbol
                    .getVariant(variantI);
                StringBuilder name = new StringBuilder();
                this.emitVariant(path, variantI, name);
                this.emitFunction(
                    variantData.returnType().get(), name.toString(),
                    Optional.empty(), variantData.argumentTypes().get(),
                    variantData.ir_body().get(), variantData.ir_context().get(),
                    out
                );
            }
        }
    }


    private void emitFunction(
        TypeVariable retType, String name, 
        Optional<Long> closureId, List<TypeVariable> argTypes, 
        List<Ir.Instr> body, Ir.Context context, StringBuilder out
    ) {
        this.enterContext(context);
        this.emitType(retType, out);
        out.append(" ");
        out.append(name);
        out.append("(");
        if(closureId.isPresent()) {
            out.append("GeraAllocation* closure_alloc");
        } else if(argTypes.size() == 0) {
            out.append("void"); 
        }
        boolean hadArg = closureId.isPresent();
        for(int argI = 0; argI < argTypes.size(); argI += 1) {
            TypeVariable argT = argTypes.get(argI);
            if(!this.shouldEmitType(argT)) { continue; }
            if(hadArg) { 
                out.append(", "); 
            }
            hadArg = true;
            this.emitType(argT, out);
            out.append(" arg_");
            out.append(argI);
        }
        out.append(") {\n");
        if(closureId.isPresent()) {
            out.append("GeraClosureCaptures");
            out.append(closureId.get());
            out.append("* captures = (GeraClosureCaptures");
            out.append(closureId.get());
            out.append("*) closure_alloc->data;\n");
        }
        List<TypeVariable> variableTypes = this.context().variableTypes;
        for(int varI = 0; varI < variableTypes.size(); varI += 1) {
            TypeVariable varT = variableTypes.get(varI);
            if(!this.shouldEmitType(varT)) { continue; }
            String capturedName = this.context().capturedNames.get(varI);
            if(capturedName != null) {
                out.append("GeraAllocation* captured_");
                out.append(capturedName);
                out.append(" = gera___alloc(sizeof(");
                this.emitType(varT, out);
                out.append("), NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
            } else {
                this.emitType(varT, out);
                out.append(" local_");
                out.append(varI);
                out.append(";\n");
            }
        }
        for(int varI = 0; varI < variableTypes.size(); varI += 1) {
            TypeVariable varT = variableTypes.get(varI);
            if(!this.shouldEmitType(varT)) { continue; }
            this.emitVariable(new Ir.Variable(varI, 0), out);
            out.append(" = ");
            int argI = -1;
            for(int cArgI = 0; cArgI < argTypes.size(); cArgI += 1) {
                int cVarI = this.context().argumentVars
                    .get(cArgI).index;
                if(cVarI != varI) { continue; }
                argI = cArgI;
                break;
            }
            if(argI == -1) {
                this.emitDefaultValue(varT, out);
            } else {
                out.append("arg_");
                out.append(argI);
            }
            out.append(";\n");            
        }
        this.emitInstructions(body, out);
        out.append("}\n");
        this.exitContext();
    }
    

    private void emitVariable(Ir.Variable v, StringBuilder out) {
        String capturedName = this.context().capturedNames.get(v.index);
        if(capturedName != null) {
            out.append("*((");
            this.emitType(this.context().variableTypes.get(v.index), out);
            out.append("*) captured_");
            out.append(capturedName);
            out.append("->data)");
        } else {
            out.append("local_");
            out.append(v.index);
        }
    }


    private void emitVariant(Namespace path, int variant, StringBuilder out) {
        this.emitPath(path, out);
        out.append("_");
        out.append(variant);
    }


    private void emitPath(Namespace path, StringBuilder out) {
        for(
            int elementI = 0; elementI < path.elements().size(); elementI += 1
        ) {
            if(elementI > 0) {
                out.append("_");
            }
            String element = path.elements().get(elementI);
            for(int charI = 0; charI < element.length(); charI += 1) {
                if(element.charAt(charI) == '_') {
                    out.append("__");
                } else {
                    out.append(element.charAt(charI));
                }
            }
        }
    }


    private void emitStringLiteral(String content, StringBuilder out) {
        out.append("\"");
        for(int charI = 0; charI < content.length(); charI += 1) {
            char c = content.charAt(charI);
            switch(c) {
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                case '\"': out.append("\\\""); break;
                case '\0': out.append("\\0"); break;
                default: out.append(c);
            }
        }
        out.append("\"");
    }


    private void emitInstructions(List<Ir.Instr> instr, StringBuilder out) {
        for(Ir.Instr i: instr) {
            this.emitInstruction(i, out);
        }
    }


    private void emitInstruction(Ir.Instr instr, StringBuilder out) {
        switch(instr.type) {
            case LOAD_BOOLEAN: {
                Ir.Instr.LoadBoolean data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value()? "1" : "0");
                out.append(";\n");
            } break;
            case LOAD_INTEGER: {
                Ir.Instr.LoadInteger data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append(";\n");
            } break;
            case LOAD_FLOAT: {
                Ir.Instr.LoadFloat data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append(";\n");
            } break;
            case LOAD_STRING: {
                Ir.Instr.LoadString data = instr.getValue();
                this.emitVariable(instr.dest.get(), out);
                out.append(" = gera___wrap_static_string(");
                this.emitStringLiteral(data.value(), out);
                out.append(");\n");
            } break;
            case LOAD_OBJECT: {
                Ir.Instr.LoadObject data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.dest.get().index);
                out.append("{\n");
                out.append("    GeraAllocation* a = gera___alloc(sizeof(");
                this.emitObjectLayoutName(objT.id, out);
                out.append("), NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
                out.append("    ");
                this.emitObjectLayoutName(objT.id, out);
                out.append("* members = (");
                this.emitObjectLayoutName(objT.id, out);
                out.append("*) a->data;\n");
                for(int memI = 0; memI < data.memberNames().size(); memI += 1) {
                    TypeVariable memT = this.context().variableTypes
                        .get(instr.arguments.get(memI).index);
                    if(!this.shouldEmitType(memT)) { continue; }
                    out.append("    members->");
                    out.append(data.memberNames().get(memI));
                    out.append(" = ");
                    this.emitVariable(instr.arguments.get(memI), out);
                    out.append(";\n");
                }
                out.append("    ");
                this.emitVariable(instr.dest.get(), out);
                out.append(" = (GeraObject) { .allocation = a };\n");
                out.append("}\n");
            } break;
            case LOAD_FIXED_ARRAY: {
                boolean isEmpty = instr.arguments.size() == 0
                    || !this.shouldEmitType(this.context().variableTypes.get(
                        instr.arguments.get(0).index
                    ));
                if(isEmpty) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(
                        " = (GeraArray) { .allocation = NULL, .length = 0,"
                            + " .data = NULL };\n"
                    );
                } else {
                    TypeVariable itemT = this.context().variableTypes
                        .get(instr.arguments.get(0).index);
                    out.append("{\n");
                    out.append("    GeraAllocation* a = gera___alloc(sizeof(");
                    this.emitType(itemT, out);
                    out.append(") * ");
                    out.append(instr.arguments.size());
                    out.append(", NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
                    out.append("    ");
                    this.emitType(itemT, out);
                    out.append("* items = (");
                    this.emitType(itemT, out);
                    out.append("*) a->data;\n");
                    for(
                        int valI = 0; valI < instr.arguments.size(); valI += 1
                    ) {
                        out.append("    items[");
                        out.append(valI);
                        out.append("] = ");
                        this.emitVariable(instr.arguments.get(valI), out);
                        out.append(";\n");
                    }
                    out.append("    ");
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = (GeraArray) { .allocation = a, .length = ");
                    out.append(instr.arguments.size());
                    out.append(", .data = a->data };\n");
                    out.append("}\n");
                }
            } break;
            case LOAD_REPEAT_ARRAY: {
                Ir.Instr.LoadRepeatArray data = instr.getValue();
                TypeVariable itemT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(!this.shouldEmitType(itemT)) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(
                        " = (GeraArray) { .allocation = NULL, .length = 0,"
                            + " .data = NULL };\n"
                    );
                } else {
                    out.append("{\n");
                    out.append("    size_t length = gera___verify_size(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(");\n");
                    out.append("    GeraAllocation* a = gera___alloc(sizeof(");
                    this.emitType(itemT, out);
                    out.append(") * length, NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
                    out.append("    ");
                    this.emitType(itemT, out);
                    out.append("* items = (");
                    this.emitType(itemT, out);
                    out.append("*) a->data;\n");
                    out.append(
                        "    for(size_t itemI = 0; itemI < length; itemI += 1)"
                            + " {\n"
                    );
                    out.append("        items[itemI] = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    out.append("    }\n");
                    out.append("    ");
                    this.emitVariable(instr.dest.get(), out);
                    out.append(
                        " = (GeraArray) { .allocation = a, .length = length,"
                            + " .data = a->data };\n"
                    );
                    out.append("}\n");
                }
            } break;
            case LOAD_VARIANT: {
                Ir.Instr.LoadVariant data = instr.getValue();
                TypeVariable valueT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(!this.shouldEmitType(valueT)) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = (GeraUnion) { .allocation = NULL, .tag = ");
                    out.append(this.getVariantTagNumber(data.variantName()));
                    out.append(" };\n");
                } else {
                    out.append("{\n");
                    out.append("    GeraAllocation* a = gera___alloc(sizeof(");
                    this.emitType(valueT, out);
                    out.append("), NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
                    out.append("    *((");
                    this.emitType(valueT, out);
                    out.append("*) a->data) = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    out.append("    ");
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = (GeraUnion) { .allocation = a, .tag = ");
                    out.append(this.getVariantTagNumber(data.variantName()));
                    out.append(" };\n");
                    out.append("}\n");
                }
            } break;
            case LOAD_CLOSURE: {
                Ir.Instr.LoadClosure data = instr.getValue();
                long closureId = this.closureBodyCount;
                this.closureBodyCount += 1;
                StringBuilder captures = new StringBuilder();
                captures.append("typedef struct GeraClosureCaptures");
                captures.append(closureId);
                captures.append(" {\n");
                for(String captureName: data.captureNames()) {
                    captures.append("    GeraAllocation* ");
                    captures.append(captureName);
                    captures.append(";\n");
                }
                captures.append("} GeraClosureCaptures");
                captures.append(closureId);
                captures.append(";\n");
                String bodyName = "gera_closure_" + closureId + "_body";
                StringBuilder body = new StringBuilder();
                this.emitFunction(
                    data.returnType(), bodyName, Optional.of(closureId), 
                    data.argumentTypes(), data.body(), data.context(), body
                );
                this.closureBodies.append(captures);
                this.closureBodies.append(body);
                this.closureBodies.append("\n");
                out.append("{\n");
                out.append("    GeraAllocation* a = gera___alloc(sizeof(");
                out.append("GeraClosureCaptures");
                out.append(closureId);
                out.append("), NULL);\n"); // TODO! REPLACE THIS `NULL` WITH ACTUAL MARK HANDLER
                out.append("    GeraClosureCaptures");
                out.append(closureId);
                out.append("* c = (GeraClosureCaptures");
                out.append(closureId);
                out.append("*) a->data;\n");
                for(String captureName: data.captureNames()) {
                    out.append("    c->");
                    out.append(captureName);
                    out.append(" = ");
                    if(data.inheritedCaptures().contains(captureName)) {
                        out.append("captures->");
                        out.append(captureName);
                    } else {
                        out.append("captured_");
                        out.append(captureName);
                    }
                    out.append(";\n");
                }
                out.append("    ");
                this.emitVariable(instr.dest.get(), out);
                out.append(" = (GeraClosure) { .captures = a, .body = &");
                out.append(bodyName);
                out.append(" };\n");
                out.append("}\n");
            } break;
            case LOAD_STATIC_VALUE: {
                // TODO!
            } break;
            case LOAD_EXT_VARIABLE: {
                // TODO!
            } break;

            case READ_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable memT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(memT)) {
                    out.append("geracoredeps_lock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ((");
                    this.emitObjectLayoutName(objT.id, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)->");
                    out.append(data.memberName());
                    out.append(";\n");
                    out.append("geracoredeps_unlock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                }
            } break;
            case WRITE_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable memT = this.context().variableTypes
                    .get(instr.arguments.get(1).index);
                if(this.shouldEmitType(memT)) {
                    out.append("geracoredeps_lock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                    out.append("((");
                    this.emitObjectLayoutName(objT.id, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)->");
                    out.append(data.memberName());
                    out.append(" = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    out.append("geracoredeps_unlock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                }
            } break;
            case READ_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                TypeVariable memT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(memT)) {
                    out.append("geracoredeps_lock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ((");
                    this.emitType(memT, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)[gera___verify_index(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".length, ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(")];\n");
                    out.append("geracoredeps_unlock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                }
            } break;
            case WRITE_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                TypeVariable memT = this.context().variableTypes
                    .get(instr.arguments.get(2).index);
                if(this.shouldEmitType(memT)) {
                    out.append("geracoredeps_lock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                    out.append("((");
                    this.emitType(memT, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)[gera___verify_index(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".length, ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(")] = ");
                    this.emitVariable(instr.arguments.get(2), out);
                    out.append(";\n");
                    out.append("geracoredeps_unlock_mutex(&");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data_mutex);\n");
                }
            } break;
            case READ_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    out.append(
                        "geracoredeps_lock_mutex("
                            + "&closure_alloc->data_mutex"
                            + ");\n"
                    );
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = *((");
                    this.emitType(valT, out);
                    out.append("*) captures->");
                    out.append(data.captureName());
                    out.append("->data);\n");
                    out.append(
                        "geracoredeps_unlock_mutex("
                            + "&closure_alloc->data_mutex"
                            + ");\n"
                    );
                }
            } break;
            case WRITE_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(valT)) {
                    out.append(
                        "geracoredeps_lock_mutex("
                            + "&closure_alloc->data_mutex"
                            + ");\n"
                    );
                    out.append("*((");
                    this.emitType(valT, out);
                    out.append("*) captures->");
                    out.append(data.captureName());
                    out.append("->data) = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    out.append(
                        "geracoredeps_unlock_mutex("
                            + "&closure_alloc->data_mutex"
                            + ");\n"
                    );
                }
            } break;

            case COPY: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                }
            } break;

            case ADD: {
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (((guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") + ((guint) ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append("))");
                } else {
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(" + ");
                    this.emitVariable(instr.arguments.get(1), out);
                }
                out.append(";\n");
            } break;
            case SUBTRACT: {
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (((guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") - ((guint) ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append("))");
                } else {
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(" - ");
                    this.emitVariable(instr.arguments.get(1), out);
                }
                out.append(";\n");
            } break;
            case MULTIPLY:  {
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (((guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") * ((guint) ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append("))");
                } else {
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(" * ");
                    this.emitVariable(instr.arguments.get(1), out);
                }
                out.append(";\n");
            } break;
            case DIVIDE: {
                Ir.Instr.Division data = instr.getValue();
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (((guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") / ((guint) gera___verify_integer_divisor(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(")))");
                } else {
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(" / ");
                    this.emitVariable(instr.arguments.get(1), out);
                }
                out.append(";\n");
            } break;
            case MODULO: {
                Ir.Instr.Division data = instr.getValue();
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (((guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") % ((guint) gera___verify_integer_divisor(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(")))");
                } else {
                    out.append("gera___float_mod(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(", ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(")");
                }
                out.append(";\n");
            } break;
            case NEGATE: {
                boolean isInteger = this.typeContext.get(
                    this.context().variableTypes.get(instr.dest.get().index)
                ).type == DataType.Type.INTEGER;
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                if(isInteger) {
                    out.append("(gint) (- (guint) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(")");
                } else {
                    this.emitVariable(instr.arguments.get(0), out);
                }
                out.append(";\n");
            } break;
            case LESS_THAN: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" < ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case GREATER_THAN: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" > ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case LESS_THAN_EQUAL: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" <= ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case GREATER_THAN_EQUAL: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(" >= ");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(";\n");
            } break;
            case EQUALS: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                StringBuilder a = new StringBuilder();
                StringBuilder b = new StringBuilder();
                this.emitVariable(instr.arguments.get(0), a);
                this.emitVariable(instr.arguments.get(1), b);
                this.emitEquality(a.toString(), b.toString(), compT, out);
                out.append(";\n");
            } break;
            case NOT_EQUALS: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = !(");
                StringBuilder a = new StringBuilder();
                StringBuilder b = new StringBuilder();
                this.emitVariable(instr.arguments.get(0), a);
                this.emitVariable(instr.arguments.get(1), b);
                this.emitEquality(a.toString(), b.toString(), compT, out);
                out.append(");\n");
            } break;
            case NOT: {
                this.emitVariable(instr.dest.get(), out);
                out.append(" = !");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
            } break;

            case BRANCH_ON_VALUE: {
                Ir.Instr.BranchOnValue data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                DataType<TypeVariable> valVT = this.typeContext.get(valT);
                int brC = data.branchBodies().size();
                if(valVT.type == DataType.Type.INTEGER) {
                    out.append("switch(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(") {\n");
                    for(int brI = 0; brI < brC; brI += 1) {
                        long brVal = data.branchValues().get(brI)
                            .<Ir.StaticValue.Int>getValue().value;
                        out.append("case ");
                        out.append(brVal);
                        out.append(":\n");
                        this.emitInstructions(
                            data.branchBodies().get(brI), out
                        );
                        out.append("break;\n");
                    }
                    out.append("default:\n");
                    this.emitInstructions(data.elseBody(), out);
                    out.append("}\n");
                } else {
                    StringBuilder v = new StringBuilder();
                    this.emitVariable(instr.arguments.get(0), v);
                    for(int brI = 0; brI < brC; brI += 1) {
                        StringBuilder bV = new StringBuilder();
                        this.emitValueRef(data.branchValues().get(brI), bV);
                        if(brI > 0) {
                            out.append(" else ");
                        }
                        out.append("if(");
                        this.emitEquality(
                            v.toString(), bV.toString(), valT, out
                        );
                        out.append(") {\n");
                        this.emitInstructions(
                            data.branchBodies().get(brI), out
                        );
                        out.append("}");
                    }
                    out.append(" else {\n");
                    this.emitInstructions(data.elseBody(), out);
                    out.append("}\n");
                }
            } break;
            case BRANCH_ON_VARIANT: {
                Ir.Instr.BranchOnVariant data = instr.getValue();
                out.append("switch(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".tag) {\n");
                for(int brI = 0; brI < data.branchBodies().size(); brI += 1) {
                    String variantName = data.branchVariants().get(brI);
                    out.append("case ");
                    out.append(this.getVariantTagNumber(variantName));
                    out.append(":\n");
                    Optional<Ir.Variable> bVar = data.branchVariables()
                        .get(brI);
                    if(bVar.isPresent()) {
                        TypeVariable valT = this.context().variableTypes
                            .get(bVar.get().index);
                        this.emitVariable(bVar.get(), out);
                        out.append(" = *((");
                        this.emitType(valT, out);
                        out.append("*) ");
                        this.emitVariable(instr.arguments.get(0), out);
                        out.append(".allocation->data);\n");
                    }
                    this.emitInstructions(data.branchBodies().get(brI), out);
                    out.append("break;\n");
                }
                out.append("default:\n");
                this.emitInstructions(data.elseBody(), out);
                out.append("}\n");
            } break;

            case CALL_PROCEDURE: {
                Ir.Instr.CallProcedure data = instr.getValue();
                TypeVariable retT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(retT)) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                }
                this.emitVariant(data.path(), data.variant(), out);
                out.append("(");
                boolean hadArg = false;
                for(int argI = 0; argI < instr.arguments.size(); argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    if(hadArg) {
                        out.append(", ");
                    }
                    hadArg = true;
                    this.emitVariable(instr.arguments.get(argI), out);
                }
                out.append(");\n");
            } break;
            case CALL_CLOSURE: {
                TypeVariable retT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(retT)) {
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                }
                out.append("((");
                this.emitType(retT, out);
                out.append(" (*)(GeraAllocation*");
                int argC = instr.arguments.size() - 1;
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI + 1).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    out.append(", ");
                    this.emitType(argT, out);
                }
                out.append("))(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".body))(");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(".captures");
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI + 1).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    out.append(", ");
                    this.emitVariable(instr.arguments.get(argI + 1), out);
                }
                out.append(");\n");
            } break;
            case RETURN: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                out.append("return");
                if(shouldEmitType(valT)) {
                    out.append(" ");
                    this.emitVariable(instr.arguments.get(0), out);
                }
                out.append(";\n");
            } break;

            case LOAD_UNIT:
            case PHI: {
                // do nothing
            } break;
        }
    }


    private void emitEquality(
        String a, String b, TypeVariable t, StringBuilder out
    ) {
        this.usedTypes.add(this.typeContext.substitutes.find(t.id));
        DataType<TypeVariable> tval = this.typeContext.get(t);
        int tRoot = this.typeContext.substitutes.find(t.id);
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: case REFERENCED: {
                out.append("1"); 
            } break;
            case BOOLEAN: case INTEGER: case FLOAT: {
                out.append(a);
                out.append(" == ");
                out.append(b); 
            } break;
            case STRING: {
                out.append("gera___string_eq(");
                out.append(a);
                out.append(", ");
                out.append(b);
                out.append(")");
            } break;
            case ARRAY: case UNORDERED_OBJECT: case UNION: {
                out.append("gera_");
                out.append(tRoot);
                out.append("_eq(");
                out.append(a);
                out.append(", ");
                out.append(b);
                out.append(")");
            } break;
            case CLOSURE: {
                out.append(a);
                out.append(".allocation == ");
                out.append(b);
                out.append(".allocation");
            } break;
        }
    }
    
}
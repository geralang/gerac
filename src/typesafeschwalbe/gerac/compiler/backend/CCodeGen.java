
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

        GeraAllocation* gera___alloc(size_t size, GeraFreeHandler free_h) {
            GeraAllocation* a = geracoredeps_malloc(
                sizeof(GeraAllocation) + size
            );
            if(a == NULL) { gera___panic("unable to allocate heap memory"); }
            a->header_mutex = geracoredeps_create_mutex();
            a->rc = 1;
            a->size = size;
            a->fh = free_h;
            a->data_mutex = geracoredeps_create_mutex();
            // printf("[GC] Allocation of %zu bytes at %p\\n", size, a);
            return a;
        }

        void gera___free(GeraAllocation* a) {
            if(a == NULL) { return; }
            // printf("[GC] Deallocation of allocation at %p\\n", a);
            if(a->fh != NULL) { (a->fh)(a); }
            geracoredeps_free_mutex(&a->header_mutex);
            geracoredeps_free_mutex(&a->data_mutex);
            geracoredeps_free(a);
        }

        void gera___ref_copied(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->header_mutex);
            a->rc += 1;
            geracoredeps_unlock_mutex(&a->header_mutex);
        }

        void gera___ref_deleted(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->header_mutex);
            a->rc -= 1;
            gbool free_alloc = a->rc == 0;
            geracoredeps_unlock_mutex(&a->header_mutex);
            if(free_alloc) { gera___free(a); }
        }

        void gera___begin_read(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->data_mutex);
        }

        void gera___end_read(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_unlock_mutex(&a->data_mutex);
        }

        void gera___begin_write(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_lock_mutex(&a->data_mutex);
        }

        void gera___end_write(GeraAllocation* a) {
            if(a == NULL) { return; }
            geracoredeps_unlock_mutex(&a->data_mutex);
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
            geracoredeps_eprint_backtrace();
            // geracoredeps_exit(1);
            volatile char x = *((char*) NULL);
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

        void gera___verify_substring_input(
            size_t src_l, gint* start_si, gint* end_si
        ) {
            gint start_i = *start_si;
            if(start_i < 0) { start_i += src_l; }
            if(start_i < 0 || start_i > src_l) {
                size_t ssl = geracoredeps_display_sint_length(*start_si);
                char ss[ssl + 1];
                geracoredeps_display_sint(*start_si, ss);
                ss[ssl] = '\\0';
                size_t lsl = geracoredeps_display_uint_length(src_l);
                char ls[lsl + 1];
                geracoredeps_display_uint(src_l, ls);
                ls[lsl] = '\\0';
                gera___panic_pre();
                geracoredeps_eprint("the start index ");
                geracoredeps_eprint(ss);
                geracoredeps_eprint(" is out of bounds for a string of length ");
                geracoredeps_eprint(ls);
                gera___panic_post();
            }
            gint end_i = *end_si;
            if(end_i < 0) { end_i += src_l; }
            if(end_i < 0 || end_i > src_l) {
                size_t esl = geracoredeps_display_sint_length(*end_si);
                char es[esl + 1];
                geracoredeps_display_sint(*end_si, es);
                es[esl] = '\\0';
                size_t lsl = geracoredeps_display_uint_length(src_l);
                char ls[lsl + 1];
                geracoredeps_display_uint(src_l, ls);
                ls[lsl] = '\\0';
                gera___panic_pre();
                geracoredeps_eprint("the end index ");
                geracoredeps_eprint(es);
                geracoredeps_eprint(" is out of bounds for a string of length ");
                geracoredeps_eprint(ls);
                gera___panic_post();

            }
            if(start_i > end_i) {
                size_t ssl = geracoredeps_display_sint_length(*start_si);
                char ss[ssl + 1];
                geracoredeps_display_sint(*start_si, ss);
                ss[ssl] = '\\0';
                size_t esl = geracoredeps_display_sint_length(*end_si);
                char es[esl + 1];
                geracoredeps_display_sint(*end_si, es);
                es[esl] = '\\0';
                size_t lsl = geracoredeps_display_uint_length(src_l);
                char ls[lsl + 1];
                geracoredeps_display_uint(src_l, ls);
                ls[lsl] = '\\0';
                gera___panic_pre();
                geracoredeps_eprint("the start index ");
                geracoredeps_eprint(ss);
                geracoredeps_eprint(" is larger than the end index ");
                geracoredeps_eprint(es);
                geracoredeps_eprint(" (length of string is ");
                geracoredeps_eprint(ls);
                geracoredeps_eprint(")");
                gera___panic_post();
            }
            *start_si = start_i;
            *end_si = end_i;
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
            gera___ref_copied(src.allocation);
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

        gint gera___hash(const unsigned char* data, size_t data_len) {
            size_t hash = 0;
            for(size_t i = 0; i < data_len; i += 1) {
                hash = data[i] + (hash << 6) + (hash << 16) - hash;
            }
            return (gint) hash;
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
                .length = argc
            };
        }
        """;


    @FunctionalInterface
    private static interface BuiltInProcedure {
        void emit(
            TypeContext tctx, List<Ir.Variable> args, List<TypeVariable> argt,
            Ir.Variable dest, StringBuilder out
        );
    }

    private void addBuiltins() {
        this.builtIns.put(
            new Namespace(List.of("core", "addr_eq")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" a = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_read", args.get(1), out);
                this.emitType(argt.get(1), out);
                out.append(" b = ");
                this.emitVariable(args.get(1), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(1), out);
                this.emitVarSync("begin_write", dest, out);
                this.emitVariable(dest, out);
                out.append(" = a.allocation == b.allocation;\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "tag_eq")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" a = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_read", args.get(1), out);
                this.emitType(argt.get(1), out);
                out.append(" b = ");
                this.emitVariable(args.get(1), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(1), out);
                out.append("gera___begin_read(a.allocation);\n");
                out.append("gera___begin_read(b.allocation);\n");
                out.append(
                    "GeraUnionData* ad = (GeraUnionData*) a.allocation->data;\n"
                );
                out.append(
                    "GeraUnionData* bd = (GeraUnionData*) b.allocation->data;\n"
                );
                out.append("gbool result = ad->tag == bd->tag;\n");
                out.append("gera___end_read(a.allocation);\n");
                out.append("gera___end_read(b.allocation);\n");
                this.emitVarSync("begin_write", dest, out);
                this.emitVariable(dest, out);
                out.append(" = result;\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "length")),
            (tctx, args, argt, dest, out) -> {
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitVarSync("begin_write", dest, out);
                this.emitVariable(dest, out);
                out.append(" = (gint) ");
                this.emitVariable(args.get(0), out);
                out.append(".length;\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("end_write", dest, out);
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "exhaust")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" iter = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                out.append("for(;;) {\n");
                out.append(
                    "GeraUnion next = ((GeraUnion (*)(GeraAllocation*)) iter.body)"
                        + "(iter.allocation);\n"
                );
                out.append("gbool at_end = next.tag == ");
                out.append(this.getVariantTagNumber("end"));
                out.append(";\n");
                out.append("gera___ref_deleted(next.allocation);\n");
                out.append("if(at_end) { break; }\n");
                out.append("}\n");
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "exhaust")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" iter = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                out.append("for(;;) {\n");
                out.append(
                    "GeraUnion next = ((GeraUnion (*)(GeraAllocation*)) iter.body)"
                        + "(iter.allocation);\n"
                );
                out.append("gbool at_end = next.tag == ");
                out.append(this.getVariantTagNumber("end"));
                out.append(";\n");
                out.append("gera___ref_deleted(next.allocation);\n");
                out.append("if(at_end) { break; }\n");
                out.append("}\n");
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "panic")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                out.append("GERA_STRING_NULL_TERM(");
                this.emitVariable(args.get(0), out);
                out.append(", panic_message_nt);\n");
                this.emitVarSync("end_read", args.get(0), out);
                out.append("gera___panic(panic_message_nt);\n");
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_str")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                if(this.shouldEmitType(argt.get(0))) {
                    this.emitVarSync("begin_read", args.get(0), out);
                    this.emitType(argt.get(0), out);
                    out.append(" converted = ");
                    this.emitVariable(args.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", args.get(0), out);
                }
                switch(tctx.get(argt.get(0)).type) {
                    case UNIT: {
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = ");
                        out.append("gera___wrap_static_string(\"unit\");\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case BOOLEAN: {
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = gera___wrap_static_string");
                        out.append("(converted? \"true\" : \"false\");\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case INTEGER: {
                        out.append(
                            "size_t result_l = geracoredeps_display_sint_length(converted);\n"
                        );
                        out.append("char result[result_l + 1];\n");
                        out.append(
                            "geracoredeps_display_sint(converted, result);\n"
                        );
                        out.append("result[result_l] = '\\0';\n");
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = gera___alloc_string(result);\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case FLOAT: {
                        out.append(
                            "size_t result_l = geracoredeps_display_float_length(converted);\n"
                        );
                        out.append("char result[result_l + 1];\n");
                        out.append(
                            "geracoredeps_display_float(converted, result);\n"
                        );
                        out.append("result[result_l] = '\\0';\n");
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = gera___alloc_string(result);\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case STRING: {
                        this.emitRefCopy("converted", argt.get(0), out);
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = converted;\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case ARRAY: {
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = ");
                        out.append("gera___wrap_static_string(\"<array>\");\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case UNORDERED_OBJECT: {
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = ");
                        out.append(
                            "gera___wrap_static_string(\"<object>\");\n"
                        );
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case CLOSURE: {
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        this.emitVariable(dest, out);
                        out.append(" = ");
                        out.append(
                            "gera___wrap_static_string(\"<closure>\");\n"
                        );
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case UNION: {
                        DataType.Union<TypeVariable> union
                            = tctx.get(argt.get(0)).getValue();
                        this.emitVarSync("begin_write", dest, out);
                        this.emitRefDelete(dest, out);
                        out.append("switch(converted.tag) {\n");
                        for(String variant: union.variantTypes().keySet()) {
                            out.append("case ");
                            out.append(this.getVariantTagNumber(variant));
                            out.append(":\n");
                            this.emitVariable(dest, out);
                            out.append(" = gera___wrap_static_string(\"#");
                            out.append(variant);
                            out.append(" <...>\");\n");
                            out.append("break;\n");
                        }
                        out.append("}\n");
                        this.emitVarSync("end_write", dest, out);
                    } break;
                    case ANY:
                    case NUMERIC:
                    case INDEXED:
                    case REFERENCED: {
                        out.append(
                            "gera___panic(\"if you read this, that means"
                                + " that the compiler fucked up real bad :(\""
                                + ");\n"
                        );
                    } break;
                }
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_int")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" converted = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_write", dest, out);
                this.emitVariable(dest, out);
                out.append(" = (gint) converted;\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "as_flt")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" converted = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_write", dest, out);
                this.emitVariable(dest, out);
                out.append(" = (gfloat) converted;\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "substring")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                out.append("GeraString src = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_read", args.get(1), out);
                out.append("gint start_i = ");
                this.emitVariable(args.get(1), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(1), out);
                this.emitVarSync("begin_read", args.get(2), out);
                out.append("gint end_i = ");
                this.emitVariable(args.get(2), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(2), out);
                out.append(
                    "gera___verify_substring_input(src.length, &start_i, &end_i);\n"
                );
                this.emitVarSync("begin_write", dest, out);
                this.emitRefDelete(dest, out);
                this.emitVariable(dest, out);
                out.append(" = gera___substring(src, start_i, end_i);\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "concat")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                out.append("GeraString a = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_read", args.get(1), out);
                out.append("GeraString b = ");
                this.emitVariable(args.get(1), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(1), out);
                this.emitVarSync("begin_write", dest, out);
                this.emitRefDelete(dest, out);
                this.emitVariable(dest, out);
                out.append(" = gera___concat(a, b);\n");
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
        this.builtIns.put(
            new Namespace(List.of("core", "hash")),
            (tctx, args, argt, dest, out) -> {
                out.append("{\n");
                this.emitVarSync("begin_read", args.get(0), out);
                this.emitType(argt.get(0), out);
                out.append(" value = ");
                this.emitVariable(args.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", args.get(0), out);
                this.emitVarSync("begin_write", dest, out);
                StringBuilder dest_str = new StringBuilder();
                this.emitVariable(dest, dest_str);
                this.emitHashValueOf(
                    "value", argt.get(0), dest_str.toString(), out
                );
                this.emitVarSync("end_write", dest, out);
                out.append("}\n");
            }
        );
    }

    private void emitHashValueOf(
        String value, TypeVariable t, String dest, StringBuilder out
    ) {
        switch(this.typeContext.get(t).type) {
            case UNIT: {
                out.append(dest);
                out.append(" = 0;\n");
            } break;
            case BOOLEAN: {
                out.append(dest);
                out.append(" = ");
                out.append(value);
                out.append(";\n");
            } break;
            case INTEGER: {
                out.append(dest);
                out.append(" = gera___hash((unsigned char*) &");
                out.append(value);
                out.append(", sizeof(gint));\n");
            } break;
            case FLOAT: {
                out.append(dest);
                out.append(" = gera___hash((unsigned char*) &");
                out.append(value);
                out.append(", sizeof(gfloat));\n");
            } break;
            case STRING: {
                out.append(dest);
                out.append(" = gera___hash((const unsigned char*) ");
                out.append(value);
                out.append(".data, ");
                out.append(value);
                out.append(".length_bytes);\n");
            } break;
            case ARRAY: case UNORDERED_OBJECT: case CLOSURE: {
                out.append(dest);
                out.append(" = gera___hash((const unsigned char*) &");
                out.append(value);
                out.append(".allocation, sizeof(GeraAllocation*));\n");
            } break;
            case UNION: {
                DataType.Union<TypeVariable> union
                    = this.typeContext.get(t).getValue();
                out.append("{\n");
                out.append("gera___begin_read(");
                out.append(value);
                out.append(".allocation);\n");
                out.append("uint32_t tag = ");
                out.append(value);
                out.append(".tag;\n");
                out.append("GeraAllocation* data_alloc = ");
                out.append(value);
                out.append(".allocation;\n");
                out.append("switch(tag) {\n");
                for(String variant: union.variantTypes().keySet()) {
                    TypeVariable variantT = union.variantTypes().get(variant);
                    out.append("case ");
                    out.append(this.getVariantTagNumber(variant));
                    out.append(":\n");
                    if(this.shouldEmitType(variantT)) {
                        out.append(
                            "GeraUnionData* data = "
                                + "(GeraUnionData*) data_alloc->data;\n"
                        );
                        this.emitType(variantT, out);
                        out.append(" value = *((");
                        this.emitType(variantT, out);
                        out.append("*) data->data);\n");
                    }
                    this.emitHashValueOf("value", variantT, dest, out);
                    out.append(dest);
                    out.append(" = (gint) (((guint) 29) * ((guint) ");
                    out.append(dest);
                    out.append(") + ((guint) tag));\n");
                    out.append("break;\n");
                }
                out.append("}\n");
                out.append("gera___end_read(");
                out.append(value);
                out.append(".allocation);\n");
                out.append("}\n");
            } break;
            case ANY:
            case NUMERIC:
            case INDEXED:
            case REFERENCED: {
                throw new RuntimeException("unsure of type!");
            }
            default: {
                throw new RuntimeException("unhandled type!");
            }
        }
    }


    private final Map<String, String> sourceFiles;
    private final Symbols symbols;
    private final TypeContext typeContext;
    private final Ir.StaticValues staticValues;
    private final Map<Namespace, BuiltInProcedure> builtIns;

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
        this.builtIns = new HashMap<>();
        this.contextStack = new LinkedList<>();
        this.addBuiltins();
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
        out.append(
            """
            //
            // Generated from Gera source code by the Gera compiler.
            // See: https://github.com/geralang
            //
            """);
        out.append("\n");
        out.append(CORE_LIB);
        out.append("\n");
        out.append(
            """
            void gera_free_captured_string(GeraAllocation* allocation) {
                gera___begin_read(allocation);
                GeraString* value = (GeraString*) allocation->data;
                gera___ref_deleted(value->allocation);
                gera___end_read(allocation);
            }   
            void gera_free_captured_array(GeraAllocation* allocation) {
                gera___begin_read(allocation);
                GeraArray* value = (GeraArray*) allocation->data;
                gera___ref_deleted(value->allocation);
                gera___end_read(allocation);
            }   
            void gera_free_captured_object(GeraAllocation* allocation) {
                gera___begin_read(allocation);
                GeraObject* value = (GeraObject*) allocation->data;
                gera___ref_deleted(value->allocation);
                gera___end_read(allocation);
            }   
            void gera_free_captured_union(GeraAllocation* allocation) {
                gera___begin_read(allocation);
                GeraUnion* value = (GeraUnion*) allocation->data;
                gera___ref_deleted(value->allocation);
                gera___end_read(allocation);
            }   
            void gera_free_captured_closure(GeraAllocation* allocation) {
                gera___begin_read(allocation);
                GeraClosure* value = (GeraClosure*) allocation->data;
                gera___ref_deleted(value->allocation);
                gera___end_read(allocation);
            } 
            typedef struct GeraUnionData {
                uint32_t tag;
                char data[];
            } GeraUnionData;
            """);
        StringBuilder valuesInit = new StringBuilder();
        this.emitValueInitializer(valuesInit);
        StringBuilder symDecls = new StringBuilder();
        this.emitSymbolDecls(symDecls);
        StringBuilder symImpls = new StringBuilder();
        this.emitSymbolImpls(symImpls);
        StringBuilder types = new StringBuilder();
        out.append("\n");
        this.emitTypeDeclarations(types, out);
        out.append("\n");
        out.append(types);
        out.append("\n");
        this.emitValueDeclarations(out);
        out.append("\n");
        out.append(symDecls);
        out.append("\n");
        out.append(this.closureBodies);
        out.append("\n");
        out.append(symImpls);
        out.append("\n");
        out.append(valuesInit);
        out.append("\n");
        out.append("int main(int argc, char** argv) {\n");
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
            TypeVariable valT = this.staticValues.valueTypes.get(val);
            if(!this.shouldEmitType(valT)) { continue; }
            out.append("static ");
            this.emitType(valT, out);
            out.append(" ");
            this.emitValueRef(val, out);
            out.append(";\n");
        }
    }


    private void emitValueInitializer(StringBuilder out) {
        out.append("void gera_init_svals(void) {\n");
        Map<Integer, Long> closureIds = new HashMap<>();
        Set<Integer> closuresWithCaptures = new HashSet<>();
        int valC = this.staticValues.values.size();
        for(int valI = 0; valI < valC; valI += 1) {
            Ir.StaticValue val = this.staticValues.values.get(valI);
            TypeVariable valT = this.staticValues.valueTypes.get(val);
            if(!this.shouldEmitType(valT)) { continue; }
            out.append("    ");
            this.emitValueRef(val, out);
            out.append(" = ");

            switch(this.typeContext.get(valT).type) {
                case BOOLEAN: {
                    out.append(
                        val.<Ir.StaticValue.Bool>getValue().value ? "1" : "0"
                    );
                } break;
                case INTEGER: {
                    out.append(val.<Ir.StaticValue.Int>getValue().value);
                } break;
                case FLOAT: {
                    double v = val.<Ir.StaticValue.Float>getValue().value;
                    if(Double.isNaN(v)) {
                        out.append("0.0 / 0.0");
                    } else if(v == Double.POSITIVE_INFINITY) {
                        out.append("1.0 / 0.0");
                    } else if(v == Double.NEGATIVE_INFINITY) {
                        out.append("-1.0 / 0.0");
                    } else {
                        out.append(String.format("%f", v));
                    }
                } break;
                case STRING: {
                    out.append("gera___wrap_static_string(");
                    this.emitStringLiteral(
                        val.<Ir.StaticValue.Str>getValue().value, out
                    );
                    out.append(")");
                } break;
                case ARRAY: {
                    DataType.Array<TypeVariable> valTD = this.typeContext
                        .get(valT).getValue();
                    Ir.StaticValue.Arr data = val.getValue();
                    out.append(
                        "(GeraArray) { .allocation = gera___alloc("
                    );
                    if(this.shouldEmitType(valTD.elementType())) {
                        out.append("sizeof(");
                        this.emitType(valTD.elementType(), out);
                        out.append(") * ");
                        out.append(data.value.size());
                    } else {
                        out.append("0");
                    }
                    out.append(", NULL), .length = ");
                    out.append(data.value.size());
                    out.append(" }");
                } break;
                case UNORDERED_OBJECT: {
                    out.append(
                        "(GeraObject) { .allocation = gera___alloc(sizeof("
                    );
                    this.emitObjectLayoutName(valT.id, out);
                    out.append("), NULL) }");
                } break;
                case UNION: {
                    DataType.Union<TypeVariable> valTD = this.typeContext
                        .get(valT).getValue();
                    Ir.StaticValue.Union data = val.getValue();
                    TypeVariable varT = valTD.variantTypes().get(data.variant);
                    if(this.shouldEmitType(varT)) {
                        out.append(
                            "(GeraUnion) { .allocation = gera___alloc("
                        );
                        out.append("sizeof(GeraUnionData) + sizeof(");
                        this.emitType(valTD.variantTypes().get(data.variant), out);
                        out.append("), NULL), .tag = ");
                        out.append(this.getVariantTagNumber(data.variant));
                        out.append(" }");
                    } else {
                        out.append(
                            "(GeraUnion) { .allocation = NULL, .tag = "
                        );
                        out.append(this.getVariantTagNumber(data.variant));
                        out.append(" }");
                    }
                } break;
                case CLOSURE: {
                    Ir.StaticValue.Closure data = val.getValue();
                    long closureId = this.closureBodyCount;
                    this.closureBodyCount += 1;
                    boolean hasCaptures = data.captureValues.size() > 0;
                    closureIds.put(valI, closureId);
                    if(hasCaptures) { closuresWithCaptures.add(valI); }
                    StringBuilder captures = new StringBuilder();
                    if(hasCaptures) {
                        captures.append("typedef struct GeraClosureCaptures");
                        captures.append(closureId);
                        captures.append(" {\n");
                        for(String captureName: data.captureValues.keySet()) {
                            captures.append("    GeraAllocation* ");
                            captures.append(captureName);
                            captures.append(";\n");
                        }
                        captures.append("} GeraClosureCaptures");
                        captures.append(closureId);
                        captures.append(";\n");
                    }
                    String bodyName = "gera_closure_" + closureId + "_body";
                    StringBuilder body = new StringBuilder();
                    this.emitFunction(
                        data.returnType, bodyName,
                        true, 
                        hasCaptures? Optional.of(closureId) : Optional.empty(), 
                        data.argumentTypes, data.body, data.context, body
                    );
                    this.closureBodies.append(captures);
                    this.closureBodies.append(body);
                    this.closureBodies.append("\n");
                    out.append("(GeraClosure) { .allocation = gera___alloc(");
                    if(hasCaptures) {
                        out.append("sizeof(GeraClosureCaptures");
                        out.append(closureId);
                        out.append(")");
                    } else {
                        out.append("0");
                    }
                    out.append(", NULL), .body = &");
                    out.append(bodyName);
                    out.append(" }");
                } break;
                case UNIT: case ANY: case NUMERIC: case INDEXED: 
                case REFERENCED:
                    throw new RuntimeException("should be filtered out!");
            }
            out.append(";\n");
        }
        for(int valI = 0; valI < valC; valI += 1) {
            Ir.StaticValue val = this.staticValues.values.get(valI);
            TypeVariable valT = this.staticValues.valueTypes.get(val);
            if(!this.shouldEmitType(valT)) { continue; }
            switch(this.typeContext.get(valT).type) {
                case BOOLEAN: case INTEGER: case FLOAT: case STRING:
                    break;
                case ARRAY: {
                    out.append("    {\n");
                    DataType.Array<TypeVariable> valTD = this.typeContext
                        .get(valT).getValue();
                    Ir.StaticValue.Arr data = val.getValue();
                    out.append("        ");
                    this.emitType(valTD.elementType(), out);
                    out.append("* items = (");
                    this.emitType(valTD.elementType(), out);
                    out.append("*) ");
                    this.emitValueRef(val, out);
                    out.append(".allocation->data;\n");
                    for(int itemI = 0; itemI < data.value.size(); itemI += 1) {
                        out.append("        items[");
                        out.append(itemI);
                        out.append("] = ");
                        this.emitValueRef(data.value.get(itemI), out);
                        out.append(";\n");
                    }
                    out.append("    }\n");
                } break;
                case UNORDERED_OBJECT: {
                    out.append("    {\n");
                    Ir.StaticValue.Obj data = val.getValue();
                    out.append("        ");
                    this.emitObjectLayoutName(valT.id, out);
                    out.append("* members = (");
                    this.emitObjectLayoutName(valT.id, out);
                    out.append("*) ");
                    this.emitValueRef(val, out);
                    out.append(".allocation->data;\n");
                    for(String member: data.value.keySet()) {
                        out.append("        members->member_");
                        out.append(member);
                        out.append(" = ");
                        this.emitValueRef(data.value.get(member), out);
                        out.append(";\n");
                    }
                    out.append("    }\n");
                } break;
                case UNION: {
                    DataType.Union<TypeVariable> valTD = this.typeContext
                        .get(valT).getValue();
                    Ir.StaticValue.Union data = val.getValue();
                    boolean hasValue = this
                        .shouldEmitType(valTD.variantTypes().get(data.variant));
                    if(hasValue) {
                        out.append("    {\n");
                        out.append("        ");
                        out.append("GeraUnionData* data = (GeraUnionData*) ");
                        this.emitValueRef(val, out);
                        out.append(".allocation->data;\n");
                        out.append("        data->tag = ");
                        out.append(this.getVariantTagNumber(data.variant));
                        out.append(";\n");
                        out.append("        *((");
                        this.emitType(valTD.variantTypes().get(data.variant), out);
                        out.append("*) data->data) = ");
                        this.emitValueRef(data.value, out);
                        out.append(";\n");
                        out.append("    }\n");
                    }
                } break;
                case CLOSURE: {
                    Ir.StaticValue.Closure data = val.getValue();
                    long closureId = closureIds.get(valI);
                    boolean hasCaptures = closuresWithCaptures.contains(valI);
                    if(hasCaptures) {
                        out.append("    {\n");
                        out.append("    GeraClosureCaptures");
                        out.append(closureId);
                        out.append("* c = (GeraClosureCaptures");
                        out.append(closureId);
                        out.append("*) ");
                        this.emitValueRef(val, out);
                        out.append(".allocation->data;\n");
                        for(String captureName: data.captureValues.keySet()) {
                            Ir.StaticValue captureValue = data.captureValues
                                .get(captureName);
                            TypeVariable captureType = this.staticValues
                                .valueTypes.get(captureValue);
                            if(this.shouldEmitType(captureType)) {
                                out.append("    c->");
                                out.append(captureName);
                                out.append(" = gera___alloc(sizeof(");
                                this.emitType(captureType, out);
                                out.append("), NULL);\n");
                                out.append("    *((");
                                this.emitType(captureType, out);
                                out.append("*) c->");
                                out.append(captureName);
                                out.append("->data) = ");
                                this.emitValueRef(captureValue, out);
                                out.append(";\n");
                            }
                        }
                        out.append("    }\n");
                    }
                } break;
                case UNIT: case ANY: case NUMERIC: case INDEXED: 
                case REFERENCED:
                    throw new RuntimeException("should be filtered out!");
            }
        }
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
                    boolean hadMember = false;
                    Iterable<String> memberNames = data.order().isPresent()
                        ? data.order().get()
                        : data.memberTypes().keySet();
                    for(String member: memberNames) {
                        TypeVariable memT = data.memberTypes().get(member);
                        if(!this.shouldEmitType(memT)) { continue; }
                        hadMember = true;
                        out.append("    ");
                        this.emitType(memT, out);
                        out.append(" member_");
                        out.append(member);
                        out.append(";\n");
                    }
                    if(!hadMember) {
                        out.append("    char empty;\n");
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
                pre.append("void gera_");
                pre.append(tid);
                pre.append("_free(GeraAllocation* allocation);\n");
            } break;
            case CLOSURE: {
                pre.append("void gera_");
                pre.append(tid);
                pre.append("_free(GeraAllocation* allocation);\n");
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
                out.append("    if(a.allocation == b.allocation) { return 1; }\n");
                out.append("    if(a.length != b.length) { return 0; }\n");
                out.append("    ");
                this.emitType(td.elementType(), out);
                out.append("* dataA = (");
                this.emitType(td.elementType(), out);
                out.append("*) a.allocation->data;\n");
                out.append("    ");
                this.emitType(td.elementType(), out);
                out.append("* dataB = (");
                this.emitType(td.elementType(), out);
                out.append("*) b.allocation->data;\n");
                out.append("    for(size_t i = 0; i < a.length; i += 1) {\n");
                out.append("        gera___begin_read(a.allocation);\n");
                out.append("        gera___begin_read(b.allocation);\n");
                out.append("        gbool items_eq = ");
                this.emitEquality("dataA[i]", "dataB[i]", td.elementType(), out);
                out.append(";\n");
                out.append("        gera___end_read(a.allocation);\n");
                out.append("        gera___end_read(b.allocation);\n");
                out.append("        if(!items_eq) { return 0; }\n");
                out.append("    }\n");
                out.append("    return 1;\n");
                out.append("}\n");
                StringBuilder itemRefDelete = new StringBuilder();
                this.emitRefDelete("items[i]", td.elementType(), itemRefDelete);
                out.append("void gera_");
                out.append(tid);
                out.append("_free(GeraAllocation* allocation) {\n");
                if(itemRefDelete.length() > 0) {
                    out.append("    gera___begin_read(allocation);\n");
                    out.append(
                        "    size_t length = allocation->size / sizeof("
                    );
                    this.emitType(td.elementType(), out);
                    out.append(");\n");
                    out.append("    ");
                    this.emitType(td.elementType(), out);
                    out.append("* items = (");
                    this.emitType(td.elementType(), out);
                    out.append("*) allocation->data;\n");
                    out.append("    for(size_t i = 0; i < length; i += 1) {\n");
                    out.append("        ");
                    out.append(itemRefDelete);
                    out.append("    }\n");
                    out.append("    gera___end_read(allocation);\n");
                }
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
                out.append("    if(a.allocation == b.allocation) { return 1; }\n");
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
                out.append("    gbool members_eq = 0;\n");
                for(String memberName: td.memberTypes().keySet()) {
                    TypeVariable memberType = td.memberTypes().get(memberName);
                    out.append("    gera___begin_read(a.allocation);\n");
                    out.append("    gera___begin_read(b.allocation);\n");
                    out.append("    members_eq = ");
                    this.emitEquality(
                        "dataA->member_" + memberName, 
                        "dataB->member_" + memberName, 
                        memberType, out
                    );
                    out.append(";\n");
                    out.append("        gera___end_read(a.allocation);\n");
                    out.append("        gera___end_read(b.allocation);\n");
                    out.append("        if(!members_eq) { return 0; }\n");
                }
                out.append("    return 1;\n");
                out.append("}\n");
                out.append("void gera_");
                out.append(tid);
                out.append("_free(GeraAllocation* allocation) {\n");
                out.append("    gera___begin_read(allocation);\n");
                out.append("    ");
                this.emitObjectLayoutName(tid, out);
                out.append("* members = (");
                this.emitObjectLayoutName(tid, out);
                out.append("*) allocation->data;\n");
                for(String memberName: td.memberTypes().keySet()) {
                    TypeVariable memberType = td.memberTypes().get(memberName);
                    StringBuilder refDelete = new StringBuilder();
                    this.emitRefDelete(
                        "members->member_" + memberName, memberType, refDelete
                    );
                    if(refDelete.length() > 0) {
                        out.append("    ");
                        out.append(refDelete);
                    }
                }
                out.append("    gera___end_read(allocation);\n");
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
                out.append("    if(a.allocation == b.allocation) { return 1; }\n");
                out.append(
                    "    GeraUnionData* ad = (GeraUnionData*) a.allocation->data"
                );
                out.append(";\n");
                out.append(
                    "    GeraUnionData* bd = (GeraUnionData*) b.allocation->data"
                );
                out.append(";\n");
                out.append("    if(ad->tag != bd->tag) { return 0; }\n");
                out.append("    switch(ad->tag) {\n");
                for(String variantName: td.variantTypes().keySet()) {
                    TypeVariable variantType = td.variantTypes()
                        .get(variantName);
                    out.append("        case ");
                    out.append(this.getVariantTagNumber(variantName));
                    out.append(": {\n");
                    if(this.shouldEmitType(variantType)) {
                        out.append("            gera___begin_read(a.allocation);\n");
                        out.append("            ");
                        this.emitType(variantType, out);
                        out.append(" av = *((");
                        this.emitType(variantType, out);
                        out.append("*) ad->data);\n");
                        out.append("            gera___end_read(a.allocation);\n");
                        out.append("            gera___begin_read(b.allocation);\n");
                        out.append("            ");
                        this.emitType(variantType, out);
                        out.append(" bv = *((");
                        this.emitType(variantType, out);
                        out.append("*) bd->data);\n");
                        out.append("            gera___end_read(b.allocation);\n");
                    }
                    out.append("            return ");
                    this.emitEquality("av", "bv", variantType, out);
                    out.append(";\n");
                    out.append("        }\n");
                }
                out.append("    }\n");
                out.append("}\n");
                out.append("void gera_");
                out.append(tid);
                out.append("_free(GeraAllocation* allocation) {\n");
                out.append("    gera___begin_read(allocation);\n");
                out.append(
                    "    GeraUnionData* data = (GeraUnionData*) allocation->data"
                );
                out.append(";\n");
                out.append("    switch(data->tag) {\n");
                for(String variantName: td.variantTypes().keySet()) {
                    TypeVariable variantType = td.variantTypes()
                        .get(variantName);
                    out.append("        case ");
                    out.append(this.getVariantTagNumber(variantName));
                    out.append(": {\n");
                    out.append("            ");
                    this.emitType(variantType, out);
                    out.append("* value = (");
                    this.emitType(variantType, out);
                    out.append("*) data->data;\n");
                    StringBuilder valueRefDelete = new StringBuilder();
                    this.emitRefDelete("(*value)", variantType, valueRefDelete);
                    if(valueRefDelete.length() > 0) {
                        out.append("        ");
                        out.append(valueRefDelete);
                    }
                    out.append("            break;\n");
                    out.append("        }\n");
                }
                out.append("    }\n");
                out.append("    gera___end_read(allocation);\n");
                out.append("}\n");
            } break;
            case CLOSURE: {
                // nothing to do
            } break;
        }
    }


    private void emitType(TypeVariable t, StringBuilder out) {
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
            case CLOSURE:
                out.append("(");
                this.emitType(t, out);
                out.append(") { .allocation = NULL }");
                return;
        }
    }


    private void emitSymbolDecls(StringBuilder out) {
        for(Namespace path: this.symbols.allSymbolPaths()) {
            Symbols.Symbol symbol = this.symbols.get(path).get();
            switch(symbol.type) {
                case VARIABLE: {
                    Symbols.Symbol.Variable symbolData = symbol.getValue();
                    if(symbol.externalName.isEmpty()) { continue; }
                    out.append("extern ");
                    this.emitType(symbolData.valueType().get(), out);
                    out.append(" ");
                    out.append(symbol.externalName.get());
                    out.append(";\n");
                } break;
                case PROCEDURE: {
                    Symbols.Symbol.Procedure symbolData = symbol.getValue();
                    if(symbolData.body().isEmpty()) {
                        if(symbol.externalName.isPresent()) {
                            Symbols.BuiltinContext builtin = symbolData
                                .builtinContext().get()
                                .apply(symbol.source);
                            this.emitType(builtin.returned(), out);
                            out.append(" ");
                            out.append(symbol.externalName.get());
                            out.append("(");
                            int argC = builtin.arguments().size();
                            boolean hadArg = false;
                            for(int argI = 0; argI < argC; argI += 1) {
                                TypeVariable argT = builtin.arguments()
                                    .get(argI);
                                if(!this.shouldEmitType(argT)) { continue; }
                                if(hadArg) { 
                                    out.append(", "); 
                                }
                                hadArg = true;
                                this.emitType(argT, out);
                                out.append(" arg_");
                                out.append(argI);
                            }
                            if(!hadArg) {
                                out.append("void");
                            }
                            out.append(");\n");
                        }
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
                        if(!hadArg) {
                            out.append("void");
                        }
                        out.append(");\n");
                    }
                } break;
            }
        }
    }


    private void emitSymbolImpls(StringBuilder out) {
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
                    false, Optional.empty(), variantData.argumentTypes().get(),
                    variantData.ir_body().get(), variantData.ir_context().get(),
                    out
                );
            }
        }
    }


    private void emitFunction(
        TypeVariable retType, String name, 
        boolean isBodyClosure, Optional<Long> closureId, 
        List<TypeVariable> argTypes, 
        List<Ir.Instr> body, Ir.Context context, StringBuilder out
    ) {
        this.enterContext(context);
        this.emitType(retType, out);
        out.append(" ");
        out.append(name);
        out.append("(");
        if(isBodyClosure) {
            out.append("GeraAllocation* closure_alloc");
        }
        boolean hadArg = isBodyClosure;
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
        if(!hadArg) {
            out.append("void");
        }
        out.append(") {\n");
        if(closureId.isPresent()) {
            out.append("GeraClosureCaptures");
            out.append(closureId.get());
            out.append("* captures = (GeraClosureCaptures");
            out.append(closureId.get());
            out.append("*) closure_alloc->data;\n");
        }
        if(this.shouldEmitType(retType)) {
            this.emitType(retType, out);
            out.append(" returned;\n");
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
                out.append("), ");
                DataType<TypeVariable> varVT = this.typeContext.get(varT);
                switch(varVT.type) {
                    case UNIT: case ANY: case NUMERIC: case INDEXED: 
                    case REFERENCED:
                    case BOOLEAN: case INTEGER: case FLOAT: out.append("NULL"); break;
                    case STRING: out.append("&gera_free_captured_string"); break;
                    case ARRAY: out.append("&gera_free_captured_array"); break;
                    case UNORDERED_OBJECT: out.append("&gera_free_captured_object"); break;
                    case UNION: out.append("&gera_free_captured_union"); break;
                    case CLOSURE: out.append("&gera_free_captured_closure"); break;
                }
                out.append(");\n");
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
        out.append("ret:\n");
        for(int varI = 0; varI < variableTypes.size(); varI += 1) {
            TypeVariable varT = variableTypes.get(varI);
            if(!this.shouldEmitType(varT)) { continue; }
            String capturedName = this.context().capturedNames.get(varI);
            if(capturedName != null) {
                out.append("gera___ref_deleted(captured_");
                out.append(capturedName);
                out.append(");\n");
            } else {
                this.emitRefDelete("local_" + varI, varT, out);
            }
        }
        if(this.shouldEmitType(retType)) {
            out.append("return returned;\n");
        } else {
            out.append("return;\n");
        }
        out.append("}\n");
        this.exitContext();
    }
    

    private void emitVariable(Ir.Variable v, StringBuilder out) {
        String capturedName = this.context().capturedNames.get(v.index);
        if(capturedName != null) {
            out.append("(*((");
            this.emitType(this.context().variableTypes.get(v.index), out);
            out.append("*) captured_");
            out.append(capturedName);
            out.append("->data))");
        } else {
            out.append("local_");
            out.append(v.index);
        }
    }


    private void emitVariant(Namespace path, int variant, StringBuilder out) {
        Symbols.Symbol symbol = this.symbols.get(path).get();
        this.emitPath(path, out);
        out.append("_");
        out.append(symbol.mappedVariantIdx(variant));
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
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value()? "1" : "0");
                out.append(";\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
            } break;
            case LOAD_INTEGER: {
                Ir.Instr.LoadInteger data = instr.getValue();
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append(";\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
            } break;
            case LOAD_FLOAT: {
                Ir.Instr.LoadFloat data = instr.getValue();
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                out.append(data.value());
                out.append(";\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
            } break;
            case LOAD_STRING: {
                Ir.Instr.LoadString data = instr.getValue();
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = gera___wrap_static_string(");
                this.emitStringLiteral(data.value(), out);
                out.append(");\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
            } break;
            case LOAD_OBJECT: {
                Ir.Instr.LoadObject data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.dest.get().index);
                out.append("{\n");
                out.append("GeraAllocation* a = gera___alloc(sizeof(");
                this.emitObjectLayoutName(objT.id, out);
                out.append("), &gera_");
                out.append(this.typeContext.substitutes.find(objT.id));
                out.append("_free);\n");
                this.emitObjectLayoutName(objT.id, out);
                out.append("* members = (");
                this.emitObjectLayoutName(objT.id, out);
                out.append("*) a->data;\n");
                for(int memI = 0; memI < data.memberNames().size(); memI += 1) {
                    TypeVariable memT = this.context().variableTypes
                        .get(instr.arguments.get(memI).index);
                    if(!this.shouldEmitType(memT)) { continue; }
                    this.emitVarSync(
                        "begin_read", instr.arguments.get(memI), out
                    );
                    this.emitRefCopy(instr.arguments.get(memI), out);
                    out.append("members->member_");
                    out.append(data.memberNames().get(memI));
                    out.append(" = ");
                    this.emitVariable(instr.arguments.get(memI), out);
                    out.append(";\n");
                    this.emitVarSync(
                        "end_read", instr.arguments.get(memI), out
                    );
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitRefDelete(instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = (GeraObject) { .allocation = a };\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case LOAD_FIXED_ARRAY: {
                TypeVariable arrT = this.context().variableTypes
                    .get(instr.dest.get().index);
                Optional<TypeVariable> itemT = Optional.empty();
                if(instr.arguments.size() > 0) {
                    itemT = Optional.of(
                        this.context().variableTypes
                            .get(instr.arguments.get(0).index)
                    );
                }
                out.append("{\n");
                out.append("GeraAllocation* a = gera___alloc(");
                if(itemT.isPresent() && this.shouldEmitType(itemT.get())) {
                    out.append("sizeof(");
                    this.emitType(itemT.get(), out);
                    out.append(") * ");
                    out.append(instr.arguments.size());
                    out.append(", &gera_");
                    out.append(this.typeContext.substitutes.find(arrT.id));
                    out.append("_free");
                } else {
                    out.append("0, NULL");
                }
                out.append(");\n");
                if(itemT.isPresent() && this.shouldEmitType(itemT.get())) {
                    this.emitType(itemT.get(), out);
                    out.append("* items = (");
                    this.emitType(itemT.get(), out);
                    out.append("*) a->data;\n");
                    for(
                        int valI = 0; valI < instr.arguments.size(); valI += 1
                    ) {
                        this.emitVarSync(
                            "begin_read", instr.arguments.get(valI), out
                        );
                        this.emitRefCopy(instr.arguments.get(valI), out);
                        out.append("items[");
                        out.append(valI);
                        out.append("] = ");
                        this.emitVariable(instr.arguments.get(valI), out);
                        out.append(";\n");
                        this.emitVarSync(
                            "end_read", instr.arguments.get(valI), out
                        );
                    }
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitRefDelete(instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = (GeraArray) { .allocation = a, .length = ");
                out.append(instr.arguments.size());
                out.append(" };\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case LOAD_REPEAT_ARRAY: {
                Ir.Instr.LoadRepeatArray data = instr.getValue();
                TypeVariable itemT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable arrT = this.context().variableTypes
                    .get(instr.dest.get().index);
                out.append("{\n");
                out.append("size_t length = gera___verify_size(");
                this.emitVariable(instr.arguments.get(1), out);
                out.append(", ");
                this.emitStringLiteral(data.source().file(), out);
                out.append(", ");
                out.append(data.source().computeLine(this.sourceFiles));
                out.append(");\n");
                out.append("GeraAllocation* a = gera___alloc(");
                if(this.shouldEmitType(itemT)) {
                    out.append("sizeof(");
                    this.emitType(itemT, out);
                    out.append(") * length, &gera_");
                    out.append(this.typeContext.substitutes.find(arrT.id));
                    out.append("_free");
                } else {
                    out.append("0, NULL");
                }
                out.append(");\n");
                if(this.shouldEmitType(itemT)) {
                    this.emitType(itemT, out);
                    out.append("* items = (");
                    this.emitType(itemT, out);
                    out.append("*) a->data;\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    out.append(
                        "for(size_t itemI = 0; itemI < length; itemI += 1) {\n"
                    );
                    this.emitRefCopy(instr.arguments.get(0), out);
                    out.append("items[itemI] = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    out.append("}\n");
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitRefDelete(instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(
                    " = (GeraArray) { .allocation = a, .length = length };\n"
                );
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case LOAD_VARIANT: {
                Ir.Instr.LoadVariant data = instr.getValue();
                TypeVariable unionT = this.context().variableTypes
                    .get(instr.dest.get().index);
                TypeVariable valueT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(!this.shouldEmitType(valueT)) {
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = (GeraUnion) { .allocation = NULL, .tag = ");
                    out.append(this.getVariantTagNumber(data.variantName()));
                    out.append(" };\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                } else {
                    out.append("{\n");
                    out.append("GeraAllocation* a = gera___alloc(");
                    out.append("sizeof(GeraUnionData)");
                    if(this.shouldEmitType(valueT)) {
                        out.append(" + sizeof(");
                        this.emitType(valueT, out);
                        out.append(")");
                    }
                    out.append(", &gera_");
                    out.append(this.typeContext.substitutes.find(unionT.id));
                    out.append("_free);\n");
                    out.append("GeraUnionData* data = (GeraUnionData*) a->data;\n");
                    out.append("data->tag = ");
                    out.append(this.getVariantTagNumber(data.variantName()));
                    out.append(";\n");
                    if(this.shouldEmitType(valueT)) {
                        this.emitVarSync("begin_read", instr.arguments.get(0), out);
                        this.emitRefCopy(instr.arguments.get(0), out);
                        out.append("*((");
                        this.emitType(valueT, out);
                        out.append("*) data->data) = ");
                        this.emitVariable(instr.arguments.get(0), out);
                        out.append(";\n");
                        this.emitVarSync("end_read", instr.arguments.get(0), out);
                    }
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = (GeraUnion) { .allocation = a, .tag = ");
                    out.append(this.getVariantTagNumber(data.variantName()));
                    out.append(" };\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case LOAD_CLOSURE: {
                Ir.Instr.LoadClosure data = instr.getValue();
                long closureId = this.closureBodyCount;
                this.closureBodyCount += 1;
                boolean hasCaptures = data.captureNames().size() > 0;
                StringBuilder captures = new StringBuilder();
                if(hasCaptures) {
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
                }
                String freeName = "gera_closure_" + closureId + "_free";
                StringBuilder free = new StringBuilder();
                if(hasCaptures) {
                    free.append("void ");
                    free.append(freeName);
                    free.append("(GeraAllocation* a) {\n");
                    free.append("    gera___begin_read(a);\n");
                    free.append("    GeraClosureCaptures");
                    free.append(closureId);
                    free.append("* captures = (GeraClosureCaptures");
                    free.append(closureId);
                    free.append("*) a->data;\n");
                    for(String captureName: data.captureNames()) {
                        free.append("    gera___ref_deleted(captures->");
                        free.append(captureName);
                        free.append(");\n");
                    }
                    free.append("    gera___end_read(a);\n");
                    free.append("}\n");
                }
                String bodyName = "gera_closure_" + closureId + "_body";
                StringBuilder body = new StringBuilder();
                this.emitFunction(
                    data.returnType(), bodyName,
                    true, hasCaptures? Optional.of(closureId) : Optional.empty(), 
                    data.argumentTypes(), data.body(), data.context(), body
                );
                this.closureBodies.append(captures);
                this.closureBodies.append(free);
                this.closureBodies.append(body);
                this.closureBodies.append("\n");
                out.append("{\n");
                out.append("GeraAllocation* a = gera___alloc(");
                if(hasCaptures) {
                    out.append("sizeof(GeraClosureCaptures");
                    out.append(closureId);
                    out.append(")");
                } else {
                    out.append("0");
                }
                out.append(", ");
                if(hasCaptures) {
                    out.append("&");
                    out.append(freeName);
                } else {
                    out.append("NULL");
                }
                out.append(");\n");
                if(hasCaptures) {
                    out.append("GeraClosureCaptures");
                    out.append(closureId);
                    out.append("* c = (GeraClosureCaptures");
                    out.append(closureId);
                    out.append("*) a->data;\n");
                    for(String captureName: data.captureNames()) {
                        StringBuilder captureValue = new StringBuilder();
                        boolean shouldEmit = true;
                        if(data.inheritedCaptures().contains(captureName)) {
                            captureValue.append("captures->");
                            captureValue.append(captureName);
                        } else {
                            captureValue.append("captured_");
                            captureValue.append(captureName);
                            for(
                                int varI = 0; 
                                varI < this.context().variableTypes.size(); 
                                varI += 1
                            ) {
                                String varCaptureName = this.context()
                                    .capturedNames.get(varI);
                                if(!captureName.equals(varCaptureName)) {
                                    continue;
                                }
                                TypeVariable valT = this.context()
                                    .variableTypes.get(varI);
                                shouldEmit = this.shouldEmitType(valT);
                                break;
                            }
                        }
                        if(shouldEmit) {
                            out.append("gera___ref_copied(");
                            out.append(captureValue);
                            out.append(");\n");
                            out.append("c->");
                            out.append(captureName);
                            out.append(" = ");
                            out.append(captureValue);
                            out.append(";\n");
                        }
                    }
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitRefDelete(instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = (GeraClosure) { .allocation = a, .body = &");
                out.append(bodyName);
                out.append(" };\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case LOAD_STATIC_VALUE: {
                Ir.Instr.LoadStaticValue data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    StringBuilder valStr = new StringBuilder();
                    this.emitValueRef(data.value(), valStr);
                    this.emitRefCopy(valStr.toString(), valT, out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    this.emitValueRef(data.value(), out);
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                }
            } break;
            case LOAD_EXT_VARIABLE: {
                Ir.Instr.LoadExtVariable data = instr.getValue();
                Symbols.Symbol symbol = this.symbols.get(data.path()).get();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    this.emitRefCopy(symbol.externalName.get(), valT, out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    out.append(symbol.externalName.get());
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                }
            } break;

            case READ_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable memT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(memT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    out.append("gera___begin_read(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitType(memT, out);
                    out.append(" value = ((");
                    this.emitObjectLayoutName(objT.id, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)->member_");
                    out.append(data.memberName());
                    out.append(";\n");
                    this.emitRefCopy("value", memT, out);
                    out.append("gera___end_read(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = value;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case WRITE_OBJECT: {
                Ir.Instr.ObjectAccess data = instr.getValue();
                TypeVariable objT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable memT = this.context().variableTypes
                    .get(instr.arguments.get(1).index);
                if(this.shouldEmitType(memT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitRefCopy(instr.arguments.get(1), out);
                    this.emitType(memT, out);
                    out.append(" value = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    out.append("gera___begin_write(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitType(memT, out);
                    out.append("* member = ");
                    out.append("&((");
                    this.emitObjectLayoutName(objT.id, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data)->member_");
                    out.append(data.memberName());
                    out.append(";\n");
                    this.emitRefDelete("(*member)", memT, out);
                    out.append("*member = value;\n");
                    out.append("gera___end_write(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    out.append("}\n");
                }
            } break;
            case READ_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                TypeVariable memT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(memT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    out.append("gera___begin_read(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitType(memT, out);
                    out.append(" value = ((");
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
                    this.emitRefCopy("value", memT, out);
                    out.append("gera___end_read(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = value;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case WRITE_ARRAY: {
                Ir.Instr.ArrayAccess data = instr.getValue();
                TypeVariable memT = this.context().variableTypes
                    .get(instr.arguments.get(2).index);
                if(this.shouldEmitType(memT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(2), out);
                    this.emitType(memT, out);
                    out.append(" value = ");
                    this.emitVariable(instr.arguments.get(2), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(2), out);
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    out.append("gera___begin_write(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitType(memT, out);
                    out.append("* element = ");
                    out.append("((");
                    this.emitType(memT, out);
                    out.append("*) ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation->data) + gera___verify_index(");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(", ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".length, ");
                    this.emitStringLiteral(data.source().file(), out);
                    out.append(", ");
                    out.append(data.source().computeLine(this.sourceFiles));
                    out.append(");\n");
                    this.emitRefDelete("(*element)", memT, out);
                    out.append("*element = value;\n");
                    out.append("gera___end_write(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(".allocation);\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    out.append("}\n");
                }
            } break;
            case READ_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    out.append("gera___begin_read(closure_alloc);\n");
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = *((");
                    this.emitType(valT, out);
                    out.append("*) captures->");
                    out.append(data.captureName());
                    out.append("->data);\n");
                    this.emitRefCopy(instr.dest.get(), out);
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("gera___end_read(closure_alloc);\n");
                }
            } break;
            case WRITE_CAPTURE: {
                Ir.Instr.CaptureAccess data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(valT)) {
                    out.append("{\n");
                    out.append("gera___begin_write(closure_alloc);\n");
                    this.emitType(valT, out);
                    out.append("* capture = ((");
                    this.emitType(valT, out);
                    out.append("*) captures->");
                    out.append(data.captureName());
                    out.append("->data);\n");
                    this.emitRefDelete("(*capture)", valT, out);
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitRefCopy(instr.arguments.get(0), out);
                    out.append("*capture = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    out.append("gera___end_write(closure_alloc);\n");
                    out.append("}\n");    
                }
            } break;

            case COPY: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitRefCopy(instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" value = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = value;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;

            case ADD: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(valT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    if(isInteger) {
                        out.append("(gint) (((guint) a) + ((guint) b))");
                    } else {
                        out.append("a + b");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case SUBTRACT: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(valT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    if(isInteger) {
                        out.append("(gint) (((guint) a) - ((guint) b))");
                    } else {
                        out.append("a - b");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case MULTIPLY:  {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(valT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    if(isInteger) {
                        out.append("(gint) (((guint) a) * ((guint) b))");
                    } else {
                        out.append("a * b");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case DIVIDE: {
                Ir.Instr.Division data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(valT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    if(isInteger) {
                        out.append("(gint) (((guint) a) ");
                        out.append("/ ((guint) gera___verify_integer_divisor(b, ");
                        this.emitStringLiteral(data.source().file(), out);
                        out.append(", ");
                        out.append(data.source().computeLine(this.sourceFiles));
                        out.append(")))");
                    } else {
                        out.append("a / b");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case MODULO: {
                Ir.Instr.Division data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(valT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = ");
                    if(isInteger) {
                        out.append("(gint) (((guint) a) ");
                        out.append("% ((guint) gera___verify_integer_divisor(b, ");
                        this.emitStringLiteral(data.source().file(), out);
                        out.append(", ");
                        out.append(data.source().computeLine(this.sourceFiles));
                        out.append(")))");
                    } else {
                        out.append("gera___float_mod(a, b)");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case NEGATE: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    boolean isInteger = this.typeContext.get(valT)
                        .type == DataType.Type.INTEGER;
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" result = ");
                    if(isInteger) {
                        out.append("(gint) (- (guint) (");
                        this.emitVariable(instr.arguments.get(0), out);
                        out.append("))");
                    } else {
                        out.append("- (");
                        this.emitVariable(instr.arguments.get(0), out);
                        out.append(")");
                    }
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = result;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case LESS_THAN: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(compT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = a < b;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case GREATER_THAN: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(compT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = a > b;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case LESS_THAN_EQUAL: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(compT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = a <= b;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case GREATER_THAN_EQUAL: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(this.shouldEmitType(compT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = a >= b;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;
            case EQUALS: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                out.append("{\n");
                if(this.shouldEmitType(compT)) {
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = ");
                this.emitEquality("a", "b", compT, out);
                out.append(";\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case NOT_EQUALS: {
                TypeVariable compT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                out.append("{\n");
                if(this.shouldEmitType(compT)) {
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(compT, out);
                    out.append(" a = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_read", instr.arguments.get(1), out);
                    this.emitType(compT, out);
                    out.append(" b = ");
                    this.emitVariable(instr.arguments.get(1), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(1), out);
                }
                this.emitVarSync("begin_write", instr.dest.get(), out);
                this.emitVariable(instr.dest.get(), out);
                out.append(" = !(");
                this.emitEquality("a", "b", compT, out);
                out.append(");\n");
                this.emitVarSync("end_write", instr.dest.get(), out);
                out.append("}\n");
            } break;
            case NOT: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.dest.get().index);
                if(this.shouldEmitType(valT)) {
                    out.append("{\n");
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" result = !(");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(");\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = result;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                    out.append("}\n");
                }
            } break;

            case BRANCH_ON_VALUE: {
                Ir.Instr.BranchOnValue data = instr.getValue();
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                DataType<TypeVariable> valVT = this.typeContext.get(valT);
                int brC = data.branchBodies().size();
                out.append("{\n");
                if(this.shouldEmitType(valT)) {
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitType(valT, out);
                    out.append(" matched = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                }
                if(valVT.type == DataType.Type.INTEGER) {
                    out.append("switch(matched) {\n");
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
                    for(int brI = 0; brI < brC; brI += 1) {
                        StringBuilder bV = new StringBuilder();
                        this.emitValueRef(data.branchValues().get(brI), bV);
                        if(brI > 0) {
                            out.append(" else ");
                        }
                        out.append("if(");
                        this.emitEquality(
                            "matched", bV.toString(), valT, out
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
                out.append("}\n");
            } break;
            case BRANCH_ON_VARIANT: {
                Ir.Instr.BranchOnVariant data = instr.getValue();
                TypeVariable matchedT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                out.append("{\n");
                this.emitVarSync("begin_read", instr.arguments.get(0), out);
                this.emitType(matchedT, out);
                out.append(" matched = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", instr.arguments.get(0), out);
                out.append("switch(matched.tag) {\n");
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
                        if(this.shouldEmitType(valT)) {
                            out.append("{\n");
                            out.append(
                                "GeraUnionData* matchedData = "
                                    + "(GeraUnionData*) matched.allocation->data;\n"
                            );
                            this.emitVarSync("begin_write", bVar.get(), out);
                            this.emitRefDelete(bVar.get(), out);
                            this.emitVariable(bVar.get(), out);
                            out.append(" = *((");
                            this.emitType(valT, out);
                            out.append("*) matchedData->data);\n");
                            this.emitRefCopy(bVar.get(), out);
                            this.emitVarSync("end_write", bVar.get(), out);
                            out.append("}\n");
                        }
                    }
                    this.emitInstructions(data.branchBodies().get(brI), out);
                    out.append("break;\n");
                }
                out.append("default:\n");
                this.emitInstructions(data.elseBody(), out);
                out.append("}\n");
                out.append("}\n");
            } break;

            case CALL_PROCEDURE: {
                Ir.Instr.CallProcedure data = instr.getValue();
                Symbols.Symbol symbol = this.symbols.get(data.path()).get();
                boolean isExternal = symbol.externalName.isPresent();
                boolean hasBody = symbol.<Symbols.Symbol.Procedure>getValue()
                    .body().isPresent();
                if(isExternal || hasBody) {
                    TypeVariable retT = this.context().variableTypes
                        .get(instr.dest.get().index);
                    out.append("{\n");
                    for(
                        int argI = 0; argI < instr.arguments.size(); argI += 1
                    ) {
                        TypeVariable argT = this.context().variableTypes
                            .get(instr.arguments.get(argI).index);
                        if(!this.shouldEmitType(argT)) { continue; }
                        this.emitVarSync(
                            "begin_read", instr.arguments.get(argI), out
                        );
                        this.emitRefCopy(instr.arguments.get(argI), out);
                        this.emitType(argT, out);
                        out.append(" call_arg_");
                        out.append(argI);
                        out.append(" = ");
                        this.emitVariable(instr.arguments.get(argI), out);
                        out.append(";\n");
                        this.emitVarSync(
                            "end_read", instr.arguments.get(argI), out
                        );
                    }
                    if(this.shouldEmitType(retT)) {
                        this.emitType(retT, out);
                        out.append(" call_ret = ");
                    }
                    if(isExternal) {
                        out.append(symbol.externalName.get());
                    } else {
                        this.emitVariant(data.path(), data.variant(), out);
                    }
                    out.append("(");
                    boolean hadArg = false;
                    for(
                        int argI = 0; argI < instr.arguments.size(); argI += 1
                    ) {
                        TypeVariable argT = this.context().variableTypes
                            .get(instr.arguments.get(argI).index);
                        if(!this.shouldEmitType(argT)) { continue; }
                        if(hadArg) {
                            out.append(", ");
                        }
                        hadArg = true;
                        out.append("call_arg_");
                        out.append(argI);
                    }
                    out.append(");\n");
                    if(this.shouldEmitType(retT)) {
                        this.emitVarSync("begin_write", instr.dest.get(), out);
                        this.emitRefDelete(instr.dest.get(), out);
                        this.emitVariable(instr.dest.get(), out);
                        out.append(" = call_ret;\n");
                        this.emitVarSync("end_write", instr.dest.get(), out);
                    }
                    out.append("}\n");
                } else {
                    this.builtIns.get(data.path()).emit(
                        this.typeContext, instr.arguments,
                        instr.arguments.stream()
                            .map(a -> this.context().variableTypes.get(a.index))
                            .toList(), 
                        instr.dest.get(), 
                        out
                    );
                }
            } break;
            case CALL_CLOSURE: {
                TypeVariable closureT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                TypeVariable retT = this.context().variableTypes
                    .get(instr.dest.get().index);
                out.append("{\n");
                this.emitVarSync("begin_read", instr.arguments.get(0), out);
                this.emitType(closureT, out);
                out.append(" called = ");
                this.emitVariable(instr.arguments.get(0), out);
                out.append(";\n");
                this.emitVarSync("end_read", instr.arguments.get(0), out);
                int argC = instr.arguments.size() - 1;
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI + 1).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    this.emitVarSync(
                        "begin_read", instr.arguments.get(argI + 1), out
                    );
                    this.emitRefCopy(instr.arguments.get(argI + 1), out);
                    this.emitType(argT, out);
                    out.append(" call_arg_");
                    out.append(argI);
                    out.append(" = ");
                    this.emitVariable(instr.arguments.get(argI + 1), out);
                    out.append(";\n");
                    this.emitVarSync(
                        "end_read", instr.arguments.get(argI + 1), out
                    );
                }
                if(this.shouldEmitType(retT)) {
                    this.emitType(retT, out);
                    out.append(" call_ret = ");
                }
                out.append("((");
                this.emitType(retT, out);
                out.append(" (*)(GeraAllocation*");
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI + 1).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    out.append(", ");
                    this.emitType(argT, out);
                }
                out.append("))(called.body))(called.allocation");
                for(int argI = 0; argI < argC; argI += 1) {
                    TypeVariable argT = this.context().variableTypes
                        .get(instr.arguments.get(argI + 1).index);
                    if(!this.shouldEmitType(argT)) { continue; }
                    out.append(", call_arg_");
                    out.append(argI);
                }
                out.append(");\n");
                if(this.shouldEmitType(retT)) {
                    this.emitVarSync("begin_write", instr.dest.get(), out);
                    this.emitRefDelete(instr.dest.get(), out);
                    this.emitVariable(instr.dest.get(), out);
                    out.append(" = call_ret;\n");
                    this.emitVarSync("end_write", instr.dest.get(), out);
                }
                out.append("}\n");
            } break;
            case RETURN: {
                TypeVariable valT = this.context().variableTypes
                    .get(instr.arguments.get(0).index);
                if(shouldEmitType(valT)) {
                    this.emitVarSync("begin_read", instr.arguments.get(0), out);
                    this.emitRefCopy(instr.arguments.get(0), out);
                    out.append("returned = ");
                    this.emitVariable(instr.arguments.get(0), out);
                    out.append(";\n");
                    this.emitVarSync("end_read", instr.arguments.get(0), out);
                }
                out.append("goto ret;\n");
            } break;

            case LOAD_UNIT:
            case PHI: {
                // do nothing
            } break;
        }
    }


    private void emitVarSync(String op, Ir.Variable var, StringBuilder out) {
        String capturedName = this.context().capturedNames.get(var.index);
        if(capturedName != null) {
            out.append("gera___");
            out.append(op);
            out.append("(captured_");
            out.append(capturedName);
            out.append(");\n");
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


    private void emitRefCopy(Ir.Variable v, StringBuilder out) {
        StringBuilder var = new StringBuilder();
        this.emitVariable(v, var);
        this.emitStackRefUpdate(
            "gera___ref_copied", var.toString(), 
            this.context().variableTypes.get(v.index), out
        );
    }

    private void emitRefCopy(String val, TypeVariable t, StringBuilder out) {
        this.emitStackRefUpdate("gera___ref_copied", val, t, out);
    }

    private void emitRefDelete(Ir.Variable v, StringBuilder out) {
        StringBuilder var = new StringBuilder();
        this.emitVariable(v, var);
        this.emitStackRefUpdate(
            "gera___ref_deleted", var.toString(), 
            this.context().variableTypes.get(v.index), out
        );
    }

    private void emitRefDelete(String val, TypeVariable t, StringBuilder out) {
        this.emitStackRefUpdate("gera___ref_deleted", val, t, out);
    }

    private void emitStackRefUpdate(
        String fn, String val, TypeVariable t, StringBuilder out
    ) {
        DataType<TypeVariable> tval = this.typeContext.get(t);
        switch(tval.type) {
            case UNIT: case ANY: case NUMERIC: case INDEXED: 
            case REFERENCED:
            case BOOLEAN: case INTEGER: case FLOAT:
                return;
            case STRING: case ARRAY: case UNORDERED_OBJECT: 
            case UNION: case CLOSURE:
                out.append(fn);    
                out.append("(");
                out.append(val);
                out.append(".allocation);\n");
                return;
        }
    }
    
}
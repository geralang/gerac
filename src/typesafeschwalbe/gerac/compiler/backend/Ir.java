package typesafeschwalbe.gerac.compiler.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class Ir {
    
    public static class StaticValues {

        public final List<Value> values;

        public StaticValues() {
            this.values = new ArrayList<>();
        }

        public StaticValue add(Value value) {
            int index = this.values.size();
            this.values.add(value);
            return new StaticValue(index);
        }

    }

    public static class StaticValue {

        public final int index;

        private StaticValue(int index) {
            this.index = index;
        }

    }

    public static class Context {

        private final List<DataType> variableTypes;
        private final List<Integer> variableVersions;

        public Context() {
            this.variableTypes = new ArrayList<>();
            this.variableVersions = new ArrayList<>();
        }

        public Variable allocate(DataType variableType) {
            int index = this.variableTypes.size();
            int version = 0;
            this.variableTypes.add(variableType);
            this.variableVersions.add(version);
            return new Variable(index, version);
        }

    }

    public static class Variable {
        
        public final int index;
        public final int version;

        private Variable(int index, int version) {
            this.index = index;
            this.version = version;
        }

    }

    public static class Instr {

        public static record LoadBoolean(boolean value) {}
        public static record LoadInteger(long value) {}
        public static record LoadFloat(double value) {}
        public static record LoadString(double value) {}
        public static record LoadObject(List<String> memberNames) {}
        public static record LoadVariant(String variantName) {}
        public static record LoadClosure(
            // values = capture values
            List<DataType> argumentTypes, DataType returnType,
            List<String> captureNames,
            Context context, List<Instr> body
        ) {}
        public static record LoadStaticValue(StaticValue value) {}

        public static record ObjectAccess(String memberName) {}
        public static record CaptureAccess(String captureName) {}

        public static record BranchOnValue(
            List<StaticValue> branchValues,
            List<List<Instr>> branchBodies,
            List<Instr> elseBody
        ) {}
        public static record BranchOnVariant(
            List<String> branchVariants,
            List<Optional<Variable>> branchVariables,
            List<List<Instr>> branchBodies,
            List<Instr> elseBody
        ) {}

        public static record CallProcedure(Namespace path) {}

        public enum Type {
            LOAD_UNIT,          // = null
            LOAD_BOOLEAN,       // LoadBoolean
            LOAD_INTEGER,       // LoadInteger
            LOAD_FLOAT,         // LoadFloat
            LOAD_STRING,        // LoadString
            LOAD_OBJECT,        // LoadObject
            LOAD_ARRAY,         // = null
            LOAD_VARIANT,       // LoadVariant
            LOAD_CLOSURE,       // LoadClosure
            LOAD_STATIC_VALUE,  // LoadStaticValue

            READ_OBJECT,        // ObjectAccess
            WRITE_OBJECT,       // ObjectAccess
            READ_ARRAY,         // = null
            WRITE_ARRAY,        // = null
            READ_CAPTURE,       // CaptureAccess
            WRITE_CAPTURE,      // CaptureAccess
            
            COPY,               // = null
            
            ADD,                // = null
            SUBTRACT,           // = null
            MULTIPLY,           // = null
            DIVIDE,             // = null
            MODULO,             // = null
            NEGATE,             // = null
            LESS_THAN,          // = null
            GREATER_THAN,       // = null
            LESS_THAN_EQUAL,    // = null
            GREATER_THAN_EQUAL, // = null
            EQUALS,             // = null
            NOT_EQUALS,         // = null
            NOT,                // = null
            
            BRANCH_ON_VALUE,    // = BranchOnValue
            BRANCH_ON_VARIANT,  // = BranchOnVariant
            
            CALL_PROCEDURE,     // CallProcedure
            CALL_CLOSURE,       // = null
            RETURN,             // = null
            
            PHI                 // = null
        }

        public final Type type;
        public final List<Variable> arguments;
        private final Object value;
        public final Optional<Variable> dest;

        public Instr(
            Type type, List<Variable> arguments, Object value,
            Optional<Variable> dest
        ) {
            this.type = type;
            this.arguments = arguments;
            this.value = value;
            this.dest = dest;
        }

        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) this.value;
        }

    }

}

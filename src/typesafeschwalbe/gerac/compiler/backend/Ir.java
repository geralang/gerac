package typesafeschwalbe.gerac.compiler.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class Ir {
    
    public static class StaticValues {

        public final List<Value> values;
        public final Map<Value, Integer> valueIndices;

        public StaticValues() {
            this.values = new ArrayList<>();
            this.valueIndices = new HashMap<>();
        }

        public StaticValue add(Value value) {
            Integer existingIndex = this.valueIndices.get(value);
            if(existingIndex != null) {
                return new StaticValue(existingIndex);
            }
            int index = this.values.size();
            this.values.add(value);
            this.valueIndices.put(value, index);
            return new StaticValue(index);
        }

    }

    public static class StaticValue {

        public final int index;

        private StaticValue(int index) {
            this.index = index;
        }

        @Override
        public String toString() {
            return "{value}";
        }

    }

    public static class Context {

        private final List<Variable> argumentVars;
        private final List<DataType> variableTypes;
        private final Set<Integer> captured;

        public Context() {
            this.argumentVars = new ArrayList<>();
            this.variableTypes = new ArrayList<>();
            this.captured = new HashSet<>();
        }

        public Variable allocate(DataType variableType) {
            int index = this.variableTypes.size();
            int version = 0;
            this.variableTypes.add(variableType);
            return new Variable(index, version);
        }

        public Variable allocateArgument(DataType argumentType) {
            Variable variable = this.allocate(argumentType);
            this.argumentVars.add(variable);
            return variable;
        }

        public void markCaptured(Variable variable) {
            this.captured.add(variable.index);
        } 

    }

    public static class Variable {
        
        public final int index;
        public int version;

        public Variable(int index, int version) {
            this.index = index;
            this.version = version;
        }

        @Override
        public Variable clone() {
            return new Variable(this.index, this.version);
        }

        @Override
        public String toString() {
            return String.valueOf(this.index)
                + "<" + String.valueOf(this.version) + ">";
        }

    }

    public static class Instr {

        public static record LoadBoolean(boolean value) {}
        public static record LoadInteger(long value) {}
        public static record LoadFloat(double value) {}
        public static record LoadString(String value) {}
        public static record LoadObject(List<String> memberNames) {}
        public static record LoadVariant(String variantName) {}
        public static record LoadClosure(
            // values = capture values
            List<DataType> argumentTypes, DataType returnType,
            List<String> captureNames,
            Context context, List<Instr> body
        ) {}
        public static record LoadStaticValue(StaticValue value) {}
        public static record LoadExtVariable(Namespace path) {}

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

        public static record CallProcedure(Namespace path, int variant) {}

        public enum Type {
            LOAD_UNIT,          // = null            | [] -> res
            LOAD_BOOLEAN,       // LoadBoolean       | [] -> res 
            LOAD_INTEGER,       // LoadInteger       | [] -> res
            LOAD_FLOAT,         // LoadFloat         | [] -> res
            LOAD_STRING,        // LoadString        | [] -> res
            LOAD_OBJECT,        // LoadObject        | [values...] -> res
            LOAD_FIXED_ARRAY,   // = null            | [values...] -> res
            LOAD_REPEAT_ARRAY,  // = null            | [value, size] -> res
            LOAD_VARIANT,       // LoadVariant       | [value] -> res
            LOAD_CLOSURE,       // LoadClosure       | [capture_vals...] -> res
            LOAD_EMPTY_CLOSURE, // = null            | [] -> res
            LOAD_STATIC_VALUE,  // LoadStaticValue   | [] -> res
            LOAD_EXT_VARIABLE,  // LoadExtVariable   | [] -> res

            READ_OBJECT,        // ObjectAccess      | [accessed] -> res
            WRITE_OBJECT,       // ObjectAccess      | [accessed, value]
            READ_ARRAY,         // = null            | [accessed, index] -> res
            WRITE_ARRAY,        // = null            | [accessed, index, value]
            READ_CAPTURE,       // CaptureAccess     | [] -> res
            WRITE_CAPTURE,      // CaptureAccess     | [value]
            
            COPY,               // = null            | [dest, value]
            
            ADD,                // = null            | [a, b] -> res
            SUBTRACT,           // = null            | [a, b] -> res
            MULTIPLY,           // = null            | [a, b] -> res
            DIVIDE,             // = null            | [a, b] -> res
            MODULO,             // = null            | [a, b] -> res
            NEGATE,             // = null            | [v] -> res
            LESS_THAN,          // = null            | [a, b] -> res
            GREATER_THAN,       // = null            | [a, b] -> res
            LESS_THAN_EQUAL,    // = null            | [a, b] -> res
            GREATER_THAN_EQUAL, // = null            | [a, b] -> res
            EQUALS,             // = null            | [a, b] -> res
            NOT_EQUALS,         // = null            | [a, b] -> res
            NOT,                // = null            | [v] -> res
            
            BRANCH_ON_VALUE,    // = BranchOnValue   | [v]
            BRANCH_ON_VARIANT,  // = BranchOnVariant | [v]
            
            CALL_PROCEDURE,     // CallProcedure     | [args...] -> res
            CALL_CLOSURE,       // = null            | [called, args...] -> res
            RETURN,             // = null            | [v]
            
            PHI                 // = null            | [options...] -> res
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

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();
            if(this.value != null) {
                out.append(this.value);
            } else {
                out.append(this.type);
            }
            out.append("(");
            out.append(String.join(
                ", ",
                this.arguments.stream()
                    .map(Variable::toString).toArray(String[]::new)
            ));
            out.append(")");
            if(this.dest.isPresent()) {
                out.append(" -> ");
                out.append(this.dest.get());
            }
            return out.toString();
        }

    }

}

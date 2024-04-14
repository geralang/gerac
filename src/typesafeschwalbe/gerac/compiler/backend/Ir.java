package typesafeschwalbe.gerac.compiler.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.ErrorException;
import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.frontend.DataType;
import typesafeschwalbe.gerac.compiler.frontend.Namespace;

public class Ir {
    
    public static class StaticValues {

        private final Lowerer lowerer;
        public final List<StaticValue> values;
        public final Map<StaticValue, Integer> valueIndices;

        public StaticValues(Lowerer lowerer) {
            this.lowerer = lowerer;
            this.values = new ArrayList<>();
            this.valueIndices = new HashMap<>();
        }

        public StaticValue add(Value value) throws ErrorException {
            StaticValue staticValue = this.asStatic(value);
            Integer existingIndex = this.valueIndices.get(staticValue);
            if(existingIndex != null) {
                return this.values.get(existingIndex);
            }
            int index = this.values.size();
            this.values.add(staticValue);
            this.valueIndices.put(staticValue, index);
            return staticValue;
        }

        public int getIndexOf(StaticValue value) {
            return this.valueIndices.get(value);
        }

        private StaticValue asStatic(Value v) throws ErrorException {
            if(v instanceof Value.Unit) {
                return StaticValue.UNIT;
            } else if(v instanceof Value.Bool) {
                return new StaticValue.Bool(
                    v.<Value.Bool>getValue().value
                );
            } else if(v instanceof Value.Int) {
                return new StaticValue.Int(
                    v.<Value.Int>getValue().value
                );
            } else if(v instanceof Value.Float) {
                return new StaticValue.Float(
                    v.<Value.Float>getValue().value
                );
            } else if(v instanceof Value.Str) {
                return new StaticValue.Str(
                    v.<Value.Str>getValue().value
                );
            } else if(v instanceof Value.Arr) {
                List<StaticValue> elements = new ArrayList<>();
                for(Value e: v.<Value.Arr>getValue().value) {
                    elements.add(this.add(e));
                }
                return new StaticValue.Arr(v, elements);
            } else if(v instanceof Value.Obj) {
                Value.Obj data = v.getValue();
                Map<String, StaticValue> members = new HashMap<>();
                for(String memberName: data.value.keySet()) {
                    members.put(
                        memberName, this.add(data.value.get(memberName))
                    );
                }
                return new StaticValue.Obj(v, members);
            } else if(v instanceof Value.Closure) {
                return this.lowerer.lowerClosureValue(v.getValue());
            } else if(v instanceof Value.Union) {
                return new StaticValue.Union(
                    v.<Value.Union>getValue().variant,
                    this.add(v.<Value.Union>getValue().value)
                );
            }
            throw new RuntimeException("unhandled value type!");
        }

    }

    public static class StaticValue {

        private StaticValue() {}

        @SuppressWarnings("unchecked")
        public <T extends StaticValue> T getValue() {
            try {
                return (T) this;
            } catch(ClassCastException e) {
                throw new RuntimeException("Value has invalid type!");
            }
        }
    
        public static class Unit extends StaticValue {
            private Unit() {}
    
            @Override
            public boolean equals(Object otherRaw) {
                return otherRaw instanceof Unit;
            }
    
            @Override
            public int hashCode() {
                return 0;
            }
        }
        public static final Unit UNIT = new Unit();
    
        public static class Bool extends StaticValue {
            public final boolean value;
    
            public Bool(boolean value) {
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Bool)) { return false; }
                Bool other = (Bool) otherRaw;
                return this.value == other.value;
            }
    
            @Override
            public int hashCode() {
                return Boolean.hashCode(this.value);
            }
        }
    
        public static class Int extends StaticValue {
            public final long value;
    
            public Int(long value) {
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Int)) { return false; }
                Int other = (Int) otherRaw;
                return this.value == other.value;
            }
    
            @Override
            public int hashCode() {
                return Long.hashCode(this.value);
            }
        }
    
        public static class Float extends StaticValue {
            public final double value;
    
            public Float(double value) {
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Float)) { return false; }
                Float other = (Float) otherRaw;
                return this.value == other.value;
            }
    
            @Override
            public int hashCode() {
                return Double.hashCode(this.value);
            }
        }
    
        public static class Str extends StaticValue {
            public final String value;
    
            public Str(String value) {
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Str)) { return false; }
                Str other = (Str) otherRaw;
                return this.value.equals(other.value);
            }
    
            @Override
            public int hashCode() {
                return this.value.hashCode();
            }
        }
    
        public static class Arr extends StaticValue {
            private final Object ptr;
            public final List<StaticValue> value;
    
            public Arr(Object ptr, List<StaticValue> value) {
                this.ptr = ptr;
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Arr)) { return false; }
                Arr other = (Arr) otherRaw;
                return this.ptr == other.ptr;
            }

            @Override
            public int hashCode() {
                return this.ptr.hashCode();
            }
        }
    
        public static class Obj extends StaticValue {
            private final Object ptr;
            public final Map<String, StaticValue> value;
    
            public Obj(Object ptr, Map<String, StaticValue> value) {
                this.ptr = ptr;
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Obj)) { return false; }
                Obj other = (Obj) otherRaw;
                return this.ptr == other.ptr;
            }

            @Override
            public int hashCode() {
                return this.ptr.hashCode();
            }
        }
    
        public static class Closure extends StaticValue {
            private final Object ptr;
            public final boolean isEmpty;
            public final Map<String, Ir.StaticValue> captureValues;
            public final List<DataType> argumentTypes;
            public final Ir.Context context;
            public final List<Ir.Instr> body;
    
            public Closure(
                Object ptr,
                boolean isEmpty,
                Map<String, Ir.StaticValue> captureValues,
                List<DataType> argumentTypes,
                Ir.Context context,
                List<Ir.Instr> body
            ) {
                this.ptr = ptr;
                this.isEmpty = isEmpty;
                this.captureValues = captureValues;
                this.argumentTypes = argumentTypes;
                this.context = context;
                this.body = body;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Closure)) { return false; }
                Closure other = (Closure) otherRaw;
                return this.ptr == other.ptr;
            }

            @Override
            public int hashCode() {
                return this.ptr.hashCode();
            }
        }
    
        public static class Union extends StaticValue {
            public final String variant;
            public final StaticValue value;
    
            public Union(String variant, StaticValue value) {
                this.variant = variant;
                this.value = value;
            }
    
            @Override
            public boolean equals(Object otherRaw) {
                if(!(otherRaw instanceof Union)) { return false; }
                Union other = (Union) otherRaw;
                return this.variant.equals(other.variant)
                    && this.value.equals(other.value);
            }
    
            @Override
            public int hashCode() {
                int hash = 7;
                hash = 29 * hash + this.variant.hashCode();
                hash = 29 * hash + this.value.hashCode();
                return hash;
            }
        }

    }

    public static class Context {

        public final List<Variable> argumentVars;
        public final List<DataType> variableTypes;
        public final Map<Integer, String> capturedNames;

        public Context() {
            this.argumentVars = new ArrayList<>();
            this.variableTypes = new ArrayList<>();
            this.capturedNames = new HashMap<>();
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

        public void markCaptured(Variable variable, String captureName) {
            this.capturedNames.put(variable.index, captureName);
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
        public static record LoadRepeatArray(Source source) {}
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
        public static record ArrayAccess(Source source) {}
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

        public static record CallProcedure(
            Namespace path, int variant, Source source
        ) {}
        public static record CallClosure(
            Source source
        ) {}

        public enum Type {
            LOAD_UNIT,          // = null            | [] -> res
            LOAD_BOOLEAN,       // LoadBoolean       | [] -> res 
            LOAD_INTEGER,       // LoadInteger       | [] -> res
            LOAD_FLOAT,         // LoadFloat         | [] -> res
            LOAD_STRING,        // LoadString        | [] -> res
            LOAD_OBJECT,        // LoadObject        | [values...] -> res
            LOAD_FIXED_ARRAY,   // = null            | [values...] -> res
            LOAD_REPEAT_ARRAY,  // LoadRepeatArray   | [value, size] -> res
            LOAD_VARIANT,       // LoadVariant       | [value] -> res
            LOAD_CLOSURE,       // LoadClosure       | [capture_vals...] -> res
            LOAD_EMPTY_CLOSURE, // = null            | [] -> res
            LOAD_STATIC_VALUE,  // LoadStaticValue   | [] -> res
            LOAD_EXT_VARIABLE,  // LoadExtVariable   | [] -> res

            READ_OBJECT,        // ObjectAccess      | [accessed] -> res
            WRITE_OBJECT,       // ObjectAccess      | [accessed, value]
            READ_ARRAY,         // ArrayAccess       | [accessed, index] -> res
            WRITE_ARRAY,        // ArrayAccess       | [accessed, index, value]
            READ_CAPTURE,       // CaptureAccess     | [] -> res
            WRITE_CAPTURE,      // CaptureAccess     | [value]
            
            COPY,               // = null            | [value] -> dest
            
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

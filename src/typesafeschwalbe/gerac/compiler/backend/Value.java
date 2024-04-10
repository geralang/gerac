
package typesafeschwalbe.gerac.compiler.backend;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import typesafeschwalbe.gerac.compiler.frontend.AstNode;

public abstract class Value {

    private Value() {}

    @SuppressWarnings("unchecked")
    public <T extends Value> T getValue() {
        try {
            return (T) this;
        } catch(ClassCastException e) {
            throw new RuntimeException("Value has invalid type!");
        }
    }

    public static class Unit extends Value {
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

    public static class Bool extends Value {
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

    public static class Int extends Value {
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

    public static class Float extends Value {
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

    public static class Str extends Value {
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

    public static class Arr extends Value {
        public final List<Value> value;

        public Arr(List<Value> value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object otherRaw) {
            if(!(otherRaw instanceof Arr)) { return false; }
            Arr other = (Arr) otherRaw;
            return this.value.equals(other.value);
        }
    }

    public static class Obj extends Value {
        public final Map<String, Value> value;

        public Obj(Map<String, Value> value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object otherRaw) {
            if(!(otherRaw instanceof Obj)) { return false; }
            Obj other = (Obj) otherRaw;
            return this.value.equals(other.value);
        }
    }

    public static class Closure extends Value {
        public final List<Map<String, Optional<Value>>> environment;
        public final List<String> argumentNames;
        public final List<AstNode> body;

        public Closure(
            List<Map<String, Optional<Value>>> environment,
            List<String> argumentNames,
            List<AstNode> body
        ) {
            this.environment = environment;
            this.argumentNames = argumentNames;
            this.body = body;
        }

        @Override
        public boolean equals(Object otherRaw) {
            return this == otherRaw;
        }
    }

    public static class Union extends Value {
        public final String variant;
        public final Value value;

        public Union(String variant, Value value) {
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


package typesafeschwalbe.gerac.compiler;

import java.util.List;

public class Result<T> {

    private final T value;
    private final List<Error> errors;

    private Result(T value, List<Error> errors) {
        this.value = value;
        this.errors = errors;
    }

    public static <T> Result<T> ofValue(T value) {
        return new Result<T>(value, null);
    }

    public static <T> Result<T> ofError(Error... errors) {
        return new Result<T>(null, List.of(errors));
    }

    public static <T> Result<T> ofError(List<Error> errors) {
        return new Result<T>(null, errors);
    }

    public boolean isValue() {
        return this.value != null;
    }

    public T getValue() {
        if(this.value == null) {
            throw new IllegalAccessError(
                "Attempted to get the value of a 'Result' without any value!"
            );
        }
        return this.value;
    }

    public boolean isError() {
        return this.errors != null;
    }

    public List<Error> getError() {
        if(this.errors == null) {
            throw new IllegalAccessError(
                "Attempted to get the errors of a 'Result' without any errors!"
            );
        }
        return this.errors;
    }

}
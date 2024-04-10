
package typesafeschwalbe.gerac.compiler;

public class ErrorException extends Exception {
    
    public final Error error;

    public ErrorException(Error error) {
        this.error = error;
    }

}

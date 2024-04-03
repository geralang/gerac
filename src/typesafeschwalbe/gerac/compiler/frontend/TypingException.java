
package typesafeschwalbe.gerac.compiler.frontend;

import typesafeschwalbe.gerac.compiler.Error;

public class TypingException extends Exception {
    
    public final Error error;

    public TypingException(Error error) {
        this.error = error;
    }

}

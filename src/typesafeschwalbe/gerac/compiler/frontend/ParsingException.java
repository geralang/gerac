
package typesafeschwalbe.gerac.compiler.frontend;

import typesafeschwalbe.gerac.compiler.Error;

public class ParsingException extends Exception {
    
    public final Error error;

    ParsingException(Error error) {
        this.error = error;
    }

}

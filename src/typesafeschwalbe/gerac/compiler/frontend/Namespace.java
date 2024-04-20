
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;

public record Namespace(List<String> elements) {

    @Override
    public String toString() {
        return String.join("::", this.elements);
    }

}

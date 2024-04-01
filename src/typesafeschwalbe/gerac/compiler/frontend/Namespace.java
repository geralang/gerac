
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.List;

public record Namespace(List<String> elements) {

    @Override
    public int hashCode() {
        return this.elements.hashCode();
    }

    @Override
    public String toString() {
        return String.join("::", this.elements);
    }

    @Override
    public boolean equals(Object otherObj) {
        if(!(otherObj instanceof Namespace)) { return false; }
        Namespace other = (Namespace) otherObj;
        if(other.elements.size() != this.elements.size()) { return false; }
        for(int elementI = 0; elementI < this.elements.size(); elementI += 1) {
            String thisElement = this.elements.get(elementI);
            String otherElement = other.elements.get(elementI);
            if(!thisElement.equals(otherElement)) { return false; }
        }
        return true;
    }

}

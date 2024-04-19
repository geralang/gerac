
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.List;

public class DisjointSet<T> {

    private static class Entry<T> {
        private T value;
        private Entry<T> parent;

        private Entry(T value) {
            this.value = value;
            this.parent = null;
        }
    }

    private final List<Entry<T>> values;

    public DisjointSet() {
        this.values = new ArrayList<>();
    }

    private Entry<T> find(int idx) {
        Entry<T> current = this.values.get(idx);
        while(current.parent != null) {
            current = current.parent;
        }
        return current;
    }

    public int add(T value) {
        int idx = this.values.size();
        this.values.add(new Entry<T>(value));
        return idx;
    }

    public T get(int idx) {
        return this.find(idx).value;
    }

    public void union(int idxA, int idxB) {
        Entry<T> rootA = this.find(idxA);
        Entry<T> rootB = this.find(idxB);
        if(rootA != rootB) {
            rootA.parent = rootB;
        }
    }

}
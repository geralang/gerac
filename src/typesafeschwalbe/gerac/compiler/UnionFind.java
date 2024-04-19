
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class UnionFind<T> {

    private static class Entry<T> {
        private T value;
        private Entry<T> parent;

        private Entry(T value) {
            this.value = value;
            this.parent = null;
        }
    }

    private final List<Entry<T>> values;

    public UnionFind() {
        this.values = new ArrayList<>();
    }

    private void compressPath(int start, Entry<T> root) {
        Entry<T> current = this.values.get(start);
        while(current.parent != null) {
            Entry<T> next = current.parent;
            current.parent = root;
            current = next;
        }
    }

    private Entry<T> find(int idx) {
        Entry<T> current = this.values.get(idx);
        while(current.parent != null) {
            current = current.parent;
        }
        this.compressPath(idx, current);
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
        this.union(idxA, idxB, (a, b) -> a);
    }

    public void union(int idxA, int idxB, BiFunction<T, T, T> f) {
        Entry<T> rootA = this.find(idxA);
        Entry<T> rootB = this.find(idxB);
        if(rootA != rootB) {
            rootB.parent = rootA;
        }
        rootA.value = f.apply(rootA.value, rootB.value);
    }

}
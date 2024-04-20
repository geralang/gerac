
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class UnionFind<T> {

    private static class Entry<T> {
        private T value;
        private int parent;

        private Entry(T value) {
            this.value = value;
            this.parent = -1;
        }
    }

    private final List<Entry<T>> values;

    public UnionFind() {
        this.values = new ArrayList<>();
    }

    private void compressPath(int start, int root) {
        int currentIdx = start;
        while(true) {
            Entry<T> current = this.values.get(currentIdx);
            if(current.parent == -1) {
                break;
            }
            currentIdx = current.parent;
            current.parent = root;
        }
    }

    public int find(int idx) {
        int currentIdx = idx;
        while(true) {
            Entry<T> current = this.values.get(currentIdx);
            if(current.parent == -1) {
                break;
            }
            currentIdx = current.parent;
        }
        this.compressPath(idx, currentIdx);
        return currentIdx;
    }

    public int add(T value) {
        int idx = this.values.size();
        this.values.add(new Entry<T>(value));
        return idx;
    }

    public T get(int idx) {
        Entry<T> entry = this.values.get(this.find(idx));
        return entry.value;
    }

    public void set(int idx, T value) {
        Entry<T> entry = this.values.get(this.find(idx));
        entry.value = value;
    }

    public void union(int idxA, int idxB) {
        this.union(idxA, idxB, (a, b) -> a);
    }

    public void union(int idxA, int idxB, BiFunction<T, T, T> f) {
        int rootA = this.find(idxA);
        int rootB = this.find(idxB);
        Entry<T> rootEntryA = this.values.get(rootA);
        Entry<T> rootEntryB = this.values.get(rootB);
        if(rootA != rootB) {
            rootEntryB.parent = rootA;
        }
        rootEntryA.value = f.apply(rootEntryA.value, rootEntryB.value);
    }

}

package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.List;

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
        int rootA = this.find(idxA);
        int rootB = this.find(idxB);
        if(rootA != rootB) {
            this.values.get(rootB).parent = rootA;
        }
    }

}
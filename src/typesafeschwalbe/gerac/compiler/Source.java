
package typesafeschwalbe.gerac.compiler;

import java.util.Map;

public class Source {

    public final String file;
    public final int startOffset;
    public final int endOffset;

    public Source(String file, int startOffset, int endOffset) {
        this.file = file;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    public Source(Source start, Source end) {
        if(!start.file.equals(end.file)) {
            throw new IllegalArgumentException(
                "Provided source locations are not from the same file!"
            );
        }
        this.file = start.file;
        this.startOffset = start.startOffset;
        this.endOffset = end.endOffset;
    }

    public int computeLine(Map<String, String> files) {
        return (int) files.get(this.file)
            .substring(0, this.startOffset)
            .lines()
            .count();
    }

    @Override
    public String toString() {
        return "@\"" + this.file + "\"";
    }

    public String toString(Map<String, String> files) {
        return "@\"" + this.file + "\":" + this.computeLine(files);
    }

}

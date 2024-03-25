
package typesafeschwalbe.gerac.compiler;

public class Source {

    public final String file;
    public final long startOffset;
    public final long endOffset;

    public Source(String file, long startOffset, long endOffset) {
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

}

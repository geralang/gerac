
package typesafeschwalbe.gerac.compiler;

import java.util.Map;

public record Source(String file, int startOffset, int endOffset) {

    public Source(Source start, Source end) {
        this(start.file, start.startOffset, end.endOffset);
        if(!start.file.equals(end.file)) {
            throw new IllegalArgumentException(
                "Provided source locations are not from the same file!"
            );
        }
    }

    public int computeLine(Map<String, String> files) {
        int lines = 1;
        String upTo = files.get(this.file).substring(0, this.startOffset);
        for(int charI = 0; charI < upTo.length(); charI += 1) {
            if(upTo.charAt(charI) == '\n') {
                lines += 1;
            }
        }
        return lines;
    }

    @Override
    public String toString() {
        return "@\"" + this.file + "\"";
    }

    public String toString(Map<String, String> files) {
        return "@\"" + this.file + "\":" + this.computeLine(files);
    }

}

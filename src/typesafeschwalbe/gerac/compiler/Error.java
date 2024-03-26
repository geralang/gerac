
package typesafeschwalbe.gerac.compiler;

import java.util.ArrayList;
import java.util.HashMap;

public class Error {
    
    public static class Marking {

        public final Source location;
        public final String note;

        public Marking(Source location, String note) {
            this.location = location;
            this.note = note;
        }

    }

    public final String message;
    public final Marking[] markings;

    public Error(String message, Marking... markings) {
        this.message = Math.floor(Math.random() * 10000) == 0
            ? "You fucked up big time"
            : message;
        this.markings = markings;
    }

    public String render(HashMap<String, String> files, boolean colored) {
        String errorWordColor = colored
            ? Color.from(Color.BOLD, Color.RED) : "";
        String errorMessageColor = colored
            ? Color.from(Color.RED) : "";
        String locationColor = colored
            ? Color.from(Color.GRAY) : "";
        String paddingLineNumberColor = colored
            ? Color.from(Color.GRAY) : "";
        String markedLineNumberColor = colored
            ? Color.from() : "";
        String separationLineColor = colored
            ? Color.from(Color.GRAY) : "";
        String paddingLineColor = colored
            ? Color.from(Color.GRAY) : "";
        String markedLineColor = colored
            ? Color.from() : "";
        String markingColor = colored
            ? Color.from(Color.RED) : "";
        String noteColor = colored
            ? Color.from(Color.RED) : "";
        StringBuilder output = new StringBuilder();
        output.append(errorWordColor);
        output.append("error: ");
        output.append(errorMessageColor);
        output.append(this.message);
        output.append("\n");
        for(Marking marked: this.markings) {
            String fileContent = files.get(marked.location.file);
            if(fileContent == null) {
                throw new IllegalArgumentException(
                    "An error source location refers to a file"
                        + " that is not present in the provided files!"
                );
            }
            char lastChar = '\0';
            int charIdx = 0;
            int lineIdx = 0;
            int lineStart = 0;
            ArrayList<String> lines = new ArrayList<>();
            ArrayList<String> linesMarked = new ArrayList<>();
            StringBuilder lineMarked = new StringBuilder();
            int markedStartLineIdx = -1;
            int markedStartLineOffset = -1;
            int markedEndLineIdx = -1;
            while(true) {
                char c = charIdx < fileContent.length()?
                    fileContent.charAt(charIdx) : '\n';
                if((c == '\n' && lastChar != '\r') || c == '\r') {
                    lines.add(fileContent.substring(lineStart, charIdx));
                    linesMarked.add(lineMarked.toString());
                    lineMarked.delete(0, lineMarked.length());
                    lineStart = charIdx + 1;
                    if(c == '\r'
                        && lineStart < fileContent.length()
                        && fileContent.charAt(lineStart) == '\n') {
                        lineStart += 1;
                    }
                    lineIdx += 1;
                } else if(charIdx < marked.location.endOffset) {
                    boolean isMarked = charIdx >= marked.location.startOffset;
                    lineMarked.append(
                        isMarked
                        ? markingColor + '^'
                        : ' '
                    );
                    if(markedStartLineIdx == -1 && isMarked) {
                        markedStartLineIdx = lineIdx;
                        markedStartLineOffset = (int) marked.location.startOffset
                            - lineStart;
                    }
                    if(charIdx + 1 == marked.location.endOffset) {
                        lineMarked.append(" ");
                        lineMarked.append(noteColor);
                        lineMarked.append(marked.note);
                        markedEndLineIdx = lineIdx;
                    }
                }
                if(charIdx >= fileContent.length()) { break; }
                charIdx += 1;
            }
            final int paddingLines = 1;
            int displayStartLineIdx = markedStartLineIdx - paddingLines;
            if(displayStartLineIdx < 0) {
                displayStartLineIdx = 0;
            }
            int displayEndLineIdx = markedEndLineIdx + paddingLines;
            if(displayEndLineIdx >= lines.size()) {
                displayEndLineIdx = lines.size() - 1;
            }
            final int paddingSpaces = 2;
            int maxLineNumberLength = String.valueOf(displayEndLineIdx + 1)
                .length();
            output.append(" ".repeat(paddingSpaces + maxLineNumberLength));
            output.append(separationLineColor);
            output.append("╭─ ");
            output.append(locationColor);
            output.append(marked.location.file);
            output.append(":");
            output.append(markedStartLineIdx + 1);
            output.append(":");
            output.append(markedStartLineOffset + 1);
            output.append("\n");
            for(int displayedLineIdx = displayStartLineIdx;
                    displayedLineIdx <= displayEndLineIdx;
                    displayedLineIdx += 1) {
                boolean isMarked = displayedLineIdx >= markedStartLineIdx
                    && displayedLineIdx <= markedEndLineIdx;
                output.append(
                    isMarked? markedLineNumberColor : paddingLineNumberColor
                );
                output.append(" ");
                output.append(displayedLineIdx + 1);
                output.append(separationLineColor);
                output.append(" │ ");
                output.append(isMarked? markedLineColor : paddingLineColor);
                output.append(lines.get(displayedLineIdx));
                output.append("\n");
                if(isMarked) {
                    output.append(separationLineColor);
                    output.append(" ".repeat(
                        paddingSpaces + maxLineNumberLength
                    ));
                    output.append("┊ ");
                    output.append(linesMarked.get(displayedLineIdx));
                    output.append("\n");
                }
            }
            output.append(separationLineColor);
            output.append(" ".repeat(paddingSpaces + maxLineNumberLength - 1));
            output.append("─╯\n");
        }
        return output.toString();
    }

}
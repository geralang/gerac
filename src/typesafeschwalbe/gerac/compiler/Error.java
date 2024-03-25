
package typesafeschwalbe.gerac.compiler;

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
        this.message = message;
        this.markings = markings;
    }

    public String render(HashMap<String, String> files, boolean colored) {
        String resetColor = colored?
            Color.from() : "";
        String errorWordColor = colored?
            Color.from(Color.BOLD, Color.RED) : "";
        String errorMessageColor = colored?
            Color.from(Color.RED) : "";
        String locationColor = colored?
            Color.from(Color.GRAY) : "";
        String lineNumberColor = colored?
            Color.from(Color.GRAY) : "";
        String markingColor = colored?
            Color.from(Color.RED) : "";
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
            long markedStartLineIdx = (
                    fileContent
                        .substring(0, (int) marked.location.startOffset)
                        + " "
                ).lines().count();
            long markedEndLineIdx = (
                    fileContent
                        .substring(0, (int) marked.location.endOffset)
                        + " "
                ).lines().count();
            output.append(locationColor);
            output.append("at: ");
            output.append(marked.location.file);
            output.append(":");
            output.append(markedStartLineIdx + 1);
            output.append("\n");
            long displayedStartLineIdx = markedStartLineIdx - 2;
            if(displayedStartLineIdx < 0) { displayedStartLineIdx = 0; }
            long displayedEndLineIdx = markedEndLineIdx + 1;
            long currentCharIdx = 0;
            long currentLineIdx = 0;
            long currentLineStartIdx = 0;
            int maxLineNumberLength = String.valueOf(displayedEndLineIdx + 1)
                .length();
            StringBuilder currentLineMarked = new StringBuilder();
            while(currentLineIdx < displayedEndLineIdx) {
                char c = currentCharIdx < fileContent.length()?
                    fileContent.charAt((int) currentCharIdx) : ' ';
                char nextChar = currentCharIdx + 1 < fileContent.length()?
                    fileContent.charAt((int) currentCharIdx + 1) : ' ';
                boolean isNewLine = currentCharIdx >= fileContent.length()
                    || (c == '\r' && nextChar != '\n')
                    || c == '\n';
                if(isNewLine) {
                    if(currentLineIdx >= displayedStartLineIdx) {
                        output.append(lineNumberColor);
                        output.append(" ");
                        String lineNumber = String.valueOf(currentLineIdx + 1);
                        output.append(" ".repeat(
                            lineNumber.length() - maxLineNumberLength
                        ));
                        output.append(lineNumber);
                        output.append("  ");
                        output.append(resetColor);
                        output.append(fileContent.substring(
                            (int) currentLineStartIdx, (int) currentCharIdx
                        ).stripTrailing());
                        output.append("\n");
                        if(currentLineMarked.toString().trim().length() > 0) {
                            final int paddingLength = 3;
                            output.append(" ".repeat(
                                maxLineNumberLength + paddingLength
                            ));
                            output.append(currentLineMarked);
                            if(currentLineIdx == markedEndLineIdx - 1) {
                                output.append(" ");
                                output.append(markingColor);
                                output.append(marked.note);
                            }
                            output.append("\n");
                        }
                    }
                    currentLineStartIdx = currentCharIdx + 1;
                    currentLineIdx += 1;
                    currentLineMarked.delete(0, currentLineMarked.length());
                }
                if(currentCharIdx >= fileContent.length()) { break; }
                if(!isNewLine && currentCharIdx < marked.location.endOffset) {
                    currentLineMarked.append(
                        currentCharIdx >= marked.location.startOffset
                            ? markingColor + "^"
                            : resetColor + " "
                    );
                }
                currentCharIdx += 1;
            }
        }
        return output.toString();
    }

}
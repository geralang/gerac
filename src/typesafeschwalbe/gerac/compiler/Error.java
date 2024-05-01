
package typesafeschwalbe.gerac.compiler;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.frontend.Lexer;

public record Error(
    String message, 
    Marking[] markings, 
    Optional<Function<Boolean, String>> appended
) {

    public static record Marking(Type type, Source location, String note) {

        private enum Type {
            ERROR(
                '^', 
                Color.from(Color.RED), Color.from(Color.RED)
            ),
            INFO(
                '~', 
                Color.from(Color.BRIGHT_BLUE), Color.from(Color.BRIGHT_BLUE)
            ),
            HELP(
                '*',
                Color.from(Color.GREEN), Color.from(Color.GREEN)
            );

            private final char marker;
            private final String markingColor;
            private final String noteColor;

            private Type(char marker, String markingColor, String noteColor) {
                this.marker = marker;
                this.markingColor = markingColor;
                this.noteColor = noteColor;
            }
        }

        public static Marking error(Source location, String note) {
            return new Marking(Type.ERROR, location, note);
        }

        public static Marking info(Source location, String note) {
            return new Marking(Type.INFO, location, note);
        }

        public static Marking help(Source location, String note) {
            return new Marking(Type.HELP, location, note);
        }

    }

    public Error(String message, Marking... markings) {
        this(message, markings, Optional.empty());
    }

    public Error(
        String message, Function<Boolean, String> appended, Marking... markings
    ) {
        this(message, markings, Optional.of(appended));
    }

    @Override
    public boolean equals(Object otherRaw) {
        if(!(otherRaw instanceof Error)) { return false; }
        Error other = (Error) otherRaw;
        return this.message.equals(other.message)
            && Arrays.equals(this.markings, other.markings)
            && this.appended.equals(other.appended);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            this.message, Arrays.hashCode(this.markings), this.appended
        );
    }

    public String render(Map<String, String> files, boolean colored) {
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
        String hiddenLinesNoteColor = colored
            ? Color.from(Color.GRAY) : "";
        StringBuilder output = new StringBuilder();
        output.append(errorWordColor);
        output.append("error: ");
        output.append(errorMessageColor);
        output.append(this.message);
        output.append("\n");
        for(Marking marked: this.markings) {
            String fileContent = files.get(marked.location.file());
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
            List<String> lines = new ArrayList<>();
            List<String> linesMarked = new ArrayList<>();
            boolean lineHadMarker = false;
            StringBuilder lineMarked = new StringBuilder();
            int markedStartLineIdx = -1;
            int markedStartLineOffset = -1;
            int markedEndLineIdx = -1;
            while(true) {
                boolean atEnd = charIdx >= fileContent.length();
                char c = atEnd? '\0' : fileContent.charAt(charIdx);
                if(charIdx < marked.location.endOffset()) {
                    boolean isMarked = charIdx >= marked.location.startOffset();
                    String markingColor = colored
                        ? marked.type.markingColor 
                        : "";
                    String noteColor = colored
                        ? marked.type.noteColor
                        : "";
                    char marker = marked.type.marker;
                    boolean displayMarking = isMarked
                        && c != '\n' && c != '\r'
                        && (!Lexer.isWhitespace(c) || lineHadMarker);
                    lineMarked.append(
                        displayMarking
                            ? markingColor + marker
                            : ' '
                    );
                    if(displayMarking) {
                        lineHadMarker = true;
                    }
                    if(markedStartLineIdx == -1 && isMarked) {
                        markedStartLineIdx = lineIdx;
                        markedStartLineOffset 
                            = (int) marked.location.startOffset() - lineStart;
                    }
                    if(charIdx + 1 == marked.location.endOffset()) {
                        lineMarked.append(" ");
                        lineMarked.append(noteColor);
                        lineMarked.append(marked.note);
                        markedEndLineIdx = lineIdx;
                    }
                }
                if((c == '\n' && lastChar != '\r') || c == '\r' || atEnd) {
                    lines.add(fileContent.substring(lineStart, charIdx));
                    linesMarked.add(lineMarked.toString());
                    lineMarked.delete(0, lineMarked.length());
                    lineHadMarker = false;
                    lineStart = charIdx + 1;
                    if(c == '\r'
                        && lineStart < fileContent.length()
                        && fileContent.charAt(lineStart) == '\n') {
                        lineStart += 1;
                    }
                    lineIdx += 1;
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
            output.append(marked.location.file());
            output.append(":");
            output.append(markedStartLineIdx + 1);
            output.append(":");
            output.append(markedStartLineOffset + 1);
            output.append("\n");
            int skipAfterLineCount = 2;
            for(int displayedLineIdx = displayStartLineIdx;
                    displayedLineIdx <= displayEndLineIdx;
                    displayedLineIdx += 1) {
                boolean skipLines = displayedLineIdx
                        > displayStartLineIdx + skipAfterLineCount
                    && displayedLineIdx
                        < displayEndLineIdx - skipAfterLineCount;
                if(skipLines) {
                    int oldDisplayLineIdx = displayedLineIdx;
                    displayedLineIdx = displayEndLineIdx - skipAfterLineCount
                        - 1;
                    output.append(separationLineColor);
                    output.append(" ".repeat(
                        paddingSpaces + maxLineNumberLength
                    ));
                    output.append("┊ ");
                    output.append(hiddenLinesNoteColor);
                    int hiddenCount = displayedLineIdx - oldDisplayLineIdx + 1;
                    output.append("(");
                    output.append(hiddenCount);
                    output.append(" line");
                    if(hiddenCount != 1) { 
                        output.append("s"); 
                    }
                    output.append(" hidden)\n");
                    continue;
                }
                boolean isMarked = displayedLineIdx >= markedStartLineIdx
                    && displayedLineIdx <= markedEndLineIdx;
                output.append(
                    isMarked? markedLineNumberColor : paddingLineNumberColor
                );
                output.append(" ");
                String lineStr = String.valueOf(displayedLineIdx + 1);
                output.append(" ".repeat(
                    maxLineNumberLength - lineStr.length()
                ));
                output.append(lineStr);
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
        if(colored) {
            output.append(Color.from());
        }
        if(this.appended.isPresent()) {
            output.append(this.appended.get().apply(colored));
        }
        if(colored) {
            output.append(Color.from());
        }
        return output.toString();
    }

}
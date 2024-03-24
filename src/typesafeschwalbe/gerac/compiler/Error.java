
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
            long markedStartLineIdx = fileContent
                .substring(0, (int) marked.location.startOffset)
                .lines().count() - 1;
            if(markedStartLineIdx < 0) { markedStartLineIdx = 0; } 
            // long markedEndLineIdx = fileContent
            //     .substring(0, (int) marked.location.endOffset)
            //     .lines().count() - 1;
            output.append(resetColor);
            output.append("at: ");
            output.append(marked.location.file);
            output.append(":");
            output.append(markedStartLineIdx + 1);
            output.append("\n");
            // String[] fileLines = fileContent.lines().toArray(String[]::new);
            // long displayedStartLineIdx = markedStartLineIdx - 2;
            // if(displayedStartLineIdx < 0) { displayedStartLineIdx = 0; }
            // long displayedEndLineIdx = markedEndLineIdx + 2;
            // long currentCharIdx = 0;
            // long currentLineIdx = 0;
            // while(currentCharIdx < fileContent.length()
            //     && currentLineIdx < displayedEndLineIdx) {
                
            // }
        }
        return output.toString();
    }

}
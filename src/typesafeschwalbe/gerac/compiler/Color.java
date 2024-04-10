
package typesafeschwalbe.gerac.compiler;

public class Color {

    public static final String BOLD = "1";

    public static final String BLACK = "30";
    public static final String RED = "31";
    public static final String GREEN = "32";
    public static final String YELLOW = "33";
    public static final String BLUE = "34";
    public static final String MAGENTA = "35";
    public static final String CYAN = "36";
    public static final String WHITE = "37";

    public static final String GRAY = "90";
    public static final String BRIGHT_RED = "91";
    public static final String BRIGHT_GREEN = "92";
    public static final String BRIGHT_YELLOW = "93";
    public static final String BRIGHT_BLUE = "94";
    public static final String BRIGHT_MAGENTA = "95";
    public static final String BRIGHT_CYAN = "96";
    public static final String BRIGHT_WHITE = "97";

    public static String from(String... properties) {
        return "\033[0"
            + (properties.length > 0? ";" : "")
            + String.join(";", properties)
            + "m";
    }

    private Color() {}

}

package typesafeschwalbe.gerac.compiler;

class Color {

    static final String BOLD = "1";

    static final String BLACK = "30";
    static final String RED = "31";
    static final String GREEN = "32";
    static final String YELLOW = "33";
    static final String BLUE = "34";
    static final String MAGENTA = "35";
    static final String CYAN = "36";
    static final String WHITE = "37";

    static final String GRAY = "90";
    static final String BRIGHT_RED = "91";
    static final String BRIGHT_GREEN = "92";
    static final String BRIGHT_YELLOW = "93";
    static final String BRIGHT_BLUE = "94";
    static final String BRIGHT_MAGENTA = "95";
    static final String BRIGHT_CYAN = "96";
    static final String BRIGHT_WHITE = "97";

    static String from(String... properties) {
        return "\033[0"
            + (properties.length > 0? ";" : "")
            + String.join(";", properties)
            + "m";
    }

    private Color() {}

}

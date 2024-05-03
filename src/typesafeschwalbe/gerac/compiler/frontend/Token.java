
package typesafeschwalbe.gerac.compiler.frontend;

import typesafeschwalbe.gerac.compiler.Source;

public class Token {
    
    public enum Type {
        WHITESPACE("a whitespace"),
        COMMENT("a line comment"),
        FILE_END("the end of the file"),

        IDENTIFIER("an identifier"),
        INTEGER("an integer"),
        FRACTION("a fractional number"),
        STRING("a string"),
        PIPE("'|'"),
        EQUALS("'='"),
        DOT("'.'"),
        HASHTAG("'#'"),
        COMMA("','"),
        ARROW("'->'"),
        DOUBLE_COLON("'::'"),
        SEMICOLON("';'"),
        EXCLAMATION_MARK("'!'"),
        ASTERISK("'*'", 3),
        SLASH("'/'", 3),
        PERCENT("'%'", 3),
        PLUS("'+'", 4),
        MINUS("'-'", 4),
        LESS_THAN("'<'", 5),
        GREATER_THAN("'>'", 5),
        LESS_THAN_EQUAL("'<='", 5),
        GREATER_THAN_EQUAL("'>='", 5),
        DOUBLE_EQUALS("'=='", 6),
        NOT_EQUALS("'!='", 6),
        DOUBLE_AMPERSAND("'&&'", 7),
        DOUBLE_PIPE("'||'", 8),
        DOUBLE_DOT("'..'", 9),
        DOUBLE_DOT_EQUALS("'..='", 9),
        FUNCTION_PIPE("'|>'", 11),
        MEMBER_PIPE("'.>'", 11),
        QUESTION_MARK("'?'", 11),
        PAREN_OPEN("'('", 1),
        PAREN_CLOSE("')'"),
        BRACKET_OPEN("'['"),
        BRACKET_CLOSE("']'"),
        BRACE_OPEN("'{'"),
        BRACE_CLOSE("'}'"),
        KEYWORD_PROCEDURE("'proc'"),
        KEYWORD_CASE("'case'"),
        KEYWORD_VALUE("'val'"),
        KEYWORD_MUTABLE("'mut'"),
        KEYWORD_RETURN("'return'"),
        KEYWORD_MODULE("'mod'"),
        KEYWORD_PUBLIC("'pub'"),
        KEYWORD_USE("'use'"),
        KEYWORD_TRUE("'true'"),
        KEYWORD_FALSE("'false'"),
        KEYWORD_ELSE("'else'"),
        KEYWORD_UNIT("'unit'"),
        KEYWORD_STATIC("'static'"),
        KEYWORD_TARGET("'target'");

        public static final int PREFIX_MINUS_PRECEDENCE = 2;
        public static final int PREFIX_EXCLAMATION_MARK_PRECEDENCE = 2;
        public static final int PREFIX_HASHTAG_PRECEDENCE = 10;

        public final String description;
        public final int infixPrecedence;

        private Type(String description, int infixPrecedence) {
            this.description = description;
            this.infixPrecedence = infixPrecedence;
        }
        private Type(String description) {
            this.description = description;
            this.infixPrecedence = 0;
        }
    }

    public final Type type;
    public final String content;
    public final Source source;

    Token(Type type, String content, Source source) {
        this.type = type;
        this.content = content;
        this.source = source;
    }

    @Override
    public String toString() {
        StringBuilder output = new StringBuilder();
        output.append("[");
        output.append(this.type);
        output.append(" - '");
        output.append(this.content);
        output.append("']");
        return output.toString();
    }
    
}



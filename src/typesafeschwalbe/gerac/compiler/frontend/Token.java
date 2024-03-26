
package typesafeschwalbe.gerac.compiler.frontend;

import typesafeschwalbe.gerac.compiler.Source;

public class Token {
    
    public enum Type {
        WHITESPACE,
        COMMENT,
        FILE_END,

        IDENTIFIER,
        INTEGER,
        FRACTION,
        STRING,
        PIPE,
        EQUALS,
        DOT,
        DOUBLE_DOT,
        DOUBLE_DOT_EQUALS,
        PLUS,
        MINUS,
        ASTERISK,
        SLASH,
        PERCENT,
        LESS_THAN,
        GREATER_THAN,
        LESS_THAN_EQUAL,
        GREATER_THAN_EQUAL,
        DOUBLE_EQUALS,
        NOT_EQUALS,
        DOUBLE_PIPE,
        FUNCTION_PIPE,
        MEMBER_PIPE,
        DOUBLE_AMPERSAND,
        EXCLAMATION_MARK,
        HASHTAG,
        COMMA,
        ARROW,
        DOUBLE_COLON,
        PAREN_OPEN,
        PAREN_CLOSE,
        BRACKET_OPEN,
        BRACKET_CLOSE,
        BRACE_OPEN,
        BRACE_CLOSE,
        KEYWORD_PROCEDURE,
        KEYWORD_CASE,
        KEYWORD_VARIABLE,
        KEYWORD_MUTABLE,
        KEYWORD_RETURN,
        KEYWORD_MODULE,
        KEYWORD_PUBLIC,
        KEYWORD_USE,
        KEYWORD_TRUE,
        KEYWORD_FALSE,
        KEYWORD_ELSE,
        KEYWORD_UNIT,
        KEYWORD_STATIC,
        KEYWORD_TARGET
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



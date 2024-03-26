
package typesafeschwalbe.gerac.compiler.frontend;

import java.util.function.Function;

import typesafeschwalbe.gerac.compiler.Source;
import typesafeschwalbe.gerac.compiler.Error;

public class Lexer {
    
    private final String fileName;
    private final String fileContent;

    private int currentPos = 0;

    public Lexer(String fileName, String fileContent) {
        this.fileName = fileName;
        this.fileContent = fileContent;
    }

    private static boolean isDigit(char c) {
        return '0' <= c && c <= '9';
    }

    private static boolean isAlphanumeral(char c) {
        return ('0' <= c && c <= '9')
            || ('A' <= c && c <= 'Z')
            || ('a' <= c && c <= 'z')
            || c == '_';
    }

    private static boolean isWhitespace(char c) {
        return c == 9   // horizontal tab
            || c == 10  // line feed
            || c == 13  // carriage feed
            || c == 32; // space
    }

    private char current() {
        if(this.atEnd()) { return '\0'; }
        return this.fileContent.charAt(this.currentPos);
    }

    private char peek() {
        if(this.currentPos + 1 >= this.fileContent.length()) { return '\0'; }
        return this.fileContent.charAt(this.currentPos + 1);
    }

    private void next() {
        this.currentPos += 1;
    }

    private boolean atEnd() {
        return this.currentPos >= this.fileContent.length();
    }

    private int find(Function<Character, Boolean> f) {
        int pos = this.currentPos;
        while(true) {
            if(pos >= this.fileContent.length()) { break; }
            if(f.apply(this.fileContent.charAt(pos))) { break; }
            pos += 1;
        }
        return pos;
    }

    private Token makeToken(String content, Token.Type type) {
        return new Token(
            type,
            content,
            new Source(
                this.fileName, this.currentPos - content.length(),
                this.currentPos
            )
        );
    }

    public Token nextToken() throws ParsingException {
        if(this.currentPos >= this.fileContent.length()) {
            return new Token(
                Token.Type.FILE_END, "", new Source(
                    this.fileName, this.fileContent.length() - 1,
                    this.fileContent.length()
                )
            );
        }
        if(Lexer.isWhitespace(this.current())) {
            int endIdx = this.find(c -> !Lexer.isWhitespace(c));
            String content = this.fileContent.substring(
                this.currentPos, endIdx
            );
            this.currentPos = endIdx;
            return this.makeToken(content, Token.Type.WHITESPACE);
        }
        // todo: tokenize integer and number
        // todo: tokenize string literal
        if(Lexer.isAlphanumeral(this.current())) {
            int endIdx = this.find(c -> !Lexer.isAlphanumeral(c));
            String content = this.fileContent.substring(
                this.currentPos, endIdx
            );
            Token.Type t;
            switch(content) {
                case "proc": t = Token.Type.KEYWORD_PROCEDURE; break;
                case "case": t = Token.Type.KEYWORD_CASE; break;
                case "var": t = Token.Type.KEYWORD_VARIABLE; break;
                case "mut": t = Token.Type.KEYWORD_MUTABLE; break;
                case "return": t = Token.Type.KEYWORD_RETURN; break;
                case "mod": t = Token.Type.KEYWORD_MODULE; break;
                case "pub": t = Token.Type.KEYWORD_PUBLIC; break;
                case "use": t = Token.Type.KEYWORD_USE; break;
                case "true": t = Token.Type.KEYWORD_TRUE; break;
                case "false": t = Token.Type.KEYWORD_FALSE; break;
                case "else": t = Token.Type.KEYWORD_ELSE; break;
                case "unit": t = Token.Type.KEYWORD_UNIT; break;
                case "static": t = Token.Type.KEYWORD_STATIC; break;
                case "target": t = Token.Type.KEYWORD_TARGET; break;
                default: t = Token.Type.IDENTIFIER;
            }
            this.currentPos += content.length();
            return this.makeToken(content, t);
        }
        switch(this.current()) {
            case '|':
                this.next();
                if(this.current() == '|') {
                    this.next();
                    return this.makeToken("||", Token.Type.DOUBLE_PIPE);
                } else if(this.current() == '>') {
                    this.next();
                    return this.makeToken("|>", Token.Type.FUNCTION_PIPE);
                } else {
                    return this.makeToken("|", Token.Type.PIPE);
                }
            case '=':
                this.next();
                if(this.current() == '=') {
                    this.next();
                    return this.makeToken("==", Token.Type.DOUBLE_EQUALS);
                } else {
                    return this.makeToken("=", Token.Type.EQUALS);
                }
            case '.':
                this.next();
                if(this.current() == '.') {
                    this.next();
                    if(this.current() == '=') {
                        this.next();
                        return this.makeToken(
                            "..=", Token.Type.DOUBLE_DOT_EQUALS
                        );
                    } else {
                        return this.makeToken("..", Token.Type.DOUBLE_DOT);
                    }
                } else if(this.current() == '>') {
                    this.next();
                    return this.makeToken(".>", Token.Type.MEMBER_PIPE);
                } else {
                    return this.makeToken(".", Token.Type.DOT);
                }
            case '+':
                this.next();
                return this.makeToken("+", Token.Type.PLUS);
            case '-':
                this.next();
                if(this.current() == '>') {
                    this.next();
                    return this.makeToken("->", Token.Type.ARROW);
                } else {
                    return this.makeToken("-", Token.Type.MINUS);
                }
            case '*':
                this.next();
                return this.makeToken("*", Token.Type.ASTERISK);
            case '/':
                int startPos = this.currentPos;
                this.next();
                if(this.current() == '/') {
                    this.next();
                    while(!this.atEnd()) {
                        char c = this.current();
                        if(c == '\n' || c == '\r') {
                            this.next();
                            if(c == '\r' && this.current() == '\n') {
                                this.next();
                            }
                            break;
                        }
                        this.next();
                    }
                    String content = this.fileContent.substring(
                        startPos, this.currentPos
                    );
                    return this.makeToken(content, Token.Type.COMMENT);
                } else {
                    return this.makeToken("/", Token.Type.SLASH);
                }
            case '%':
                this.next();
                return this.makeToken("%", Token.Type.PERCENT);
            case '<':
                this.next();
                if(this.current() == '=') {
                    this.next();
                    return this.makeToken("<=", Token.Type.LESS_THAN);
                } else {
                    return this.makeToken("<", Token.Type.LESS_THAN_EQUAL);
                }
            case '>':
                this.next();
                if(this.current() == '=') {
                    this.next();
                    return this.makeToken(">=", Token.Type.GREATER_THAN);
                } else {
                    return this.makeToken(">", Token.Type.GREATER_THAN_EQUAL);
                }
            case '!':
                this.next();
                if(this.current() == '=') {
                    this.next();
                    return this.makeToken("!=", Token.Type.NOT_EQUALS);
                } else {
                    return this.makeToken("!", Token.Type.EXCLAMATION_MARK);
                }
            case '&':
                if(this.peek() == '&') {
                    this.next();
                    this.next();
                    return this.makeToken("&&", Token.Type.DOUBLE_AMPERSAND);
                }
                break;
            case ':':
                if(this.peek() == ':') {
                    this.next();
                    this.next();
                    return this.makeToken("::", Token.Type.DOUBLE_DOT);
                }
                break;
            case '#':
                this.next();
                return this.makeToken("#", Token.Type.HASHTAG);
            case ',':
                this.next();
                return this.makeToken(",", Token.Type.COMMA);
            case '(':
                this.next();
                return this.makeToken("(", Token.Type.PAREN_OPEN);
            case ')':
                this.next();
                return this.makeToken(")", Token.Type.PAREN_CLOSE);
            case '[':
                this.next();
                return this.makeToken("[", Token.Type.BRACKET_OPEN);
            case ']':
                this.next();
                return this.makeToken("]", Token.Type.BRACKET_CLOSE);
            case '{':
                this.next();
                return this.makeToken("{", Token.Type.BRACE_OPEN);
            case '}':
                this.next();
                return this.makeToken("}", Token.Type.BRACE_CLOSE);
        }
        throw new ParsingException(new Error(
            "Invalid character",
            new Error.Marking(
                new Source(this.fileName, this.currentPos, this.currentPos + 1),
                "'" + this.current() + "' is not a valid character"
            )
        ));
    }

    public Token nextFilteredToken() throws ParsingException {
        while(true) {
            Token c = this.nextToken();
            boolean filter = c.type != Token.Type.WHITESPACE
                && c.type != Token.Type.COMMENT;
            if(filter) { return c; }
        }
    }

}

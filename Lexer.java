package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid or missing.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation a lot easier.
 */
public final class Lexer {


    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> list = new ArrayList();
        while(chars.has(0)) {
            if (peek("\\s")) {
                chars.advance();
                chars.skip();
            }
            else {
                list.add(lexToken());
            }
        }
        return list;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if(peek("[.]")){
            return lexOperator();
        }
        if(peek("[a-zA-Z_]")){
            return lexIdentifier();
        }
        if(peek("[+]")) {
            return lexNumber();
        }
        if(peek("-")) {
            return lexNumber();
        }
        if(peek("[0123456789]")){
            return lexNumber();
        }
        if(peek("\'")){
            return lexCharacter();
        }
        if(peek("\"")){
            return lexString();
        }
        else{
            return lexOperator();
        }
    }

    public Token lexIdentifier() {

        while(peek("[A-Za-z0-9_-]")){
            chars.advance();
        }
        Token answerToken = chars.emit(Token.Type.IDENTIFIER);

        return answerToken;
    }

    public Token lexNumber() {

        if(peek("[+-]")){
            chars.advance();
            if(!peek("[0-9]")){
                return chars.emit(Token.Type.OPERATOR);
            }
        }
        while(match("[0-9]")){
            //chars.advance();
        }
        if(match("[.]", "[0-9]")){
            while(peek("[0-9]")){
                chars.advance();
            }
            return chars.emit(Token.Type.DECIMAL);
        }
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        if(match("[']"))
        {
            if(chars.has(0) && !peek("[\\\'\\\\\\\n\\\r]")){
                chars.advance();
                if(peek("[\']")){
                    chars.advance();
                    return chars.emit(Token.Type.CHARACTER);
                }
            }
            if(peek("[\\\\]")){
                chars.advance();
                if(match("[bnrt\\\'\\\"\\\\]")){
                    if(match("[']")){
                        return chars.emit(Token.Type.CHARACTER);
                    }
                }
            }
        }
        throw new ParseException("Incorrect char token", chars.index);
    }

    public Token lexString() {
        if(match("[\\\"]")){
            while(chars.has(0) && !peek("[\\\"]")){
                if(!peek("[\\\"\\\\\n\\\r]")){
                    chars.advance();
                }
                else if(peek("[\\\\]")){
                    chars.advance();
                    if(peek("[bnrt\\\'\\\"\\\\]"))
                    {
                        chars.advance();
                    }
                    else{
                        throw new ParseException("Invalid escape character", chars.index);
                    }
                }
            }
            if(!chars.has(0)){
                throw new ParseException("Unterminated string token", chars.index);
            }
            else{
                chars.advance();
                return chars.emit(Token.Type.STRING);
            }
        }
        throw new ParseException("Incorrect string token", chars.index);
        //TODO
    }

    public void lexEscape() {
        throw new UnsupportedOperationException(); //TODO
    }

    public Token lexOperator() {
        if(peek("[=<>!]")){
            chars.advance();
            if(peek("[=]")){
                chars.advance();
            }
            return chars.emit(Token.Type.OPERATOR);
        }
        chars.advance();
        return chars.emit(Token.Type.OPERATOR);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if(!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])){
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {

        boolean peek = peek(patterns);

        if(peek){
            for(int i = 0; i < patterns.length; i++){
                chars.advance();
            }
        }

        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}

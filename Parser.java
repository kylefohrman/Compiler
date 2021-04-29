package plc.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource(){
        ArrayList<Ast.Field> fields = new ArrayList<>();
        ArrayList<Ast.Method> methods = new ArrayList<>();
        while(tokens.has(0)){
            if(match("LET")){
                fields.add(parseField());
            }
            else if(match("DEF")){
                methods.add(parseMethod());
            }
            else if(tokens.get(0).getLiteral().equals("\000")) {
                tokens.advance();
            }
            else{
                throwException("Expected 'LET' or 'DEF'");
            }
        }

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() {
        String name = "", typename = null;
        Ast.Expr value;

        if(peek(Token.Type.IDENTIFIER))
        {
            name = tokens.consume().getLiteral();
        }
        else
        {
            throwException("Expected Identifier");
        }

        if(match(":")){
            if(peek(Token.Type.IDENTIFIER)){
                typename = tokens.consume().getLiteral();
            }
            else{
                throwException("Expected type name");
            }
        }
        else{
            throwException("Expected :");
        }

        if(match("=")){
            value = parseExpression();
        }
        else
        {
            value = null;
        }

        if(!match(";")) {
            throwException("Expected ';'");
        }

        if(typename == null){
            return new Ast.Field(name, Optional.ofNullable(value));
        }
        return new Ast.Field(name, typename, Optional.ofNullable(value));
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() {
        String name = null;
        Optional<String> returntypename = Optional.empty();
        ArrayList<String> parameters = new ArrayList<>(), parametertypenames = new ArrayList<>();
        ArrayList<Ast.Stmt> statements = new ArrayList<>();

        if(peek(Token.Type.IDENTIFIER))
        {
            name = tokens.consume().getLiteral();
        }
        else
        {
            throwException("Expected method name");
        }
        if(!tokens.consume().getLiteral().equals("(")) throwException("Expected '('");
        while(!match(")"))
        {
            if(!peek(Token.Type.IDENTIFIER)) throwException("Expected identifier or ')'");
            parameters.add(tokens.consume().getLiteral());
            if(!match(":")) throwException("Expected ':'");
            if(!peek(Token.Type.IDENTIFIER)) throwException("Expected parameter type name");
            parametertypenames.add(tokens.consume().getLiteral());
            if(match(','))
            {
                while(!match(','))
                {
                    if(!peek(Token.Type.IDENTIFIER)) throwException("Expected identifier or ')'");
                    parameters.add(tokens.consume().getLiteral());
                    if(!match(":")) throwException("Expected ':'");
                    if(!peek(Token.Type.IDENTIFIER)) throwException("Expected parameter type name");
                    parametertypenames.add(tokens.consume().getLiteral());
                }
            }
            else
            {
                if(!peek(")")) throwException("Expected ')'");
            }
        }
        if(match(":"))
        {
            if(!peek(Token.Type.IDENTIFIER)) throwException("Expected return type");
            returntypename = Optional.of(tokens.consume().getLiteral());
        }
        if(!match("DO")) throwException("Expected 'DO'");
        while(!match("END"))
        {
            statements.add(parseStatement());
        }

        return new Ast.Method(name, parameters, parametertypenames, returntypename, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() {
        if(match("LET")){
            return parseDeclarationStatement();
        }
        else if(match("IF")){
            return parseIfStatement();
        }
        else if(match("FOR")){
            return parseForStatement();
        }
        else if(match("WHILE")){
            return parseWhileStatement();
        }
        else if(match("RETURN")){
            return parseReturnStatement();
        }
        else{
            Ast.Expr first = parseExpression();
            if(match("=")){
                Ast.Expr second = parseExpression();
                if(!match(";")) {
                    throwException("Expected ';'");
                }
                return new Ast.Stmt.Assignment(first, second);
            }
            if(!match(";")) {
                throwException("Expected ';'");
            }
            return new Ast.Stmt.Expression(first);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() {
        String name;
        Optional<String> typeName = Optional.empty();
        Optional<Ast.Expr> value = Optional.empty();
        if(!peek(Token.Type.IDENTIFIER)) {
            throwException("Expected identifier");
        }
        name = tokens.consume().getLiteral();
        if(match(":")){
            if(!peek(Token.Type.IDENTIFIER)) {
                throwException("Expected type name after ':'");
            }
            typeName = Optional.of(tokens.consume().getLiteral());
        }
        if(match("=")){
            value = Optional.of(parseExpression());
        }
        if(!match(";")) {
            throwException("Expected semicolon ;");
        }

        return new Ast.Stmt.Declaration(name, typeName, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() {
        Ast.Expr condition;
        ArrayList<Ast.Stmt> thenStatements = new ArrayList<>();
        ArrayList<Ast.Stmt> elseStatements = new ArrayList<>();

        condition = parseExpression();
        if(!match("DO")) throwException("Expected 'DO'");
        while(!match("END")){
            while(!peek("ELSE") && !peek("END")){
                thenStatements.add(parseStatement());
            }
            if(match("ELSE")){
                while(!peek("END")){
                    elseStatements.add(parseStatement());
                }
            }

        }

        return new Ast.Stmt.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() {
        String name;
        Ast.Expr value;
        ArrayList<Ast.Stmt> statements = new ArrayList<>();

        if(!peek(Token.Type.IDENTIFIER)) throwException("Expected identifier");
        name = tokens.consume().getLiteral();
        if(!match("IN")) throwException("Expected 'IN'");
        value = parseExpression();
        if(!match("DO")) throwException("Expected 'DO'");
        while(!match("END")){
            statements.add(parseStatement());
        }

        return new Ast.Stmt.For(name, value, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() {
        Ast.Expr condition;
        ArrayList<Ast.Stmt> statements = new ArrayList<>();

        condition = parseExpression();
        if(!match("DO")) throwException("Expected 'DO'");
        while(!match("END")){
            statements.add(parseStatement());
        }

        return new Ast.Stmt.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() {
        Ast.Expr value = parseExpression();

        if(!match(";")) throwException("Expected ';'");

        return new Ast.Stmt.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression(){
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() {
        String operator;
        Ast.Expr left, right;

        left = parseEqualityExpression();
        while((peek("AND") || peek("OR"))){
            operator = tokens.consume().getLiteral();
            right = parseEqualityExpression();
            tokens.advance();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() {
        String operator;
        Ast.Expr left, right;

        left = parseAdditiveExpression();
        while((peek("<") || peek("<=") || peek(">") || peek(">=") || peek("==") || peek("!="))){
            operator = tokens.get(0).getLiteral();
            tokens.advance();
            right = parseAdditiveExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() {
        String operator;
        Ast.Expr left, right;

        left = parseMultiplicativeExpression();
        while((peek("+") || peek("-"))){
            operator = tokens.get(0).getLiteral();
            tokens.advance();
            right = parseMultiplicativeExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() {
        String operator;
        Ast.Expr left, right;

        left = parseSecondaryExpression();

        while((peek("*") || peek("/"))){
            operator = tokens.get(0).getLiteral();
            tokens.advance();
            right = parseSecondaryExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() {
        Ast.Expr receiver = parsePrimaryExpression();
        List<Ast.Expr> arguments = new ArrayList<>();

        if(match(".")) {
            String name = tokens.consume().getLiteral();
            if(!match("(")) {
                return new Ast.Expr.Access(Optional.of(receiver), name);
            }
            else {
                if(!peek(")")) {
                    arguments.add(parseExpression());
                    while (match(",")) {
                        arguments.add(parseExpression());
                    }
                }
                if(!match(")")) {
                    throwException("Expected ')' after function");
                }
                return new Ast.Expr.Function(Optional.of(receiver), name, arguments);
            }
        }

        return receiver;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() {
        if(peek(Token.Type.STRING))
        {
            String input = tokens.consume().getLiteral();
            input = input.substring(1, input.length() - 1);
            input = input.replace("\\n", "\n")
                    .replace("\"", "")
                    .replace("\\\\", "\\")
                    .replace("\\b", "\b")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\'", "\'");
            return new Ast.Expr.Literal(input);
        }
        else if(peek(Token.Type.IDENTIFIER)){
            String name = tokens.consume().getLiteral();
            List<Ast.Expr> arguments = new ArrayList<>();
            switch(name){
                case "NIL":
                    return new Ast.Expr.Literal(null);
                case "TRUE":
                    return new Ast.Expr.Literal(true);
                case "FALSE":
                    return new Ast.Expr.Literal(false);
                default: //functions
                {
                    if(!match("(")) {
                        return new Ast.Expr.Access(Optional.empty(), name);
                    }
                    else {
                        if(!peek(")")) {
                            arguments.add(parseExpression());
                            while (match(",")) {
                                arguments.add(parseExpression());
                            }
                        }
                        if(!match(")")) {
                            throwException("Expected ')' after function");
                        }
                        return new Ast.Expr.Function(Optional.empty(), name, arguments);
                    }
                }
            }
        }
        else if(peek(Token.Type.INTEGER)){
            java.math.BigInteger input = java.math.BigInteger.valueOf(Integer.parseInt(tokens.consume().getLiteral()));
            return new Ast.Expr.Literal(input);
        }
        else if(peek(Token.Type.DECIMAL)){
            java.math.BigDecimal input = java.math.BigDecimal.valueOf(Float.parseFloat(tokens.consume().getLiteral()));
            return new Ast.Expr.Literal(input);
        }
        else if(peek(Token.Type.CHARACTER)){
            String input = tokens.get(0).getLiteral();
            input = input.substring(1, input.length() - 1);
            input = input.replace("\\n", "\n")
                    .replace("\"", "")
                    .replace("\\\\", "\\")
                    .replace("\\b", "\b")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\'", "\'");
            Character charInput = input.charAt(0);
            return new Ast.Expr.Literal(charInput);
        }
        else if(peek("(")){
            tokens.advance();
            Ast.Expr input = parseExpression();
            if(peek(")")){
                tokens.advance();
                return new Ast.Expr.Group(input);
            }
            throwException("Missing ')' operator");
        }
        throwException("Missing valid token at Primary Expression");

        throw new ParseException("Missing valid token at Primary Expression", tokens.index);
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private void throwException(String message) {
        int index;
        if (tokens.has(0)) {
            index = tokens.index;
        }
        else {
            index = tokens.index - 1;
        }
        throw new ParseException(message, index);
    }

    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i++){
            if(!tokens.has(i)){
                return false;
            }
            else if (patterns[i] instanceof Token.Type){
                if (patterns[i] != tokens.get(i).getType()){
                    return false;
                }
            }
            else if (patterns[i] instanceof String) {
                if(!patterns[i].equals(tokens.get(i).getLiteral())){
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if(peek){
            for(int i = 0; i < patterns.length; i++){
                tokens.advance();
            }
        }

        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

        public Token consume(){
            Token temp = tokens.get(index);
            index++;
            return temp;
        }

    }

}

package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        for(int i = 0; i < ast.getFields().size(); i++) {
            visit(ast.getFields().get(i));
        }
        for(int i = 0; i < ast.getMethods().size(); i++) {
            visit(ast.getMethods().get(i));
        }

        if(scope.lookupFunction("main", 0).getReturnType() != Environment.Type.INTEGER) {
            throw new RuntimeException();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if(ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(ast.getValue().get().getType(), Environment.getType(ast.getTypeName()));
        }
        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        List<Environment.Type> parameterTypes = new ArrayList<>();

        if(!ast.getParameters().isEmpty()) {
            for (int i = 0; i < ast.getParameterTypeNames().size(); i++) {
                parameterTypes.add(Environment.getType(ast.getParameterTypeNames().get(i)));
            }
        }

        Environment.Function function = scope.defineFunction(ast.getName(), ast.getName(),
                parameterTypes, Environment.getType(ast.getReturnTypeName().get()), args -> Environment.NIL);

        ast.setFunction(function);

        try {
            scope = new Scope(scope);

            scope.defineVariable("Return Type", "Return Type", Environment.getType(ast.getReturnTypeName().get()), Environment.NIL);

            for(int i = 0; i < ast.getParameters().size(); i++) {
                scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i),
                        Environment.getType(ast.getParameterTypeNames().get(i)), Environment.NIL);
            }

            for(int i = 0; i < ast.getStatements().size(); i++) {
                visit(ast.getStatements().get(i));
            }
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        if(!(ast.getExpression() instanceof Ast.Expr.Function)) {
            throw new RuntimeException();
        }

        visit(ast.getExpression());

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        Environment.Type type = Environment.Type.NIL;
        Environment.PlcObject value = Environment.NIL;

        if(ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            if(ast.getTypeName().isPresent()) {
                requireAssignable(Environment.getType(ast.getTypeName().get()), ast.getValue().get().getType());
            }
            type = ast.getValue().get().getType();
        }
        else if(ast.getTypeName().isPresent()){
            type = Environment.getType(ast.getTypeName().get());
        }
        else {
            throw new RuntimeException();
        }


        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, value));

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        if(!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException();
        }

        visit(ast.getReceiver());
        visit(ast.getValue());

        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());

        scope.defineVariable(((Ast.Expr.Access)ast.getReceiver()).getName(), ((Ast.Expr.Access)ast.getReceiver()).getName(), ast.getReceiver().getType(), Environment.NIL);

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        visit(ast.getCondition());
        if(ast.getCondition().getType() != Environment.Type.BOOLEAN) {
            throw new RuntimeException();
        }
        if(ast.getThenStatements().isEmpty()) {
            throw new RuntimeException();
        }

        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getThenStatements().size(); i++) {
                visit(ast.getThenStatements().get(i));
            }
        }
        finally{
            scope = scope.getParent();
        }

        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getElseStatements().size(); i++) {
                visit(ast.getElseStatements().get(i));
            }
        }
        finally {
            scope = scope.getParent();
        }


        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        visit(ast.getValue());
        if(ast.getValue().getType() != Environment.Type.INTEGER_ITERABLE) {
            throw new RuntimeException();
        }
        if(ast.getStatements().isEmpty()) {
            throw new RuntimeException();
        }
        try{
            scope = new Scope(scope);

            for(Ast.Stmt stmt : ast.getStatements()){
                visit(stmt);
            }
        }
        finally{
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try{
            scope = new Scope(scope);

            for(Ast.Stmt stmt : ast.getStatements()){
                visit(stmt);
            }
        }
        finally{
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        visit(ast.getValue());

        requireAssignable(scope.lookupVariable("Return Type").getType(), ast.getValue().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if(ast.getLiteral() == null) {
            ast.setType(Environment.Type.NIL);
        }
        else if(ast.getLiteral() instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if(ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else if(ast.getLiteral() instanceof BigInteger) {
            if(((BigInteger)ast.getLiteral()).compareTo(BigInteger.valueOf(2147483647)) > 0 ||
                    ((BigInteger)ast.getLiteral()).compareTo(BigInteger.valueOf(-2147483648)) < 0) {
                throw new RuntimeException();
            }
            else {
                ast.setType(Environment.Type.INTEGER);
            }
        }
        else if(ast.getLiteral() instanceof BigDecimal) {
            double checkVal = ((BigDecimal)ast.getLiteral()).doubleValue();
            if(checkVal == Double.POSITIVE_INFINITY || checkVal == Double.NEGATIVE_INFINITY) {
                throw new RuntimeException();
            }
            else {
                ast.setType(Environment.Type.DECIMAL);
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        if(!(ast.getExpression() instanceof Ast.Expr.Binary)) {
            throw new RuntimeException();
        }
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        visit(ast.getLeft());
        visit(ast.getRight());
        switch(ast.getOperator()) {
            case "AND":

            case "OR":
                if (ast.getLeft().getType() == Environment.Type.BOOLEAN && ast.getRight().getType() == Environment.Type.BOOLEAN) {
                    ast.setType(Environment.Type.BOOLEAN);
                }
                else {
                    throw new RuntimeException();
                }
                break;
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
                switch(ast.getLeft().getType().getJvmName()) {
                    case "int":
                    case "double":
                    case "char":
                    case "String":
                    case "Comparable":
                        if(ast.getLeft().getType() == ast.getRight().getType()) {
                            ast.setType(Environment.Type.BOOLEAN);
                        }
                        else {
                            throw new RuntimeException();
                        }
                        break;
                    default:
                        throw new RuntimeException();
                }
                break;
            case "+":
                if(ast.getLeft().getType() == Environment.Type.STRING || ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.STRING);
                }
                else if(ast.getLeft().getType() == Environment.Type.INTEGER || ast.getLeft().getType() == Environment.Type.DECIMAL) {
                    if(ast.getLeft().getType() == ast.getRight().getType()) {
                        ast.setType(ast.getLeft().getType());
                    }
                    else {
                        throw new RuntimeException();
                    }
                }
                else {
                    throw new RuntimeException();
                }
                break;
            case "-":
            case "*":
            case "/":
                if(ast.getLeft().getType() == ast.getRight().getType()) {
                    switch (ast.getLeft().getType().getJvmName()) {
                        case "int":
                            if(ast.getRight().getType().getJvmName().equals("int")) {
                                ast.setType(Environment.Type.INTEGER);
                            }
                            else {
                                throw new RuntimeException();
                            }
                            break;
                        case "double":
                            if(ast.getRight().getType().getJvmName().equals("double")) {
                                ast.setType(Environment.Type.DECIMAL);
                            }
                            else {
                                throw new RuntimeException();
                            }
                            break;
                        default:
                            throw new RuntimeException();
                    }
                }
                else {
                    throw new RuntimeException();
                }
                break;
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());

            Ast.Expr receiver = ast.getReceiver().get();
            ast.setVariable(receiver.getType().getField(ast.getName()));

            return null;
        }

        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if(ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());

            for(int i = 0; i < ast.getArguments().size(); i++) {
                visit(ast.getArguments().get(i));

            }

            Ast.Expr receiver = ast.getReceiver().get();
            ast.setFunction(receiver.getType().getMethod(ast.getName(), ast.getArguments().size()));

            for(int i = 0; i < ast.getArguments().size(); i++) {
                requireAssignable(ast.getFunction().getParameterTypes().get(i + 1), ast.getArguments().get(i).getType());
            }

            return null;
        }

        for(int i = 0; i < ast.getArguments().size(); i++) {
            visit(ast.getArguments().get(i));
        }
        ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));

        for(int i = 0; i < ast.getArguments().size(); i++) {
            requireAssignable(ast.getFunction().getParameterTypes().get(i), ast.getArguments().get(i).getType());
        }

        return null; //TODO
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if(target == Environment.Type.ANY){
            return;
        }
        if(target == Environment.Type.COMPARABLE) {
            if(type == Environment.Type.CHARACTER || type == Environment.Type.STRING ||
                    type == Environment.Type.INTEGER || type == Environment.Type.DECIMAL){
                return;
            }
        }
        if(target == type) {
            return;
        }

        throw new RuntimeException();
    }

}

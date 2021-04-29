package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for(int i = 0; i < ast.getFields().size(); i++) {
            visit(ast.getFields().get(i));
        }
        for(int i = 0; i < ast.getMethods().size(); i++) {
            visit(ast.getMethods().get(i));
        }

        ArrayList<Environment.PlcObject> args = new ArrayList<>();
        return scope.lookupFunction("main", 0).invoke(args);
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if(ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        /**
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
        */
        Function<List<Environment.PlcObject>, Environment.PlcObject> function = args -> {
            try {
                scope = new Scope(scope);
                for(int i = 0; i < ast.getParameters().size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), args.get(i));
                }
                for(int i = 0; i < ast.getStatements().size(); i++) {
                    visit(ast.getStatements().get(i));
                }
            }
            catch(Return deliverable) {
                scope = scope.getParent();
                return deliverable.value;
            }

            return Environment.NIL;
        };

        scope.defineFunction(ast.getName(), ast.getParameters().size(), function);

        return Environment.NIL; //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if(ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException();
        }
        else {
            Ast.Expr.Access receiver = (Ast.Expr.Access)ast.getReceiver();
            if (receiver.getReceiver().isPresent()) {
                visit(receiver.getReceiver().get()).setField(receiver.getName(), visit(ast.getValue()));
                return Environment.NIL;
            }
            scope.lookupVariable(receiver.getName()).setValue(visit(ast.getValue()));
            return Environment.NIL;
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        if(requireType(Boolean.class, visit(ast.getCondition()))) {
            scope = new Scope(scope);
            try {
                for (Ast.Stmt stmt : ast.getThenStatements()) {
                    visit(stmt);
                }
            }
            finally {
                scope = scope.getParent();
            }
        }
        else {
            scope = new Scope(scope);
            try {
                for (Ast.Stmt stmt : ast.getElseStatements()) {
                    visit(stmt);
                }
            }
            finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        Iterable<Environment.PlcObject> range = requireType(Iterable.class, visit(ast.getValue()));
        for(Object iter : range) {
            scope = new Scope(scope);
            scope.defineVariable(ast.getName(), Environment.create(((Environment.PlcObject)iter).getValue()));

            try {
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);

                }
            }
            finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                for(Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        Environment.PlcObject value = visit(ast.getValue());

        throw new Return(value);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        if(ast.getLiteral() == null) {
            return Environment.NIL;
        }

        if(ast.getLiteral() instanceof String) { return new Environment.PlcObject(Environment.Type.STRING, new Scope(null), ast.getLiteral());}
        if(ast.getLiteral() instanceof BigInteger) { return new Environment.PlcObject(Environment.Type.INTEGER, new Scope(null), ast.getLiteral());}
        if(ast.getLiteral() instanceof BigDecimal) { return new Environment.PlcObject(Environment.Type.DECIMAL, new Scope(null), ast.getLiteral());}
        if(ast.getLiteral() instanceof Character) { return new Environment.PlcObject(Environment.Type.CHARACTER, new Scope(null), ast.getLiteral());}
        if(ast.getLiteral() instanceof Boolean) { return new Environment.PlcObject(Environment.Type.BOOLEAN, new Scope(null), ast.getLiteral());}
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        switch(ast.getOperator()) {
            case "AND":
                if (requireType(Boolean.class, visit(ast.getLeft()))) {
                    if (requireType(Boolean.class, visit(ast.getRight()))) {
                        return Environment.create(true);
                    }
                }
                return Environment.create(false);
            case "OR":
                if(requireType(Boolean.class, visit(ast.getLeft()))) {
                    return Environment.create(true);
                }
                else {
                    if(requireType(Boolean.class, visit(ast.getRight()))) {
                        return Environment.create(true);
                    }
                    return Environment.create(false);
                }
            case "<":
                if(visit(ast.getLeft()).getValue().getClass() != visit(ast.getRight()).getValue().getClass()) {
                    throw new RuntimeException();
                }
                if(requireType(Comparable.class, visit(ast.getLeft())).compareTo(requireType(Comparable.class, visit(ast.getRight()))) < 0) {
                    return Environment.create(true);
                }
                return Environment.create(false);
            case "<=":
                if(visit(ast.getLeft()).getValue().getClass() != visit(ast.getRight()).getValue().getClass()) {
                    throw new RuntimeException();
                }
                if(requireType(Comparable.class, visit(ast.getLeft())).compareTo(requireType(Comparable.class, visit(ast.getRight()))) <= 0) {
                    return Environment.create(true);
                }
                return Environment.create(false);
            case ">":
                if(visit(ast.getLeft()).getValue().getClass() != visit(ast.getRight()).getValue().getClass()) {
                    throw new RuntimeException();
                }
                if(requireType(Comparable.class, visit(ast.getLeft())).compareTo(requireType(Comparable.class, visit(ast.getRight()))) > 0) {
                    return Environment.create(true);
                }
                return Environment.create(false);
            case ">=":
                if(visit(ast.getLeft()).getValue().getClass() != visit(ast.getRight()).getValue().getClass()) {
                    throw new RuntimeException();
                }
                if(requireType(Comparable.class, visit(ast.getLeft())).compareTo(requireType(Comparable.class, visit(ast.getRight()))) >= 0) {
                    return Environment.create(true);
                }
                return Environment.create(false);
            case "==":
                if(visit(ast.getLeft()).getValue().equals(visit(ast.getRight()).getValue())) {
                    return Environment.create(true);
                }
                return Environment.create(false);
            case "!=":
                if(visit(ast.getLeft()).getValue().equals(visit(ast.getRight()).getValue())) {
                    return Environment.create(false);
                }
                return Environment.create(true);
            case "+":
                if(visit(ast.getLeft()).getValue() instanceof String || visit(ast.getRight()).getValue() instanceof String) {
                    return Environment.create((String)visit(ast.getLeft()).getValue() + (String)visit(ast.getRight()).getValue());
                }
                else if(visit(ast.getLeft()).getValue() instanceof BigInteger && visit(ast.getRight()).getValue() instanceof BigInteger) {
                    BigInteger result = requireType(BigInteger.class, visit(ast.getLeft())).add(requireType(BigInteger.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                else if(visit(ast.getLeft()).getValue() instanceof BigDecimal && visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    BigDecimal result = requireType(BigDecimal.class, visit(ast.getLeft())).add(requireType(BigDecimal.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                throw new RuntimeException();
            case "-":
                if(visit(ast.getLeft()).getValue() instanceof BigInteger && visit(ast.getRight()).getValue() instanceof BigInteger) {
                    BigInteger result = requireType(BigInteger.class, visit(ast.getLeft())).subtract(requireType(BigInteger.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                else if(visit(ast.getLeft()).getValue() instanceof BigDecimal && visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    BigDecimal result = requireType(BigDecimal.class, visit(ast.getLeft())).subtract(requireType(BigDecimal.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                throw new RuntimeException();
            case "*":
                if(visit(ast.getLeft()).getValue() instanceof BigInteger && visit(ast.getRight()).getValue() instanceof BigInteger) {
                    BigInteger result = requireType(BigInteger.class, visit(ast.getLeft())).multiply(requireType(BigInteger.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                else if(visit(ast.getLeft()).getValue() instanceof BigDecimal && visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    BigDecimal result = requireType(BigDecimal.class, visit(ast.getLeft())).multiply(requireType(BigDecimal.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                throw new RuntimeException();
            case "/":
                if(visit(ast.getLeft()).getValue() instanceof BigInteger && visit(ast.getRight()).getValue() instanceof BigInteger) {
                    if(requireType(BigInteger.class, visit(ast.getRight())).equals(0)) {
                        throw new RuntimeException();
                    }

                    BigInteger result = requireType(BigInteger.class, visit(ast.getLeft())).divide(requireType(BigInteger.class, visit(ast.getRight())));
                    return Environment.create(result);
                }
                else if(visit(ast.getLeft()).getValue() instanceof BigDecimal && visit(ast.getRight()).getValue() instanceof BigDecimal) {
                    if(requireType(BigDecimal.class, visit(ast.getRight())).equals(0)) {
                        throw new RuntimeException();
                    }

                    BigDecimal result = requireType(BigDecimal.class, visit(ast.getLeft())).divide(requireType(BigDecimal.class, visit(ast.getRight())), 1, RoundingMode.HALF_EVEN);
                    return Environment.create(result);
                }
                throw new RuntimeException();
        }
        throw new RuntimeException();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()) {
            return visit(ast.getReceiver().get()).getField(ast.getName()).getValue();
        }
        return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        ArrayList<Environment.PlcObject> args = new ArrayList<>();
        for(int i = 0; i < ast.getArguments().size(); i++) {
            args.add(visit(ast.getArguments().get(i)));
        }

        if(ast.getReceiver().isPresent()) {
            /**
             * Scope object = new Scope(null);
             *         object.defineFunction("method", 1, args -> Environment.create("object.method"));
             *         scope.defineVariable("object", new Environment.PlcObject(object, "object"));
             *
             *         new Ast.Expr.Function(Optional.of(new Ast.Expr.Access(Optional.empty(), "object")), "method", Arrays.asList())
             */
            Environment.PlcObject receiver = visit(ast.getReceiver().get());

            List<Environment.PlcObject> objects = new ArrayList<>();
            for(int i = 0; i < ast.getArguments().size(); i++) {
                objects.add(visit(ast.getArguments().get(i)));
            }
            return receiver.callMethod(ast.getName(), objects);
        }

        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(args); //TODO
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}

package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        //create a "class Main {"
        //  declare fields
        //  declare "public static void main(String[] args) {
        //      System.exit(new Main().main());
        //  }"
        //  declare each of our methods
        // one of our methods is called main!

        print("public class Main {");
        newline(0);
        ++indent;
        if(!ast.getFields().isEmpty()) {
            for(int i = 0; i < ast.getFields().size(); i++) {
                if(i != 0) newline(indent);
                print(ast.getFields().get(i));
            }
            newline(0);
        }

        newline(indent);
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");
        newline(0);

        if(!ast.getMethods().isEmpty()) {
            for(int i = 0; i < ast.getMethods().size(); i++) {
                newline(indent);
                print(ast.getMethods().get(i));
                newline(0);
            }
        }

        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getTypeName(), " ", ast.getName());
        if(ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        if(!ast.getReturnTypeName().isPresent()) throw new RuntimeException();

        print(Environment.getType(ast.getReturnTypeName().get()).getJvmName(), " ",ast.getName());
        print("(");
        if(!ast.getParameters().isEmpty()) {
            for(int i = 0; i < ast.getParameters().size(); i++) {
                if(i != 0) print(", ");
                print(Environment.getType(ast.getParameterTypeNames().get(i)).getJvmName(), " ", ast.getParameters().get(i));
            }
        }
        print(") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0) newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        print(ast.getExpression(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        //write: TYPE variable_name
        //is there an assigned value? if so, write equals sign + value
        //write: ;

        print(ast.getVariable().getType().getJvmName()," ",ast.getVariable().getJvmName());
        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(),";");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (", ast.getCondition(), ") {");

        if(!ast.getThenStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getThenStatements().size(); i++) {
                if(i != 0) { newline(indent); }
                print(ast.getThenStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        if(!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);

            for(int i = 0; i < ast.getElseStatements().size(); i++) {
                if(i != 0) newline(indent);
                print(ast.getElseStatements().get(i));
            }

            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (int ", ast.getName(), " : ", ast.getValue(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        //print the while structure, including condition
        //determine if there are statements to process, if so:
            //setup the next line
            //handle all statements in the while statement body
            //check if newline and indent is needed
                //setup the next line
            //print the next statement }
        //setup the next line }
        //close the while

        print("while (", ast.getCondition(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");


        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if(ast.getType() == Environment.Type.STRING) {
            print("\"", ast.getLiteral(), "\"");
        }
        else if(ast.getType() == Environment.Type.CHARACTER) {
            print("\'", ast.getLiteral(), "\'");
        }
        else if(ast.getType() == Environment.Type.BOOLEAN) {
            if(ast.getLiteral() == (Object) true) { print("true"); }
            else { print("false"); }
        }
        else if(ast.getType() == Environment.Type.NIL) {
            print("null");
        }
        else if(ast.getType() == Environment.Type.INTEGER) {
            print(ast.getLiteral());
        }
        else if(ast.getType() == Environment.Type.DECIMAL) {
            print(ast.getLiteral()); //keep in mind precision when writing tests
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(",ast.getExpression(),")");

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        String operator = ast.getOperator();

        if(operator.equals("AND")) operator = "&&";
        else if (operator.equals("OR")) operator = "||";

        print(ast.getLeft(), " ", operator, " ", ast.getRight());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if(ast.getReceiver().isPresent()) { print(ast.getReceiver().get(), ".");}
        print(ast.getVariable().getJvmName());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if(ast.getReceiver().isPresent()){ print(ast.getReceiver().get(), ".");}
        print(ast.getFunction().getJvmName(), "(");
        if(!ast.getArguments().isEmpty()) {
            for(int i = 0; i < ast.getArguments().size(); i++) {
                if(i != 0) { print(", ");}
                print(ast.getArguments().get(i));
            }
        }
        print(")");

        return null;
    }

}

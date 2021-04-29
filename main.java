package plc.project;

import java.io.*;

public class main {

    public static void main(String[] args) throws IOException {
        /***
         * change fileName path as needed, using these functions to help define relative path:
         * File file = new File(".");
         * for(String fileNames : file.list()) System.out.println(fileNames);
         */
        String fileName = "src\\main\\java\\plc\\project\\input.txt";

        FileInputStream fis = new FileInputStream(fileName);
        byte[] buffer = new byte[10];
        StringBuilder sb = new StringBuilder();
        while(fis.read(buffer) != -1) {
            sb.append(new String(buffer));
            buffer = new byte[10];
        }
        fis.close();

        String input = sb.toString();

        input.replaceAll("\0", "");

        System.out.println("\nInput Code:\n");
        System.out.println(input);
        System.out.println("\nResult:\n");

        Lexer lexer = new Lexer(input);

        Parser parser = new Parser(lexer.lex());
        Ast.Source source = parser.parseSource();


        Interpreter interpreter = new Interpreter(null);
        interpreter.visit(source);

        Analyzer analyzer = new Analyzer(null);
        analyzer.visit(source);

        PrintWriter writer = new PrintWriter(System.out);
        Generator generator = new Generator(writer);
        generator.visit(source);

        return;
    }
}

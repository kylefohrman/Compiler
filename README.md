# Compiler
Compiler to convert custom code into working Java programs, created as a project in Spring of 2021

***Example Input Code:***
```
LET x: Integer = 0;
LET y: Integer = 2;
LET i: Integer;
DEF main(): Integer DO
	print("Hello, World!");
	i = 0;
	WHILE i < 3 DO
		x = x + i;
		print(x * y);
		i = i + 1;
	END
	RETURN 0;
END         
```
***Result:***
```
Hello, World!
0
2
6
```

##Basic Grammar
  - Field statements precede method definitions
  - Field statements are marked by "LET", method definitions begin with "DEF" and enclose their statements within a "DO END" block
  - Variable, argument and method return types must be defined, valid types are Boolean, Integer, Decimal, String, Character
  - The file must contain a method named "main" of arity 0 in order to compile

To create your own custom code, simply write it in the file "input.txt" and run main.java. Please note that input.txt's relative path may have to be changed within main (I've marked the section of code to make it as easy as possible). Additionally, the files themselves may have to be repackaged according to your project's needs.

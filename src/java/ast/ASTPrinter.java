package ast;

import java.io.PrintWriter;

public class ASTPrinter {

    private final PrintWriter writer;
    private int indentLevel = 0;  

    public ASTPrinter(PrintWriter writer) {
            this.writer = writer;
    }

    private void indent() {
        for (int i = 0; i < indentLevel; i++) {
            writer.print("  "); 
        }
    }

    private void newline() {
        writer.println();
        indent();
    }

    public void visit(ASTNode node) {
        if (node == null)
            throw new IllegalStateException("Unexpected null value");
        writer.print(node.getClass().getSimpleName()+"(");

        switch(node) {

            case FunDef fd -> {
                visit(fd.type);
                writer.print(","+fd.name);
                for (VarDecl vd : fd.params) {
                    writer.print(",");
                    visit(vd);

                }
                writer.print(",");
                visit(fd.block);
            }

            case FunDecl fd -> {
                visit(fd.type);
                writer.print(","+fd.name);
                for (VarDecl vd : fd.params) {
                    writer.print(",");
                    visit(vd);
                }
            }

            case VarDecl vd -> {
                visit(vd.type);
                writer.print(","+vd.name);
            }

            case VarExpr v -> {
                writer.print(v.name);
            }

            // to complete ...

            default -> {
                String delimiter = "";
                for (ASTNode child : node.children()) {
                    writer.print(delimiter);
                    delimiter = ",";
                    visit(child);
                }
            }
        }

        indentLevel--;
        newline();
        writer.print(")");

        switch(node) {
            case Program ignored -> {
                writer.flush(); // ensures the writer flushes all the writes at the end of our program
            }
            default -> {}
        }

    }


    
}

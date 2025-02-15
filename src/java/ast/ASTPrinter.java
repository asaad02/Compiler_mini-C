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
    if (node == null) throw new IllegalStateException("Unexpected null value");

    switch (node) {
      case BaseType bt -> {
        writer.print(bt.name());
        return;
      }

      default -> {
        indentLevel++;
        writer.print(node.getClass().getSimpleName() + "(");
        switch (node) {
          case FunDef fd -> {
            visit(fd.type);
            writer.print("," + fd.name);
            for (VarDecl vd : fd.params) {
              writer.print(",");
              newline();
              visit(vd);
            }
            writer.print(",");
            newline();
            visit(fd.block);
          }

          case FunDecl fd -> {
            visit(fd.type);
            writer.print("," + fd.name);
            newline();
            for (VarDecl vd : fd.params) {
              writer.print(",");
              newline();
              visit(vd);
            }
          }

          case VarDecl vd -> {
            visit(vd.type);
            writer.print("," + vd.name);
          }

          case VarExpr v -> {
            writer.print(v.name);
          }

          case IntLiteral i -> writer.print(i.value);

          case StrLiteral s -> writer.print(s.value);

          case ChrLiteral c -> writer.print(c.value);

          case BinOp b -> {
            visit(b.left);
            writer.print("," + b.op + ",");
            visit(b.right);
          }

          case Assign a -> {
            visit(a.left);
            writer.print(",");
            visit(a.right);
          }

          case Block blk -> {
            for (ASTNode child : blk.children()) {
              if (child != blk.children().get(0)) {
                writer.print(",");
              }
              newline();
              visit(child);
            }
          }

          case If ifs -> {
            visit(ifs.condition);
            writer.print(",");
            visit(ifs.thenBranch);
            if (ifs.elseBranch != null) {
              writer.print(",");
              visit(ifs.elseBranch);
            }
          }

          case While ws -> {
            visit(ws.condition);
            writer.print(",");
            newline();
            visit(ws.body);
            newline();
          }

          case Return rs -> {
            if (rs.expr != null) {
              visit(rs.expr);
            } else {
              writer.print("");
            }
          }

          case Continue ignored -> writer.print("");

          case Break ignored -> writer.print("");

          case ExprStmt es -> {
            visit(es.expr);
          }

          case StructTypeDecl std -> {
            visit(std.structType);
            for (VarDecl vd : std.fields) {
              writer.print(",");
              newline();
              visit(vd);
            }
          }

          case PointerType pt -> {
            visit(pt.baseType);
          }

          case StructType st -> writer.print(st.name);

          case ArrayType at -> {
            visit(at.elementType);
            writer.print("," + at.size);
          }

          case FunCallExpr fc -> {
            writer.print(fc.name);
            for (Expr arg : fc.args) {
              writer.print(",");
              newline();
              visit(arg);
            }
          }

          case TypecastExpr tc -> {
            visit(tc.type);
            writer.print(",");
            newline();
            if (tc.expr == null) {
              writer.print("");
            } else {
              visit(tc.expr);
            }
          }

          case SizeOfExpr sz -> {
            visit(sz.type);
          }

          case ValueAtExpr va -> {
            visit(va.expr);
          }

          case AddressOfExpr ao -> {
            visit(ao.expr);
          }

          case ArrayAccessExpr aa -> {
            visit(aa.array);
            writer.print(",");
            visit(aa.index);
          }

          case FieldAccessExpr fa -> {
            visit(fa.structure);
            writer.print("," + fa.field);
          }

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
        writer.print(")");
      }
    }

    switch (node) {
      case Program ignored -> {
        writer.flush(); // ensures the writer flushes all the writes at the end of our program
      }
      default -> {}
    }
  }
}

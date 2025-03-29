package gen;

import ast.*;
import gen.asm.*;
import java.util.List;
import java.util.Stack;

/**
 * Generates assembly code for statements, including: Control structures (if/else, while loops)
 * Blocks and expression statements Function returns Break and continue support for nested loops
 */
public class StmtCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  private final FunDef currentFunctionDef;
  private final Stack<LoopLabels> loopStack = new Stack<>();
  private final List<String> definedFunctions;

  /** class to store labels for break and continue statements inside loops. */
  private static class LoopLabels {
    final Label start;
    final Label end;

    LoopLabels(Label start, Label end) {
      this.start = start;
      this.end = end;
    }
  }

  public StmtCodeGen(
      AssemblyProgram asmProg,
      MemAllocCodeGen allocator,
      FunDef currentFunctionDef,
      List<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.currentFunctionDef = currentFunctionDef;
    this.definedFunctions = definedFunctions;
  }

  /** Dispatches statement processing based on statement type. */
  void visit(Stmt s) {
    System.out.println("[StmtCodeGen] Processing statement: " + s.getClass().getSimpleName());

    switch (s) {
      case Block b -> handleBlock(b);
      case ExprStmt es -> handleExprStmt(es);
      case If i -> handleIf(i);
      case While w -> handleWhile(w);
      case Return rs -> handleReturn(rs);
      case Continue c -> handleContinue(c);
      case Break b -> handleBreak(b);
      default ->
          throw new UnsupportedOperationException(
              "[StmtCodeGen] Unsupported statement type: " + s.getClass().getSimpleName());
    }
  }

  // Handles block statements by recursively visiting each statement inside the block.
  private void handleBlock(Block b) {
    System.out.println("[StmtCodeGen] Entering block...");
    allocator.enterScope();

    // allocate variables declared in this block
    for (VarDecl vd : b.vds) {
      allocator.allocateVariable(vd);
    }

    // process statements within the block
    for (Stmt stmt : b.stmts) {
      visit(stmt);
    }

    allocator.exitScope();
    System.out.println("[StmtCodeGen] Exiting block.");
  }

  // handles expression statements (assignments, function calls, increments, and decrements).
  private void handleExprStmt(ExprStmt es) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    // Evaluate Expression
    new ExprValCodeGen(asmProg, allocator, definedFunctions).visit(es.expr);
  }

  /** Handles if else statements by generating conditional branching. */
  private void handleIf(If i) {
    System.out.println("[StmtCodeGen] Processing if statement...");
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    ExprValCodeGen exprGen = new ExprValCodeGen(asmProg, allocator, definedFunctions);

    // Generate unique labels for else and end
    Label elseLabel =
        i.elseBranch != null ? Label.create(currentFunctionDef.name + "_if_else") : null;
    Label endLabel = Label.create(currentFunctionDef.name + "_if_end");

    // Evaluate condition
    Register condReg = exprGen.visit(i.condition);
    if (elseLabel != null) {
      text.emit(OpCode.BEQ, condReg, Register.Arch.zero, elseLabel);
    } else {
      text.emit(OpCode.BEQ, condReg, Register.Arch.zero, endLabel);
    }

    // Then branch
    visit(i.thenBranch);
    text.emit(OpCode.J, endLabel);

    // Emit else label properly
    if (elseLabel != null) {
      text.emit(elseLabel);
      visit(i.elseBranch);
    }

    text.emit(endLabel);
  }

  // Handles while loops by generating loop start, condition check, and loop body. */
  private void handleWhile(While w) {
    System.out.println("[StmtCodeGen] Processing while loop...");
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();

    // Generate unique labels for condition check, loop body, and loop exit
    Label conditionLabel = Label.create(currentFunctionDef.name + "_loop_cond");
    Label startLabel = Label.create(currentFunctionDef.name + "_loop_start");
    Label endLabel = Label.create(currentFunctionDef.name + "_loop_end");

    // Push loop labels for break/continue handling
    loopStack.push(new LoopLabels(conditionLabel, endLabel));

    // While loop condition check first
    text.emit(conditionLabel);
    Register condReg = new ExprValCodeGen(asmProg, allocator, definedFunctions).visit(w.condition);

    // If condition is false, exit loop
    text.emit(OpCode.BEQZ, condReg, endLabel);

    // Loop body
    text.emit(startLabel);
    visit(w.body);

    // Jump back to condition check
    text.emit(OpCode.J, conditionLabel);

    text.emit(endLabel);
    loopStack.pop();
    System.out.println("[StmtCodeGen] Exiting while loop.");
  }

  private void handleReturn(Return rs) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    System.out.println("[StmtCodeGen] Processing return statement...");

    if (rs.expr != null) {
      ExprValCodeGen valGen = new ExprValCodeGen(asmProg, allocator, definedFunctions);
      ExprAddrCodeGen addrGen = new ExprAddrCodeGen(asmProg, allocator, definedFunctions);

      if (rs.expr.type instanceof StructType structType) {
        int structSize = allocator.computeSize(structType);

        Register returnAddr = Register.Arch.a0; // or load from $fp + offset if full compliance

        // Allocate memory for struct return
        text.emit(OpCode.ADDIU, returnAddr, Register.Arch.sp, -structSize);
        text.emit(OpCode.ADDU, Register.Arch.v0, returnAddr, Register.Arch.zero);

        Register srcReg = addrGen.visit(rs.expr);
        for (int offset = 0; offset < structSize; offset += 4) {
          Register temp = Register.Virtual.create();
          text.emit(OpCode.LW, temp, srcReg, offset);
          text.emit(OpCode.SW, temp, returnAddr, offset);
        }

      } else if (rs.expr.type instanceof ArrayType) {
        // Arrays are returned by reference — just return address
        Register addrReg = addrGen.visit(rs.expr);
        text.emit(OpCode.ADDU, Register.Arch.v0, addrReg, Register.Arch.zero);

      } else {
        // Primitive return
        Register resultReg = valGen.visit(rs.expr);
        text.emit(OpCode.ADDU, Register.Arch.v0, resultReg, Register.Arch.zero);
      }
    }

    text.emit(OpCode.J, Label.get("func_epilogue_" + currentFunctionDef.name));
  }

  // handles continue statements by jumping to the start of the nearest enclosing loop.
  private void handleContinue(Continue c) {
    System.out.println("[StmtCodeGen] Processing continue statement...");
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("[StmtCodeGen] ERROR: Continue statement outside loop.");
    }
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    text.emit(OpCode.J, loopStack.peek().start);
  }

  // Handles break statements by jumping to the end of the nearest enclosing loop.
  private void handleBreak(Break b) {
    System.out.println("[StmtCodeGen] Processing break statement...");
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("[StmtCodeGen] ERROR: Break statement outside loop.");
    }
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    text.emit(OpCode.J, loopStack.peek().end);
  }
}

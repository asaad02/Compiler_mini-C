package gen;

import ast.*;
import gen.asm.*;
import java.util.Stack;

/**
 * Generates assembly code for statements, including: Control structures (if/else, while loops)
 * Blocks and expression statements Function returns Break and continue support for nested loops
 */
public class StmtCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  private final FunDef currentFunctionDef;
  private final Stack<LoopLabels> loopStack = new Stack<>();

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
      AssemblyProgram asmProg, MemAllocCodeGen allocator, FunDef currentFunctionDef) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.currentFunctionDef = currentFunctionDef;
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

  /** handles block statements by recursively visiting each statement inside the block. */
  private void handleBlock(Block b) {
    System.out.println("[StmtCodeGen] Entering block...");
    // alocate all variable inside the block
    b.vds.forEach((vd) -> allocator.visit(vd));
    b.stmts.forEach(this::visit);
    System.out.println("[StmtCodeGen] Exiting block.");
  }

  /** handles expression statements (e.g., function calls, assignments). */
  private void handleExprStmt(ExprStmt es) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    Register result = new ExprValCodeGen(asmProg, allocator).visit(es.expr);

    if (!(es.expr instanceof FunCallExpr)) {
      text.emit(OpCode.ADDU, Register.Arch.zero, result, Register.Arch.zero);
    }
  }

  /** Handles if else statements by generating conditional branching. */
  private void handleIf(If i) {
    System.out.println("[StmtCodeGen] Processing if statement...");
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    ExprValCodeGen exprGen = new ExprValCodeGen(asmProg, allocator);

    // Generate unique labels for else and end
    Label elseLabel = Label.create(currentFunctionDef.name + "_if_else");
    Label endLabel = Label.create(currentFunctionDef.name + "_if_end");

    // Evaluate condition
    Register condReg = exprGen.visit(i.condition);
    text.emit(OpCode.BEQ, condReg, Register.Arch.zero, elseLabel);

    // Then branch
    visit(i.thenBranch);
    // Jump to end after then-block
    text.emit(OpCode.J, endLabel);

    // Else branch (only emit if there's an else branch)
    text.emit(elseLabel);
    if (i.elseBranch != null) visit(i.elseBranch);

    // End of if statement
    text.emit(endLabel);
  }

  /** Handles while loops by generating loop start, condition check, and loop body. */
  private void handleWhile(While w) {
    System.out.println("[StmtCodeGen] Processing while loop...");
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();

    // Generate unique labels for loop start and end
    Label startLabel = Label.create(currentFunctionDef.name + "_loop_start");
    Label endLabel = Label.create(currentFunctionDef.name + "_loop_end");

    // Push loop labels for break/continue handling
    loopStack.push(new LoopLabels(startLabel, endLabel));

    // Loop condition check
    text.emit(startLabel);
    Register condReg = new ExprValCodeGen(asmProg, allocator).visit(w.condition);
    text.emit(OpCode.BEQ, condReg, Register.Arch.zero, endLabel);

    // Loop body
    visit(w.body);
    // Jump back to start
    text.emit(OpCode.J, startLabel);

    // Loop exit
    text.emit(endLabel);
    loopStack.pop();
    System.out.println("[StmtCodeGen] Exiting while loop.");
  }

  /** Handles return statements, including returning values. */
  private void handleReturn(Return rs) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    System.out.println("[StmtCodeGen] Processing return statement...");

    // save function result in $v0 if returning a value
    if (rs.expr != null) {
      Register resultReg = new ExprValCodeGen(asmProg, allocator).visit(rs.expr);
      text.emit(OpCode.ADDU, Register.Arch.v0, resultReg, Register.Arch.zero);
    }
  }

  /** Handles continue statements by jumping to the start of the nearest enclosing loop. */
  private void handleContinue(Continue c) {
    System.out.println("[StmtCodeGen] Processing continue statement...");
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("[StmtCodeGen] Continue outside loop");
    }
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    // Ensure stack is aligned before jumping back to loop start
    Register tempReg = Register.Virtual.create();
    Register negTempReg = Register.Virtual.create();

    text.emit(OpCode.ANDI, tempReg, Register.Arch.sp, 15); // tempReg = sp % 16
    text.emit(
        OpCode.BEQ,
        tempReg,
        Register.Arch.zero,
        loopStack.peek().start); // If already aligned, jump
    // negTempReg = -tempReg
    text.emit(OpCode.SUBU, negTempReg, Register.Arch.zero, tempReg);
    // Correct misalignment
    text.emit(OpCode.ADDU, Register.Arch.sp, Register.Arch.sp, negTempReg);
    // Jump to loop start
    text.emit(OpCode.J, loopStack.peek().start);
  }

  /** Handles break statements by jumping to the end of the nearest enclosing loop. */
  private void handleBreak(Break b) {
    System.out.println("[StmtCodeGen] Processing break statement...");
    if (loopStack.isEmpty()) {
      throw new IllegalStateException("[StmtCodeGen] Break outside loop");
    }
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    // Jump to loop end
    text.emit(OpCode.J, loopStack.peek().end);
  }
}

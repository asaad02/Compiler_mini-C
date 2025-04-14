# Part V: Object-Oriented Features


The goal of part V is to extend your compiler to support a small set of object-oriented features.
Since the interface to the compiler is not changing in the Main file, we will reuse Main4.java for this part.

### 1. Class Definition: 

A Class is a user-defined data type with two type of members: fields and functions.
A class is declared using the `class` keyword.
To simplify, we will assume that there is no access modifiers (e.g. public, private) and that every member is public.

We also assume that there is a unique implicit constructor for each class automatically defined.
The implicit constructor takes no arguments and simply allocate space for storing the data corresponding to the instantiated object.
The following code is an example of a valid class definition in our language:

```C
class Course {
    char name[20];
    int credit;
    int courseWorkScore;

    void whereToAttend(){
        print_s((char*)"Not determined! The course will be held virtually or in person!\n");
    }
    int hasExam(){
        if(courseWorkScore == 100)
            return 0;
        else
            return 1;
    }
}
```

### 2. Class Instantiation:

No memory is allocated upon the definition of a class.
Classes are like blueprints for objects, meaning that allocation only happens when a class is instantiated.
Instantiation is done using the `new` keyword, followed by the type of the class (which always starts with `class`, similarly to structures) and a set of empty parenthesis to call the default constructor.

An example of a class instantiation is given below:

```C
void main(){
    class Course comp520;
    comp520 = new class Course();
}
```
You can see that even though there is no constructor defined in the `Course` body, the object `comp520` is instantiated using the implicit default class constructor.

### 3. Field Access:

Since all class fields are public, they can be accessed directly and the following is valid:

```C
int credit;
credit = comp520.credit; // field access
comp520.credit = 4;      // field assignment
```


### 4. Instance Method Call:

Like field access, you can call an object's method anywhere:
```C
if(comp520.hasExam())
    print_s((char*)"Be ready for the exam!\n");
else
    print_s((char*)"Be ready for the project implementation!:)\n");
```

### 5. Class inheritance:

The capability of a class to derive properties and characteristics from another class is called "Inheritance".
Inheritance is indicated using `extends` followed by the parent's name in the class signature: 
```C
class VirtualCourse extends Course {
    char zoomLink[200];
}
```
Our language only supports single inheritance: a class can only inherit from at most one class.
Please that is illegal to inherit recursively: a class cannot have itself as one of its ancestor.
Any parent class must be declared fully first before it can be inherited from. 

#### 5.1. Overriding methods

Subclasses can override methods from their ancestor.
If a class has a method with the same name as a method in an ancestor class, then the ancestor's method is override.
When overriding, the type signature of the override method must match exactly that of the overriding method (i.e same return type, same number of arguments, and same arguments' types).

#### 5.2. Fields

Note that only functions can be overridden, and it is forbidden to override a field in a subclass if it already exists in an ancestor class.
In other words, a subclass is not allowed to have a field with the same name as one in an ancestor class.

### 6. Class Passing by Reference:

To simplify the implementation, similar to Java and unlike C++, we assume that class objects are simply references that point to the memory heap. 
This means that `comp520` in the previous example is only the reference to its object, so there is no need to use `&` or `*` for referencing and dereferencing class objects, respectively.
Using these two operators, `&` and `*` only influence the class's reference, not the object.
It results that objects are passed by reference when a function is called.


#### 6.1. EQ/NEQ Binary Expression: 

When comparing two expression of class type, you should compare if the operands points to the same location in memory. 

#### 6.2. SizeOf Operator:

The `sizeof` an class object should be the same as the size of a pointer.

#### 6.3. Assignment Statement: 

Assignment between expression of type class should be treated like pointer assignment (unlike structs).

Be aware that when assigning between two objects, their type must be equal.
If one class is a subtype of another class, you will have to use a typecast operation explicitly since our language is strongly typed.


### 7. Virtual dispatch:

Our language supports virtual dispatch when function are called on some objects.
The function being called depends on the actual runtime type of the object and not its static type.

You should implement this mechanism using virtual tables as seen in class.
Consider the following example:
```C
class VirtualCourse extends Course {
    char zoomLink[200];
    int isOnZoom;    
    
    void whereToAttend(){
        print_s((char*)"The course is going to be held on Zoom!\n")
    }
}

int main() {
    class Course course;
    class Course vcourse;
    
    course = new class Course();
    vcourse = (class Course) new class VirtualCourse();

    course.whereToAttend();
    vcourse.whereToAttend();
    return 0;
}
```
In the above example, `VirtualCourse` overrides the `whereToAttend` method of `Course`.
So, the code produces the following output:

```C
Not determined! The course will be held virtually or in person!
The course is going to be held on Zoom!
```

### 8. Typecasting and Subtyping:

Our compiler support typecasting, in which you can assign an ancestor's type to its descendants' class objects.
This feature is called "Upcasting".
We do not support "Downcasting", and it is forbidden to assign a descendant's class type to its ancestors.
To avoid any ambiguity in the grammar between parenthesis for arithmetic operations and class typecasting, you should use the `class` keyword followed by the class name inside the parenthesis for typecasting.

Here is an example of type casting taken from the example above:
```C
course = (class Course) vcourse;
```
Once typecasting is done, you can no longer access fields and methods declared in the original child class.
You can only call methods, and access fields declared in the newly-assigned class type and its ancestors.
In this piece of code, the first statement is valid while the second is not.
```C
course.credit = 4; //Valid
course.isOnZoom = 1; //Invalid
```
Subtyping refers to the compatibility of the types.
In general in object-oriented languages, if an expression of type class A is expected, and you provided an expression of type class B, where class B is a subtype of class A (i.e. class B directly or indirectly inherit from class A), this is valid.
However, since our language is strongly typed, you have to explicit cast between subtypes.
For example, if a function receives A as an argument, and you want to pass B object to it, you have to use the typecast operator `(A) b` explicitly and pass the object to the function.


To support all these new language features, you will have to make some modifications to your compiler.
Here is a summary of the changes you need to made followed by more details:

1. **Lexer:** Add new tokens (CLASS, NEW, EXTENDS) and update your tokenizer.
2. **Parser:** Update your parsed based on the new syntax grammar.
3. **Name Analyzer:** Add new symbols for class declaration and instantiation and update your symbol tables.
4. **Type Checker:** Make sure that accessed fields and called functions exist in the class or its ancestors, ensuring newly added typing rules are followed, and support class typecasting.
5. **Code Generator:** Create virtual tables, implement dynamic dispatch, build object layout, and pass implicit pointers to class function calls.
6. **Register Allocator:** Nothing to change.

Please note that there is no partial implementation provided for this phase.
You have to modify your current compiler based on the description, and updated grammars and typing-rules.


## 0. Our Language!


Since the C compiler is not designed to support object-oriented programming (OOP), these features are borrowed from C++ and Java. These web tutorials are a good starting point if you are not familiar with [C++](https://www.cplusplus.com/doc/tutorial/) or [Java](https://www.javatpoint.com/java-oops-concepts).
You do not have to read all of them since a subset of OOP features are considered for this project and some features, like reference passing, are not entirely compatible with either C++ or Java.
Pay special attention to features described earlier or provided in the EBNF grammar.

Note that there is no standard compiler available to support the syntax and features we considered in this phase.
However, you can check the output of your code in C++ or Java standard compilers by altering the input program to meet our language definition.
This code modification might be a little tricky, but it is a good practice for you to understand the differences between two languages and our modified language.

## 1. Lexing

You have to introduce new tokens `CLASS`, `EXTENDS`, and `NEW` to tokenize the `class`, `extends`, and `new` keywords, respectively.
Then, you have to alter the next() method to detect newly introduced keywords.
Note again that you are **not** allowed to use [the Java regular expression matcher](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html) in your implementation!

## 2. Grammar 

To support OOP in your compiler, you have to add additional rules to your grammar.
You can see these changes in newly-updated EBNF grammar.
Any new modification is indicated with the comment `#part V` in the grammar file.
Some important notes regarding EBNF grammar are listed below:

- **Class Declaration:** This rule indicates a class definition.
- **Instance Function Call:** This rule is dedicated to the instance function calls. 
- **Object Field Access:** Notably, object field access is similar to structure field member access. So, there is no need to change the grammar file, and you have to take care of it later.
- **Class Type:** The updated grammar has a new type named `classtype,` an identifier indicating a declared class in the program.
- **New Instance:** This rule is used to produce an expression for instantiating a new class object.

Your next job is transforming the updated grammar into an equivalent context-free LL(k) grammar like what you did in part I.
Do not forget to ensure that you eliminate left-recursion from the grammar and, if needed, left-factorize it.
Since the changes to the grammar are relative few, you should be able to patch ut your existing massaged grammar without restarting from scratch.

## 3. Parser

In this step, you should extend your `Parser`-class to determine object-oriented programmed source code is syntactically correct.
As before, make sure you use the `error`-method in the class to report errors correctly!

## 4. Operator precedence and associativity

`.` operator has the precedence of 1 and is dedicated to class/struct field access and class function call.
In the updated grammar, eliminate left recursion, and like part II, ensure that dot,`.`, operator precedence is respected for newly-added object-oriented features.
Again you can start from your already massaged grammar and make slight changes rather than starting from scratch.

The `new` operator has the same precedence level as the unary `+` operator with Right-to-left associativity.

## 5. AST Nodes

In this step, you should add some AST nodes to your compiler based on the newly-updated provided abstract grammar file.
Like before, ensure that the design of your classes follows the abstract grammar.
New rules are indicated using the comment `#Part V` in the [abstract grammar file](./abstract_grammar.txt).
Please pay special attention to inheritance while adding new AST nodes.
You will probably want to store the class's parent in its AST node to be able to access it later (or use an external data structures that maps all the classes declared to their parent). 

## 6. Parser Modification

Similar to Part II, update your parser to create and return the newly-added AST nodes while it parses object-oriented-related parts of your input program.

## 7. AST Printer

We will not mark based on the AST printer; however, modifying your AST printer to support OOP features would be helpful to verify your AST construction.

## 8. Name Analysis

A class can have multiple fields and method declarations inside its scope and inherits from another class.
The body of a class cannot redeclare fields and methods with the same name.
Class declarations are made in the global scope.

 


## 9. Type Analysis

You must make sure each declared class has a unique name in your type analyzer.
Then, you must ensure that when facing a class function call, or field access, the function or field exists in the corresponding class declaration or its parents.
This note is the essential part of object-oriented programming language type checking, in which you must have to check all ancestors of a class while analyzing field accesses or function calls.

The typing rules have been updated and can be accessed [here](./typing-rules/rules.pdf).
All new or updated rules are written in red.
The `<:` operator indicates subtyping: if `T <: U`, it means that `T` is either the class `U` or a descendant of `U`.
You should update your `sem.TypeCheckVisitor` to take into account all the newly-added/updated rules.



#### Checking lvalues:
Please note that *lvalues* have not been changed in the modified grammar.
However, be aware that FieldAccessExpr is now used for both struct and class.


## 10. Virtual Table Creation

Before heading to the code generation part, you have to create virtual tables for your classes' functions to support dynamic dispatch.
To simplify its concept, we break it down into multiple stages to implement:

### 10.1. Label Creation:
For each method in each class, we need a unique label to emit.
In this step, you should traverse your class declaration nodes and generate a label for each declared method inside their bodies. 

### 10.2. Virtual Table Creation:
Since the virtual table is shared among all objects instantiated from the same class, we need one per class and a pointer in the object to point to the corresponding "vtable."
To do so, traversing all class declaration nodes, do the following steps:

#### Step I
For each class, find all its ancestors recursively.
#### Step II
When you reach an ancestor who does not have any parents, start to collect its methods' labels in the order of declaration.
Consider a map of the function name to the function label to simplify the label collection.
Maintaining the order of elements in the virtual table when inserting is vital; you can use a `LinkedHashMap` for instance in Java to maintain a fixed position for all methods.
#### Step III
Then, traverse the subtree downward to reach the original class declaration.
While traversing, append each new declaration to your method label map with its corresponding label.
Because we should call the most-recently-overridden function for a class, if you visit a previously-declared method, you should update its element to the newly-visited method's label.
Stop this process whenever you visit all the declared functions in the corresponding class declaration and its ancestors.
#### Step IV
Now, you have your virtual table for your class.
Treat these tables like globally-declared variables and declare them after the global variable declarations in the assembly program. 
#### Step V
Each class object must have a pointer to this table's first element.
To do so, you can add a field to the `ClassType` AST node, which stores the address of the first element of the virtual table.
Doing so, upon each function call on an object, you should:

*  First retrieve the class object's virtual table pointer;
*  Then get the label (address) of the corresponding function for a subroutine call; and
*  Finally, `jump&link` to the subroutine.


## 11. Code Generation

After creating virtual tables and setting pointers in their corresponding class objects, you should generate code for object-oriented-related parts in the input program.
The trickiest part of this process is dedicated to dynamic dispatch, which was explained in the previous section.
Now, we should take care of class variables in programs.
For each ClassType variable, we have a struct-like data type with an array-like pointer.
You should follow the policy of storing structures but treat the variable similar to an array and pass its pointer to functions.
Doing so, executing operations like `class assignment`, `EQ/NEQ`, `sizeof` will be straightforward. 

**Important**: When a function is declared inside a class, it may access the class's fields and change their values.
In doing so, the function needs a pointer to its corresponding object layout while it is being called to know the exact location of fields.
So, You must pass the object class, which is actually a pointer, to the object's function when called.
To simplify your implementation, while generating code for class functions calls, always pass its corresponding object pointer as an implicit first argument to the function.

**Tip**:, you could write a pass which modifies your AST to add the reference to the object to every function call on an object, and then use this reference to access the field of your class within the function.
This will keep the logic of your code generator simpler and lead to a cleaner design.

To produce code for class object variables, first, we have to create the object layout for them with a similar procedure we did for method dispatch.
As mentioned in lectures, every field x is at the same offset from the start of the object in one arbitrary class and its parent!
So, with a minor modification, you have to implement label creation and virtual tables supporting (steps 1 to 3) for objects' fields.
The difference is that we do not have redeclared fields in class inheritance.
So, you must append any field declaration you see and its label to the map while traversing the tree downward.

You will have an object layout containing all declared fields' labels in the object's body and its ancestors' with the same offset for each object class.
Then, you only need to pass the pointer to the first field of this object as `instance ptr`.
Whenever a field value modification is done, you retrieve the corresponding field address from the object layout based on its pointer and update the field's value. 

## 12. Register Allocation

If you reach this step, it means you are done! **Congratulations!**
There is no need to change your register allocator!

You deserve a good break now, and enjoy your newly extended compiler! :)
Oh, and one last thing, there will be no hidden tests for this part and your score will only depend on the visible tests on the scoreboard!

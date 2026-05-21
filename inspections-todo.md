# Java Inspections TODO

This file is a curated backlog of JetBrains Inspectopedia inspections for core Java. It is intentionally centred on the
Java language, type system, control flow, initialisation, modules, exceptions, visibility, numeric semantics,
serialization, and other standard-library-adjacent correctness checks, while excluding most framework-specific areas
like JUnit, TestNG, Lombok, Hibernate-specific style rules, and similar ecosystem-only buckets.

Primary source: JetBrains Inspectopedia Java catalogue and category pages.

Status legend:

- `[x]` implemented in the current Railroad inspection engine
- `[-]` partially implemented or only covered for a narrower subset of cases
- `[ ]` not implemented yet

Difficulty legend:

- `[starter]` approachable for non-experts; mostly local syntax/AST checks
- `[easy]` local AST checks with a little surrounding context
- `[medium]` usually needs symbol resolution or modest semantic reasoning
- `[hard]` usually needs control-flow, data-flow, type-system, inheritance, or project-wide reasoning
- Unmarked items should currently be treated as `[hard]`

Relevant source pages:

- https://www.jetbrains.com/help/inspectopedia/Java.html
- https://www.jetbrains.com/help/inspectopedia/Java-Abstraction-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Assignment-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Class-structure.html
- https://www.jetbrains.com/help/inspectopedia/Java-Compiler-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Control-flow-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Data-flow.html
- https://www.jetbrains.com/help/inspectopedia/Java-Declaration-redundancy.html
- https://www.jetbrains.com/help/inspectopedia/Java-Encapsulation.html
- https://www.jetbrains.com/help/inspectopedia/Java-Error-handling.html
- https://www.jetbrains.com/help/inspectopedia/Java-Imports.html
- https://www.jetbrains.com/help/inspectopedia/Java-Inheritance-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Initialization.html
- https://www.jetbrains.com/help/inspectopedia/Java-Java-language-level-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Numeric-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Resource-management.html
- https://www.jetbrains.com/help/inspectopedia/Java-Serialization-issues.html
- https://www.jetbrains.com/help/inspectopedia/Java-Visibility.html
- https://www.jetbrains.com/help/inspectopedia/Java-Verbose-or-redundant-code-constructs.html
- https://www.jetbrains.com/help/inspectopedia/Java-Probable-bugs.html

## Railroad semantic baseline already implemented

These are core semantic/compiler-style checks already present in the codebase, even when they do not map 1:1 to a single
Inspectopedia label in the sections below.

- [x] [medium] `Duplicate declaration` - Detects duplicate symbols declared in the same effective scope.
- [x] [medium] `Unresolved name` - Detects identifiers that fail semantic resolution.
- [x] [starter] `Duplicate import` - Detects repeated imports of the same target.
- [x] [medium] `Ambiguous import` - Detects conflicting imports with the same simple name.
- [x] [medium] `Unresolved import` - Detects imports whose type/package/member target cannot be resolved.
- [x] [medium] `Unresolved type` - Detects type references that do not resolve semantically.
- [x] [medium] `Unresolved member` - Detects field/member references that do not resolve.
- [x] [medium] `Unresolved call` - Detects method invocations and constructor calls that do not resolve.
- [x] [hard] `Inaccessible type` - Detects type references that violate Java accessibility rules.
- [x] [hard] `Inaccessible member` - Detects member references that violate Java accessibility rules.
- [x] [hard] `Inaccessible call` - Detects calls to inaccessible methods or constructors.
- [x] [medium] `Invalid inheritance` - Detects illegal `extends`/`implements` relationships.
- [x] [hard] `Missing implementation` - Detects concrete types that fail to implement inherited abstract methods.
- [x] [hard] `Invalid override` - Detects override errors involving final/static/access/return/throws compatibility.
- [x] [medium] `Illegal modifier usage` - Detects invalid modifier combinations and placement.
- [x] [hard] `Missing return on some paths` - Detects non-void methods that do not return on every path.
- [x] [medium] `Incompatible assignment` - Detects assignments and initialisers with incompatible types.
- [x] [hard] `Variable might not have been initialized` - Detects definite-assignment failures for local variables.
- [x] [medium] `Illegal final assignment` - Detects writes to final variables in illegal contexts.
- [x] [hard] `Final field might not be initialized` - Detects final fields that are not definitely assigned.

## Abstraction issues

- [ ] [easy] `'instanceof' check for 'this'` - Detect `this instanceof ...` or equivalent class checks that usually
  indicate misplaced polymorphism.
- [ ] [easy] `'Optional' used as field or parameter type` - Flag `Optional` as a field or parameter type where it
  complicates APIs or object state.
- [x] [hard] `'public' method not exposed in interface` - Find public class methods that are not represented in an
  interface contract.
- [ ] [starter] `'public' method with 'boolean' parameter` - Detect public APIs that use booleans as behaviour switches.
- [ ] [easy] `Chain of 'instanceof' checks` - Find if-else type dispatch chains that should likely become polymorphism
  or pattern matching.
- [ ] [hard] `Class references one of its subclasses` - Detect superclass knowledge of subclasses, which inverts the
  abstraction direction.
- [ ] [easy] `Collection declared by class, not interface` - Prefer `List`/`Set`/`Map`-style abstractions over concrete
  collection types in declarations.
- [x] [hard] `Feature envy` - Find code that mostly manipulates another object's data instead of its own.
- [x] [medium] `Interface method clashes with method in 'Object'` - Detect interface methods that conflict with `Object`
  protocol expectations.
- [ ] [easy] `Magic number` - Identify unnamed numeric literals that should become named constants.
- [x] [medium] `Overly strong type cast` - Detect casts stronger than necessary for the operations performed.
- [ ] [medium] `Private method only used from inner class` - Find private helpers that are effectively scoped for
  inner-type use only.
- [ ] [hard] `Static member only used from one other class` - Detect static members whose usage suggests relocation.
- [ ] [hard] `Type may be weakened` - Replace concrete or overly-specific types with weaker abstractions where valid.
- [ ] [easy] `Use of concrete class` - Detect APIs and declarations that depend on implementations instead of
  interfaces.

## Assignment issues

- [ ] [starter] `'null' assignment` - Report assignments that explicitly write `null` outside a declaration.
- [ ] [easy] `Assignment can be replaced with operator assignment` - Detect `x = x + y` forms that can become compound
  assignment.
- [ ] [easy] `Assignment to 'catch' block parameter` - Flag mutation of a caught exception variable.
- [ ] [easy] `Assignment to 'for' loop parameter` - Detect loop-variable mutation inside enhanced-for bodies.
- [ ] [easy] `Assignment to lambda parameter` - Flag writes to lambda parameters.
- [ ] [easy] `Assignment to method parameter` - Detect mutation of incoming method parameters.
- [ ] [easy] `Assignment to static field from instance context` - Find instance methods that mutate class state.
- [ ] [starter] `Assignment used as condition` - Detect assignments embedded in `if`, `while`, `for`, `do`, or ternary
  conditions.
- [ ] [medium] `Constructor assigns value to field defined in superclass` - Flag subclass constructors mutating
  inherited state directly.
- [ ] [starter] `Nested assignment` - Detect assignment expressions used inside larger expressions.
- [ ] [easy] `Result of '++' or '--' used` - Flag pre-/post-increment results used as expression values rather than
  standalone statements.

## Class structure

- [ ] [starter] `'private' method declared 'final'` - Detect meaningless `final` on private methods.
- [ ] [hard] `'public' constructor can be replaced with factory method` - Identify constructors where a factory-style
  API may be preferable.
- [ ] [starter] `'static' method declared 'final'` - Detect meaningless `final` on static methods.
- [ ] [starter] `'static', non-'final' field` - Flag mutable static state.
- [ ] [medium] `Abstract 'class' may be 'interface'` - Identify abstract classes with interface-like shape.
- [ ] [hard] `Anonymous class can be replaced with inner class` - Detect anonymous classes better modelled as named
  nested types.
- [ ] [easy] `Class is closed to inheritance` - Track final classes and inheritance restrictions.
- [-] [easy] `Class name differs from file name` - Implemented for public top-level types; broader file/type structural
  cases are still missing.
- [ ] [easy] `Class with only 'private' constructors should be declared 'final'` - Flag utility-like classes that still
  allow subclassing.
- [ ] [easy] `Constant declared in 'abstract' class` - Detect public constants living in abstract classes.
- [ ] [easy] `Constant declared in interface` - Flag interface constants as a structure smell.
- [ ] [starter] `Empty class` - Detect empty type declarations and empty Java files.
- [x] [hard] `Field can be local variable` - Find fields whose lifetime can be reduced to a local variable.
- [ ] [starter] `Inner class of interface` - Detect nested classes declared inside interfaces.
- [x] [medium] `Interface may be annotated as '@FunctionalInterface'` - Find SAM interfaces that should declare intent
  explicitly.
- [ ] [easy] `Local class` - Flag local class declarations inside methods or blocks.
- [ ] [easy] `Marker interface` - Detect interfaces with no members, often replaceable with annotations or other
  mechanisms.
- [-] [medium] `Method can't be overridden` - Partially covered via invalid-override checks, especially
  final/static/access-signature conflicts.
- [ ] [starter] `Multiple top level classes in single file` - Flag multiple top-level types in one compilation unit.
- [ ] [easy] `No-op method in 'abstract' class` - Find empty methods in abstract base types that may hide required
  behaviour.
- [ ] [starter] `Non-'static' initializer` - Detect instance initialiser blocks.
- [ ] [easy] `Non-final field in 'enum'` - Flag mutable enum instance state.
- [ ] [medium] `Singleton` - Detect singleton implementations for review and intentionality.
- [ ] [easy] `Utility class` - Identify classes made entirely of static utilities.
- [ ] [medium] `Utility class can be 'enum'` - Detect utility classes that can use enum-singleton mechanics.
- [ ] [easy] `Utility class is not 'final'` - Flag utility classes that still permit subclassing.
- [ ] [easy] `Utility class with 'public' constructor` - Detect utility types that can be instantiated.
- [ ] [easy] `Utility class without 'private' constructor` - Require constructors to prevent instantiation.
- [ ] [hard] `Value passed as parameter never read` - Find parameters whose incoming value is ignored and replaced.

## Compiler issues

- [x] [medium] `Illegal modifier usage` - Detects invalid modifier combinations and placements across types, fields,
  methods, constructors, locals, parameters, and record components.
- [ ] [hard] `Javac quirks` - Mirror known `javac` incompatibilities, performance issues, and corner cases.
- [ ] [easy] `Preview Feature warning` - Flag use of preview language or API features.
- [ ] [hard] `Unchecked warning` - Surface code that triggers unchecked generic warnings in `javac`.
- [ ] [medium] `Value-based warnings` - Detect illegal synchronisation and similar misuse of value-based classes.

## Control flow issues

- [-] [medium] `'break' statement` - Implemented for illegal placement and unresolved labels, not for broader
  style/simplification heuristics.
- [-] [medium] `'break' statement with label` - Implemented for label resolution validity, not for
  readability/redundancy heuristics.
- [-] [medium] `'continue' statement` - Implemented for illegal placement and bad targets, not for broader
  style/simplification heuristics.
- [-] [medium] `'continue' statement with label` - Implemented for label resolution and loop-target validity, not for
  redundancy heuristics.
- [ ] [starter] `'default' not last case in 'switch'` - Enforce expected `default` placement in `switch`.
- [ ] [easy] `'for' loop may be replaced by 'while' loop` - Find `for` loops with no init/update sections.
- [ ] [starter] `'for' loop with missing components` - Detect sparse `for` headers that obscure loop intent.
- [ ] [medium] `'if' statement with identical branches or common parts` - Find branch bodies that can be merged or
  extracted.
- [ ] [easy] `'if' statement with negated condition` - Detect `if (!cond) ... else ...` shapes that can be inverted.
- [ ] [starter] `'if' statement with too many branches` - Flag very large branch chains.
- [ ] [hard] `'switch' statement` - Track ordinary `switch` usage where alternatives may be preferable.
- [ ] [medium] `'switch' statement with too low of a branch density` - Detect `switch` constructs with too little actual
  dispatch value.
- [ ] [easy] `'switch' statement without 'default' branch` - Flag non-exhaustive switches.
- [ ] [medium] `'while' can be replaced with 'do while'` - Detect loops whose body must run at least once.
- [x] [medium] `Assertion can be replaced with 'if' statement` - Find asserts that should be enforced with explicit
  runtime checks.
- [ ] [easy] `Boolean expression can be replaced with conditional expression` - Simplify verbose boolean logic.
- [ ] [medium] `Common subexpression can be extracted from 'switch'` - Pull repeated branch logic around a `switch`.
- [ ] [medium] `Conditional break inside loop` - Replace leading/trailing conditional breaks with loop conditions when
  clearer.
- [ ] [medium] `Conditional can be pushed inside branch expression` - Simplify duplicated ternary branch structure.
- [ ] [hard] `Conditional expression` - Flag ternaries where statement form may be clearer.
- [x] [starter] `Conditional expression with identical branches` - Detect ternaries that produce the same result on both
  sides.
- [ ] [easy] `Conditional expression with negated condition` - Simplify negated ternary conditions.
- [x] [medium] `Constant conditional expression` - Detect conditions known at compile/data-flow time.
- [x] [starter] `Double negation` - Simplify `!!expr` style logic.
- [ ] [easy] `Duplicate condition` - Find repeated boolean conditions in the same decision logic.
- [ ] [medium] `Enum 'switch' statement that misses case` - Detect non-exhaustive enum switches.
- [ ] [medium] `Expression can be factorized` - Identify algebraically factorable expressions.
- [x] [medium] `Fallthrough in 'switch' statement` - Report implicit or suspicious fallthrough between cases.
- [ ] [hard] `Idempotent loop body` - Detect loops whose later iterations do no additional useful work.
- [ ] [medium] `Infinite loop statement` - Flag loops that only terminate by exception or external exit.
- [ ] [starter] `Labeled statement` - Detect labels that make flow harder to reason about.
- [x] [hard] `Missing return on some control-flow paths` - Detects non-void methods whose bodies do not definitely
  return or throw on every path.
- [ ] [medium] `Local variable used and declared in different 'switch' branches` - Flag branch-local variable leakage
  across cases.
- [ ] [medium] `Loop statement that does not loop` - Detect loops that execute at most once.
- [ ] [medium] `Loop variable not updated inside loop` - Flag loop conditions that depend on unchanged values.
- [ ] [easy] `Loop with implicit termination condition` - Detect `while(true)`-style loops with hidden exit logic.
- [ ] [starter] `Maximum 'switch' branches` - Flag switches that are too large.
- [ ] [starter] `Minimum 'switch' branches` - Detect tiny switches better modelled as `if` chains.
- [ ] [easy] `Negated conditional expression` - Flag ternaries negated from the outside.
- [ ] [starter] `Negated equality expression` - Simplify negated equality checks.
- [ ] [starter] `Nested 'switch' statement` - Detect nested switch structures.
- [ ] [starter] `Nested conditional expression` - Flag nested ternaries.
- [ ] [easy] `Overly complex boolean expression` - Detect boolean expressions with too many terms.
- [ ] [easy] `Pointless 'indexOf()' comparison` - Find redundant `indexOf` comparison patterns.
- [ ] [medium] `Pointless boolean expression` - Detect logic that always reduces to a simpler expression.
- [ ] [easy] `Redundant 'else'` - Remove `else` after a terminal branch.
- [ ] [easy] `Redundant 'if' statement` - Collapse `if` to direct assignment, return, or assert when possible.
- [ ] [medium] `Simplifiable boolean expression` - Reduce boolean logic to a simpler equivalent.
- [ ] [easy] `Simplifiable conditional expression` - Reduce ternaries to simpler forms.
- [ ] [medium] `Statement can be replaced with 'assert' or 'Objects.requireNonNull'` - Detect guard statements
  expressible by assertion or null-check helpers.
- [ ] [medium] `Unnecessary 'null' check before method call` - Remove null checks before methods already null-safe or
  deterministically false.

## Data flow

- [ ] [hard] `Boolean method is always inverted` - Detect boolean-returning methods that callers nearly always negate.
- [ ] [medium] `Boolean variable is always inverted` - Detect booleans that are consistently used negated.
- [ ] [easy] `Law of Demeter` - Find call chains that reach too far through object graphs.
- [ ] [starter] `Negatively named boolean variable` - Flag boolean names like `disabled` or `notReady` that invert
  readability.
- [ ] [medium] `Redundant local variable` - Remove locals that add no value beyond the source expression.
- [ ] [easy] `Reuse of local variable` - Flag locals repurposed for unrelated values.
- [ ] [medium] `Scope of variable is too broad` - Narrow declarations to the smallest valid scope.
- [ ] [medium] `Use of variable whose value is known to be constant` - Detect places where constant propagation exposes
  unnecessary indirection.

## Declaration redundancy

- [ ] [starter] `'final' method in 'final' class` - Detect redundant `final` on methods in a final class.
- [ ] [easy] `'protected' member in 'final' class` - Flag protected members that cannot actually be inherited.
- [ ] [medium] `@SafeVarargs is not applicable to reifiable types` - Detect pointless `@SafeVarargs` on reifiable
  varargs.
- [ ] [easy] `Access static member via instance reference` - Require class qualification for static members.
- [ ] [hard] `Declaration access can be weaker` - Narrow visibility where usage permits.
- [ ] [medium] `Declaration can have 'final' modifier` - Add `final` where the declaration is never varied.
- [ ] [easy] `Default annotation parameter value` - Remove annotation elements explicitly set to their default.
- [ ] [starter] `Duplicate throws` - Detect repeated exception types in a `throws` list.
- [ ] [starter] `Empty class initializer` - Remove empty initialiser blocks.
- [ ] [medium] `Functional expression can be folded` - Replace lambdas or method refs with a simpler direct form.
- [ ] [hard] `Java module definition problems` - Detect miscellaneous `module-info.java` declaration issues.
- [ ] [medium] `Method always returns the same value` - Find constant-return methods.
- [ ] [hard] `Method can be made 'void'` - Detect methods whose return values are never used.
- [ ] [hard] `Method parameter always has the same value` - Detect effectively constant parameters.
- [ ] [easy] `Redundant 'close()'` - Remove explicit `close()` inside try-with-resources.
- [ ] [medium] `Redundant 'requires' directive in module-info` - Remove unnecessary module dependencies.
- [ ] [hard] `Redundant 'throws' clause` - Remove declared exceptions that are never thrown.
- [ ] [easy] `Redundant lambda parameter types` - Omit lambda parameter types when type inference suffices.
- [ ] [easy] `Redundant record constructor` - Remove record constructors that duplicate the canonical default behaviour.
- [ ] [medium] `Trivial usage of functional expression` - Inline immediately-invoked functional expressions.
- [ ] [hard] `Unnecessary module dependency` - Remove unused module requirements.
- [ ] [hard] `Unused declaration` - Detect dead classes, methods, and fields.
- [ ] [starter] `Unused label` - Remove labels never targeted by control flow.
- [ ] [starter] `Variable is assigned to itself` - Flag self-assignment.

## Encapsulation

- [ ] [starter] `'public' field` - Detect publicly exposed mutable or structural state.
- [ ] [starter] `'public' nested class` - Flag broad nested-type exposure.
- [ ] [medium] `Accessing a non-public field of another object` - Detect access to another instance's
  protected/private-ish internals.
- [ ] [hard] `Assignment or return of field with mutable type` - Flag leaking or storing mutable references unsafely.
- [ ] [starter] `Package-visible field` - Detect package-private fields.
- [ ] [starter] `Package-visible nested class` - Detect package-private nested types.
- [ ] [starter] `Protected field` - Flag protected fields that widen mutation surface.
- [ ] [starter] `Protected nested class` - Flag protected nested type exposure.

## Error handling

- [x] [starter] `Empty 'catch' block` - Detects `catch` clauses with no statements.
- [ ] [starter] `'continue' or 'break' inside 'finally' block` - Detect control-flow escapes from `finally`.
- [ ] [medium] `'Error' not rethrown` - Flag catching `Error` without rethrowing it.
- [ ] [medium] `'finally' block which can not complete normally` - Detect `finally` blocks that terminate via return,
  throw, break, continue, or yield.
- [ ] [starter] `'instanceof' on 'catch' parameter` - Flag type switching on a caught exception.
- [ ] [starter] `'null' thrown` - Detect `throw null`.
- [ ] [starter] `'return' inside 'finally' block` - Forbid returns from `finally`.
- [ ] [medium] `'ThreadDeath' not rethrown` - Flag swallowing `ThreadDeath`.
- [ ] [medium] `'throw' caught by containing 'try' statement` - Detect throws that are immediately caught by the
  surrounding try.
- [ ] [medium] `'throw' inside 'catch' block which ignores the caught exception` - Flag rethrows that discard original
  failure context.
- [ ] [starter] `'throw' inside 'finally' block` - Detect throws from `finally`.
- [ ] [medium] `Catch block may ignore exception` - Find empty or effectively swallowing catches.
- [ ] [easy] `Caught exception is immediately rethrown` - Detect useless catch/rethrow blocks.
- [ ] [easy] `Checked exception class` - Track checked-exception type declarations.
- [ ] [easy] `Class directly extends 'Throwable'` - Flag custom types extending `Throwable` instead of `Exception`/
  `RuntimeException`.
- [ ] [starter] `Empty 'finally' block` - Remove no-op `finally` blocks.
- [ ] [starter] `Empty 'try' block` - Remove empty `try` or try-with-resources bodies.
- [ ] [easy] `Exception constructor called without arguments` - Prefer richer exception construction with message and/or
  cause.
- [ ] [starter] `Nested 'try' statement` - Detect nested try structures that obscure error paths.
- [ ] [easy] `Non-final field of 'Exception' class` - Flag mutable exception state.
- [ ] [medium] `Overly broad 'catch' block` - Narrow overly generic catch parameters.
- [ ] [medium] `Overly broad 'throws' clause` - Narrow overly generic declared exceptions.
- [ ] [medium] `Prohibited 'Exception' caught` - Detect disallowed catch targets like generic `Exception` in configured
  contexts.
- [x] [medium] `Prohibited exception declared` - Flag disallowed exceptions in method signatures.
- [ ] [medium] `Prohibited exception thrown` - Flag disallowed exception types in `throw`.
- [ ] [easy] `Throwable supplier never returns a value` - Detect `orElseThrow` suppliers that throw instead of returning
  the throwable.
- [ ] [easy] `Unchecked 'Exception' class` - Track runtime-exception type declarations.
- [ ] [easy] `Unchecked exception declared in 'throws' clause` - Remove unnecessary unchecked exceptions from method
  signatures.
- [ ] [easy] `Unnecessary call to 'Throwable.initCause()'` - Prefer constructors that directly accept the cause.

## Imports

- [x] [medium] `Ambiguous import` - Detects imported types with the same simple name that conflict.
- [x] [starter] `Duplicate import` - Detects repeated imports of the same symbol.
- [x] [medium] `Unresolved import` - Detects imports that do not resolve to a package, type, or static member.
- [x] [starter] `'*' import` - Detects on-demand package or static imports.
- [ ] [starter] `Missorted imports` - Enforce code-style import ordering.
- [ ] [starter] `Single class import` - Track explicit class imports where style policy matters.
- [ ] [starter] `Static import` - Track static imports for style or readability review.
- [ ] [medium] `Static import can be used based on the auto-import table` - Detect qualifiers that could become
  configured static imports.
- [ ] [starter] `Unnecessary import from the 'java.lang' package` - Remove redundant `java.lang` imports.
- [ ] [starter] `Unnecessary import from the same package` - Remove imports of sibling-package types.
- [ ] [starter] `Unused import` - Remove dead imports.

## Inheritance issues

- [ ] [hard] `Abstract class extends concrete class` - Detect abstraction layers built on concrete implementations.
- [ ] [hard] `Abstract class which has no concrete subclass` - Find abstract classes with no realisations.
- [ ] [hard] `Abstract class without 'abstract' methods` - Detect abstract classes that do not actually define
  abstraction points.
- [ ] [hard] `Abstract method overrides abstract method` - Flag repeated abstract declarations in the hierarchy.
- [ ] [hard] `Abstract method overrides concrete method` - Detect abstract redeclaration of implemented behaviour.
- [x] [hard] `Abstract method with missing implementations` - Detects concrete classes and records that fail to
  implement required inherited abstract methods.
- [ ] [easy] `Class explicitly extends a 'Collection' class` - Discourage inheriting concrete collection
  implementations.
- [ ] [easy] `Class extends annotation interface` - Detect illegal or nonsensical extension of annotation interfaces.
- [ ] [easy] `Class extends utility class` - Flag inheritance from utility-only types.
- [ ] [hard] `Class may extend a commonly used base class` - Suggest better-known base abstractions when applicable.
- [ ] [hard] `Final declaration can't be overridden at runtime` - Detect `final` members/classes that break framework
  subclassing/proxy expectations.
- [ ] [hard] `Interface which has no concrete subclass` - Find interfaces never implemented concretely.
- [ ] [medium] `Method does not call super method` - Flag overrides that skip required or expected `super` behaviour.
- [ ] [medium] `Method is identical to its super method` - Remove overrides that add no behaviour.
- [ ] [easy] `Missing '@Override' annotation` - Require explicit override markers.
- [ ] [medium] `Non-varargs method overrides varargs method` - Detect override signature mismatches involving varargs.
- [ ] [medium] `Parameter type prevents overriding` - Find visually similar but package-different parameter types that
  break overriding.
- [ ] [easy] `Public constructor in abstract class` - Flag instantiation-oriented constructors on abstract types.
- [x] [medium] `Redundant interface declaration` - Remove interfaces already inherited through a superclass or
  superinterface.
- [ ] [easy] `Static inheritance` - Detect interfaces used only to inherit constants.
- [ ] [easy] `Type parameter extends 'final' class` - Flag generic bounds that cannot vary beyond a final type.

## Initialization

- [x] [hard] `'this' reference escaped in object construction` - Detect `this` escaping before construction completes.
- [ ] [hard] `Abstract method called during object construction` - Flag constructor-time calls to abstract methods.
- [ ] [easy] `Double brace initialization` - Detect double-brace initialisation and its hidden class/allocation costs.
- [-] [hard] `Instance field may not be initialized` - Partially covered through definite-assignment checks for final
  fields, but not general non-final field initialisation analysis.
- [ ] [medium] `Instance field used before initialization` - Detect reads of instance fields before they are
  initialised.
- [ ] [medium] `Non-final static field is used during class initialization` - Flag mutable static state used during
  class initialisation.
- [x] [hard] `Overridable method called during object construction` - Detect constructor-time calls to non-final
  overridable methods.
- [x] [hard] `Overridden method called during object construction` - Detect calls to methods whose runtime dispatch may
  hit subclass behaviour during construction.
- [-] [hard] `Static field may not be initialized` - Partially covered through definite-assignment checks for final
  fields, but not general static-field initialisation analysis.
- [ ] [medium] `Static field used before initialization` - Detect reads of static fields before safe initialisation.
- [ ] [hard] `Unsafe lazy initialization of 'static' field` - Flag racy lazy-init patterns for static state.

## Java language level issues

- [ ] [easy] `'assert' statement` - Track `assert` usage relative to target language/runtime compatibility.
- [ ] [easy] `Annotation` - Detect annotation usage when targeting an older language level.
- [ ] [easy] `Annotation interface` - Detect annotation type declarations on unsupported language levels.
- [ ] [easy] `Enhanced 'for' statement` - Track foreach syntax against language level requirements.
- [ ] [easy] `Enumerated class` - Detect enum declarations when targeting older Java versions.
- [ ] [hard] `Forward compatibility` - Flag identifiers or constructs likely to become invalid in future Java versions.
- [ ] [easy] `Varargs method` - Detect variable-arity methods for downgrade or compatibility analysis.

## Numeric issues

- [ ] [easy] `'char' expression used in arithmetic context` - Flag `char` arithmetic that can be surprising.
- [x] [easy] `'equals()' called on 'BigDecimal'` - Detect scale-sensitive `BigDecimal.equals()` comparisons.
- [ ] [starter] `'long' literal ending with 'l' instead of 'L'` - Prefer uppercase `L` to avoid confusion with `1`.
- [ ] [medium] `Call to 'BigDecimal' method without a rounding mode argument` - Require explicit rounding where
  division/scale operations need it.
- [ ] [easy] `Comparison of 'short' and 'char' values` - Flag suspicious comparisons across small integral types.
- [ ] [starter] `Comparison to 'Double.NaN' or 'Float.NaN'` - Replace with `isNaN` semantics.
- [ ] [starter] `Confusing floating-point literal` - Detect float/double literals that are easy to misread.
- [ ] [medium] `Constant call to 'Math'` - Replace `Math` calls with compile-time constants when possible.
- [ ] [medium] `Division by zero` - Detect definite divide-by-zero or modulo-by-zero.
- [ ] [easy] `Floating-point equality comparison` - Flag direct `==`/`!=` on floating-point values.
- [ ] [medium] `Implicit numeric conversion` - Detect silent widening/narrowing conversions.
- [-] [hard] `Integer division in floating-point context` - Flag truncated integer division feeding float/double usage. (WIP)
- [x] [medium] `Negative int hexadecimal constant in long context` - Detect surprising sign behaviour with hex literals.
- [ ] [medium] `Non-reproducible call to 'Math'` - Flag math calls whose results are not guaranteed bit-for-bit
  reproducible.
- [ ] [easy] `Number constructor call with primitive argument` - Replace boxed-number constructors with
  valueOf/autoboxing.
- [ ] [hard] `Numeric overflow` - Detect compile-time or data-flow-visible overflow.
- [ ] [easy] `Octal and decimal integers in same array` - Flag mixed-base literals in one initialiser.
- [ ] [starter] `Octal integer` - Detect octal integer literals.
- [ ] [starter] `Overly complex arithmetic expression` - Flag arithmetic with too many terms.
- [ ] [easy] `Pointless arithmetic expression` - Remove identity or no-op arithmetic.
- [ ] [medium] `Possibly lossy implicit cast in compound assignment` - Detect hidden narrowing in `+=`, `*=`, and
  similar operators.
- [ ] [easy] `Suspicious oddness check` - Flag `% 2 == 1` patterns that fail for negatives.
- [ ] [starter] `Suspicious underscore in number literal` - Detect digit grouping that suggests accidental formatting.
- [ ] [starter] `Unary plus` - Flag redundant unary `+`.
- [ ] [starter] `Underscores in numeric literal` - Track underscore-separated numeric literals based on
  style/compatibility policy.
- [ ] [starter] `Unnecessary unary minus` - Remove negation that has no semantic effect.
- [ ] [easy] `Unpredictable 'BigDecimal' constructor call` - Flag `new BigDecimal(double)` precision traps.
- [ ] [easy] `Unreadable numeric literal` - Require separators in long numeric literals.

## Resource management

- [ ] [hard] `'Channel' opened but not safely closed` - Detect leaked `Channel` resources.
- [x] [medium] `AutoCloseable used without 'try'-with-resources` - Require TWR for closeable resources.
- [ ] [hard] `Hibernate resource opened but not safely closed` - Detect leaked Hibernate sessions where applicable.
- [ ] [hard] `I/O resource opened but not safely closed` - Detect leaked stream/reader/writer resources.
- [ ] [hard] `JDBC resource opened but not safely closed` - Detect leaked DB resources.
- [ ] [hard] `JNDI resource opened but not safely closed` - Detect leaked naming resources.
- [ ] [hard] `Socket opened but not safely closed` - Detect leaked sockets.
- [ ] [hard] `Use of 'DriverManager' to get JDBC connection` - Flag direct `DriverManager` acquisition where
  pooled/data-source usage is preferred.

## Serialization issues

- [ ] [easy] `'@Serial' annotation can be used` - Add `@Serial` where serialization protocol members support it.
- [ ] [easy] `'@Serial' annotation used on wrong member` - Detect misapplied `@Serial`.
- [ ] [medium] `'Comparator' class not declared 'Serializable'` - Flag comparators likely to be stored or transported
  but not serializable.
- [ ] [easy] `'Externalizable' class without 'public' no-arg constructor` - Enforce required constructor shape.
- [ ] [easy] `'readObject()' or 'writeObject()' not declared 'private'` - Enforce correct serialization hook visibility.
- [ ] [easy] `'readResolve()' or 'writeReplace()' not declared 'protected'` - Enforce serialization replacement hook
  visibility.
- [ ] [easy] `'record' contains ignored members` - Detect serialization members ignored by record semantics.
- [ ] [hard] `'Serializable' object implicitly stores non-'Serializable' object` - Find hidden non-serializable captures
  or references.
- [ ] [easy] `'serialPersistentFields' field not declared 'private static final ObjectStreamField[]'` - Enforce exact
  declaration contract.
- [ ] [easy] `'serialVersionUID' field not declared 'private static final long'` - Enforce exact `serialVersionUID`
  declaration.
- [ ] [easy] `Externalizable class with 'readObject()' or 'writeObject()'` - Detect conflicting serialization protocols.
- [ ] [hard] `Instance field may not be initialized by 'readObject()'` - Find fields left unsafe after deserialization.
- [ ] [easy] `Non-serializable class with 'readObject()' or 'writeObject()'` - Flag serialization hooks on
  non-serializable types.
- [ ] [easy] `Non-serializable class with 'serialVersionUID'` - Flag stray `serialVersionUID` declarations.
- [ ] [medium] `Non-serializable field in a 'Serializable' class` - Detect incompatible field types in serializable
  objects.
- [ ] [easy] `Non-serializable object passed to 'ObjectOutputStream'` - Flag direct attempts to write non-serializable
  objects.
- [x] [medium] `Serializable class with unconstructable ancestor` - Detect serializable types whose non-serializable
  ancestor lacks a no-arg constructor.
- [ ] [hard] `Serializable class without 'readObject()' and 'writeObject()'` - Track serializable classes missing
  explicit custom protocol hooks where required by policy.
- [ ] [medium] `Serializable non-'static' inner class with non-Serializable outer class` - Flag inner/outer
  serializability mismatch.
- [ ] [easy] `Serializable non-static inner class without 'serialVersionUID'` - Require explicit UID on serializable
  inner classes.
- [ ] [easy] `Transient field in non-serializable class` - Flag meaningless `transient` fields.
- [ ] [hard] `Transient field is not initialized on deserialization` - Detect transient state not restored by
  `readObject`.

## Visibility

- [ ] [starter] `'public' constructor in non-public class` - Flag public constructors on types that still are not
  publicly accessible.
- [ ] [medium] `Access to inherited field looks like access to element from surrounding code` - Detect confusing
  inherited-field shadowing in inner scopes.
- [ ] [easy] `Anonymous class variable hides variable in containing method` - Flag anonymous-class fields hiding locals
  or parameters.
- [ ] [medium] `Call to inherited method looks like call to local method` - Detect inherited method calls that read like
  local method calls from surrounding scope.
- [ ] [hard] `Class is exposed outside of its visibility scope` - Detect method/field signatures leaking less-visible
  types.
- [ ] [starter] `Empty 'module-info.java' file` - Flag empty module descriptors.
- [ ] [easy] `Inner class field hides outer class field` - Detect nested-field shadowing.
- [ ] [easy] `Lambda parameter hides field` - Flag lambda parameter names that shadow fields.
- [ ] [easy] `Local variable hides field` - Flag locals that shadow fields.
- [ ] [medium] `Method overrides inaccessible method of superclass` - Detect same-signature methods that do not actually
  override due to visibility.
- [x] [medium] `Method tries to override 'static' method of superclass` - Covered by invalid-override detection for
  static/non-static mismatches.
- [ ] [starter] `Module exports/opens package to itself` - Detect self-export/self-open directives in JPMS.
- [ ] [easy] `Parameter hides field` - Flag parameter names shadowing fields.
- [ ] [easy] `Pattern variable hides field` - Flag pattern variables shadowing fields.
- [ ] [medium] `Possibly unintended overload of method from superclass` - Detect near-override overloads with
  incompatible parameter types.
- [ ] [medium] `Subclass field hides superclass field` - Flag field shadowing across class hierarchies.
- [ ] [easy] `Type parameter hides visible type` - Detect generic parameter names that shadow visible classes.
- [ ] [hard] `Usage of service not declared in 'module-info'` - Require `uses` declarations for `ServiceLoader`
  consumption in modules.

## Verbose or redundant code constructs

- [ ] [easy] `'StringBuilder' can be replaced with 'String'` - Replace trivial builder usage with plain concatenation.
- [ ] [medium] `Cast can be replaced with variable` - Reuse an existing variable or pattern variable instead of
  recasting.
- [ ] [medium] `Comparator method can be simplified` - Simplify comparator combinator chains.
- [ ] [starter] `Concatenation with empty string` - Remove empty-string concatenation hacks.
- [ ] [medium] `Condition is covered by further condition` - Remove earlier conditions made redundant by later ones.
- [ ] [medium] `Duplicate branches in 'switch'` - Merge equal switch branches.
- [ ] [easy] `Excessive lambda usage` - Replace trivial lambdas with simpler direct forms when available.
- [ ] [easy] `Excessive range check` - Collapse multi-branch range checks into a single clearer condition.
- [ ] [easy] `Explicit array filling` - Replace manual filling loops with `Arrays.fill` or `Arrays.setAll`.
- [ ] [easy] `Manual min/max calculation` - Replace manual comparisons with `Math.min`/`Math.max`.
- [ ] [medium] `Multiple occurrences of the same expression` - Extract repeated equivalent expressions.
- [ ] [medium] `Non-strict inequality '>=' or '<=' can be replaced with '=='` - Narrow inequalities that data flow
  proves single-valued.
- [ ] [medium] `Null-check method is called with obviously non-null argument` - Remove `requireNonNull`-style checks on
  values already known non-null.
- [ ] [medium] `Only one element is used` - Detect containers created only to immediately access a single element.
- [ ] [medium] `Optional call chain can be simplified` - Collapse verbose optional pipelines.
- [ ] [easy] `Redundant 'Collection' operation` - Replace unnecessarily complex collection usage with a simpler
  equivalent.
- [ ] [easy] `Redundant 'compare()' method call` - Remove superfluous `compare()` wrapping in comparisons.
- [ ] [easy] `Redundant 'File' instance creation` - Pass paths directly where `File` allocation is unnecessary.
- [ ] [easy] `Redundant 'isInstance()' or 'cast()' call` - Remove needless `Class.isInstance` or `Class.cast` use.
- [ ] [easy] `Redundant 'String' operation` - Remove unnecessary `String` constructors or no-op string methods.
- [ ] [easy] `Redundant array creation` - Remove array allocation used only for varargs passing.
- [ ] [medium] `Redundant array length check` - Remove array length guards rendered unnecessary by the iteration
  pattern.
- [ ] [easy] `Redundant escape in regex replacement string` - Remove needless escaping in replacement text.
- [ ] [easy] `Redundant operation on 'java.time' object` - Remove no-op or avoidable `java.time` transformations.
- [ ] [medium] `Redundant step in 'Stream' or 'Optional' call chain` - Remove identity `map`, useless `filter`,
  redundant `sorted`, and similar steps.
- [ ] [medium] `Redundant type arguments` - Rely on compiler inference instead of explicit generic arguments.
- [ ] [easy] `Redundant type cast` - Remove unnecessary casts.
- [ ] [easy] `Replacement operation has no effect` - Detect `replace`/`replaceAll` calls that cannot change the string.
- [ ] [medium] `Simplifiable collector` - Reduce a collector pipeline to a simpler standard collector.
- [ ] [medium] `Stream API call chain can be simplified` - Collapse verbose stream pipelines.
- [ ] [medium] `Too weak variable type leads to unnecessary cast` - Narrow a declaration type to remove casts.
- [ ] [starter] `Unnecessarily escaped character` - Remove escapes not required in literals.
- [ ] [easy] `Unnecessary 'break' statement` - Remove dead or redundant `break`.
- [ ] [easy] `Unnecessary 'continue' statement` - Remove trailing or redundant `continue`.
- [ ] [medium] `Unnecessary 'default' for enum 'switch' statement` - Remove impossible `default` branches from
  exhaustive enum switches.
- [ ] [starter] `Unnecessary 'return' statement` - Remove terminal `return;` in `void` methods and constructors.
- [ ] [easy] `Unnecessary label on 'break' statement` - Remove labels on `break` when not needed.
- [ ] [easy] `Unnecessary label on 'continue' statement` - Remove labels on `continue` when not needed.
- [x] [medium] `Unreachable catch section` - Detects catch blocks that can never be matched.

## High-priority probable bugs

- [x] [hard] `'assert' statement with side effects` - Detect asserts whose condition mutates state.
- [ ] [medium] `'Comparable' implemented but 'equals()' not overridden` - Enforce consistency between ordering and
  equality contracts.
- [ ] [easy] `'equals()' and 'hashCode()' not paired` - Require both methods to be implemented together.
- [ ] [medium] `'equals()' between objects of inconvertible types` - Flag impossible equality checks.
- [ ] [easy] `'equals()' called on array` - Use `Arrays.equals`/`deepEquals` instead of reference-equality semantics.
- [ ] [starter] `'equals()' called on itself` - Detect self-comparison that is always true.
- [ ] [medium] `'instanceof' with incompatible type` - Flag impossible type tests.
- [ ] [starter] `'Math.random()' cast to 'int'` - Detect the classic always-zero bug.
- [ ] [easy] `'Throwable' not thrown` - Flag exception objects created and then ignored.
- [ ] [easy] `Array comparison using '==', instead of 'Arrays.equals()'` - Replace reference comparison for arrays.
- [ ] [easy] `Call to 'toString()' on array` - Replace array `toString` misuse with proper array formatting helpers.
- [x] [medium] `Cast conflicts with 'instanceof'` - Detect casts incompatible with an earlier type test.
- [ ] [medium] `Cast to incompatible type` - Flag impossible casts.
- [ ] [starter] `Collection added to itself` - Detect self-add/self-put container bugs.
- [ ] [easy] `Confusing 'main()' method` - Flag methods named `main` that are not actual entry points.
- [ ] [medium] `Confusing argument to varargs method` - Detect null/array varargs calls with ambiguous behaviour.
- [ ] [medium] `Constant condition in 'assert' statement` - Find asserts that always succeed or fail trivially.
- [ ] [hard] `Constant values` - Detect expressions or conditions proven constant by analysis.
- [ ] [medium] `Copy constructor misses field` - Find copy constructors that omit part of object state.
- [ ] [easy] `Covariant 'equals()'` - Detect `equals(T)` without proper `equals(Object)`.
- [ ] [starter] `Expression is compared to itself` - Flag `x == x`-style accidental self-comparisons.
- [x] [hard] `Infinite recursion` - Detect self-calls that never bottom out.
- [ ] [medium] `Invalid method reference used for 'Comparator'` - Flag method references that violate comparator
  contract expectations.
- [ ] [medium] `Loop executes zero or billions of times` - Detect loops that do not terminate as intended due to
  overflow or bounds errors.
- [ ] [easy] `Malformed format string` - Validate `String.format` style placeholders.
- [ ] [hard] `Nullability and data flow problems` - Surface definite nullability violations and other data-flow-proven
  bugs.
- [ ] [easy] `Number comparison using '==', instead of 'equals()'` - Detect boxed-number reference comparison.
- [ ] [easy] `Object comparison using '==', instead of 'equals()'` - Detect accidental reference equality.
- [x] [medium] `Optional.get() is called without isPresent() check` - Detect unsafe optional extraction.
- [ ] [medium] `Result of method call ignored` - Flag ignored return values from meaningful methods.
- [ ] [starter] `Result of object allocation ignored` - Detect `new` expressions whose result is unused.
- [ ] [medium] `Sorted collection with non-comparable elements` - Detect natural-order sorted collections whose element
  type cannot be ordered.
- [-] [easy] `Statement with empty body` - Partially covered via dedicated checks for empty `catch`, `switch`, and
  `synchronized` bodies.
- [ ] [easy] `String comparison using '==', instead of 'equals()'` - Detect reference comparison on strings.
- [ ] [easy] `Subtraction in 'compareTo()'` - Flag overflow-prone subtraction-based comparison.
- [ ] [medium] `Suspicious 'Collection.toArray()' call` - Detect incorrect or misleading `toArray` usage.
- [ ] [medium] `Suspicious 'Comparator.compare()' implementation` - Validate comparator contract behaviour.
- [ ] [medium] `Suspicious 'List.remove()' in loop` - Flag ascending-index removal loops that skip elements.
- [ ] [medium] `Suspicious 'System.arraycopy()' call` - Detect invalid or mismatched array copy arguments.
- [ ] [medium] `Suspicious array cast` - Flag array casts likely to fail at runtime.
- [ ] [medium] `Suspicious collection method call` - Detect generic collection calls with the wrong apparent element
  type.
- [ ] [starter] `Suspicious indentation after control statement without braces` - Flag misleading indentation that hides
  actual scope.
- [ ] [easy] `Suspicious integer division assignment` - Detect truncating integer division assigned where a precise
  result seems intended.
- [ ] [easy] `Suspicious regex expression argument` - Detect regex metacharacters passed where literal intent seems
  likely.
- [x] [hard] `Unreachable code` - Detects statements after terminating control flow and related unreachable regions.
- [ ] [easy] `Unsafe call to 'Class.newInstance()'` - Replace deprecated/unsafe reflective instantiation.
- [ ] [hard] `Unused assignment` - Detect assignments overwritten or never subsequently observed.
- [ ] [medium] `Use of Optional.ofNullable with null or non-null argument` - Replace `ofNullable` when argument
  nullability is already known.
- [ ] [easy] `Use of shallow or 'Objects' methods with arrays` - Detect wrong array equality/hash helpers.
- [ ] [easy] `Whitespace may be missing in string concatenation` - Flag concatenations likely missing a separating
  space.
- [ ] [hard] `Write-only object` - Detect objects that are mutated but never meaningfully read.
- [ ] [easy] `Wrong package statement` - Detect package declarations that do not match directory structure.

## Suggested implementation order

1. Parser and semantic model prerequisites.
2. Definite assignment, definite unassignment, and initialisation checks.
3. Visibility, inheritance, and override resolution checks.
4. Control-flow and reachability checks.
5. Nullability/data-flow and high-value probable bug checks.
6. Numeric, exception, resource, and serialization checks.
7. Style, redundancy, and simplification inspections.

Note: This file was initially generated and status-annotated with AI assistance, then grounded against JetBrains
Inspectopedia and the current Railroad codebase. AI has also been used to suggest the difficulty, but these are very
rough and should be reviewed and adjusted based on actual implementation experience.

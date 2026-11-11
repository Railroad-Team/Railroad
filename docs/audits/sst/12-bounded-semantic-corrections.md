# Bounded semantic corrections

2026-11-11, following the compilation-context correction and the explicit-final-record fix. The starting saved-source baseline has **95** self-analysis ERROR diagnostics. These fixes remove exactly the **11** requested reports, leaving **84**, with **no added diagnostic records** in the complete source-set scan. The zero-error assertion remains failing, without thresholds or suppressions.

| Requested group | Before | After |
|---|---:|---:|
| Inherited nested-type lookup | 2 | 0 |
| Pattern-variable scope leakage | 3 | 0 |
| Bounded type-variable assignability | 1 | 0 |
| Static-import varargs filtering | 4 | 0 |
| Fixed-arity versus varargs overload selection | 1 | 0 |
| **Total requested reports** | **11** | **0** |

## Changes

- `JavaSemanticAnalyzer`: inherited member-type lookup starts at the nearest lexical type and searches enclosing type hierarchies outward. Existing source/binary hierarchy lookup is reused. Callable candidates retain actual variable-arity metadata from source parameter declarations or binary `ACC_VARARGS`, including project source constructors. Source varargs signatures are normalized consistently with the project extractor. Static-import collection preserves compatible variable-arity candidates. Selection tries fixed arity (including an explicit array passed to a varargs declaration) before expanded varargs.
- `JavaDeclarationAnalysis`: pattern bindings enter matched-path scopes instead of the surrounding lexical scope. Scopes follow negation, short-circuit operators, conditional branches, matching loop bodies and ternary expressions; switch rules are isolated. Guard continuations retain bindings when the other path exits with return, throw, yield, break or continue. The initial full scan exposed a missing yield-guard case in the new implementation; that was fixed and covered before final verification.
- `JavaRuleContext`: assignment checks accept an optional usage site and consult the nearest lexical type-parameter declaration, following upper bounds and intersection/transitive bounds with cycle protection. Assignment inspection supplies its syntax node. Method-applicability fallback uses the same actual varargs metadata and fixed-before-expanded ordering instead of treating every trailing array as varargs.
- `JavaSemanticCorrectnessRegressionTest`: **18 tests** cover the reported shapes and controls, including inner hierarchy preference/outer fallback; repeated pattern names, unmatched paths, short-circuit conditions, loops, switch rules, nested record patterns and return/yield guards; method/class/transitive/intersection bounds and shadowing/reverse-assignment rejection; named/wildcard binary static imports, project source varargs, plain arrays, incompatible element types, constructor metadata, fixed-arity preference and the actual `ProcessBuilder.command()` return type.

Application source expressions were not rewritten to work around analysis. The harness's seven source sets, ten valid corpus fixtures, 13 providers, actual dependency inputs and zero-error assertion are unchanged. No parsing or general generic/lambda inference changes were made.

## Diagnostic evidence

The [baseline](w0/semantic-corrections/before.json), [final ledger](w0/semantic-corrections/after.json) and [exact tuple comparison](w0/semantic-corrections/comparison.json) preserve path, offset, diagnostic code and message. Removed reports are the three formatter reports, two `EditCommands` calls, the `KeybindHandler` bounded assignment, the `ProcessBuilder` getter assignment and four static-imported `assertAll` calls. No newly introduced report remains.

| Source set | Before | After |
|---|---:|---:|
| main | 61 | 58 |
| test | 31 | 26 |
| formatter | 3 | 0 |
| CSS tool/test, Javadoc tool/test, valid corpus | 0 | 0 |
| **Total** | **95** | **84** |

The remaining 84 are the previously attributed **20 local-class parsing reports**, **58 generic/lambda inference reports**, and **six harness stream-chain reports** whose precise inference cause remains provisional. This package does not attempt those repairs.

## Verification and reproduction

Windows, Temurin **25.0.1+8-LTS**, Gradle **9.1.0**, configured JDK/release **25**, test worker **-Xmx4g**. `RAILROAD_SCAN_PATH` was unset. The post-build source-plus-binary boundary remains as documented in [the harness report](11-self-analysis-harness-correction.md).

- Final full fresh-build `check`: **1,126 application tests: 1,125 pass, 1 failure, 0 skipped**; the sole failure is self-analysis with 84 ERROR diagnostics. This includes all **18 new tests** and the existing semantic analyzer, rule-context, assignment/call, inspection, parser and captured/editor/workspace suites. CSS tests **5/5**, Javadoc tests **12/12**, Spotless passes and **67 stylesheets** validate. All 23 Gradle tasks executed; duration **5m 28s**. [Full console](w0/semantic-corrections/final-check.log), [totals and failed-case list](w0/semantic-corrections/verification.json), [focused test XML](w0/semantic-corrections/focused-tests.xml), [self-analysis XML](w0/semantic-corrections/self-analysis.xml).
- Independent Javac 25 controls: **9/9 positive snippets compile**, with generated probe classes kept outside source sets and the symbol index. [Sources and results](w0/semantic-corrections/javac-controls.json).
- `git diff --check`: passes.

A separate Gradle check was observed running concurrently during the initial validation. The final run uses a temporary [init script](w0/semantic-corrections/final-validation.init.gradle) solely to place JUnit/HTML/binary test reports in separate directories. It does not alter test selection, analysis inputs, JVM selection, heap or assertions; the other check had exited before the final self-analysis completed. Normal reproduction does not require this output-isolation script:

```powershell
$env:JAVA_HOME = 'D:\.jdks\temurin-25.0.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test --tests '*JavaSemanticCorrectnessRegressionTest' --console=plain
.\gradlew.bat check --continue --rerun-tasks --no-build-cache --console=plain
```

The exact final command added `-I build/semantic-correctness/final-validation.init.gradle`; its report root is `build/semantic-correctness/final-results`. A nonzero full-check exit remains expected until the other 84 diagnostics are resolved. No GitHub issues were modified, and no commit or push was made.

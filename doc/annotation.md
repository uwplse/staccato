# Annotating a Program and Writing a Rule file

## Annotations

Staccato offers two primary annotations, with a third annotation to control what types
are tracked. **Note**: all types listed here are specified relative to
the `edu.washington.cse.instrumentation.runtime` package. Both propagation and check annotations
can be added to class definitions and method definitions. Class level definitions are short-hand
for applying the same annotation to all the methods within the class. However, method-level
annotations override class-level annotations.

### Propagation

Propagation is controlled with the
`...annotation.StaccatoPropagate`. StaccatoPropagate takes one argument, a variant
of the enum `PropagationTarget`. `PropagationTarget` can be one of:

* `RECEIVER` - Configuration information is propagated from all configuration carrying
arguments is propagated to the receiver object. Disallowed on static methods.
* `RETURN` - Configuration information is propagated from all configuration carrying
arguments is propagated to the return object. If the method is non-static, the receivers
configuration information, if present, is also propagated. Disallowed on constructors.
* `NONE` - No configuration information is propagated. Used only to override class-level
annotations on methods.

### Check

Checking is controlled with the `...annotation.StaccatoCheck`. `StaccatoCheck` takes two
arguments, both optional. The first argument, `value`, is an enum of type `CheckLevel`.
`CheckLevel` can be one of:

* `LINEAR` - Check the consistency condition within the method.
* `STRICT` - Check the staleness condition within the method.
* `NONE` - Do not check the method.

The second argument, `argsOnly` is a boolean. If true, only the arguments of the method are
checked on method entry: all value reads within the method are not checked. If false, all
values read within the method (as described in the Staccato paper) are checked. The default
is false.

### Tracking Annotations

Staccato usually infers the types that should be tracked using propagation annotation and rules
(see below). A type may be explicitly marked for tracking with `...annotation.StaccatoTrack`.

## The Staccato Rule File Format

The Staccato Rule file format is an alternative method for annotating code. They are primarily used
for instrumenting classes for which the source code is available. The rule file format is line based, one rule
per line.
A limited form of comments is supported: lines starting with `//` (no preceding whitespace) are ignored.
Rules from other files may pulled in with statements of the form `#include other.rules` (note the lack of quotes).
This rule file `other.rules` must currently reside in the same directory as the file containing the include.

Rules primarily *select* methods for instrumentation and describe what should be done with that method. Each
rule either describes how methods should be selected for either checking or propagation.

### A Note on Selection

In the following two sections, we will discuss how a rule will *select* methods for instrumentation to add
checking or propagation. Selection is generally applied to a single class `C`:
each rule can select zero or more methods for instrumentation in `C`.
A subclass `D` of `C` that overrides methods selected by the rules applied to `C`
will also have those methods instrumented. However, the rules for `C` will not be applied to `D`.

For example, suppose `C` declares a method `foo(int)` and we have a rule that selects all
methods named `foo` in `C`. If we have a class `D` subclasses `C` that overrides `foo(int)`, `D`'s copy of
`foo(int)` will be selected for instrumentation.
However, if `D` also declares a method `foo(int, String)` that does **not**
appear in `C` it will not be selected for instrumentation, despite matching `C`'s name rule.

### Checking
A check line begins with a `!`. A check line that begins with only one `!` selects methods for checking the
consistency condition, whereas `!!` indicates the staleness condition. A check rule takes the following form:

```
!com.example.ClassName:methodName
```

This will select all methods named `methodName` in `com.example.ClassName`.

### Propagation

There several ways to selected methods for propagation. By default the propagation rules specify
propagation to the returned object. However, propagation to the receiver may be selected by prefixing
a rule with `^`. The propagation rules are as follows:

* `com.example.ClassName` : Select all methods in `com.example.ClassName` that also return a `com.example.ClassName`
* `<com.example.ClassName,com.example.OtherClass>`: Select all methods in `com.example.ClassName` that return a `com.example.OtherClass`
* `com.example.ClassName:methodName(<methodDescriptor>)` : Select precisely the method named `methodName` in `com.example.ClassName` with the JVM descriptor `<methodDescriptor>`
* `com.example.ClassName:ClassName(<methodDescriptor>)` : Select precisely the *constructor* of `com.example.ClassName` with the descriptor `<methodDescriptor>`. Note that the selected constructor will always propagate to the receiver
* `com.example.ClassName:methodName` : Select all methods named `methodName` in `com.example.ClassName`. All methods
selected with this rule must have the same return type
* `com.example.ClassName:ClassName` : Select all constructors of `com.example.ClassName`. Note that the selected
constructor will always propagate to the receiver.

In addition there is a special short hand for handling so called "java
beans". A rule of the form: ```@com.example:set*``` Selects all methods
prefixed with `set` in all classes that reside in the `com.example`
package (this rule does not apply to sub-packages). The methods
selected using this rule are instrumented to perform propagation to
the receiver object. This rule also selects for receiver-propagation
all constructors of classes appearing in the package
`com.example`. Note that the string `set` is not required and may
substituted with any other string.

Finally, Staccato allows for fine-grained control over what classes can track configuration information.
Usually this information is inferred from the Propagation rules but if this is not sufficient the following rules
may be used:

* `-com.class.NoTrack`: explicitly disables configuration tracking for `com.class.NoTrack`
* `+com.class.DefinitelyTrack`: explicitly enables configuration tracking for `com.class.DefinitelyTrack`

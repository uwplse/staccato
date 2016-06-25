# Staccato

This README details how to build/hack/run Staccato. Where necessary longer topics have
been split out into separate pages (found within this directory).

## Requirements

* Java 1.7
* Gradle >= 2.11

## Building

### Configuration

Before Staccato can be built, you must configure some paths required by the build script.
In the `bin/` directory create a `paths.sh` file containing a definition of *at least*
the following bash variables:

* `PHOSPHOR_PATH` - absolute path to a check out of the [patched version of Phosphor](https://github.com/uwplse/phosphor-fork)
* `JAVA_INST` - absolute path to the directory in which the Phosphor-instrumented JVM will be placed (e.g. `/opt/jvm/jvm-inst`)
* `ENUM_JAVA_INST` - absolute path to the directory in which the Phosphor-instrumented JVM (with enum support) will be placed (e.g., `/opt/jvm/jvm-enum-inst`)
* `JVM_PATH` - absolute path to the JRE environment to be used by Staccato/Phosphor. This should be the top level directory of the JRE (i.e., the folder containing `bin/`, `lib/` etc.)

A sample `paths.sh` file can be found at `bin/sample_paths.sh`. There are several other paths
commented out. These are required only if you wish to instrument the programs used in the
Staccato paper (see below).

### Building the Staccato Instrumentation Tool

In the top-level directory run `gradle instrumentJar`. This will produce
`staccato-instrument.jar` in the top-level directory. This is a so-called fat-jar
that contains all of the dependencies needed to instrument a program with Staccato.
However due to the complexity of options, it is strongly recommended 
you use the automation built into Staccato's build script (see below).

## Instrumenting a Program with Staccato

There are several ways to instrument use Staccato, based on how much effort you would like
to put in and the type of program being instrumented. You have the following options:

1. [A WAR via build script automation](instrumenting-war.md)
2. [A JAR with dependency libraries via build script automation](instrumenting-jar.md)
3. [Manually invoking Staccato](manual-staccato.md)

The manual option is the most difficult but provides the most flexibility.

Before you run Staccato, you will need to annotate your program,
integrate with the Staccato configuration abstraction, and write a rule file (see below).

## Instrumenting the JVM

Staccato programs should be run on a JVM instrumented with Phopshor. To get
such a JVM, run `gradle instrumentJVM` which will generate an instrumented JVM
in the folder specified with `JAVA_INST`.

## Enum Support

As mentioned in the Staccato paper enum support requires you to opt-in at several stages.

1. The JVM should be instrumented by Phosphor with enum support enabled.
2. The Phosphor instrumentation pass on the artifact needs to have enum support enabled.
3. Enum support should be enabled when running Staccato.

The steps for accomplishing this are laid out below.

**JVM Instrumentation**

Simply run `gradle instrumentEnumJVM` which will generate an enum-enabled instrumented JVM
in the folder specified by `ENUM_JAVA_INST`.

**Phosphor and Staccato**

Currently build automation only supports enums for JAR instrumentation. This is accomplished
by adding `enum_by_val true` to the `instrument` block. In addition, add the following:
`"staccato.jvm": $ENUM_PATH + "/lib/*", "staccato.check-enum": true` to the
`extraProps` dict. `$ENUM_PATH` should be replaced with the path specified for `ENUM_JAVA_INST`.
The steps for enabling enum support in the manual case can be found in
[that method's documentation](manual-staccato.md).

## Annotating a Program and Writing a Rule file

See the [separate annotation guide](annotation.md) for details.

## Integrating Staccato's Configuration Abstraction

All configuration operations need to be delegated to the Staccato runtime. The methods of
interest are found in `edu.washington.cse.instrumentation.runtime.TaintHelper` and consist of:

* setNewProp(String key, String value, Map<String, String>|Properties map)
* getProp(String key, Map<String, String>|Properties map[, String default])
* deleteProp(String key, Map<String, String>|Properties map)
* casProp(String key, String value, Map<String, String>|Properties map)

## Running a Program Instrumented with Staccato

To run a program instrumented with Staccato, add the arguments `-Xbootclasspath/p:<path-to-staccato-runtime.jar>:<path-to-phosphor.jar> -javaagent:<path-to-phosphor.jar>` somewhere to
the program command line.

The JVM used to run the instrumented program application must be been pre-instrumented with
Phosphor. If the application was instrumented to check enums, remember to
use a JVM that was also instrumented with enum support.

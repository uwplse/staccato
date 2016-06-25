# Manually Invoking Staccato

The Staccato instrumentation tool is flexible but requires several command-line flags to operate. This
documentation will skip over some options that are used for evaluation purposes or control experimental.
Staccato has two different entry points depending on the type of artifact being instrumented, a WAR or JAR.
However, the general pattern is the same between the two entry points. In either case the JAR or WAR being
instrumented must have been instrumented first with the forked version of Phosphor.

## Instrumenting with Phosphor

Phosphor should be invoked as follows:

`java -classpath <path-to-phosphor.jar> -multiTaint [extra-flags] [input-jar-or-war] [output-path]`

The `[extra-flags]` can be left blank unless you with to opt-in to enum checking.
In this case add `-withEnumsByValue -forceUnboxAcmpEq`.
*The input jar/war will be unchanged*: all Staccato instrumentation should be done
on the file specified in `[output-path]`.

## Common Options
Staccato should be invoked using the following parameters:

`java -classpath <path-to-staccato-instrument.jar>:<path-to-phosphor.jar> -Dstaccato.method-linear=true -Dstaccato.check-all-lin=true -Dstaccato.runtime-jar=<path-to-staccato.jar> -Dstaccato.wrap-volatile=true -Dstaccato.phosphor-jar=<path-to-phosphor.jar> -Dstaccato.app-classes=<app-pattern> -Dstaccato.jvm=<path-to-instrumented-jvm> [additional flags] [main-class] [args...]`

The value of these parameters should be mostly self-explanatory. `staccato.app-classes` is
identifies which classes belong to the application (and not dependent libraries).
This should be specified as a regular expression that matches `JVM internal names` (i.e., all classnames have
`'/'` as a package separator instead of `'.'`). The exact value of the `staccato.app-classes` parameter will
depend primarily on your use case.

The main-class and args will depend on whether you are instrumenting a JAR or WAR file. To add enum support
for your application, add the flag `-Dstaccato.check-enum=true`.
If you have opted into enum support, the instrumented JVM path should refer to
a JVM instrumented by Phosphor with enum support enabled. Similarly the artifact **must**
have been instrumented by Phosphor with enum support enabled (see above).

## Instrumenting a Jar

The main class for Jar instrumentation is `edu.washington.cse.instrumentation.InstrumentJar`. The arguments
are, in order, `[input-jar] [rule-file] [output-jar-name]`. The rule file is explained below.

Any class dependencies needed by the input jar must be included on the classpath of the JVM invoking the
Staccato instrumentation. This can be achieved by added another `-classpath` parameter or appending the
necessary JAR paths to the `-classpath` specified above.

Note that if you have library jars that you wish to instrument you will need to invoke `InstrumentJar` multiple
times.

## Instrumenting a WAR

WARs package dependencies in one single archive. This means application and library instrumentation can be
completed in one step. However setting up the instrumentation process is somewhat more complicated.

First you must unzip the entire WAR file to a directory somewhere on your file system. We will call this
directory the `expanded-war-path`. The path to the original, zipped WAR will be called `original-war-path`.

The main class to use when instrumenting a WAR is `edu.washington.cse.instrumentation.InstrumentWar`.
The arguments are as follows: `[expanded-war-path] [original-war-path] [rule-file] [lib-jar-names]...`

The `rule-file` is explained below. The `[lib-jar-names]` parameter is a (potentially) empty list of jar files
included as dependencies in the WAR that contain classes targeted for instrumentation by your rule file. These
must be the name *only*, and not a path within the archive. For example, to instrument classes in the dependency
library `awesome-db-lib.jar` simply include `awesome-db-lib.jar` after the `rule-file` on the command line.

# Instrumenting a collection of JARs via Build Automation

Let's say you want to instrument a program called "my-program" that consists of a single
"application" jar and several library jars. First, complete the
[common build automation steps](common-build.md).

Next, follow these steps:

5) Create a build task.

```groovy
task buildMyProgram(type: ...) { t ->
	ext.IsBuild = true
	inputs.files("/path/to/my-program/src")
	outputs.files(t.getTemporaryDir().path + "/my-program.jar")
	doLast {
		copy {
			from "/path/to/my-program/output/my-program.jar"
			into t.getTemporaryDir()
		}
	}
}
```

The exact steps to build my-program depends on my-program. For the appropriate
task and arguments, consult the [Gradle DSL Reference](https://docs.gradle.org/current/dsl/).
Regardless of the concrete build steps, the above snippet assumes that the compilation
process produces a file called `/path/to/my-program/output/my-program.jar`. The exact
name and location of the output artifact will vary based on your specific program.
The above snippet also assumes all source files for my-program can be found in
`/path/to/my-program/src`. The exact location and name will vary for your program.

6) Create an instrumentation task for my-program.

```groovy
task instrumentMyProgram { t ->
	instrument(t, buildMyProgram) {
		rule_file "/path/to/my-program.rules"
		outputJar "/path/to/my-program/output/my-program.jar"
		classpath '/path/to/my-program/lib/*'
		extraProps(["staccato.app-classes": '^com/my/program/.+$'])
	}
}
```

The `rule_file` configuration is the path to the rule file written for my-program. The
`outputJar` is where the instrumented jar will be placed.
`classpath` specifies the classpath of the my-program application: this is usually
the dependency jars found in the program's build directory.
`extraProps` can be used to pass extra arguments to Staccato. The only argument you should
need is the `staccato.app-classes` argument. This is a regular expression over
Java internal class names that specifies all application classes of my-program. These
application classes are instrumented using the default consistent condition.

7) For each library jar that requires instrumentation, create an instrumentation task:

```groovy
task instrumentLibrary { t->
	instrument(t, "/path/to/my-program/lib/library.jar") {
		rule_file "/path/to/library.rules"
		outputJar "/path/to/my-program/output/lib/library.jar"
	}
}
```

The `rule_file` and `outputJar` commands remain the same as in the previous step. The second
argument to `instrument` however is the uninstrumented version of the library jar. Be careful
not to overwrite the original library jar with the instrumented version: Staccato and Phosphor
are not idempotent and will fail on already instrumented inputs.


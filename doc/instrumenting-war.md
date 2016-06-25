# Instrumenting a WAR via Build Automation

Let's say you want to instrument a web-app called "my-program" with Staccato. First, complete
the [common build automation steps](common-build.md).

Next, follow these steps:

5) Create a build task for my-program. The following snippet shows
the required components:

```groovy
task buildMyProgram(type: ...) { t ->
  // ...
  ext.IsBuild = true
  inputs.files("/path/to/my-program/src")
  outputs.files(t.getTemporaryDir().path + "/my-program-artifact.war")
  doLast {
	copy {
	  from "/path/to/my-program/output/my-program-artifact.war"
	  into t.getTemporaryDir()
	}
  }
}
```

The exact steps to build my-program depends on my-program. For the appropriate
task and arguments, consult the [Gradle DSL Reference](https://docs.gradle.org/current/dsl/).
Regardless of the concrete build steps, the above snippet assumes that the compilation
process produces a file called `/path/to/my-program/output/my-program-artifact.war`. The exact
name and location of the output artifact will vary based on your specific program.
The above snippet also assumes all source files for my-program can be found in
`/path/to/my-program/src`. The exact location and name will vary for your program.

6) Create your instrumentation task.

```groovy
task instrumentMyProgram { t -> 
  instrument_war(t, buildMyProgram) {
	rule_file "/path/to/rule_file.rules"
	output_war "/path/to/my-program/output/my-program/artifact.war"
	lib_jar "libjar1.jar", "libjar2.jar", ...
	extraProps(["staccato.app-classes": '^com/my/program/.*$'])
  }
}
```

The `rule_file` configuration is the path to the rule file written for my-program. The
`output_war` is where the instrumented war will be placed.
`lib_jar` is a list of library jar names in the WAR that require instrumentation (as specified by your rule file, see [here](annotation.md) for details). Notice that only the names of these archives are specified: the path within the WAR is **not** included.
`extraProps` can be used to pass extra arguments to Staccato. The only argument you should
need is the `staccato.app-classes` argument. This is a regular expression over
Java internal class names that specifies all application classes of my-program. These
application classes are instrumented using the default consistent condition.

That's it! Although this may seem like a lot of work it pays off: with the above configuration
gradle will automatically rebuild your project whenever phosphor source, staccato source,
my-program's the rule file, or my-program's source changes.

To instrument your program now simply run: `gradle my-program:instrumentMyProgram` which will
generate the instrumented war at the location specified in `output_war`.

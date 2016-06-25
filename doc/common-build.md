# Common Build Automation Steps

Let's say you want to instrument a program called "my-program" with Staccato. Regardless
of whether the program is a standalone WAR or a collection of JARs, build automation
requires you first follow these steps:

1) Create a sub-project in the Staccato project with a name of your choosing (e.g., "my-program"). To do this, simply add `include "my-program"` to `settings.gradle`

2) Add a configuration block for this project to `build.gradle`. `build.gradle` already contains
similar blocks for jforum, openfire, and subsonic and you can refer to them as sample
configurations. All future configuration steps should be written within this block.
Project configuration blocks are written as follows:

```groovy
project("my-program") {
 // configuration goes here
}
```

3) Add the following boiler plate as the first line in the configuration: `buildDir = "$rootProject.buildDir/my-project"`

4) If the program has a library folder which contains dependent 
library jars, add the following line `ext.build_lib_dir = /path/to/lib/dib`. This is where
"my-program" looks for libraries during compilation.

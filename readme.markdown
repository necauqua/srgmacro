# SRG macro

This is a silly Gradle task that replaces calls
like `srg("some MCP name")` with a string literal with
appropriate SRG name from the mapping you're using.

I've extracted it into a small plugin from one of
my `build.gradle` files so that it can be reused 
and updated without copying a bunch of lines of code.

While making Java have "macros" like that is silly,
this plugin is still pretty useful for Minecraft
moddding, at least when you have to deal with obfuscated
name a lot, such as when making coremods.

## Usage
You can add it to your `build.gradle` like so:
```groovy
plugins {
    id 'dev.necauqua.srgmacro' version '1.0.0'
}
```
Or, if you're into legacy systems, like so:
```groovy
buildscript {
    repositories {
        maven { url = 'https://maven.necauqua.dev' }
    }
    dependencies {
        classpath 'dev.necauqua.srgmacro:srgmacro:1.0.0'
    }
}

apply plugin: 'dev.necauqua.srgmacro'
```

It will just work after that, there is no additional
configuration (yet).
Keep in mind that this plugin makes no sense without
ForgeGradle and SRG mappings it provides.

Obviously you'd need to have a stub `srg` method.

My preferred way to have this method is as follows:
```java
public static String srg(String mcpName) {
    // allow it to run without preprocessing from Intellij IDEA
    // (or use some other hack for your IDE)
    if (System.getProperty("java.class.path").contains("idea_rt.jar")) {
        return mcpName;
    }
    // but still keep the check for incorrect compilation or whatever
    throw new IllegalStateException("Gradle preprocessing was not applied! Macro: srg(\"" + mcpName + "\")");
}
```

Note that you might also need to use and so to define a 

`String srg(String mcpName, String simpleClassName)`

and a

`String srg(String mcpName, String simpleClassName, String desc)`

variations.

## CONTRIBUTING
This plugin is very small and looks pretty finished,
so if you have issues - make issues on GitHub.

## LICENSE
Licensed under MIT license - do whatever you can with
the code without any warranties from me as long as
you include the `LICENSE` file, which has my name
at the top.

# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/virl/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# http://proguard.sourceforge.net/manual/examples.html#library
# http://proguard.sourceforge.net/manual/usage.html

#-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,EnclosingMethod,MethodParameters,SourceFile,LineNumberTable

-flattenpackagehierarchy 'io.underdark.impl'
-allowaccessmodification

-keep class io.underdark.** { *; }
#-keep class io.underdark.**$** { *; }

-keeppackagenames io.underdark.**

# SLF4J
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Netty
-dontwarn io.netty.**
-keep class io.netty.** { *; }

-assumenosideeffects class impl.underdark.logging.Logger {
    public static void debug(...);
    public static void info(...);
    public static void warn(...);
    public static void error(...);
}
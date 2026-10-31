# Keep line numbers so Crashlytics stack traces - deobfuscated with the mapping file the Crashlytics
# Gradle plugin uploads for minified builds - point at real source lines. The source file name itself
# is still obfuscated.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

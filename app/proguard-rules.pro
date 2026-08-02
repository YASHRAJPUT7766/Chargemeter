# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- iText7 (PDF report export) ---
# iText7's core module optionally references BouncyCastle (for encryption)
# and SLF4J's StaticLoggerBinder (for older logging bridges). Neither is a
# real dependency of this app — they're reflectively probed at runtime and
# iText7 falls back gracefully when absent. Without these rules, R8 treats
# the missing classes as a hard build error during release minification.
-dontwarn com.itextpdf.bouncycastle.**
-dontwarn com.itextpdf.bouncycastlefips.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**

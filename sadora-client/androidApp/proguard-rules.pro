# R8 rules for the release build.
#
# Everything here exists because something in the app is found by name at runtime rather
# than by a call R8 can see. The wire format is the main one: kotlinx.serialization
# generates a `$$serializer` per class and looks it up reflectively, so a shrunk build
# without these rules compiles, installs, and then fails on the first API call.

# Stack traces from Play Console are unreadable without the line numbers, and the
# mapping file is what turns them back into names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Signature and the annotations are what the serializer and Ktor's type information read.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ------------------------------------------------------------------ serialization

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The DTOs shared with the backend, and every enum they carry. `:contract` and the app
# both live under uz.sadora, so one pair of rules covers them.
-keep,includedescriptorclasses class uz.sadora.**$$serializer { *; }
-keepclassmembers class uz.sadora.** {
    *** Companion;
}
-keepclasseswithmembers class uz.sadora.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# valueOf() is how the serializer turns a wire string back into an enum constant.
-keepclassmembers enum uz.sadora.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------------ ktor and okhttp

-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Ktor logs through SLF4J, and Android has no binding for it. The calls are no-ops; the
# warnings are about a logging backend that is deliberately absent.
-dontwarn org.slf4j.**

# Referenced by OkHttp's optional TLS paths, which this app does not take.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ------------------------------------------------------------------ coroutines

-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

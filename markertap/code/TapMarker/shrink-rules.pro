# Shrink unused library code. Preserve names, native bindings, app-process
# entry points, provider reflection, and all user-facing application functions.
-dontobfuscate
-dontoptimize
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*
-keep class jp.tapmarker.** { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }
-keep class org.conscrypt.** { *; }
# Preserve the reference peer used to verify the shipped pairing cipher both ways.
-keepclassmembers class io.github.muntashirakon.adb.PairingAuthCtx {
    public static io.github.muntashirakon.adb.PairingAuthCtx createBob(byte[]);
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn org.conscrypt.**
-dontwarn dalvik.system.**
-dontwarn org.bouncycastle.**
# D8 desugars the Java 8 lambda bootstrap after the host-testable classfile step.
-dontwarn java.lang.invoke.LambdaMetafactory
# AndroidX uses these as optional compile-time annotation metadata only.
-dontwarn kotlin.Metadata
-dontwarn kotlin.annotation.**

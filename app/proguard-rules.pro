# Proguard rules for GitSyncer
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.eclipse.jgit.annotations.* <fields>;
    @org.eclipse.jgit.annotations.* <methods>;
}
-dontwarn org.eclipse.jgit.**
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**

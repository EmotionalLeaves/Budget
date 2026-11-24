# Sprout release rules.

# Strip android.util.Log calls from release builds so nothing can ever be logged on users' phones.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static boolean isLoggable(java.lang.String, int);
}

# Room: entities and DAOs are generated code referenced by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }

# kotlinx.serialization: Sprout parses JSON as a tree (no @Serializable classes), but keep the
# serializer lookup paths intact in case any are added later.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# WorkManager workers are created by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# OkHttp ships its own rules; silence optional platform warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

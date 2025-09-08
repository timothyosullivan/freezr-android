# Keep Hilt generated components & entry points
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# Room (keep entities, Dao method names, schema)
-keep class androidx.room.** { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.freezr.data.model.** { *; }

# ML Kit Barcode (safe keep for reflection)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }

# Keep composable metadata (Compose tooling uses)
-keep class **$ComposableSingletons* { *; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep MainActivity intent action strings & receivers
-keep class com.freezr.MainActivity { *; }
-keep class com.freezr.reminder.** { *; }

# Avoid stripping enums used in SQL string comparisons
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Kotlin reflection / metadata
-keep class kotlin.Metadata { *; }

# Suppress warnings for generated code we don't use
-dontwarn dagger.hilt.internal.**
-dontwarn javax.annotation.**

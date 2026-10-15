# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Kotlin when-mappings (needed for sealed class branches)
-keepclassmembers class **$WhenMappings { *; }

# ML Kit / Play Services (tránh strip class native)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }

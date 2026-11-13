# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

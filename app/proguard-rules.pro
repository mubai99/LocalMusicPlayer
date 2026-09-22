# 保留 Media3 / ExoPlayer 需要的类
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# 保留 Room 生成的 DAO 实现
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Coil
-dontwarn coil.**

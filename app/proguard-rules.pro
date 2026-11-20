# 保留 Kotlin 序列化生成器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.hoopoe.zyfuntv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Room ----
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ---- App data models / entities (serialized or reflected) ----
-keep class com.hoopoe.zyfuntv.data.remote.model.** { *; }
-keep class com.hoopoe.zyfuntv.data.local.** { *; }
-keep class com.hoopoe.zyfuntv.core.model.** { *; }
-keep class com.hoopoe.zyfuntv.data.repository.BackupSummary { *; }

# ---- Media3 / ExoPlayer ----
-dontnote androidx.media3.**
-dontwarn androidx.media3.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**

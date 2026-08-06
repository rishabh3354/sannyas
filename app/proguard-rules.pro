# --- kotlinx.serialization -------------------------------------------------
# The catalog is decoded reflectively via generated serializers; R8 must keep
# them or discourses.json fails to parse in release builds.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.osho.player.data.local.catalog.** {
    *** Companion;
}
-keepclasseswithmembers class com.osho.player.data.local.catalog.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.osho.player.data.local.catalog.**$$serializer { *; }

# --- Media3 ----------------------------------------------------------------
# DownloadService and MediaLibraryService are instantiated by the framework
# from the manifest, so their no-arg constructors must survive shrinking.
-keep class com.osho.player.playback.PlaybackService { *; }
-keep class com.osho.player.data.download.AudioDownloadService { *; }
-dontwarn androidx.media3.**

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---------------------------------------------------------
-dontwarn com.google.errorprone.annotations.**

# --- Coroutines ------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Add project specific ProGuard rules here.

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **.*$serializer { static **.$serializer INSTANCE; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Timber
-dontwarn org.slf4j.**

# Vendored BRouter routing engine (reads profiles/data reflectively-ish; keep it intact)
-keep class btools.** { *; }
-dontwarn btools.**

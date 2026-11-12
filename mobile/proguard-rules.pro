# Add project specific ProGuard rules here.

# Keep GraphHopper classes
-keep class com.graphhopper.** { *; }
-keep interface com.graphhopper.** { *; }
-dontwarn com.graphhopper.**

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

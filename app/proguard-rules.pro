# Add project specific ProGuard rules here.
-keep class com.example.model.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.codehaus.mojo.**
-dontwarn javax.annotation.**

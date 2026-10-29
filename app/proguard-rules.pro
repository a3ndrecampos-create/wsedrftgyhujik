# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.promoapp.**$$serializer { *; }
-keepclassmembers class com.promoapp.** { *** Companion; }
-keepclasseswithmembers class com.promoapp.** { kotlinx.serialization.KSerializer serializer(...); }

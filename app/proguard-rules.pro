# kotlinx.serialization — keep generated serializers of our models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.mlg.quedalle.** {
    *** Companion;
}
-keepclasseswithmembers class dev.mlg.quedalle.** {
    kotlinx.serialization.KSerializer serializer(...);
}

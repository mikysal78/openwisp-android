# kotlinx.serialization: i serializer generati vengono cercati per nome
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class it.ninuxnnxx.openwisp.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class it.ninuxnnxx.openwisp.**$$serializer { *; }

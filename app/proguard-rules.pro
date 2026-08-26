-keep class com.comsat.audio.data.model.** { *; }
-keepclassmembers class com.comsat.audio.data.model.** { *; }

# Gson relies on generic signatures at runtime; R8 strips them by default.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses,EnclosingMethod

# Anonymous TypeToken subclasses must keep their type argument.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Gson builds these reflectively. Without a keep, R8 sees no allocation site,
# marks the class abstract, and deserialization dies with
# "Abstract classes can't be instantiated!". A -if rule is no good here: its
# condition only fires for members that are already live, and nothing but Gson
# ever touches these fields.
-keepclasseswithmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    <init>(...);
}

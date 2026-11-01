# Chaquopy and copyparty are reflected into from Kotlin: keep the python bridge and serializers.
-keep class com.peaspilaf.copyparty.** { *; }
-keepclassmembers class * { *** Companion; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Keep Leanback / TV public API
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# Keep Glide generated registries
-keep class * extends com.bumptech.glide.module.AppGlideModule
-keep class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

# Hilt
-keepattributes *Annotation*
-keep class dagger.hilt.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Reflective access for ViewModels
-keepclassmembers class * extends androidx.lifecycle.ViewModel { <init>(...); }

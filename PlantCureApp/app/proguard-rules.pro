# ─── PlantCure AI ProGuard Rules ───

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Retrofit
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn retrofit2.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder { *** rewind(); }

# PlantCure AI data classes (prevent obfuscation of API models)
-keep class com.plantcure.ai.data.remote.** { *; }
-keep class com.plantcure.ai.data.local.entity.** { *; }
-keep class com.plantcure.ai.domain.model.** { *; }

# Hilt
-dontwarn dagger.hilt.**

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Lottie
-dontwarn com.airbnb.lottie.**

# Google Maps
-keep class com.google.android.gms.maps.** { *; }

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

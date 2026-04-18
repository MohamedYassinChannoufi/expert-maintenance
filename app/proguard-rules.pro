# Expert Maintenance - ProGuard/R8 Configuration
# Add project specific ProGuard rules here.

# Keep model classes (used for JSON serialization)
-keep class com.expert.maintenance.data.** { *; }
-keep class com.expert.maintenance.data.local.entity.** { *; }
-keep class com.expert.maintenance.data.local.dao.** { *; }
-keep class com.expert.maintenance.api.ApiService$* { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes AnnotationInnerClasses
-keepattributes EnclosingMethod
-keep class retrofit2.** { *; }
-keep class retrofit2.** { *; }
-keepclasseswithmembernames class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class com.expert.maintenance.data.local.entity.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# RecyclerView & Adapters
-keep class com.expert.maintenance.adapters.** { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView.Adapter { *; }
-keep class * extends androidx.recyclerview.widget.RecyclerView.ViewHolder { *; }

# UI Activities
-keep class com.expert.maintenance.ui.** { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }

# ViewModel & LiveData
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Data Binding (if used)
-keepclassmembers,allowobfuscation class * extends androidx.databinding.BaseObservable {
    @androidx.databinding.BindingMethod <methods>;
    @androidx.databinding.BindingMethods <methods>;
    @androidx.databinding.BindingAdapter <methods>;
}

# Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Prevent obfuscation of model fields used in JSON
-keepclassmembers class com.expert.maintenance.** {
    <fields>;
}

# Keep generic signatures for Retrofit
-keep,allowobfuscation,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class * {
    @retrofit2.http.* <methods>;
}

# Keep files path configuration
-keep class androidx.core.content.FileProvider

# Keep NavigationView
-keep class * implements com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener { *; }

# Keep DialogFragments
-keep class * extends androidx.fragment.app.DialogFragment { *; }

# Keep custom views
-keep class * extends android.view.View {
    <init>(android.content.Context);
    <init>(android.content.Context, android.util.AttributeSet);
    <init>(android.content.Context, android.util.AttributeSet, int);
    <init>(android.content.Context, android.util.AttributeSet, int, int);
}

# Keep constructors for data binding
-keepclasseswithmembers class * {
    @androidx.databinding.BindingMethod <methods>;
    @androidx.databinding.BindingMethods <methods>;
    @androidx.databinding.BindingAdapter <methods>;
}

# Logging (remove in production if desired)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# Keep sync manager
-keep class com.expert.maintenance.data.SyncManager { *; }
-keep class com.expert.maintenance.data.SyncResult { *; }
-keep class com.expert.maintenance.data.UploadResult { *; }

# Keep API response classes
-keep class com.expert.maintenance.api.ApiService$* { *; }

# Keep constructors for JSON deserialization
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep enum fields
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotation attributes
-keepattributes *Annotation*

# Don't warn about missing dependencies
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.collections.ArraysKt

# Optimize for smaller APK (optional - comment out for debugging)
# -optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
# -optimizationpasses 5
# -allowaccessmodification
# -repackageclasses ''
# -aggressive-opts

# Keep R classes
-keep class **.R
-keep class **.R$* {
    <clinit>;
    public static <fields>;
}

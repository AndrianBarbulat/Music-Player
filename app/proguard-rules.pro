# ──────────────────────────────────────────────────────────────────────────────
# Stack-trace readability
# ──────────────────────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin
# ──────────────────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ──────────────────────────────────────────────────────────────────────────────
# Kotlin Coroutines
# ──────────────────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ──────────────────────────────────────────────────────────────────────────────
# Jetpack Compose — compiler generates synthetic classes; keep them all
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ──────────────────────────────────────────────────────────────────────────────
# AndroidX ViewModel
# ──────────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ──────────────────────────────────────────────────────────────────────────────
# Media3 / ExoPlayer — uses reflection for codec and renderer selection
# ──────────────────────────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ──────────────────────────────────────────────────────────────────────────────
# Guava — bundled by Media3 for ListenableFuture
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.google.common.util.concurrent.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**

# ──────────────────────────────────────────────────────────────────────────────
# Coil
# ──────────────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ──────────────────────────────────────────────────────────────────────────────
# Kotlinx Immutable Collections
# ──────────────────────────────────────────────────────────────────────────────
-keep class kotlinx.collections.immutable.** { *; }
-dontwarn kotlinx.collections.immutable.**

# ──────────────────────────────────────────────────────────────────────────────
# App: data models used with JSON serialisation (SharedPreferences)
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.example.musicplayerdeck.data.model.** { *; }
-keep class com.example.musicplayerdeck.data.repository.** { *; }

# ──────────────────────────────────────────────────────────────────────────────
# App: Android entry points declared in the manifest
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.example.musicplayerdeck.MainActivity { *; }
-keep class com.example.musicplayerdeck.service.PlaybackService { *; }

# ──────────────────────────────────────────────────────────────────────────────
# Standard Android component keeps
# ──────────────────────────────────────────────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

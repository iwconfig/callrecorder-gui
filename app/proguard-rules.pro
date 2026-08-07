# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name of the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep AI data models for JSON serialization
-keep class com.android.bcrgui.model.Bookmark { *; }
-keep class com.android.bcrgui.model.AiTranscription { *; }
-keep class com.android.bcrgui.model.AiMetadata { *; }
-keep class com.android.bcrgui.model.TranscriptionSegment { *; }

# Keep transcription repositories
-keep class com.android.bcrgui.transcription.** { *; }

# Keep WorkManager workers
-keep class androidx.work.** { *; }
-keep class com.android.bcrgui.transcription.TranscriptionWorker { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
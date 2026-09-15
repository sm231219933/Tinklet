# R8 Optimization rules for memory and performance
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-overloadaggressively

# General Optimizations
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Keep WebRTC classes (Essential for Calling)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Socket.io (Essential for Signaling)
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# Firebase/Google Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.firebase.** { *; }

# OkHttp/Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Gson/Moshi serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

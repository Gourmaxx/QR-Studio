# ZXing core : conservé tel quel (utilisé par réflexion pour certains formats).
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

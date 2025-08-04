# Add project specific ProGuard rules here.

# Open Source WebRTC ProGuard rules
-keep class com.casparvdbroek.securecam.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

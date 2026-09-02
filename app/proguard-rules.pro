# ------------------------------------------------------------------
# DEBUGGING & CRASH REPORTING
# Preserves line numbers for production crash reports (Crashlytics/Play Console)
# ------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------
# APP ARCHITECTURE (Room, Hilt, Navigation)
# ------------------------------------------------------------------
# Keep all data models so databases/serialization don't crash
-keep class com.ben.emberr.domain.model.** { *; }

# Keep all ViewModels so Hilt can inject them properly
-keep class com.ben.emberr.presentation.**.*ViewModel { *; }

# Keep Compose Navigation arguments safe
-keepnames class androidx.navigation.compose.** { *; }

# ------------------------------------------------------------------
# THIRD-PARTY LIBRARIES
# ------------------------------------------------------------------
# Keep SQLCipher and its native JNI fields intact
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }

# Suppress R8 warnings for compile-time only annotations
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
# 岁余默认不开启混淆；如需开启，保留 ML Kit 相关类
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

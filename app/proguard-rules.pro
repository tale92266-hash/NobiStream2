# NobiStream 2 — debug builds are not shrunk (Sam's standing rule:
# complete debug APKs, no R8). These rules only apply to release builds.
-keep class com.nobi.nobistream2.** { *; }

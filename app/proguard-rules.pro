# Project-specific R8 rules go here.

# ML Kit builds its parts from class names listed in the manifest. R8 keeps those names but
# drops the constructors nothing calls directly, and the barcode scanner then crashes on open.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

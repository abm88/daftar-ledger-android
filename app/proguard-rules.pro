# Compose + Hilt work out of the box with the default rules.
# Add API-model keep rules here once the network layer is integrated.
# Gson maps the documented wire names reflectively. Keep wire models stable in
# minified release builds; domain/UI classes remain eligible for shrinking.
-keepattributes Signature
-keep class com.daftar.app.data.remote.**Dto { *; }
-keep class com.daftar.app.data.remote.**Request { *; }
-keep class com.daftar.app.data.remote.**Response { *; }

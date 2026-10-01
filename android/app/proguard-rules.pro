# No reflection-based serialization is used.
# Glance records which GlanceAppWidget class serves each receiver by class name; renaming it
# across upgrades would leave updateAll() finding no widgets until the next system update.
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget

# Room generates adapters at compile time; no reflection rules required.
-keepattributes *Annotation*

# Keep enum names used by Room type converters.
-keepclassmembers enum com.homerentmanager.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

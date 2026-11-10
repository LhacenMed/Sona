# NewPipe's extractor, which reads YouTube in-process - the rules the NewPipe app ships with it.
# TimeAgoPatternsManager loads each language's patterns by class name.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.** implements java.io.Serializable {
    static final long serialVersionUID;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}
# Rhino, which runs YouTube's player script where a stream asks for it, binds Java members reflectively.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
# YouTube's request and response messages are read by field name.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# jaudiotagger reaches three of its own packages only by reflection, so a minified build would strip them
# and tagging would fail there while working in debug - YTDLnis's rules. Each keep needs the constructors:
#   framebody   ID3 frame bodies found by name, Class.forName("..FrameBody" + frameId)
#   datatype    frame body fields copied through getConstructor(), on every ID3 version change
#   asf.io      WMA chunk readers made from their registered Class
-keep class org.jaudiotagger.tag.id3.framebody.** { *; }
-keep class org.jaudiotagger.tag.datatype.** { *; }
-keep class org.jaudiotagger.audio.asf.io.** { *; }

# Its desktop artwork path reaches for AWT and ImageIO, which Android has neither of. It only has to link:
# the editor embeds a cover as bytes, and never decodes one through it.
-dontwarn java.awt.**
-dontwarn javax.imageio.**

package it.polito.mad.streamsender.encoding;

/**
 * Created by luigi on 12/04/16.
 */
public class StreamSenderJNI {

    static {
        try {
            System.loadLibrary("MediaEncoder");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native code library failed to load.\n" + e);
            System.exit(1);
        }
    }

    public static native void nativeInitEncoder(//final int width,
                                                //final int height
                                                 );
    public static native void nativeReleaseEncoder();

    public static native void nativeApplyParams(final int width,
                                          final int height,
                                          final int bitrateKbps);

    public static native byte[] nativeDoEncode(final int width,
                                      final int height, byte[] yuv,
                                      final int flag);

    // A native method that returns a Java String to be displayed on the
    // TextView
    public static native String hello();

    public static native byte[][] nativeGetHeaders();

}

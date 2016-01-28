package it.polito.mad.streamsender;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import it.polito.mad.websocket.AsyncClientImpl;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class EncoderTask extends AsyncTask<Void, VideoChunks.Chunk, Void> {

    public static abstract class Listener {
        void onEncodedDataAvailable(byte[] data){}
    }

    private static final String TAG = "ENCODER";
    private static final boolean VERBOSE = true;

    //private static final int TIMEOUT_US = -1;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 500000;
    private static final long NUM_FRAMES = -1;

    private Listener mListener;
    private VideoChunks mRawFrames = new VideoChunks();
    private int mWidth = 640, mHeight = 480;

    public void setListener(Listener mListener) {
        this.mListener = mListener;
    }

    public void submitAccessUnit(byte[] data){
        mRawFrames.addChunk(data, 0, 0);
    }

    @Override
    protected Void doInBackground(Void... params) {
        MediaCodec encoder = null;
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        if (codecInfo == null) {
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return null;
        }
        Log.d(TAG, codecInfo.toString());
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                //selectColorFormat(codecInfo, MIME_TYPE);
        if (colorFormat == 0){
            Log.e(TAG,"couldn't find a good color format for " + codecInfo.getName() + " / " + MIME_TYPE);
            return null;
        }

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE_BPS);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        try{
            encoder = MediaCodec.createByCodecName(codecInfo.getName());
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return null;
        }

        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        long framesCounter = 0, encodedSize = 0;

        boolean inputDone = false;
        boolean outputDone = false;
        int inputStatus = -1, outputStatus = -1;

        if (VERBOSE) Log.d(TAG, "Encoder starts...");
        while (!outputDone) {
            if (!inputDone) {
                //if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
                inputStatus = encoder.dequeueInputBuffer(-1);
                if (inputStatus < 0){
                    Log.e(TAG, "Unknown input buffer status: "+inputStatus);
                    continue;
                }
                long pts = computePresentationTime(framesCounter);
                if (framesCounter == NUM_FRAMES) {
                    // Send an empty frame with the end-of-stream flags set.  If we set EOS
                    // on a frame with data, that frame data will be ignored, and the
                    // output will be short one frame.
                    encoder.queueInputBuffer(inputStatus, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputDone = true;
                    if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                }
                else {
                    ByteBuffer inputBuf = encoderInputBuffers[inputStatus];
                    inputBuf.clear();

                    if (VERBOSE) Log.d(TAG, "Waiting for new access unit from camera...");
                    VideoChunks.Chunk chunk = mRawFrames.getNextChunk();
                    if (chunk == null){
                        if (VERBOSE) Log.d(TAG, "Cancelling thread...");
                        break;
                    }
                    byte[] previewData = chunk.data;

                    inputBuf.put(previewData);
                    encoder.queueInputBuffer(inputStatus, 0, previewData.length, pts, 0);
                    if (VERBOSE) Log.d(TAG, "submitted frame " + framesCounter);
                }
                framesCounter++;
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (VERBOSE) Log.i(TAG, "Waiting for output buffer");
            outputStatus = encoder.dequeueOutputBuffer(info, -1);

            if (outputStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
                if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
            } else if (outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
                ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);
            } else if (outputStatus < 0) {
                Log.e(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + outputStatus);
            } else { // outputStatus >= 0
                ByteBuffer encodedData = encoderOutputBuffers[outputStatus];

                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);
                encodedSize += info.size;

                //publish result to the caller
                byte[] encodedArray = new byte[encodedData.remaining()]; //converting bytebuffer to byte array
                encodedData.get(encodedArray);
                VideoChunks.Chunk c =
                        new VideoChunks.Chunk(encodedArray, info.flags, info.presentationTimeUs);
                publishProgress(c);
                Log.d(TAG, "Published chunk # "+framesCounter+" to decoder");


                if (VERBOSE) Log.d(TAG, "Encoded buffer size: "+info.size+" " +
                        "TOTAL Encoded size: "+encodedSize);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (VERBOSE) Log.i(TAG, "First coded packet ");
                } else {
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
                encoder.releaseOutputBuffer(outputStatus, false);
            }
        }
        encoder.stop();
        encoder.release();
        Log.i(TAG, "Encoder Released!! Closing...");
        return null;
    }
/*
    @Override
    protected void onProgressUpdate(byte[]... values) {
        if (mListener != null){
            byte[] data = values[0];
            mListener.onEncodedDataAvailable(data);
        }
    }
*/

    private static MediaCodecInfo selectCodec(String mimeType) {
        MediaCodecInfo res = null;
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (codecInfo.isEncoder()) {
                String[] types = codecInfo.getSupportedTypes();
                for (int j = 0; j < types.length; j++) {
                    if (types[j].equalsIgnoreCase(mimeType)) {
                        Log.d("CODECINFO", codecInfo.toString());
                        return codecInfo;
                        //res = codecInfo;
                    }
                }
            }
        }
        return null;
        //return res;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int idx=-1;
        String tag = "COLOR";
        for (int i=0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            switch (colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                    Log.d(tag, "COLOR_FormatYUV420Planar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                    Log.d(tag, "COLOR_FormatYUV420PackedPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                    Log.d(tag, "COLOR_FormatYUV420SemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                    Log.d(tag, "COLOR_FormatYUV420PackedSemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                    Log.d(tag, "COLOR_TI_FormatYUV420PackedSemiPlanar");break;
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                    Log.d(tag, "COLOR_FormatYUV420Flexible");// break;

                    return colorFormat;
            }
            idx=i;
        }
        if (idx >= 0){
            return capabilities.colorFormats[idx];
        }
        return 0;
    }

    private static long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}

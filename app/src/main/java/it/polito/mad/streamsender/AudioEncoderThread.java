package it.polito.mad.streamsender;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class AudioEncoderThread implements Runnable {

    public interface Listener {
        void onEncodedDataAvailable(MediaChunks.Chunk chunk, boolean configBytes);
    }

    private static final String TAG = "AUDIOENC";
    private static final boolean VERBOSE = false;

    //private static final int TIMEOUT_US = -1;
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_COUNT = 2;

    private Thread mWorkerThread;
    private Listener mListener;
    private MediaChunks mRawFrames = new MediaChunks();

    public AudioEncoderThread(Listener listener){
        mListener = listener;
    }

    public void startThread(){
        if (mWorkerThread != null){
            return;
        }
        mWorkerThread = new Thread(this);
        mWorkerThread.start();
    }

    public void requestStop(){
        if (mWorkerThread == null){
            return;
        }
        mWorkerThread.interrupt();
    }

    public boolean waitForTermination(){
        if (mWorkerThread == null){
            return true;
        }
        boolean result = true;
        try{
            mWorkerThread.join();
        } catch(InterruptedException e){
            result = false;
        }
        mWorkerThread = null;
        return result;
    }

    public boolean isRunning(){
        return mWorkerThread != null;
    }

    public void setListener(Listener mListener) {
        this.mListener = mListener;
    }

    public void submitAccessUnit(byte[] data){
        mRawFrames.addChunk(true, data, 0, 0);
    }

    public void drain(){
        mRawFrames.clear();
    }

    @Override
    public void run() {
        MediaCodec encoder = null;

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64*1024);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);

        try{
            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return;
        }

        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        long framesCounter = 0, encodedSize = 0;

        boolean inputDone = false;
        boolean outputDone = false;
        int inputStatus = -1, outputStatus = -1;

        Log.d(TAG, "Encoder started");
        while (!Thread.interrupted() && !outputDone) {
            if (!inputDone) {
                //if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
                inputStatus = encoder.dequeueInputBuffer(10000);
                if (inputStatus < 0){
                    if (VERBOSE) Log.w(TAG, "No input buffer available. retry");
                    //Log.e(TAG, "Unknown input buffer status: "+inputStatus);
                    continue;
                }

                long pts = computePresentationTime(framesCounter);
                ByteBuffer inputBuf = encoderInputBuffers[inputStatus];
                inputBuf.clear();
                int bufferLength = 0;
                int flags = 0;
                if (VERBOSE) Log.d(TAG, "Waiting for new audio frame...");
                MediaChunks.Chunk chunk = mRawFrames.getNextChunk();
                if (chunk == null){
                    if (VERBOSE) Log.d(TAG, "Cancelling thread...");
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    inputDone = true;
                    if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                    encoder.queueInputBuffer(inputStatus, 0, bufferLength, pts, flags);
                }
                else{
                    byte[] previewData = chunk.data;
                    if (inputBuf.remaining() >= previewData.length) {

                        bufferLength = previewData.length;
                        //Log.d(TAG, "buf remaining()=" + inputBuf.remaining() + " byte[] size=" + previewData.length);
                        inputBuf.put(previewData);
                        encoder.queueInputBuffer(inputStatus, 0, bufferLength, pts, flags);
                    }
                }

                //encoder.queueInputBuffer(inputStatus, 0, bufferLength, pts, flags);

                framesCounter++;
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (VERBOSE) Log.i(TAG, "Waiting for output buffer");
            outputStatus = encoder.dequeueOutputBuffer(info, 10000);
            if (outputStatus == MediaCodec.INFO_TRY_AGAIN_LATER){
                if (VERBOSE) Log.w(TAG, "No output buffer available. retry");
                continue;
            }
            else if (outputStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
                if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
            } else if (outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // not expected for an encoder
                MediaFormat newFormat = encoder.getOutputFormat();
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
                MediaChunks.Chunk c =
                        new MediaChunks.Chunk(true, encodedArray, info.flags, info.presentationTimeUs);
                Log.d(TAG, "PTS encoded: "+info.presentationTimeUs);

                boolean isConfigData = ((c.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
                if (mListener != null) mListener.onEncodedDataAvailable(c, isConfigData);
                //publishProgress(c);

                //Log.d(TAG, "Published chunk # "+framesCounter+" to decoder");


                if (VERBOSE) Log.d(TAG, "Encoded buffer size: "+info.size+" " +
                        "TOTAL Encoded size: "+encodedSize);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    if (VERBOSE) Log.i(TAG, "First coded packet ");
                } else {
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (VERBOSE) if (outputDone) Log.i(TAG, "Last coded packet ");
                }
                encoder.releaseOutputBuffer(outputStatus, false);
            }
        }
        encoder.stop();
        encoder.release();
        Log.i(TAG, "Encoder Released!! Closing.");
    }


    private static long computePresentationTime(long frameIndex) {
        return 1024L * frameIndex * 1000000 / SAMPLE_RATE ;
    }
}

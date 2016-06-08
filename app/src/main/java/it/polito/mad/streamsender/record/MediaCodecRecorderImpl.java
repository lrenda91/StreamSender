package it.polito.mad.streamsender.record;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;

import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.MediaCodecEncoderThread;
import it.polito.mad.streamsender.encoding.EncodingListener;
import it.polito.mad.streamsender.encoding.Params;
import it.polito.mad.streamsender.encoding.VideoChunks;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class MediaCodecRecorderImpl extends AbsCamcorder implements Camera1ManagerImpl.Callback {

    private static final String TAG = "MediaCodecEncoder";

    private Camera1Manager mCameraManager;
    private MediaCodecEncoderThread mEncoderThread;

    public MediaCodecRecorderImpl(Context context){
        super(context);
        mCameraManager = new Camera1ManagerImpl(context, this);
        mEncoderThread = new MediaCodecEncoderThread(new MediaCodecEncoderThread.Listener() {

            @Override
            public void onCodecStarted(int width, int height, int bitRate, int frameRate) {
                Params params = new Params.Builder()
                        .width(width)
                        .height(height)
                        .bitRate(bitRate * 1000)
                        .frameRate(frameRate)
                        .build();
                notifyEncodingStarted(params);
            }

            @Override
            public void onCodecSpecificData(VideoChunks.Chunk chunk, int width, int height, int bitRate, int frameRate) {
                notifyConfigBytesAvailable(chunk, width, height, bitRate, frameRate);
            }

            @Override
            public void onCodecEncodedData(VideoChunks.Chunk chunk) {
                notifyEncodedChunkAvailable(chunk);
            }
        });
        //mEncoderThread.mEncodeListeners = this.mEncodeListeners;
    }

    @Override
    public void onCameraCapturedFrame(byte[] frame) {
        Size size = mCameraManager.getCurrentSize();
        int format = mCameraManager.getImageFormat();
        mEncoderThread.submitAccessUnit(Util.swapColors(frame, size.getWidth(), size.getHeight(), format));
    }

    @Override
    public void onCameraPreviewSizeChanged(int width, int height) {

    }

    @Override
    public void startRecording() {
        Size current = mCameraManager.getCurrentSize();
        mEncoderThread.startThread(current.getWidth(), current.getHeight());

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
    }

    @Override
    public void pauseRecording() {
        mCameraManager.disableFrameCapture();
        notifyEncodingPaused();
    }

    @Override
    public void stopRecording() {
        mEncoderThread.requestStop();

        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        mEncoderThread.waitForTermination();

        notifyEncodingStopped();
    }

    @Override
    public void switchToVideoQuality(Params params){
        mCurrentParams = params;
        Size size = new Size(params.width(), params.height());
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        boolean wasRecording = (mEncoderThread.isRunning());
        mEncoderThread.requestStop();

        try{
            mCameraManager.switchToSize(size);
        }
        catch(IllegalArgumentException e){
            Log.e(TAG, e.getMessage());
        }

        mCameraManager.startPreview();

        if (wasRecording){
            mEncoderThread.waitForTermination();
            startRecording();
        }
    }

}

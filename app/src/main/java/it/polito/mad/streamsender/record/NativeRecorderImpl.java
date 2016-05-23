package it.polito.mad.streamsender.record;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import it.polito.mad.streamsender.encoding.*;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class NativeRecorderImpl extends AbsCamcorder implements Camera1ManagerImpl.Callback {

    private static final String TAG = "x264EncodingThread";
    private static final boolean VERBOSE = true;

    private EncodingCallback mEncoderListener;
    private boolean mIsRecording = false;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    public NativeRecorderImpl(Context context){
        super(context);
        mCameraManager = new Camera1ManagerImpl(context, this);
    }

    @Override
    public void setEncoderListener(EncodingCallback listener) {
        mEncoderListener = listener;
    }

    @Override
    public void openCamera() {
        super.openCamera();
        Log.d(TAG, "params: "+mPresets.toString());
        startBackgroundThread();
    }

    @Override
    public void onCameraCapturedFrame(final byte[] frame) {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                byte[] res = StreamSenderJNI.nativeDoEncode(
                        mCurrentParams.width(),
                        mCurrentParams.height(),
                        frame,
                        mCurrentParams.bitrate());
                //Log.d("jni", "encoded["+res.length+"]");
                if (res == null){
                    Log.e(TAG, "NULL");
                    return;
                }
                if (mEncoderListener != null){
                    VideoChunks.Chunk chunk = new VideoChunks.Chunk(res, 0, System.currentTimeMillis());
                    mEncoderListener.onEncodedChunk(chunk);
                }
            }
        });
    }

    @Override
    public void onCameraPreviewSizeChanged(int width, int height) { }

    @Override
    public void startRecording() {
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                byte[][] headers = StreamSenderJNI.nativeGetHeaders();
                byte[] sps = headers[0], pps = headers[1];
                byte[] merged = new byte[sps.length + pps.length];
                System.arraycopy(sps, 0, merged, 0, sps.length);
                System.arraycopy(pps, 0, merged, sps.length, pps.length);

                if (mEncoderListener != null){
                    VideoChunks.Chunk chunk = new VideoChunks.Chunk(merged, 2, 0);
                    mEncoderListener.onConfigBytes(
                            chunk, mCurrentParams.width(), mCurrentParams.height(),
                            mCurrentParams.bitrate()*1000, mCurrentParams.frameRate());
                }
            }
        });

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
        mIsRecording = true;
    }

    @Override
    public void pauseRecording() {
        mCameraManager.disableFrameCapture();
        //remove all pending encoding frames callbacks -> prevent stalling on server sde!!
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mIsRecording = false;
    }

    @Override
    public void stopRecording() {
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();
        //remove all pending encoding frames callbacks -> prevent stalling on server sde!!
        mBackgroundHandler.removeCallbacksAndMessages(null);
        mIsRecording = false;
    }
/*
    @Override
    public void switchToNextVideoQuality() {
        boolean wasRecording = mIsRecording;
        if (wasRecording){
            mCameraManager.disableFrameCapture();
            mCameraManager.stopPreview();
        }
        mCameraManager.switchToMajorSize();
        mCameraManager.startPreview();

        if (wasRecording) {
            startRecording();
        }
    }
*/
    @Override
    public void switchToVideoQuality(final Params params){
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        final Size size = new Size(params.width(), params.height());
        try{
            mCameraManager.switchToSize(size);
            mCameraManager.startPreview();
        }
        catch(IllegalArgumentException e){
            Log.e("RECORDER", e.getMessage());
        }

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                mCurrentParams = params;
                StreamSenderJNI.nativeApplyParams(
                        mCurrentParams.width(),
                        mCurrentParams.height(),
                        mCurrentParams.bitrate()
                );
            }
        });

        if (mIsRecording){
            startRecording();
        }
    }

    @Override
    public void closeCamera() {
        mCameraManager.releaseCamera();
        stopBackgroundThread();
    }


    private void startBackgroundThread(){
        if (mBackgroundThread != null){
            throw new RuntimeException("HandlerThread still alive. Can't start it");
        }
        mBackgroundThread = new HandlerThread(TAG){
            @Override
            protected void onLooperPrepared() {
                mBackgroundHandler = new Handler(Looper.myLooper());
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        StreamSenderJNI.nativeInitEncoder();
                        Log.d(TAG, "nativeInit");
                    }
                });
            }
        };
        mBackgroundThread.start();
        if (VERBOSE) Log.d("REC", "HandlerThread started");
    }

    private void stopBackgroundThread(){
        if (mBackgroundThread == null){
            throw new RuntimeException("HandlerThread dead. Can't stop it");
        }
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                StreamSenderJNI.nativeReleaseEncoder();
                Log.d(TAG, "nativeRelease");
                Looper.myLooper().quit();
            }
        });
        //mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            //Log.d(TAG, "Looper: "+mBackgroundThread.getLooper());
            if (VERBOSE) Log.d("REC", "HandlerThread quit");
        }catch(InterruptedException e){
            Log.d(TAG, e.toString());
        }
        mBackgroundHandler = null;
        mBackgroundThread = null;
    }

}

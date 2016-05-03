package it.polito.mad.streamsender.record;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

import it.polito.mad.streamsender.encoding.*;
import it.polito.mad.streamsender.Util;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class NativeRecorderImpl implements Camera1Recorder,Camera1ManagerImpl.Callback {

    private static final int sRatioHeight = 4;
    private static final int sRatioWidth = 3;

    private Context mContext;
    private Camera1Manager mCameraManager;
    private EncodingCallback mEncoderListener;
    private boolean mIsRecording = false;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Params mParams;

    public NativeRecorderImpl(Context context){
        if (context == null){
            throw new IllegalArgumentException("Context mustn't be null");
        }
        mContext = context;
        mCameraManager = new Camera1ManagerImpl(context, this);
    }

    public Camera1Manager getCameraManager(){
        return mCameraManager;
    }

    @Override
    public void setEncoderListener(EncodingCallback listener) {
        mEncoderListener = listener;
    }

    @Override
    public void acquireCamera() {
        mCameraManager.acquireCamera();
        startBackgroundThread();
    }

    @Override
    public void setSurfaceView(final SurfaceView surfaceView) {
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int measuredHeight = surfaceView.getMeasuredHeight();
                ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
                lp.width = measuredHeight * sRatioWidth / sRatioHeight;
                surfaceView.setLayoutParams(lp);
                try {
                    mCameraManager.setPreviewSurface(surfaceView.getHolder());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onCameraCapturedFrame(final byte[] frame) {
        final Camera.Size size = mCameraManager.getCurrentSize();
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                int format = mCameraManager.getImageFormat();
                //Util.swapColors(frame, mParams.width(), mParams.height(), format);
                byte[] res = StreamSenderJNI.nativeDoEncode(
                        mParams.width(),
                        mParams.height(),
                        frame,
                        mParams.bitrate());
                //Log.d("jni", "encoded["+res.length+"]");
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
                    mEncoderListener.onConfigBytes(chunk, mParams.width(),
                            mParams.height(), mParams.bitrate()*1000, mParams.frameRate());
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
        mIsRecording = false;
    }

    @Override
    public void stopRecording() {
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();
        mIsRecording = false;
    }

    @Override
    public void switchToNextVideoQuality() {
        /*boolean wasRecording = mIsRecording;
        if (wasRecording){
            mCameraManager.disableFrameCapture();
            mCameraManager.stopPreview();
        }


        mCameraManager.switchToMajorSize();
        mCameraManager.startPreview();

        if (wasRecording) {
            startRecording();
        }*/

    }

    @Override
    public void switchToVideoQuality(final Params params){
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        final Camera.Size size = mCameraManager.getCameraInstance().new Size(params.width(), params.height());
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
                mParams = params;
                StreamSenderJNI.nativeApplyParams(
                        mParams.width(),
                        mParams.height(),
                        mParams.bitrate()
                );
            }
        });

        if (mIsRecording){
            startRecording();
        }
    }

    @Override
    public void releaseCamera() {
        mCameraManager.releaseCamera();
        stopBackgroundThread();
    }


    private void startBackgroundThread(){
        if (mBackgroundThread == null){
            mBackgroundThread = new HandlerThread("x264EncodingThread");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                StreamSenderJNI.nativeInitEncoder();
                Log.d("ht", "nativeInit");
            }
        });
    }

    @TargetApi(21)
    private void stopBackgroundThread(){
        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                StreamSenderJNI.nativeReleaseEncoder();
                Log.d("ht", "nativeRelease");
                //Looper.myLooper().quit();
            }
        });
        mBackgroundThread.quitSafely();
        //mBackgroundHandler = null;
        //mBackgroundThread = null;
    }

}

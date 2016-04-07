package it.polito.mad.streamsender.record;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

import it.polito.mad.streamsender.Util;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class Camera1RecorderImpl implements Camera1Recorder,Camera1ManagerImpl.Callback {

    private static final int sRatioWidth = 4;
    private static final int sRatioHeight = 3;

    private Context mContext;
    private Camera1Manager mCameraManager;
    private EncoderThread mEncoderThread;

    public Camera1RecorderImpl(Context context){
        if (context == null){
            throw new IllegalArgumentException("Context mustn't be null");
        }
        mContext = context;
        mCameraManager = new Camera1ManagerImpl(context, this);
        mEncoderThread = new EncoderThread(null);
    }

    public void registerEncoderListener(EncoderThread.Listener listener){
        mEncoderThread.setListener(listener);
    }

    public Camera1Manager getCameraManager(){
        return mCameraManager;
    }

    public void unregisterEncoderListener(){
        mEncoderThread.setListener(null);
    }

    @Override
    public void acquireCamera() {
        mCameraManager.acquireCamera();
    }

    @Override
    public void setSurfaceView(final SurfaceView surfaceView) {
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                //int w = surfaceView.getMeasuredWidth();
                int measuredHeight = surfaceView.getMeasuredHeight();
                ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
                lp.width = measuredHeight * 3 / 4;
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
    public void onCameraCapturedFrame(byte[] frame) {
        Camera.Size size = mCameraManager.getCurrentSize();
        int format = mCameraManager.getImageFormat();
        mEncoderThread.submitAccessUnit(Util.swapColors(frame, size.width, size.height, format));
    }

    @Override
    public void onCameraPreviewSizeChanged(int width, int height) {

    }

    @Override
    public void startRecording() {
        Camera.Size current = mCameraManager.getCurrentSize();
        mEncoderThread.startThread(current.width, current.height);

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
    }

    @Override
    public void pauseRecording() {
        mCameraManager.disableFrameCapture();
    }

    @Override
    public void stopRecording() {
        mEncoderThread.requestStop();

        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        mEncoderThread.waitForTermination();
    }

    @Override
    public void switchToNextVideoQuality() {
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        boolean wasRecording = (mEncoderThread.isRunning());
        mEncoderThread.requestStop();

        mCameraManager.switchToMajorSize();

        mEncoderThread.waitForTermination();
        //mEncoderThread.drain();

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();

        if (wasRecording){
            startRecording();
        }
    }

    @Override
    public void switchToVideoQuality(int width, int height){
        Camera.Size size = mCameraManager.getCameraInstance().new Size(width, height);
        mCameraManager.disableFrameCapture();

        boolean wasRecording = (mEncoderThread.isRunning());
        mEncoderThread.requestStop();

        try{
            mCameraManager.switchToSize(size);
        }
        catch(IllegalArgumentException e){
            Log.d("RECORDER", e.getMessage());
        }

        mEncoderThread.waitForTermination();
        //mEncoderThread.drain();

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();

        if (wasRecording){
            startRecording();
        }
    }

    @Override
    public void releaseCamera() {
        mCameraManager.releaseCamera();
    }
}

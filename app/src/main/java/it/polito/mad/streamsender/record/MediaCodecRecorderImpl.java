package it.polito.mad.streamsender.record;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.EncoderThread;
import it.polito.mad.streamsender.encoding.EncodingCallback;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class MediaCodecRecorderImpl implements Camera1Recorder,Camera1ManagerImpl.Callback {

    private static final int sRatioHeight = 4;
    private static final int sRatioWidth = 3;

    private Context mContext;
    private Camera1Manager mCameraManager;
    private EncoderThread mEncoderThread;

    public MediaCodecRecorderImpl(Context context){
        if (context == null){
            throw new IllegalArgumentException("Context mustn't be null");
        }
        mContext = context;
        mCameraManager = new Camera1ManagerImpl(context, this);
        mEncoderThread = new EncoderThread(null);
    }

    @Override
    public void setEncoderListener(EncodingCallback listener){
        mEncoderThread.setListener(listener);
    }

    public Camera1Manager getCameraManager(){
        return mCameraManager;
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
        mCameraManager.startPreview();

        if (wasRecording){
            mEncoderThread.waitForTermination();
            startRecording();
            mCameraManager.enableFrameCapture();
        }
    }

    @Override
    public void switchToVideoQuality(int width, int height){
        Camera.Size size = mCameraManager.getCameraInstance().new Size(width, height);
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        boolean wasRecording = (mEncoderThread.isRunning());
        mEncoderThread.requestStop();

        try{
            mCameraManager.switchToSize(size);
        }
        catch(IllegalArgumentException e){
            Log.e("RECORDER", e.getMessage());
        }

        mCameraManager.startPreview();

        if (wasRecording){
            mEncoderThread.waitForTermination();
            startRecording();
            mCameraManager.enableFrameCapture();
        }
    }

    @Override
    public void releaseCamera() {
        mCameraManager.releaseCamera();
    }

}

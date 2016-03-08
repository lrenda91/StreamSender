package it.polito.mad.record;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;

import java.io.IOException;

import it.polito.mad.Util;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class Camera1RecorderImpl implements Camera1Recorder,Camera1ManagerImpl.Callback {

    private Context mContext;
    private Camera1Manager mCameraManager;
    private EncoderThread mEncoderThread;

    //private SurfaceView mCameraView;

    //private Params mRecorderParams = new Params();

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
        Log.d("recorder", "acquire camera");
        mCameraManager.acquireCamera();
    }

    @Override
    public void setSurfaceView(SurfaceView surfaceView) {
        try {
            mCameraManager.setPreviewSurface(surfaceView.getHolder());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCameraCapturedFrame(byte[] frame) {
        Camera.Size size = mCameraManager.getCurrentSize();
        int format = mCameraManager.getImageFormat();
        mEncoderThread.submitAccessUnit(Util.swapColors(frame, size.width, size.height, format));
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
        mEncoderThread.waitForTermination();

        mCameraManager.stopPreview();
    }

    @Override
    public void switchToNextVideoQuality() {
        mCameraManager.disableFrameCapture();
        mEncoderThread.requestStop();

        //Camera camera = mCameraManager.getCameraInstance();
        //Camera.Size newSize = camera.new Size(params.width, params.height);
        //mCameraManager.switchToSize(newSize);
        mCameraManager.switchToMajorSize();
        /*try {
            mCameraManager.setPreviewSurface(mCameraView.getHolder());
        }catch(IOException e){
            throw new RuntimeException(e);
        }*/

        mEncoderThread.waitForTermination();
        mEncoderThread.drain();

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
    }

    @Override
    public void switchToVideoQuality(int width, int height){
        Camera.Size size = mCameraManager.getCameraInstance().new Size(width, height);
        mCameraManager.disableFrameCapture();
        mEncoderThread.requestStop();

        try{
            mCameraManager.switchToSize(size);
        }
        catch(IllegalArgumentException e){
            Log.d("RECORDER", e.getMessage());
        }

        mEncoderThread.waitForTermination();
        mEncoderThread.drain();

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();
    }

    @Override
    public void releaseCamera() {
        mCameraManager.releaseCamera();
    }
}

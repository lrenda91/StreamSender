package it.polito.mad.record;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import it.polito.mad.Util;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class Camera1ManagerImpl implements Camera1Manager {

    public interface Callback {
        void onCameraCapturedFrame(byte[] frame);
    }

    private static final String TAG = "Camera";

    private static final int sRatioWidth = 4;
    private static final int sRatioHeight = 3;

    //by default, but it could be YV12
    public int mImageFormat = ImageFormat.NV21;

    private static final int mNumOfBuffers = 3;
    private byte[][] mBuffers;
    private int mCurrentBufferIdx = 0;
    private int mCurrentBuffersSize;
    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data.length != mCurrentBuffersSize){
                return;
            }
            useNextCallbackBuffer();

            if (mCallback != null){
                mCallback.onCameraCapturedFrame(data);
            }
        }
    };

    private Context mContext;

    private Camera mCamera;
    private Callback mCallback;

    private List<Camera.Size> mSuitableSizes;
    private int mSuitableSizesIndex = 0;

    public Camera1ManagerImpl(Context context, Callback callback){
        mContext = context;
        mCallback = callback;
    }

    @Override
    public void acquireCamera() {
        checkForCameraAcquisition(false);

        mCamera = Camera.open(0);
        Util.getAvailableProfiles(0);
        mSuitableSizes = new LinkedList<>();
        for (Camera.Size s : mCamera.getParameters().getSupportedPreviewSizes()){
            if ((s.width * sRatioHeight / sRatioWidth) == s.height){
                mSuitableSizes.add(s);
            }
        }
        Util.logCameraSizes(TAG, mSuitableSizes);
        mImageFormat = getColorFormat(mCamera.getParameters());
        Util.logCameraPictureFormat("ImageFormat", mImageFormat);
        Camera.Size currentSize = getCurrentSize();
        switchToSize(currentSize);
    }

    @Override
    public Camera getCameraInstance() {
        return mCamera;
    }

    @Override
    public List<Camera.Size> getSuitableSizes() {
        return mSuitableSizes;
    }

    @Override
    public Camera.Size getCurrentSize() {
        return mSuitableSizes.get(mSuitableSizesIndex);
    }

    @Override
    public void switchToMinorSize() {
        if (mSuitableSizesIndex == 0){ mSuitableSizesIndex = mSuitableSizes.size() - 1; }
        else{ mSuitableSizesIndex--; }
        switchToSize(getCurrentSize());
    }

    @Override
    public void switchToMajorSize() {
        mSuitableSizesIndex = (mSuitableSizesIndex + 1) % mSuitableSizes.size();
        switchToSize(getCurrentSize());
    }

    @Override
    public void switchToSize(Camera.Size newSize) throws IllegalArgumentException{
        int idx = mSuitableSizes.indexOf(newSize);
        if (idx < 0){
            throw new IllegalArgumentException("Illegal size: "+newSize.width+"x"+newSize.height);
        }
        mSuitableSizesIndex = idx;
        Log.d(TAG, "idx="+mSuitableSizesIndex);
        int width = newSize.width;
        int height = newSize.height;
        resizeBuffers(width, height);
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewFormat(mImageFormat);
        parameters.setPreviewSize(width, height);
        mCamera.setParameters(parameters);
    }

    @Override
    public void setPreviewSurface(SurfaceHolder surfaceHolder) throws IOException {
        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final int rotation = wm.getDefaultDisplay().getRotation();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {}
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try {
                    checkForCameraAcquisition(true);
                    mCamera.stopPreview();
                    switch (rotation) {
                        case Surface.ROTATION_0:
                            mCamera.setDisplayOrientation(90);
                            break;
                        case Surface.ROTATION_270:
                            mCamera.setDisplayOrientation(180);
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_180:
                            break;
                    }
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();

                } catch (IOException e) {

                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
    }

    @Override
    public void startPreview() {
        checkForCameraAcquisition(true);
        mCamera.startPreview();
    }

    @Override
    public void enableFrameCapture() {
        checkForCameraAcquisition(true);
        useNextCallbackBuffer();
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
    }

    @Override
    public void disableFrameCapture() {
        checkForCameraAcquisition(true);
        mCamera.setPreviewCallbackWithBuffer(null);
    }

    @Override
    public void stopPreview() {
        checkForCameraAcquisition(true);
        disableFrameCapture();
        mCamera.stopPreview();
    }

    @Override
    public void releaseCamera() {
        if (mCamera == null){
            return;
        }
        mCamera.release();
        mCamera = null;
    }

    @Override
    public int getImageFormat() {
        return mImageFormat;
    }

    private void resizeBuffers(int width, int height){
        float bitsPerPixel = (float) ImageFormat.getBitsPerPixel(mImageFormat);
        float bytesPerPixel = bitsPerPixel / 8F;
        float bufferSize = width * height * bytesPerPixel;
        if (bufferSize != ((int) bufferSize)){
            //if (mWidth * mHeight *bytesPerPixel) is not integer, let's round it!!
            bufferSize++;
            Log.w(TAG, "Not integer size: " + bytesPerPixel);
        }
        mCurrentBuffersSize = (int) bufferSize;
        mBuffers = new byte[mNumOfBuffers][(int)bufferSize];

        Log.d(TAG, "new preview size="+width+"x"+height+" ; Buffers size= "+ mBuffers[0].length);
    }


    private int getColorFormat(Camera.Parameters params){
        int res = -1;
        for (int format : params.getSupportedPreviewFormats()) {
            switch (format) {
                case ImageFormat.NV21:
                case ImageFormat.YV12:
                    res = format;
                    break;
            }
        }
        if (res < 0) res = ImageFormat.NV21;
        Util.logCameraPictureFormat(TAG, res);
        return res;
    }

    private void useNextCallbackBuffer(){
        mCamera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);
        mCurrentBufferIdx = (mCurrentBufferIdx +1) % mNumOfBuffers;
    }

    private void checkForCameraAcquisition(boolean cameraRequired){
        boolean cameraAcquiredNow = (mCamera != null);
        //if ((cameraRequired && mCamera == null) || (!cameraRequired && ))
        if (cameraAcquiredNow ^ cameraRequired){
            throw new IllegalStateException("State");
        }
    }

}

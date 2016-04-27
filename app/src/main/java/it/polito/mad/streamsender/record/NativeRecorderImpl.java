package it.polito.mad.streamsender.record;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

import it.polito.mad.streamsender.encoding.StreamSenderJNI;
import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.VideoChunks;

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
        StreamSenderJNI.nativeInitEncoder();
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
        Util.swapColors(frame, size.width, size.height, format);
        try {
            byte[] res = StreamSenderJNI.nativeDoEncode(size.width, size.height, frame, 1);
            if (mEncoderListener != null){
                VideoChunks.Chunk chunk = new VideoChunks.Chunk(res, 0, System.currentTimeMillis());
                mEncoderListener.onEncodedChunk(chunk);
            }
        }catch(Throwable t){
            Log.e("native", t.getMessage());
        }

    }

    @Override
    public void onCameraPreviewSizeChanged(int width, int height) {

    }

    @Override
    public void startRecording() {
        Camera.Size current = mCameraManager.getCurrentSize();
        StreamSenderJNI.nativeApplyParams(current.width, current.height, 500);

        mCameraManager.enableFrameCapture();
        mCameraManager.startPreview();

        /**
         * prova

        if (mEncoderThread.getListener() != null){
            byte[] data = { 0, 0, 0, 1, 103, 66, (byte)192, 13, (byte)218, 5, 7, (byte)232, 64,
                    0, 0, 3, 0, 64, 0, 0, 12, (byte)163, (byte)197, 10, (byte)168,
                    0, 0, 0, 1, 104, (byte)206, 60, (byte)128};
        }*/
        if (mEncoderListener != null){
            byte[][] headers = StreamSenderJNI.nativeGetHeaders();
            byte[] sps = headers[0], pps = headers[1];
            byte[] data2 = new byte[sps.length + pps.length];
            System.arraycopy(sps, 0, data2, 0, sps.length);
            System.arraycopy(pps, 0, data2, sps.length, pps.length);
            VideoChunks.Chunk chunk = new VideoChunks.Chunk(data2, 2, 0);
            mEncoderListener.onConfigBytes(chunk, current.width, current.height, 500000, 20);
        }
    }

    @Override
    public void pauseRecording() {
        mCameraManager.disableFrameCapture();
    }

    @Override
    public void stopRecording() {
        //mEncoderThread.requestStop();

        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        //mEncoderThread.waitForTermination();
    }

    @Override
    public void switchToNextVideoQuality() {
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        //boolean wasRecording = (mEncoderThread.isRunning());
        //mEncoderThread.requestStop();

        mCameraManager.switchToMajorSize();
        mCameraManager.startPreview();

        //if (wasRecording){
        //  mEncoderThread.waitForTermination();
            startRecording();
            mCameraManager.enableFrameCapture();
        //}
    }

    @Override
    public void switchToVideoQuality(int width, int height){
        Camera.Size size = mCameraManager.getCameraInstance().new Size(width, height);
        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        //boolean wasRecording = (mEncoderThread.isRunning());
        //mEncoderThread.requestStop();

        try{
            mCameraManager.switchToSize(size);
        }
        catch(IllegalArgumentException e){
            Log.e("RECORDER", e.getMessage());
        }

        mCameraManager.startPreview();

        //if (wasRecording){
          //  mEncoderThread.waitForTermination();
            startRecording();
            mCameraManager.enableFrameCapture();
        //}
    }

    @Override
    public void releaseCamera() {
        mCameraManager.releaseCamera();
        StreamSenderJNI.nativeReleaseEncoder();
    }
}

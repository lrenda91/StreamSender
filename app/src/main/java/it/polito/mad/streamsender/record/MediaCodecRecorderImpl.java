package it.polito.mad.streamsender.record;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;

import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.MediaCodecEncoderThread;
import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.Params;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public class MediaCodecRecorderImpl extends AbsCamcorder implements Camera1ManagerImpl.Callback {

    private Camera1Manager mCameraManager;
    private MediaCodecEncoderThread mEncoderThread;

    public MediaCodecRecorderImpl(Context context){
        super(context);
        mCameraManager = new Camera1ManagerImpl(context, this);
        mEncoderThread = new MediaCodecEncoderThread(null);
    }

    @Override
    public void setEncoderListener(EncodingCallback listener){
        mEncoderThread.setListener(listener);
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
    }

    @Override
    public void stopRecording() {
        mEncoderThread.requestStop();

        mCameraManager.disableFrameCapture();
        mCameraManager.stopPreview();

        mEncoderThread.waitForTermination();
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
            Log.e("RECORDER", e.getMessage());
        }

        mCameraManager.startPreview();

        if (wasRecording){
            mEncoderThread.waitForTermination();
            startRecording();
        }
    }

}

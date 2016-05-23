package it.polito.mad.streamsender.record;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.Params;

/**
 * Created by luigi on 20/05/16.
 */
public abstract class AbsCamcorder implements Camcorder {

    protected static final Size sRatio = new Size(3,4);

    protected Context mContext;
    protected Camera1Manager mCameraManager;
    protected final List<Params> mPresets = new LinkedList<>();
    protected Params mCurrentParams;

    public abstract void setEncoderListener(EncodingCallback callback);

    public AbsCamcorder(Context context){
        if (context == null){
            throw new IllegalArgumentException("Context mustn't be null");
        }
        mContext = context;
    }

    @Override
    public Params getCurrentParams() {
        return mCurrentParams;
    }

    public final List<Params> getPresets(){
        return mPresets;
    }

    public boolean switchToHigherQuality() throws IllegalStateException {
        int idx = mPresets.indexOf(getCurrentParams());
        if (idx < 0) {
            throw new IllegalStateException("Can't find " + getCurrentParams() + " among presets");
        }
        int nextIdx = idx + 1;
        if (nextIdx >= mPresets.size()){
            return false;
        }
        Params next = mPresets.get(nextIdx);
        switchToVideoQuality(next);
        return true;
    }

    public boolean switchToLowerQuality() throws IllegalStateException {
        int idx = mPresets.indexOf(getCurrentParams());
        if (idx < 0) {
            throw new IllegalStateException("Can't find " + getCurrentParams() + " among presets");
        }
        int previousIdx = idx - 1;
        if (previousIdx < 0){
            return false;
        }
        Params prev = mPresets.get(previousIdx);
        switchToVideoQuality(prev);
        return true;
    }

    @Override
    public Camera1Manager getCameraManager() {
        return mCameraManager;
    }

    @Override
    public void openCamera() {
        mCameraManager.acquireCamera();
        //now, we have available sizes -> keep suitable presets
        List<Size> sizes = mCameraManager.getSuitableSizes();
        for (Params params : Params.PRESETS){
            if (sizes.contains(new Size(params.width(), params.height()))){
                mPresets.add(params);
            }
        }
    }

    @Override
    public void closeCamera() {
        mCameraManager.releaseCamera();
    }

    @Override
    public void setSurfaceView(final SurfaceView surfaceView) {
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                int measuredHeight = surfaceView.getMeasuredHeight();
                ViewGroup.LayoutParams lp = surfaceView.getLayoutParams();
                lp.width = measuredHeight * sRatio.getWidth() / sRatio.getHeight();
                surfaceView.setLayoutParams(lp);
                try {
                    mCameraManager.setPreviewSurface(surfaceView.getHolder());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

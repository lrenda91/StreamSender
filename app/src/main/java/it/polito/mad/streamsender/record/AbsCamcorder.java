package it.polito.mad.streamsender.record;

import android.content.Context;
import android.os.Handler;
import android.support.v4.util.Pair;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import it.polito.mad.streamsender.encoding.EncodingListener;
import it.polito.mad.streamsender.encoding.Params;
import it.polito.mad.streamsender.encoding.VideoChunks;

/**
 * Created by luigi on 20/05/16.
 */
public abstract class AbsCamcorder implements Camcorder {

    protected static final Size sRatio = new Size(3,4);

    protected Context mContext;
    protected Camera1Manager mCameraManager;
    protected final List<Params> mPresets = new LinkedList<>();
    protected Params mCurrentParams;
    protected final Set<Pair<Handler,EncodingListener>> mEncodeListeners = new HashSet<>();

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

    public boolean switchToHigherQuality() throws Exception {
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

    public boolean switchToLowerQuality() throws Exception {
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
        /*for (Params params : Params.PRESETS){
            if (sizes.contains(new Size(params.width(), params.height()))){
                mPresets.add(params);
            }
        }*/
        for (Size s : sizes){
            for (Params bestPreset : Params.getNearestPresets(s)){
                mPresets.add(new Params.Builder()
                        .width(s.getWidth())
                        .height(s.getHeight())
                        .bitRate(bestPreset.bitrate())
                        .frameRate(bestPreset.frameRate()).build());
            }
        }
        mCurrentParams = mPresets.get(0);
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

    public boolean registerEncodingListener(EncodingListener listener, Handler handler){
        if (listener == null){
            return false;
        }
        return mEncodeListeners.add(new Pair<>(handler,listener));
    }

    public void clearEncodingListeners(){
        mEncodeListeners.clear();
    }

    protected void notifyEncodingStarted(final Params params){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingStarted(params);
                    }
                });
            } else {
                listener.second.onEncodingStarted(params);
            }
        }
    }

    protected void notifyConfigBytesAvailable(final VideoChunks.Chunk chunk,
                                              final int width,
                                              final int height,
                                              final int encodeBps,
                                              final int frameRate){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onConfigBytes(chunk, width, height, encodeBps, frameRate);
                    }
                });
            } else {
                listener.second.onConfigBytes(chunk, width, height, encodeBps, frameRate);
            }
        }
    }

    protected void notifyEncodedChunkAvailable(final VideoChunks.Chunk chunk){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodedChunk(chunk);
                    }
                });
            } else {
                listener.second.onEncodedChunk(chunk);
            }
        }
    }

    protected void notifyEncodingPaused(){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingPaused();
                    }
                });
            } else {
                listener.second.onEncodingPaused();
            }
        }
    }

    protected void notifyEncodingStopped(){
        for (final Pair<Handler,EncodingListener> listener : mEncodeListeners){
            if (listener.first != null) {
                listener.first.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.second.onEncodingStopped();
                    }
                });
            } else {
                listener.second.onEncodingStopped();
            }
        }
    }
}

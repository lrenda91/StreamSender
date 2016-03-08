package it.polito.mad.record;

import android.view.SurfaceView;

import it.polito.mad.streamsender.Params;

/**
 * Created by luigi on 24/02/16.
 */
public interface Camera1Recorder {

    void acquireCamera();

    void setSurfaceView(SurfaceView surfaceView);

    void startRecording();

    void pauseRecording();

    void stopRecording();

    void switchToNextVideoQuality();

    void switchToVideoQuality(int width, int height);

    void releaseCamera();

}

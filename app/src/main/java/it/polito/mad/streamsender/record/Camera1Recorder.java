package it.polito.mad.streamsender.record;

import android.view.SurfaceView;

import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.Params;

/**
 * Created by luigi on 24/02/16.
 */
public interface Camera1Recorder {

    void setEncoderListener(EncodingCallback listener);

    void acquireCamera();

    void setSurfaceView(SurfaceView surfaceView);

    void startRecording();

    void pauseRecording();

    void stopRecording();

    void switchToNextVideoQuality();

    void switchToVideoQuality(Params params);

    void releaseCamera();

    Camera1Manager getCameraManager();

}

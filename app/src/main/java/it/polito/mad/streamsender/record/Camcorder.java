package it.polito.mad.streamsender.record;

import android.util.Size;
import android.view.SurfaceView;

import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.Params;

/**
 * Created by luigi on 24/02/16.
 */
public interface Camcorder {

    void openCamera();

    void setSurfaceView(SurfaceView surfaceView);

    void startRecording();

    void pauseRecording();

    void stopRecording();

    void switchToVideoQuality(Params params);

    void closeCamera();

    Camera1Manager getCameraManager();

    Params getCurrentParams();

}

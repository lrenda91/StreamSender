package it.polito.mad.record;

import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.List;

/**
 * Created by luigi on 24/02/16.
 */
@SuppressWarnings("deprecation")
public interface Camera1Manager {

    void acquireCamera();

    Camera getCameraInstance();

    List<Camera.Size> getSuitableSizes();

    int getImageFormat();

    Camera.Size getCurrentSize();

    void switchToMinorSize();

    void switchToMajorSize();

    void switchToSize(Camera.Size newSize) throws IllegalArgumentException;

    void setPreviewSurface(SurfaceHolder surfaceHolder) throws IOException;

    void startPreview();

    void enableFrameCapture();

    void disableFrameCapture();

    void stopPreview();

    void releaseCamera();

}

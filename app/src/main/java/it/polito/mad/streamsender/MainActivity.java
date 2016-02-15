package it.polito.mad.streamsender;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import it.polito.mad.websocket.WSClient;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity
        implements VideoEncoderThread.Listener {

    private static final String TAG = "PREVIEW";

    private static final int sRatioWidth = 4;
    private static final int sRatioHeight = 3;
    public static int sImageFormat ;

    private Button rec, pause, stop, connect, switchQuality;
    private EditText mIP, mPort;
    //private FrameLayout mContainer;
    private SurfaceView mCameraView;
    private int mRotation;


    private int mWidth;
    private int mHeight;

    private static final int mNumOfBuffers = 3;
    private byte[][] mBuffers;
    private int mCurrentBufferIdx = 0;
    private int mCurrentBuffersSize;

    private Camera mCamera;
    private List<Camera.Size> mSuitableSizes;
    private int mSuitableSizesIndex = 0;

    //private EncoderTask mEncoderTask;
    private VideoEncoderThread mEncoderThread;

    private AudioRecProva mAudioProva;

    private WSClient mClient = new WSClient(new WSClient.Listener() {
        @Override
        public void onConnectionEstablished() {
            Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_LONG).show();
            rec.setEnabled(true);
        }

        @Override
        public void onServerUnreachable(Exception e) {
            Toast.makeText(MainActivity.this, "Can't connect to server: "
                    + e.getClass().getSimpleName()+": "+e.getMessage(), Toast.LENGTH_LONG).show();

        }
    });

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (data.length != mCurrentBuffersSize){
                return;
            }
            Log.d(TAG, "previewCbk");
            mEncoderThread.submitAccessUnit(Util.swapColors(data, mWidth, mHeight, sImageFormat));
            camera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);
            mCurrentBufferIdx = (mCurrentBufferIdx +1) % mNumOfBuffers;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mEncoderThread = new VideoEncoderThread(this);

        rec = (Button) findViewById(R.id.button_rec);
        pause = (Button) findViewById(R.id.button_pause);
        stop = (Button) findViewById(R.id.button_stop);
        connect = (Button) findViewById(R.id.button_connect);
        switchQuality = (Button) findViewById(R.id.button_quality_switch);
        mIP = (EditText) findViewById(R.id.ip);
        mPort = (EditText) findViewById(R.id.port);

        mCameraView = new SurfaceView(this);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mRotation = wm.getDefaultDisplay().getRotation();

        //mCameraView.getHolder().setSizeFromLayout();
        mCameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (holder.getSurface() == null || mCamera == null) {
                    return;
                }
                try {
                    mCamera.stopPreview();
                    switch (mRotation) {
                        case Surface.ROTATION_0:
                            mCamera.setDisplayOrientation(90);
                            break;
                        case Surface.ROTATION_90:
                            break;
                        case Surface.ROTATION_180:
                            break;
                        case Surface.ROTATION_270:
                            mCamera.setDisplayOrientation(180);
                            break;
                    }
                    //mCamera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);

                    Camera.Parameters parameters = mCamera.getParameters();

                    parameters.setPreviewFormat(sImageFormat);
                    parameters.setPreviewSize(mWidth, mHeight);
                    mCamera.setParameters(parameters);
                    mCamera.setPreviewDisplay(holder);
                    mCamera.startPreview();

                } catch (IOException e) {

                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });

        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //initializeEncoderThread();//.execute();
                //mEncoderThread.start();
                mEncoderThread.startThread(mWidth, mHeight);
                mAudioProva.startThread();

                mCamera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);
                mCurrentBufferIdx = (mCurrentBufferIdx +1) % mNumOfBuffers;
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                mCamera.startPreview();
            }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mEncoderThread.isRunning()){
                    mEncoderThread.requestStop();
                    mEncoderThread.waitForTermination();
                }
                mAudioProva.stop();

                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.stopPreview();
            }
        });
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = mIP.getText().toString();
                int port = Integer.parseInt(mPort.getText().toString());
                mClient.connect(ip, port, 2000);
            }
        });
        switchQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextQuality();
                mEncoderThread.startThread(mWidth, mHeight);
                Log.d(TAG, "RESTARTED");
            }
        });

        FrameLayout mContainer = (FrameLayout) findViewById(R.id.preview);
        mContainer.addView(mCameraView);

        mAudioProva = new AudioRecProva(new AudioEncoderThread(new AudioEncoderThread.Listener() {
            @Override
            public void onEncodedDataAvailable(MediaChunks.Chunk chunk, boolean configBytes) {
                if (mClient.isOpen()) {
                    if (configBytes) {
                        mClient.sendConfigBytes(true, chunk.data);
                    } else {
                        mClient.sendStreamBytes(chunk);
                    }
                }
            }
        }));

    }

    @Override
    public void onEncodedDataAvailable(MediaChunks.Chunk chunk, boolean configBytes) {
        if (mClient.isOpen()) {
            if (configBytes) {
                mClient.sendConfigBytes(false, chunk.data);
            } else {
                mClient.sendStreamBytes(chunk);
            }
        }
    }

    public void acquireCamera() {
        try {
            mCamera = Camera.open(0);
            mSuitableSizes = new LinkedList<>();
            for (Camera.Size s : mCamera.getParameters().getSupportedPreviewSizes()){
                if ((s.width * sRatioHeight / sRatioWidth) == s.height){
                    //if (s.width < 700)
                    mSuitableSizes.add(s);
                }
            }
            sImageFormat = getImageFormat(mCamera.getParameters());
            //nextQuality();


            Camera.Size current = mSuitableSizes.get(mSuitableSizesIndex);
            switchQuality.setText(current.width+"x"+current.height);
            mWidth = current.width;
            mHeight = current.height;

            float bitsPerPixel = (float) ImageFormat.getBitsPerPixel(sImageFormat);
            float bytesPerPixel = bitsPerPixel / 8F;
            float bufferSize = mWidth * mHeight * bytesPerPixel;
            if (bufferSize != ((int) bufferSize)){
                //for instance if (mWidth * mHeight *bytesPerPixel) is equals to 76800.5
                //lets consider it 76801.5 so when it's casted it becomes equals to 76801!!
                bufferSize++;
                Log.e(TAG, "Not integer size: "+bytesPerPixel);
            }
            mCurrentBuffersSize = (int) bufferSize;

            mBuffers = new byte[mNumOfBuffers][(int)bufferSize];
            Log.d(TAG, "new preview size="+current.width+"x"+current.height+" ; new mBuffers size= "+ mBuffers[0].length);

            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewFormat(sImageFormat);
            parameters.setPreviewSize(mWidth, mHeight);
            mCamera.setParameters(parameters);

            try {
                mCamera.setPreviewDisplay(mCameraView.getHolder());
            }
            catch(IOException e){
                Toast.makeText(this, "Next quality: "+e.getMessage(), Toast.LENGTH_LONG).show();
            }

            mSuitableSizesIndex = (mSuitableSizesIndex + 1) % mSuitableSizes.size();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        acquireCamera();
    }

    @Override
    public void onPause() {
        if (mEncoderThread.isRunning()){
            mEncoderThread.requestStop();
        }
        if (mClient.isOpen()){
            mClient.closeConnection();
        }
        //mEncoderThread.interrupt();
        releaseCamera();
        super.onPause();
    }

    private int getImageFormat(Camera.Parameters params){
        int res = -1;
        for (int format : params.getSupportedPreviewFormats()) {
            Util.logCameraPictureFormat("Loop supported", format);
            switch (format) {
                case ImageFormat.NV21:
                case ImageFormat.YV12:
                    return format;
            }
        }
        if (res < 0) res = ImageFormat.NV21;
        Util.logCameraPictureFormat(TAG, res);
        return res;
    }

    private void nextQuality(){
        //pause, so no other chunks will be added, then request encoder closure
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.stopPreview();
        mEncoderThread.requestStop();
        //mEncoderThread.interrupt();

        Camera.Size current = mSuitableSizes.get(mSuitableSizesIndex);
        switchQuality.setText(current.width+"x"+current.height);
        mWidth = current.width;
        mHeight = current.height;

        float bitsPerPixel = (float) ImageFormat.getBitsPerPixel(sImageFormat);
        float bytesPerPixel = bitsPerPixel / 8F;
        float bufferSize = mWidth * mHeight * bytesPerPixel;
        if (bufferSize != ((int) bufferSize)){
            //for instance if (mWidth * mHeight *bytesPerPixel) is equals to 76800.5
            //lets consider it 76801.5 so when it's casted it becomes equals to 76801!!
            bufferSize++;
            Log.e(TAG, "Not integer size: "+bytesPerPixel);
        }
        mCurrentBuffersSize = (int) bufferSize;

        mBuffers = new byte[mNumOfBuffers][(int)bufferSize];
        Log.d(TAG, "new preview size="+current.width+"x"+current.height+" ; new mBuffers size= "+ mBuffers[0].length);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewFormat(sImageFormat);
        parameters.setPreviewSize(mWidth, mHeight);
        mCamera.setParameters(parameters);

        try {
            mCamera.setPreviewDisplay(mCameraView.getHolder());
        }
        catch(IOException e){
            Toast.makeText(this, "Next quality: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }

        mSuitableSizesIndex = (mSuitableSizesIndex + 1) % mSuitableSizes.size();

        mEncoderThread.waitForTermination();
        Log.d(TAG, "Encoder thread finished");

        //now, encoder thread has finished.
        //------------- BUT ------------
        //CHUNKS LIST MAY STILL CONTAIN SOME CHUNKS WITH 'OLD' SIZE
        //WE MUST EMPTY IT
        mEncoderThread.drain();

        mCamera.addCallbackBuffer(mBuffers[mCurrentBufferIdx]);
        mCurrentBufferIdx = (mCurrentBufferIdx +1) % mNumOfBuffers;
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
        mCamera.startPreview();

    }

}

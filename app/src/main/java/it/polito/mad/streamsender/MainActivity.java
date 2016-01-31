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

import it.polito.mad.websocket.AsyncClientImpl;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity
        implements EncoderThread.Listener {

    private static final String TAG = "PREVIEW";

    private static final int sRatioWidth = 4;
    private static final int sRatioHeight = 3;
    private static final int sImageFormat = ImageFormat.YV12;

    private Button rec, pause, stop, connect, switchQuality;
    private EditText mIP, mPort;
    //private FrameLayout mContainer;
    private SurfaceView mCameraView;
    private int mRotation;

    private static final int mNumOfBuffers = 3;
    private int mWidth;
    private int mHeight;

    private byte[][] buffers;
    private int idx = 0;

    private Camera mCamera;
    private List<Camera.Size> mSuitableSizes;
    private int mSupportedSizesIndex = 0;

    //private EncoderTask mEncoderTask;
    private EncoderThread mEncoderThread;

    private AsyncClientImpl mClient = new AsyncClientImpl(new AsyncClientImpl.Listener() {
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
            mEncoderThread.submitAccessUnit(Util.swapNV21_UV(data));
            //mEncoderTask.submitAccessUnit(Util.swapNV21_UV(data));

            camera.addCallbackBuffer(buffers[idx]);
            idx = (idx+1) % mNumOfBuffers;
            //Log.d(TAG, "data["+data.length+"]");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                    //mCamera.addCallbackBuffer(buffers[idx]);

                    Camera.Parameters parameters = mCamera.getParameters();

                    String logSuppFormats = "Supported preview formats: ";
                    for (int f : parameters.getSupportedPreviewFormats()) {
                        switch (f) {
                            case ImageFormat.NV21:
                                logSuppFormats += "NV21 ";
                                break;
                            case ImageFormat.YV12:
                                logSuppFormats += "YV12 ";
                                break;
                            default:
                                logSuppFormats += f + " ";
                        }
                    }
                    Log.d(TAG, logSuppFormats);
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
                initializeEncoderThread();//.execute();
                mEncoderThread.start();

                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                mCamera.startPreview();
            }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera.setPreviewCallback(null);
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mEncoderThread.interrupt();
                try{
                    mEncoderThread.join();
                }catch(InterruptedException e){}
                mEncoderThread = null;
                //mEncoderTask.cancel(true);

                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mClient.closeConnection();
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
            }
        });



        initializeEncoderThread();

        FrameLayout mContainer = (FrameLayout) findViewById(R.id.preview);
        mContainer.addView(mCameraView);

    }

    private void initializeEncoderThread(){
        mEncoderThread = new EncoderThread(mWidth, mHeight, this);
    }

    @Override
    public void onEncodedDataAvailable(VideoChunks.Chunk chunk, boolean configBytes) {
        if (mClient.getSocket() == null) return;
        if (configBytes){
            mClient.sendConfigBytes(chunk.data);
        }
        else{
            mClient.sendStreamBytes(chunk);
        }
    }

    /*
            private EncoderTask initializeEncoderThread(){
                mEncoderTask = new EncoderTask(){
                    @Override
                    protected void onProgressUpdate(VideoChunks.Chunk... values) {
                        VideoChunks.Chunk c = values[0];
                        if ((c.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0){
                            //first packet
                            String s = "[";
                            for (int i=0;i<c.data.length;i++) s += c.data[i]+" ";
                            s+="]";
                            Log.d(TAG, s);
                            mClient.sendConfigBytes(c.data);
                        }
                        else{
                            mClient.sendStreamBytes(c);
                        }
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        //initializeEncoderThread().execute();
                    }
                };
                return mEncoderTask;
            }
        */
    public void acquireCamera() {
        try {
            mCamera = Camera.open(0);
            mSuitableSizes = new LinkedList<>();
            for (Camera.Size s : mCamera.getParameters().getSupportedPreviewSizes()){
                if ((s.width * sRatioHeight / sRatioWidth) == s.height){
                    if (s.width < 700)
                    mSuitableSizes.add(s);
                }
            }
            nextQuality();

        } catch (Exception e) {
            Log.e("CAMERA", e.getMessage());
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
        releaseCamera();
        super.onPause();
    }

    private void nextQuality(){

        //pause, so no other chunks will be added, then request encoder closure
        mCamera.setPreviewCallbackWithBuffer(null);
        mEncoderThread.interrupt();

        Camera.Size current = mSuitableSizes.get(mSupportedSizesIndex);
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

        buffers = new byte[mNumOfBuffers][(int)bufferSize];
        Log.d(TAG, "new preview size="+current.width+"x"+current.height+" ; new buffers size= "+buffers[0].length);

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mWidth, mHeight);
        mCamera.setParameters(parameters);

        try {
            mCamera.setPreviewDisplay(mCameraView.getHolder());
        }
        catch(IOException e){
            Toast.makeText(this, "Next quality: "+e.getMessage(), Toast.LENGTH_LONG).show();
        }

        mSupportedSizesIndex = (mSupportedSizesIndex + 1) % mSuitableSizes.size();

        try{
            mEncoderThread.join();
            Thread.sleep(100);
        }catch(InterruptedException e){}
        Log.d(TAG, "Encoder thread finished");

        //now, encoder thread has finished.
        //------------- BUT ------------
        //CHUNKS LIST MAY STILL CONTAIN SOME CHUNKS WITH 'OLD' SIZE
        //WE MUST EMPTY IT
        mEncoderThread.drain();

        mCamera.addCallbackBuffer(buffers[idx]);
        idx = (idx+1) % mNumOfBuffers;
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);

        Log.d(TAG, "CLEARED");
        initializeEncoderThread();
        mEncoderThread.start();
        Log.d(TAG, "RESTARTED");
    }

}

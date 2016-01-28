package it.polito.mad.streamsender;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
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

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import it.polito.mad.websocket.AsyncClientImpl;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PREVIEW";

    private Button rec, pause, stop, connect;
    private EditText mIP, mPort;
    private SurfaceView mCameraView;
    private int mRotation;

    private static final int mNumOfBuffers = 3;
    private int mWidth = 640;
    private int mHeigth = 480;

    private byte[][] buffers;
    private int idx = 0;

    private Camera mCamera;

    private EncoderTask mEncoderTask;

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
    public byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width*height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width*height; i < width*height + (width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i + (width/2*height/2)];
        for (int i = width*height + (width/2*height/2); i < width*height + 2*(width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i - (width/2*height/2)];
        return i420bytes;
    }

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mEncoderTask.submitAccessUnit(swapYV12toI420(data, mWidth, mHeigth));
            camera.addCallbackBuffer(buffers[idx]);
            idx = (idx+1) % mNumOfBuffers;
            Log.d(TAG, "data["+data.length+"]");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buffers = new byte[mNumOfBuffers][mWidth * mHeigth * 3 / 2];

        rec = (Button) findViewById(R.id.button_rec);
        pause = (Button) findViewById(R.id.button_pause);
        stop = (Button) findViewById(R.id.button_stop);
        connect = (Button) findViewById(R.id.button_connect);
        mIP = (EditText) findViewById(R.id.ip);
        mPort = (EditText) findViewById(R.id.port);

        mCameraView = new SurfaceView(this);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mRotation = wm.getDefaultDisplay().getRotation();
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
                    mCamera.addCallbackBuffer(buffers[idx]);
                    //mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                    Camera.Parameters parameters = mCamera.getParameters();
                    String logSuppFormats = "Supported formats: [ ";
                    for (int f : parameters.getSupportedPreviewFormats()){
                        switch(f){
                            case ImageFormat.NV21:
                                logSuppFormats += "NV21 ";
                                break;
                            case ImageFormat.YV12:
                                logSuppFormats += "YV12 ";
                                break;
                            default:
                                logSuppFormats += f+" ";
                        }
                    }
                    Log.d(TAG, logSuppFormats);
                    parameters.setPreviewFormat(ImageFormat.YV12);
                    parameters.setPreviewSize(mWidth, mHeigth);
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
                initializeEncoderThread().execute();
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
                mEncoderTask.cancel(true);
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


        initializeEncoderThread();

        FrameLayout container = (FrameLayout) findViewById(R.id.preview);
        container.addView(mCameraView);


    }



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
        };
        return mEncoderTask;
    }

    public void acquireCamera() {
        try {
            mCamera = Camera.open(0);
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

}

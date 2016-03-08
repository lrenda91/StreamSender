package it.polito.mad.streamsender;

import android.hardware.Camera;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import it.polito.mad.record.Camera1RecorderImpl;
import it.polito.mad.record.EncoderThread;
import it.polito.mad.record.VideoChunks;
import it.polito.mad.websocket.WSClientImpl;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PREVIEW";

    private Button rec, pause, stop, connect, switchQuality;
    private EditText mIP, mPort;
    private TextView tx,rx;


    private WSClientImpl mClient = new WSClientImpl(new WSClientImpl.Listener() {
        @Override
        public void onConnectionEstablished() {
            Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_LONG).show();
            rec.setEnabled(true);

            String device = Build.MANUFACTURER + " " + Build.MODEL;
            List<Camera.Size> sizes = mRecorder.getCameraManager().getSuitableSizes();
            String[] qualities = new String[sizes.size()];
            for (int i=0;i< sizes.size(); i++){
                qualities[i] = sizes.get(i).width + "x" + sizes.get(i).height;
            }
            mClient.sendHelloMessage(device, qualities);
        }

        @Override
        public void onServerUnreachable(Exception e) {
            Toast.makeText(MainActivity.this, "Can't connect to server: "
                    + e.getClass().getSimpleName()+": "+e.getMessage(), Toast.LENGTH_LONG).show();

        }

        @Override
        public void onResetReceived(int w, int h) {
            Toast.makeText(MainActivity.this, w+"x"+h+" received", Toast.LENGTH_LONG).show();
            mRecorder.switchToVideoQuality(w, h);
            mRecorder.startRecording();
            Log.d(TAG, "RESTARTED");
        }
    });


    private NetworkMonitor mNetMonitor = new NetworkMonitor(new NetworkMonitor.Callback() {
        @Override
        public void onData(long txBytes, long rxBytes) {

        }
        @Override
        public void onDataRate(long txBps, long rxBps) {
            tx.setText("TX: "+txBps+" bps");
            rx.setText("RX: "+rxBps+" bps");
        }
        @Override
        public void onUnsupportedTrafficStats() {
            Toast.makeText(MainActivity.this, "Unsupproted TraficStats", Toast.LENGTH_SHORT).show();
        }
    });
    private Camera1RecorderImpl mRecorder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rec = (Button) findViewById(R.id.button_rec);
        pause = (Button) findViewById(R.id.button_pause);
        stop = (Button) findViewById(R.id.button_stop);
        connect = (Button) findViewById(R.id.button_connect);
        switchQuality = (Button) findViewById(R.id.button_quality_switch);
        mIP = (EditText) findViewById(R.id.ip);
        mPort = (EditText) findViewById(R.id.port);
        tx = (TextView) findViewById(R.id.tx_text);
        rx = (TextView) findViewById(R.id.rx_text);

        SurfaceView mCameraView = (SurfaceView) findViewById(R.id.preview);
        //SurfaceView mCameraView = new SurfaceView(this);
        mRecorder = new Camera1RecorderImpl(this);
        mRecorder.setSurfaceView(mCameraView);
        mRecorder.registerEncoderListener(new EncoderThread.Listener() {
            @Override
            public void onConfigFrameAvailable(VideoChunks.Chunk chunk, int width, int height, int encodeBps, int frameRate) {
                if (mClient.isOpen()){
                    mClient.sendConfigBytes(chunk.data);
                }
            }
            @Override
            public void onEncodedDataAvailable(VideoChunks.Chunk chunk) {
                if (mClient.isOpen()) {
                    mClient.sendStreamBytes(chunk);
                }
            }
            @Override
            public void onStopped() {

            }
        });

        rec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.startRecording();
            }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.pauseRecording();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.stopRecording();
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
                mRecorder.switchToNextVideoQuality();
                mRecorder.startRecording();
                Log.d(TAG, "RESTARTED");
            }
        });

        //FrameLayout mContainer = (FrameLayout) findViewById(R.id.preview);
        //mContainer.addView(mCameraView);

        findViewById(R.id.toggle_net_monitor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNetMonitor.isRunning()){
                    mNetMonitor.stop();
                }
                else{
                    mNetMonitor.start();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "resume");
        mRecorder.acquireCamera();
    }

    @Override
    public void onPause() {
        mRecorder.stopRecording();
        if (mClient.isOpen()){
            mClient.closeConnection();
        }
        mRecorder.releaseCamera();
        super.onPause();
    }

}

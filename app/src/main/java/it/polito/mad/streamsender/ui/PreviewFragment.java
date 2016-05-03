package it.polito.mad.streamsender.ui;


import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

import it.polito.mad.streamsender.R;
import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.SimpleEncodingCallback;
import it.polito.mad.streamsender.record.Camera1Recorder;
import it.polito.mad.streamsender.encoding.VideoChunks;
import it.polito.mad.streamsender.record.NativeRecorderImpl;
import it.polito.mad.streamsender.encoding.Params;
import it.polito.mad.streamsender.websocket.WSClientImpl;

@SuppressWarnings("deprecation")
public class PreviewFragment extends Fragment {

    private static final String TAG = "PREVIEW";

    private static final int[] BITRATES_KBPS = { 250, 500, 800, 1000 };

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final String[] PERMISSIONS = new String[]{ Manifest.permission.CAMERA };
    //By default, camera permission is granted.
    //If current API is >= 23, it is denied until user explicitly grants it
    private boolean mCameraPermissionGranted = (Build.VERSION.SDK_INT < 23);

    private SurfaceView preview;
    private Button rec, pause, stop, connect, switchQuality;
    private SeekBar mResolutionSeekBar, mBitrateSeekBar;
    private TextView mResolutionLabel, mBitrateLabel;
    private SharedPreferences mPreferences;

    private Camera1Recorder mRecorder;

    private WSClientImpl mClient;
    private WSClientImpl.Listener mWebSocketListener = new WSClientImpl.Listener() {

        @Override
        public void onConnectionEstablished(String uri) {
            rec.setEnabled(true);
            pause.setEnabled(true);
            stop.setEnabled(true);
            String device = Util.getCompleteDeviceName();
            List<Camera.Size> sizes = mRecorder.getCameraManager().getSuitableSizes();
            String[] qualities = new String[sizes.size()];
            for (int i=0;i< sizes.size(); i++){
                qualities[i] = Util.sizeToString(sizes.get(i));
            }
            Camera.Size actualSize = mRecorder.getCameraManager().getCurrentSize();
            int actualSizeIdx = sizes.indexOf(actualSize);
            int actualBitrateIdx = mBitrateSeekBar.getProgress();
            mClient.sendHelloMessage(device, qualities, actualSizeIdx, BITRATES_KBPS, actualBitrateIdx);
            Toast.makeText(getContext(), "Connected to "+uri, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionError(Exception e) {
            Toast.makeText(getContext(), "Can't connect to server: "
                    + e.getMessage(), Toast.LENGTH_LONG).show();

        }

        @Override
        public void onConnectionClosed(boolean closedByServer) {
            rec.setEnabled(false);
            pause.setEnabled(false);
            stop.setEnabled(false);
            String message = closedByServer ? "closed by server" : "closed";
            Log.d(TAG, message);
        }

        @Override
        public void onResetReceived(int w, int h, int kbps) {
            String format = String.format("%dx%d %d Kbps",w,h,kbps);
            Camera.Size size = mRecorder.getCameraManager().getCameraInstance().new Size(w,h);
            int sizeIdx = mRecorder.getCameraManager().getSuitableSizes().indexOf(size);
            int bitrateIdx = -1;
            for (int i=0; i<BITRATES_KBPS.length; i++){
                if (BITRATES_KBPS[i] == kbps){
                    bitrateIdx = i;
                    break;
                }
            }
            if (bitrateIdx < 0 || sizeIdx < 0){
                Log.e(TAG, "Cannot reset. "+format+" is not available for this device");
                return;
            }
            Params p = new Params.Builder().width(w).height(h).bitRate(kbps).build();
            mRecorder.switchToVideoQuality(p);

            mResolutionSeekBar.setProgress(sizeIdx);
            mBitrateSeekBar.setProgress(bitrateIdx);
        }

    };
    private EncodingCallback mEncoderListener = new SimpleEncodingCallback() {
        @Override
        public void onConfigBytes(VideoChunks.Chunk chunk, int width, int height, int encodeBps, int frameRate) {
            if (mClient.isOpen()) {
                mClient.sendConfigBytes(chunk.data, width, height, encodeBps, frameRate);
            }
        }
        @Override
        public void onEncodedChunk(VideoChunks.Chunk chunk) {
            if (mClient.isOpen()) {
                mClient.sendStreamBytes(chunk);
            }
        }
    };


    public PreviewFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        mClient = new WSClientImpl(mWebSocketListener);
        mRecorder = new NativeRecorderImpl(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preview = (SurfaceView) view.findViewById(R.id.preview);
        rec = (Button) view.findViewById(R.id.button_rec);
        pause = (Button) view.findViewById(R.id.button_pause);
        stop = (Button) view.findViewById(R.id.button_stop);
        connect = (Button) view.findViewById(R.id.button_connect);
        //switchQuality = (Button) view.findViewById(R.id.button_quality_switch);
        mResolutionSeekBar = (SeekBar) view.findViewById(R.id.resolution_seek_bar);
        mBitrateSeekBar = (SeekBar) view.findViewById(R.id.bitrate_seek_bar);
        mResolutionLabel = (TextView) view.findViewById(R.id.resolution_text_view);
        mBitrateLabel = (TextView) view.findViewById(R.id.bitrate_text_view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasPermissions(PERMISSIONS)){
            requestPermissions(PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        else{
            mCameraPermissionGranted = true;
            setupCameraRecorder();
        }
    }

    @Override
    public void onPause() {
        if (mCameraPermissionGranted) {
            mRecorder.stopRecording();
            mRecorder.releaseCamera();
            mRecorder.setEncoderListener(null);
        }
        if (mClient.isOpen()){
            mClient.closeConnection();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE){
            for (int i=0; i<permissions.length; i++){
                if (permissions[i].equals(Manifest.permission.CAMERA)){
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED){
                        mCameraPermissionGranted = true;
                    }
                    else {
                        mCameraPermissionGranted = false;
                        Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void setupCameraRecorder(){
        mRecorder.acquireCamera();
        mRecorder.setSurfaceView(preview);
        mRecorder.setEncoderListener(mEncoderListener);

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
                String ip = mPreferences.getString(
                        getString(R.string.pref_key_server_ip), //key
                        getString(R.string.pref_server_ip_default_value));  //default
                int port = Integer.parseInt(mPreferences.getString(
                        getString(R.string.pref_key_server_port),   //key
                        getString(R.string.pref_server_port_default_value)) //default
                );
                mClient.connect(ip, port, 2000);
            }
        });
        /*switchQuality.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mRecorder.switchToNextVideoQuality();
                //Camera.Size size = mRecorder.getCameraManager().getCurrentSize();
                //Toast.makeText(getContext(), Util.sizeToString(size), Toast.LENGTH_SHORT).show();
            }
        });*/


        mResolutionSeekBar.setMax(mRecorder.getCameraManager().getSuitableSizes().size()-1);
        mResolutionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Camera.Size size = mRecorder.getCameraManager().getSuitableSizes().get(progress);
                mResolutionLabel.setText(Util.sizeToString(size));
                if (fromUser) applyActualParams();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mBitrateSeekBar.setMax(BITRATES_KBPS.length-1);
        mBitrateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bitRate = BITRATES_KBPS[progress];
                mBitrateLabel.setText(String.format("%d Kbps", bitRate));
                if (fromUser) applyActualParams();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mResolutionSeekBar.setProgress(0);
        mBitrateSeekBar.setProgress(0);
        applyActualParams();
    }

    private void applyActualParams(){
        Camera.Size size = mRecorder.getCameraManager().getSuitableSizes().get(mResolutionSeekBar.getProgress());
        /*mResolutionLabel.setText(Util.sizeToString(size));
        int bitRatesIdx = mBitrateSeekBar.getProgress();
        int bitRate = BITRATES_KBPS[bitRatesIdx];
        mBitrateLabel.setText(String.format("%d Kbps", bitRate));
        */
        int bitRate = BITRATES_KBPS[mBitrateSeekBar.getProgress()];
        Params.Builder builder = new Params.Builder()
                .width(size.width)
                .height(size.height)
                .bitRate(bitRate);
        mRecorder.switchToVideoQuality(builder.build());
    }

    private boolean hasPermissions(String[] permissions){
        for (String perm : permissions){
            if (ContextCompat.checkSelfPermission(getActivity(), perm) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

}

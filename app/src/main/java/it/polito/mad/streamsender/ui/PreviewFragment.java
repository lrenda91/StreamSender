package it.polito.mad.streamsender.ui;


import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.TreeSet;

import it.polito.mad.streamsender.R;
import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.EncodingCallback;
import it.polito.mad.streamsender.encoding.SimpleEncodingCallback;
import it.polito.mad.streamsender.record.AbsCamcorder;
import it.polito.mad.streamsender.record.Size;
import it.polito.mad.streamsender.encoding.VideoChunks;
import it.polito.mad.streamsender.record.NativeRecorderImpl;
import it.polito.mad.streamsender.encoding.Params;
import it.polito.mad.streamsender.net.WSClientImpl;

@SuppressWarnings("deprecation")
public class PreviewFragment extends Fragment {

    private static final String TAG = "PREVIEW";

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final String[] PERMISSIONS = new String[]{ Manifest.permission.CAMERA };
    //By default, camera permission is granted.
    //If current API is >= 23, it is denied until user explicitly grants it
    private boolean mCameraPermissionGranted = (Build.VERSION.SDK_INT < 23);

    private SurfaceView preview;
    private Button rec, pause, stop, connect;
    private SeekBar mResolutionSeekBar, mBitrateSeekBar;
    private TextView mResolutionLabel, mBitrateLabel;
    private SharedPreferences mPreferences;

    private AbsCamcorder mRecorder;
    private ArrayList<Size> mSizes;
    private ArrayList<Integer> mBitRates;

    private WSClientImpl mClient;
    private WSClientImpl.Listener mWebSocketListener = new WSClientImpl.Listener() {
        @Override
        public void onConnectionEstablished(String uri) {
            rec.setEnabled(true);
            pause.setEnabled(true);
            stop.setEnabled(true);
            String device = Util.getCompleteDeviceName();
            String[] qualities = new String[mSizes.size()];
            for (int i=0;i< mSizes.size(); i++){
                qualities[i] = Util.sizeToString(mSizes.get(i));
            }
            Size actualSize = mRecorder.getCameraManager().getCurrentSize();
            int actualSizeIdx = mSizes.indexOf(actualSize);
            int[] bitRates = new int[mBitRates.size()];
            for (int i=0;i< mBitRates.size(); i++){
                bitRates[i] = mBitRates.get(i);
            }
            int actualBitrateIdx = mBitrateSeekBar.getProgress();
            mClient.sendHelloMessage(device, qualities, actualSizeIdx, bitRates, actualBitrateIdx);
            Toast.makeText(getContext(), "Connected to "+uri, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionError(Exception e) {
            Toast.makeText(getContext(), "Can't connect to server: "
                    + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onBandwidthChange(int Kbps, double performancesPercentage) {
            Log.d(TAG, Kbps+" Kbps "+performancesPercentage+"%");
            try{
                if (performancesPercentage > 95.0){
                    mRecorder.switchToHigherQuality();
                }
                else {
                    mRecorder.switchToLowerQuality();
                }
            }catch(Exception ex){
                Log.w(TAG, ex.getMessage());
            }

            /*Params targetParams = null;
            ListIterator<Params> iterator = mRecorder.getPresets().listIterator();
            while (iterator.hasPrevious()){
                Params params = iterator.previous();
                if (params.bitrate() <= Kbps){
                    targetParams = params;
                    break;
                }
            }Log.d(TAG, "SWITCH TO "+targetParams);
            if (targetParams != null){

                mRecorder.switchToVideoQuality(targetParams);
            }*/
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
            String format = String.format("RESET: %dx%d %d Kbps",w,h,kbps);
            Size size = new Size(w,h);
            int sizeIdx = mSizes.indexOf(size);
            int bitrateIdx = mBitRates.indexOf(kbps);
            if (bitrateIdx < 0 || sizeIdx < 0){
                Log.e(TAG, "Cannot reset. "+format+" is not available for this device");
                return;
            }
            mResolutionSeekBar.setProgress(sizeIdx);
            mBitrateSeekBar.setProgress(bitrateIdx);
            applyActualParams();
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
            mRecorder.setEncoderListener(null);
            mRecorder.closeCamera();
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
        mRecorder.openCamera();
        mRecorder.setSurfaceView(preview);
        mRecorder.setEncoderListener(mEncoderListener);

        Set<Size> sizesSet= new TreeSet<>();
        Set<Integer> bitRatesSet = new TreeSet<>();
        for (Params params : mRecorder.getPresets()){
            sizesSet.add(new Size(params.width(), params.height()));
            bitRatesSet.add(params.bitrate());
        }
        mSizes = new ArrayList<>(sizesSet);
        mBitRates = new ArrayList<>(bitRatesSet);

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

        mResolutionSeekBar.setMax(mSizes.size()-1);
        mResolutionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Size size = mSizes.get(progress);
                mResolutionLabel.setText(Util.sizeToString(size));
                if (fromUser) applyActualParams();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mBitrateSeekBar.setMax(mBitRates.size()-1);
        mBitrateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bitRate = mBitRates.get(progress);
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
        Size size = mSizes.get(mResolutionSeekBar.getProgress());
        int bitRate = mBitRates.get(mBitrateSeekBar.getProgress());
        Params.Builder builder = new Params.Builder()
                .width(size.getWidth())
                .height(size.getHeight())
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

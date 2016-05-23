package it.polito.mad.streamsender.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import it.polito.mad.streamsender.net.ws.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import it.polito.mad.streamsender.encoding.VideoChunks;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClientImpl extends WebSocketAdapter implements WSClient {

    private static final boolean VERBOSE = true;
    private static final String TAG = "WSClient";
    private static final String WS_URI_FORMAT = "ws://%s:%d";
    private static final String HTTP_URI_FORMAT = "http://%s:%d";

    private Handler mMainHandler;
    private String mServerIP;
    private int mPort;

    public interface Listener {
        void onConnectionEstablished(String uri);
        void onConnectionClosed(boolean closedByServer);
        void onConnectionError(Exception e);
        void onResetReceived(int w, int h, int kbps);
        void onBandwidthChange(int Kbps, double performancesPercentage);
    }

    protected WebSocket mWebSocket;
    private Listener mListener;

    public WSClientImpl(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    @Override
    public WebSocket getWebSocket() {
        return mWebSocket;
    }

    public boolean isOpen(){
        return mWebSocket != null && mWebSocket.isOpen();
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        mServerIP = serverIP;
        mPort = port;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    String mConnectURI = String.format(WS_URI_FORMAT, serverIP, port);
                    mWebSocket = new WebSocketFactory().createSocket(mConnectURI, timeout);
                    mWebSocket.addListener(WSClientImpl.this);
                    mWebSocket.connect();
                }
                catch(final Exception e){
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onConnectionError(e);
                        }
                    });
                    return;
                }
            }
        }).start();
    }

    @Override
    public void closeConnection() {
        mWebSocket.sendClose();
    }

    public void sendHelloMessage(String device, String[] qualities, int actualSizeIdx,
        int[] bitrates, int actualBitrateIdx){
        try {
            JSONObject configMsg = JSONMessageFactory.createHelloMessage(device, qualities, actualSizeIdx,
                    bitrates, actualBitrateIdx);
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void sendConfigBytes(final byte[] configData, int width, int height, int encodeBps, int frameRate){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigMessage(configData, width, height, encodeBps, frameRate);
            mWebSocket.sendText(configMsg.toString());

        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void sendStreamBytes(final VideoChunks.Chunk chunk){
        try {
            JSONObject obj = JSONMessageFactory.createStreamMessage(chunk);
            String text = obj.toString();
            sumToSend.addAndGet(text.length());
            contToSend.incrementAndGet();
            mWebSocket.sendText(text);
        }
        catch(JSONException e){
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        mMeasureThread = new Thread(mMeasureRunnable);
        mMeasureThread.start();
        final String mConnectURI = String.format(WS_URI_FORMAT, mServerIP, mPort);
        if (VERBOSE) Log.d(TAG, "Successfully connected to " + mConnectURI);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionEstablished(mConnectURI);
            }
        });
    }

    @Override
    public void onConnectError(WebSocket websocket, final WebSocketException exception) throws Exception {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionError(exception);
            }
        });
    }


    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, final boolean closedByServer) throws Exception {
        try{
            mMeasureThread.interrupt();
            mMeasureThread.join();
            mMeasureThread = null;
        }catch(InterruptedException e){}

        if (VERBOSE) Log.d("WS", "disconnected by server: " + closedByServer);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionClosed(closedByServer);
            }
        });
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        try{
            JSONObject obj = new JSONObject(text);
            if (obj.has("type")){
                if (obj.get("type").equals("reset")){
                    if (obj.has("width")
                            && obj.has("height")
                            && obj.has("bitrate")){
                        final int width = obj.getInt("width");
                        final int height = obj.getInt("height");
                        final int bitrate = obj.getInt("bitrate");
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) mListener.onResetReceived(width, height, bitrate);
                            }
                        });
                    }
                }
            }
        }catch(JSONException e){}
    }

    @Override
    public void onFrameSent(WebSocket websocket, final WebSocketFrame frame) throws Exception {
        super.onFrameSent(websocket, frame);
        //try{Thread.sleep(50);}catch (InterruptedException e){}
        sumSent.addAndGet(frame.getPayloadLength());
        contSent.incrementAndGet();
    }

    private AtomicLong sumSent = new AtomicLong(0), contSent = new AtomicLong(0) ;
    private AtomicLong sumToSend = new AtomicLong(0), contToSend = new AtomicLong(0);
    private double mPercentage = 100;
    private Thread mMeasureThread;

    private Runnable mMeasureRunnable = new Runnable() {
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (!Thread.interrupted()){
                double toSendValue = (double) sumToSend.get();
                double sentValue = (double) sumSent.get();
                if (toSendValue > 0){
                    double ratio = sentValue / toSendValue * 100.0;
                    double percentage = Math.round(ratio * 100.0) / 100.0;
                    double millis = (double) (System.currentTimeMillis() - startTime);
                    double elapsedSeconds = millis / 1000.0;
                    int Bps = (int)(sentValue / elapsedSeconds);
                    final int Kbps = Bps * 8 / 1000;
                    mPercentage = percentage;
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onBandwidthChange(Kbps, mPercentage);
                        }
                    });
                }
                try{
                    Thread.sleep(5000);
                } catch (InterruptedException e){
                    break;
                }
            }
            Log.d(TAG, "STOP");
        }
    };

}

package it.polito.mad.websocket;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mad.JSONMessageFactory;
import it.polito.mad.streamsender.VideoChunks;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClient extends AbstractWSClient {

    private static final boolean VERBOSE = true;
    private static final String TAG = "WebSocketClient";

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler, mMainHandler;

    public interface Listener {
        void onConnectionEstablished();
        void onServerUnreachable(Exception e);
    }

    private Listener mListener;

    public WSClient(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    public boolean isOpen(){
        return mWebSocket != null && mWebSocket.isOpen();
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String uri = "ws://" + serverIP + ":" + port;
                    mWebSocket = new WebSocketFactory().createSocket(uri, timeout);
                    mWebSocket.addListener(WSClient.this);

                    mWebSocket.connect();
                    if (VERBOSE) Log.d(TAG, "Successfully connected to " + uri);
                } catch (final Exception e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onServerUnreachable(e);
                        }
                    });
                    return;
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) mListener.onConnectionEstablished();
                    }
                });

            }
        }).start();
    }

    @Override
    public void closeConnection() {
        try {
            JSONObject obj = JSONMessageFactory.createResetMessage();
            mWebSocket.sendText(obj.toString());
        }
        catch(JSONException e){

        }
        mWebSocket.sendClose();
    }

    public void sendConfigBytes(final byte[] configData){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigMessage(configData);
            mWebSocket.sendText(configMsg.toString());
            //String base64 = configMsg.getString(JSONMessageFactory.DATA_KEY);

        } catch (JSONException e) {

        }
    }

    public void sendStreamBytes(final VideoChunks.Chunk chunk){
        try {
            JSONObject obj = JSONMessageFactory.createStreamMessage(chunk);
            mWebSocket.sendText(obj.toString());
        }
        catch(JSONException e){

        }
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.d("WS", "disconnected by server: "+closedByServer);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground"){
            @Override
            protected void onLooperPrepared() {
                super.onLooperPrepared();
                mBackgroundHandler = new Handler();
            }
        };
        mBackgroundThread.start();
        if (VERBOSE) Log.d(TAG, "Background thread started!!");
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
            Log.d(TAG, "Web socket Client TERMINATED!");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

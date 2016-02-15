package it.polito.mad.websocket;

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
import it.polito.mad.streamsender.MediaChunks;

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

    public void sendConfigBytes(boolean audio, final byte[] configData){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigMessage(audio, configData);
            mWebSocket.sendText(configMsg.toString());
            //String base64 = configMsg.getString(JSONMessageFactory.DATA_KEY);

        } catch (JSONException e) {

        }
    }

    public void sendStreamBytes(final MediaChunks.Chunk chunk){
        try {
            JSONObject obj = JSONMessageFactory.createStreamMessage(chunk);
            mWebSocket.sendText(obj.toString());
        }
        catch(JSONException e){

        }
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.d("WS", serverCloseFrame.getCloseReason());
        Log.d("WS", clientCloseFrame.getCloseReason());
        Log.d("WS", "disconnected by server: "+closedByServer);
    }


}

package it.polito.mad.websocket;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import it.polito.mad.record.VideoChunks;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClientImpl extends WebSocketAdapter implements WSClient {

    private static final boolean VERBOSE = true;
    private static final String TAG = "WSClient";
    private static final String URI_FORMAT = "ws://%s:%d";

    private Handler mMainHandler;
    private String mConnectURI;

    public interface Listener {
        void onConnectionEstablished();
        void onServerUnreachable(Exception e);
        void onResetReceived(int w, int h);
    }

    protected WebSocket mWebSocket;
    private Listener mListener;

    public WSClientImpl(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    @Override
    public WebSocket getSocket() {
        return mWebSocket;
    }

    public boolean isOpen(){
        return mWebSocket != null && mWebSocket.isOpen();
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        mConnectURI = String.format(URI_FORMAT, serverIP, port);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    mWebSocket = new WebSocketFactory().createSocket(mConnectURI, timeout);
                    mWebSocket.addListener(WSClientImpl.this);
                    mWebSocket.connect();
                }
                catch(final Exception e){
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onServerUnreachable(e);
                        }
                    });
                    return;
                }
            }
        }).start();
    }

    @Override
    public void closeConnection() {
        /*try {
            JSONObject obj = JSONMessageFactory.createResetMessage();
            mWebSocket.sendText(obj.toString());
        }
        catch(JSONException e){

        }
        */
        mWebSocket.sendClose();
    }

    public void sendHelloMessage(String device, String[] qualities){
        try {
            JSONObject configMsg = JSONMessageFactory.createHelloMessage(device, qualities);
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {

        }
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
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
        if (VERBOSE) Log.d(TAG, "Successfully connected to " + mConnectURI);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onConnectionEstablished();
            }
        });
    }

    @Override
    public void onConnectError(WebSocket websocket, final WebSocketException exception) throws Exception {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) mListener.onServerUnreachable(exception);
            }
        });
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onFrameSent(websocket, frame);

    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.d("WS", "disconnected by server: " + closedByServer);
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        try{
            JSONObject obj = new JSONObject(text);
            if (obj.has("type")){
                if (obj.get("type").equals("reset")){
                    if (obj.has("width") && obj.has("height")){
                        final int width = obj.getInt("width");
                        final int height = obj.getInt("height");
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) mListener.onResetReceived(width, height);
                            }
                        });
                    }
                }
            }
        }catch(JSONException e){}
    }
}

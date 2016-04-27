package it.polito.mad.streamsender.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import it.polito.mad.streamsender.encoding.VideoChunks;

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
        void onConnectionEstablished(String uri);
        void onConnectionClosed(boolean closedByServer);
        void onConnectionError(Exception e);
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

    public void sendHelloMessage(String device, String[] qualities, int currentIdx){
        try {
            JSONObject configMsg = JSONMessageFactory.createHelloMessage(device, qualities, currentIdx);
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {

        }
    }

    public void sendConfigBytes(final byte[] configData, int width, int height, int encodeBps, int frameRate){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigMessage(configData, width, height, encodeBps, frameRate);
            mWebSocket.sendText(configMsg.toString());

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
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onFrameSent(websocket, frame);

    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, final boolean closedByServer) throws Exception {
        Log.d("WS", "disconnected by server: " + closedByServer);
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

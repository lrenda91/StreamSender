package it.polito.mad.websocket;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;

import java.net.InetAddress;

/**
 * Created by luigi on 02/12/15.
 */
public interface WSClient {

    WebSocket getSocket();

    void connect(String serverIP, int port, int timeout);

    void closeConnection();

}

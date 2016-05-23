package it.polito.mad.streamsender.net;

import it.polito.mad.streamsender.net.ws.WebSocket;

/**
 * Created by luigi on 02/12/15.
 */
public interface WSClient {

    WebSocket getWebSocket();

    void connect(String serverIP, int port, int timeout);

    void closeConnection();

}

package it.polito.mad.streamsender.encoding;

/**
 * Created by luigi on 27/04/16.
 */
public interface EncodingListener {

    void onEncodingStarted(Params actualParams);
    /*void onConfigHeaders(VideoChunks.Chunk chunk,
                       int width,
                       int height,
                       int encodeBps,
                       int frameRate);*/
    void onParamsChanged(Params actualParams);
    void onConfigHeaders(VideoChunks.Chunk chunk,
                         Params actualParams);
    void onEncodedChunk(VideoChunks.Chunk chunk);
    void onEncodingPaused();
    void onEncodingStopped();

}

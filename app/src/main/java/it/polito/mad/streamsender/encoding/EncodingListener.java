package it.polito.mad.streamsender.encoding;

/**
 * Created by luigi on 27/04/16.
 */
public interface EncodingListener {

    void onEncodingStarted(Params actualParams);
    void onConfigBytes(VideoChunks.Chunk chunk,
                       int width,
                       int height,
                       int encodeBps,
                       int frameRate);
    void onEncodedChunk(VideoChunks.Chunk chunk);
    void onEncodingPaused();
    void onEncodingStopped();

}

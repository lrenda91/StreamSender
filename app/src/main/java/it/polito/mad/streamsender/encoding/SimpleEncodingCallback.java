package it.polito.mad.streamsender.encoding;

/**
 * Created by luigi on 27/04/16.
 */
public abstract class SimpleEncodingCallback implements EncodingCallback {

    @Override
    public void onStartedEncoding() { }

    @Override
    public void onConfigBytes(VideoChunks.Chunk chunk, int width, int height, int encodeBps, int frameRate) { }

    @Override
    public void onEncodedChunk(VideoChunks.Chunk chunk) { }

    @Override
    public void onStoppedEncoding() { }

}

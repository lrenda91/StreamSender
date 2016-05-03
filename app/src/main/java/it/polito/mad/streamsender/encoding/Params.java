package it.polito.mad.streamsender.encoding;

/**
 * Created by luigi on 29/04/16.
 */
public class Params {

    private int mWidth = 0, mHeight = 0, mBitrate = 0, mFrameRate = 30; //by default

    private Params(int w, int h, int kbps, int fps){
        mWidth = w;
        mHeight = h;
        mBitrate = kbps;
        mFrameRate = fps;
    }

    public int bitrate() {
        return mBitrate;
    }

    public int frameRate() {
        return mFrameRate;
    }

    public int height() {
        return mHeight;
    }

    public int width() {
        return mWidth;
    }

    public static class Builder {
        private int wid, hei, brate, fps;
        public Builder width(int w){ wid = w; return this; }
        public Builder height(int h){ hei = h; return this; }
        public Builder bitRate(int kbps){ brate = kbps; return this; }
        public Builder frameRate(int frames){ fps = frames; return this; }
        public Params build(){
            return new Params(wid, hei, brate, fps);
        }
    }

    @Override
    public String toString() {
        return String.format("[(%dx%d) %d Kbps %d fps]",mWidth, mHeight, mBitrate, mFrameRate);
    }
}

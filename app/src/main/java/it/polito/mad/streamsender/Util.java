package it.polito.mad.streamsender;

import android.util.Log;

/**
 * Created by luigi on 27/01/16.
 */
public class Util {


    public static byte[] swapNV21_UV(byte[] NV21){

        int p = NV21.length * 2 / 3;    //<-- this is equals to width*height
        int idx = p;
        int Clen = p/4;

        for (int i=0; i< Clen; i++){
            int uIdx = idx+i;
            int vIdx = idx+i+Clen;
            byte U = NV21[uIdx];
            byte V = NV21[vIdx];
            byte tmp = U;
            NV21[uIdx] = V;
            NV21[vIdx] = tmp;
        }

        return NV21;
    }

    public byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width*height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width*height; i < width*height + (width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i + (width/2*height/2)];
        for (int i = width*height + (width/2*height/2); i < width*height + 2*(width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i - (width/2*height/2)];
        return i420bytes;
    }

}

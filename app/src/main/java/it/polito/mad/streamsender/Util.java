package it.polito.mad.streamsender;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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

    public static byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width*height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width*height; i < width*height + (width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i + (width/2*height/2)];
        for (int i = width*height + (width/2*height/2); i < width*height + 2*(width/2*height/2); i++)
            i420bytes[i] = yv12bytes[i - (width/2*height/2)];
        return i420bytes;
    }

    public static byte[] swapColors(byte[] data, int w, int h, int pictureFormat){
        switch(pictureFormat){
            case ImageFormat.YV12:
                return swapYV12toI420(data,w, h);
            case ImageFormat.NV21:
                return swapNV21_UV(data);
            default:
                Log.w("Util", "No color format to swap");
        }
        return data;
    }

    public static int getEncoderColorFormat(int previewFormat){
        if (Build.VERSION.SDK_INT >= 21){
            //return MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
        }
        switch(previewFormat){
            case ImageFormat.NV21:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
            case ImageFormat.YV12:
                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        }
        return -1;
    }

    public static void logColorFormat(String tag, int colorFormat){
        switch(colorFormat){
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Log.d(tag, "COLOR_FormatYUV420Planar"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                Log.d(tag, "COLOR_FormatYUV420SemiPlanar"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Log.d(tag, "COLOR_FormatYUV420Flexible"); break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface:
                Log.d(tag, "COLOR_FormatSurface"); break;
        }
    }

    public static void logCameraPictureFormat(String tag, int picFormat){
        switch(picFormat){
            case ImageFormat.NV21:
                Log.d(tag, "NV21"); break;
            case ImageFormat.YV12:
                Log.d(tag, "YV12"); break;
            case ImageFormat.JPEG:
                Log.d(tag, "JPEG"); break;
            case ImageFormat.YUY2:
                Log.d(tag, "YUY2"); break;
            case ImageFormat.YUV_420_888:
                Log.d(tag, "YUV_420_888"); break;
            case ImageFormat.YUV_422_888:
                Log.d(tag, "YUV_422_888"); break;
            case ImageFormat.YUV_444_888:
                Log.d(tag, "YUV_444_888"); break;
            default:
                Log.d(tag, "unknown ImageFormat"); break;
        }
    }

    public static List<CamcorderProfile> getSupportedProfiles(int cameraID){
        List<CamcorderProfile> res = new LinkedList<>();
        int[] profiles = {
                CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_2160P,
                CamcorderProfile.QUALITY_480P,
                CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_CIF,
                CamcorderProfile.QUALITY_HIGH,
                CamcorderProfile.QUALITY_HIGH_SPEED_1080P,
                CamcorderProfile.QUALITY_HIGH_SPEED_2160P,
                CamcorderProfile.QUALITY_HIGH_SPEED_720P,
                CamcorderProfile.QUALITY_HIGH_SPEED_480P,
                CamcorderProfile.QUALITY_HIGH_SPEED_HIGH,
                CamcorderProfile.QUALITY_HIGH_SPEED_LOW,
                CamcorderProfile.QUALITY_LOW,
                CamcorderProfile.QUALITY_QCIF,
                CamcorderProfile.QUALITY_QVGA
        };
        for (int p : profiles){
            if (CamcorderProfile.hasProfile(cameraID, p)){
                logCamcorderProfile(p);
                res.add(CamcorderProfile.get(cameraID, p));
            }
        }
        return res;
    }

    public static void logCamcorderProfile(int quality){
        switch (quality){
            case CamcorderProfile.QUALITY_1080P:
                Log.d("PROFILE", "QUALITY_1080P");
                break;
            case CamcorderProfile.QUALITY_2160P:
                Log.d("PROFILE", "QUALITY_2160P");
                break;
            case CamcorderProfile.QUALITY_480P:
                Log.d("PROFILE", "QUALITY_480P");
                break;
            case CamcorderProfile.QUALITY_720P:
                Log.d("PROFILE", "QUALITY_720P");
                break;
                    case CamcorderProfile.QUALITY_CIF:
                        Log.d("PROFILE", "QUALITY_CIF");
                        break;
            case CamcorderProfile.QUALITY_HIGH:
                Log.d("PROFILE", "QUALITY_HIGH");
                break;
            case CamcorderProfile.QUALITY_HIGH_SPEED_1080P:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_1080P");
                break;
            case   CamcorderProfile.QUALITY_HIGH_SPEED_2160P:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_2160P");
                break;
            case    CamcorderProfile.QUALITY_HIGH_SPEED_720P:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_720P");
                break;
            case    CamcorderProfile.QUALITY_HIGH_SPEED_480P:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_480P");
                break;
            case    CamcorderProfile.QUALITY_HIGH_SPEED_HIGH:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_HIGH");
                break;
            case    CamcorderProfile.QUALITY_HIGH_SPEED_LOW:
                Log.d("PROFILE", "QUALITY_HIGH_SPEED_LOW");
                break;
            case    CamcorderProfile.QUALITY_LOW:
                Log.d("PROFILE", "QUALITY_LOW");
                break;
            case    CamcorderProfile.QUALITY_QCIF:
                Log.d("PROFILE", "QUALITY_QCIF");
                break;
            case    CamcorderProfile.QUALITY_QVGA:
                Log.d("PROFILE", "QUALITY_QVGA");
                break;
        }
    }

}

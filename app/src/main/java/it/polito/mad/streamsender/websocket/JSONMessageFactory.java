package it.polito.mad.streamsender.websocket;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mad.streamsender.encoding.VideoChunks;

/**
 * Created by luigi on 24/01/16.
 */
public class JSONMessageFactory {

    public static final String RULE_KEY = "rule";
    public static final String RULE_VALUE = "pub";

    public static final String TYPE_KEY = "type";
    public static final String DATA_KEY = "data";
    public static final String FLAGS_KEY = "flags";
    public static final String WIDTH_KEY = "width";
    public static final String HEIGHT_KEY = "height";
    public static final String BPS_KEY = "encodeBps";
    public static final String FPS_KEY = "frameRate";
    public static final String TIMESTAMP_KEY = "ts";
    public static final String DEVICE_KEY = "device";
    public static final String QUALITIES_KEY = "qualities";
    public static final String CURRENT_QUALITY_KEY = "current";

    private JSONMessageFactory(){}

    private static JSONObject get() throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put(RULE_KEY, RULE_VALUE);
        return msg;
    }

    public static JSONObject createHelloMessage(String device, String[] qualities,
                                                int currentIdx) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "hello");
        msg.put(DEVICE_KEY, device);
        JSONArray array = new JSONArray();
        for (String s : qualities) {
            array.put(s);
        }
        msg.put(QUALITIES_KEY, array);
        msg.put(CURRENT_QUALITY_KEY, qualities[currentIdx]);
        return msg;
    }


    /**
     * Creates JSON object which wraps the first frame (SPS-PPS configuration)
     * @param configData
     * @return
     * @throws JSONException
     */
    public static JSONObject createConfigMessage(byte[] configData,
            int width, int height, int encodeBps, int frameRate) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "config");
        String base64 = Base64.encodeToString(configData, Base64.DEFAULT);
        msg.put(DATA_KEY, base64);
        msg.put(WIDTH_KEY, width);
        msg.put(HEIGHT_KEY, height);
        msg.put(BPS_KEY, encodeBps);
        msg.put(FPS_KEY, frameRate);
        return msg;
    }

    public static JSONObject createStreamMessage(VideoChunks.Chunk chunk) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "stream");
        String base64 = Base64.encodeToString(chunk.data, Base64.DEFAULT);
        msg.put(DATA_KEY, base64);
        msg.put(FLAGS_KEY, chunk.flags);
        msg.put(TIMESTAMP_KEY, chunk.presentationTimestampUs);
        return msg;
    }

    public static JSONObject createResetMessage() throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "reset");
        return msg;
    }

}

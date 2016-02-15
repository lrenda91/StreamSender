package it.polito.mad;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mad.streamsender.MediaChunks;

/**
 * Created by luigi on 24/01/16.
 */
public class JSONMessageFactory {

    public static final String RULE_KEY = "rule";
    public static final String RULE_VALUE = "pub";

    public static final String TYPE_KEY = "type";
    public static final String DATA_KEY = "data";
    public static final String FLAGS_KEY = "flags";
    public static final String TIMESTAMP_KEY = "ts";
    /*private static final String CONFIG_TYPE_VALUE = "config";
    private static final String STREAM_TYPE_VALUE = "stream";
    private static final String RESET_TYPE_VALUE = "reset";*/
    private static final String VIDEO_TYPE_VALUE = "video";
    private static final String AUDIO_TYPE_VALUE = "audio";

    private JSONMessageFactory(){}

    private static JSONObject get() throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put(RULE_KEY, RULE_VALUE);
        return msg;
    }

    public static JSONObject createConfigMessage(boolean audio, byte[] configData) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, "config-" + (audio ? "audio" : "video"));
        String base64 = Base64.encodeToString(configData, Base64.DEFAULT);
        try {
            msg.put(DATA_KEY, base64);
        }
        catch(Exception e){
            throw new JSONException(e.getClass().getSimpleName());
        }
        return msg;
    }

    public static JSONObject createStreamMessage(MediaChunks.Chunk chunk) throws JSONException {
        JSONObject msg = get();
        msg.put(TYPE_KEY, chunk.isAudio ? AUDIO_TYPE_VALUE : VIDEO_TYPE_VALUE);
        //msg.put(TYPE_KEY, "stream");
        String base64 = Base64.encodeToString(chunk.data, Base64.DEFAULT);
        try {
            //msg[data] = Buffer() in javascript
            msg.put(DATA_KEY, base64);
        }
        catch(Exception e){
            throw new JSONException(e.getClass().getSimpleName());
        }
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

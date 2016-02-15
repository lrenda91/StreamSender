package it.polito.mad.streamsender;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by luigi on 05/02/16.
 */
public class AudioRecProva {

    private static final String TAG = "AUDIO";

    private AudioEncoderThread mEncoder;

    public AudioRecProva(AudioEncoderThread thread){
        mEncoder = thread;
    }

    private void go() throws IOException {

        mEncoder.startThread();

        int source = MediaRecorder.AudioSource.MIC;
        int sampleRate = 44100;
        int channelsIn = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelsIn, audioFormat);

        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE){
            Log.d(TAG, "Problem computing min buffer size");
            return;
        }
        AudioRecord record = new AudioRecord(
                source,
                sampleRate,
                channelsIn,
                audioFormat,
                minBufferSize
        );
        record.startRecording();

        byte[] buffer = new byte[minBufferSize];

        while (!Thread.interrupted()){
            int read = record.read(buffer,0, minBufferSize);
            if(AudioRecord.ERROR_INVALID_OPERATION == read){
                Log.e(TAG, "Invalid operation AAAAAAAAAAAAAAAA");
                continue;
            }
            //Log.d(TAG, "read buf size: " + read);
            mEncoder.submitAccessUnit(buffer);
        }
        record.stop();
        record.release();

        mEncoder.requestStop();
        mEncoder.waitForTermination();
        Log.d(TAG, "End!!!");


    }

    private Thread workingThread;

    public void startThread(){
        workingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    go();
                }catch(IOException e){
                    Log.e(TAG, e.getMessage());
                }
            }
        });
        workingThread.start();
    }

    public void stop(){
        if (workingThread != null){
            workingThread.interrupt();
        }
    }

}

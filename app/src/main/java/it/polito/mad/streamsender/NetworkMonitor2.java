package it.polito.mad.streamsender;

import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.Socket;
import java.net.SocketException;

/**
 * Created by luigi on 24/02/16.
 */
public class NetworkMonitor2 {

    public interface Callback {
        void onData(long txBytes, long rxBytes);
        void onDataRate(long txBps, long rxBps);
        void onUnsupportedTrafficStats();
    }

    private static final boolean VERBOSE = true;
    private static final String TAG = "NetMonitor2";

    private int mAppUID = android.os.Process.myUid();
    private long mStartRX, mStartTX, mPreviousRX, mPreviousTX;
    private boolean mRunning = false;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Callback mCallback;

    private Socket mSocket;

    private final Runnable mRunnable = new Runnable() {
        public void run() {
            long txBytes = TrafficStats.getUidTxBytes(mAppUID) - mStartTX;
            long rxBytes = TrafficStats.getUidRxBytes(mAppUID) - mStartRX;
            long txBps = txBytes - mPreviousTX;
            long rxBps = rxBytes - mPreviousRX;
            mPreviousTX = txBytes;
            mPreviousRX = rxBytes;
            if (mCallback != null){
                mCallback.onData(txBytes, rxBytes);
                mCallback.onDataRate(txBps, rxBps);
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    public NetworkMonitor2(Callback callback){
        mCallback = callback;
    }

    public void start(Socket socket) throws SocketException {
        mPreviousRX = 0L;
        mPreviousTX = 0L;
        mStartTX = TrafficStats.getUidTxBytes(mAppUID);
        mStartRX = TrafficStats.getUidRxBytes(mAppUID);
        if (mStartTX == TrafficStats.UNSUPPORTED || mStartTX == TrafficStats.UNSUPPORTED){
            if (mCallback != null){
                mCallback.onUnsupportedTrafficStats();
            }
            return;
        }
        mRunning = true;
        mHandler.postDelayed(mRunnable, 1000);
        if (VERBOSE) Log.d(TAG, "Started");
    }

    public void stop() throws SocketException {
        mHandler.removeCallbacks(mRunnable);
        mRunning = false;
        if (VERBOSE) Log.d(TAG, "Stopped");
    }

    public boolean isRunning(){
        return mRunning;
    }


    private long mExpectedSentBytes = 0;
    public void notifyExpectedSentBytes(final int sentBytes){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mExpectedSentBytes += sentBytes;
                long d = (TrafficStats.getUidTxBytes(mAppUID) - mStartTX);
                Log.d(TAG, "expected: "+mExpectedSentBytes+" ; effectively sent: "+d+" diff="+(mExpectedSentBytes-d));
            }
        });
    }

}

package it.polito.mad.streamsender.ui;

import android.content.Context;
import android.content.Intent;
import android.net.TrafficStats;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.net.Socket;
import java.net.SocketException;

import it.polito.mad.streamsender.NetworkMonitor2;
import it.polito.mad.streamsender.R;
import it.polito.mad.streamsender.Util;
import it.polito.mad.streamsender.encoding.StreamSenderJNI;

public class MainActivity extends AppCompatActivity {

    private ViewPagerAdapter mAdapter;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewPager viewPager = (ViewPager) findViewById(R.id.view_pager);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());
        mAdapter.addFrag(new PreviewFragment(),"tab1");
        mAdapter.addFrag(new Page2Fragment(), "tab2");
        viewPager.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "My wakelook");
        wakeLock.acquire();
    }

    @Override
    protected void onPause() {
        /*try{
            mNetMonitor.stop();
        }
        catch (SocketException e){
            Log.e("ACT", e.getMessage());
        }*/

        wakeLock.release();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

/*
    private NetworkMonitor2 mNetMonitor = new NetworkMonitor2(new NetworkMonitor2.Callback() {
        @Override
        public void onData(long txBytes, long rxBytes) {
            Log.d("ACT", txBytes + " B TX");
            //Log.d("ACT", rxBytes + " B RX");
        }
        @Override
        public void onDataRate(long txBps, long rxBps) {
            double txKbps = ((double) txBps)  //bytes per second
                    * 8.0                       //bits per second
                    / 1000.0;                   //Kbits per second
            double rxKbps = ((double) rxBps)  //bytes per second
                    * 8.0                       //bits per second
                    / 1000.0;                   //Kbits per second
            Log.d("ACT", txKbps + " Kbps TX");
            Log.d("ACT", rxKbps + " Kbps RX");
        }
        @Override
        public void onUnsupportedTrafficStats() {
            Log.d("ACT", "UNSUPPORTED");
        }
    });
*/
}

package it.polito.mad.streamsender.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import it.polito.mad.streamsender.NetworkMonitor;
import it.polito.mad.streamsender.R;

/**
 * Created by luigi on 08/03/16.
 */
public class Page2Fragment extends Fragment {

    public static Page2Fragment newInstance(){
        Page2Fragment fragment = new Page2Fragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    private NetworkMonitor mNetMonitor = new NetworkMonitor(new NetworkMonitor.Callback() {
        @Override
        public void onData(long txBytes, long rxBytes) {
            txTotal.setText(txBytes + " B");
            rxTotal.setText(rxBytes + " B");
        }
        @Override
        public void onDataRate(long txBps, long rxBps) {
            txRate.setText(txBps + " Bps");
            rxRate.setText(rxBps + " Bps");
        }
        @Override
        public void onUnsupportedTrafficStats() {
            txTotal.setText("UNSUPPORTED");
            rxTotal.setText("UNSUPPORTED");
            rxRate.setText("UNSUPPORTED");
            txRate.setText("UNSUPPORTED");
            Toast.makeText(getContext(), "Unsupproted TraficStats", Toast.LENGTH_SHORT).show();
        }
    });

    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener mPreferencesChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(getString(R.string.pref_key_net_monitor))) {
                boolean b = sharedPreferences.getBoolean(key, true);
                if (b) {
                    mNetMonitor.start();
                } else {
                    mNetMonitor.stop();
                    txTotal.setText("DISABLED");
                    rxTotal.setText("DISABLED");
                    rxRate.setText("DISABLED");
                    txRate.setText("DISABLED");
                }
            }
        }
    };

    private TextView txRate, rxRate, txTotal, rxTotal;

    public Page2Fragment(){}

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_page2, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        txRate = (TextView) view.findViewById(R.id.tx_rate_text);
        rxRate = (TextView) view.findViewById(R.id.rx_rate_text);
        txTotal = (TextView) view.findViewById(R.id.tx_total_text);
        rxTotal = (TextView) view.findViewById(R.id.rx_total_text);

        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefs.registerOnSharedPreferenceChangeListener(mPreferencesChangeListener);
        mPreferencesChangeListener.onSharedPreferenceChanged(prefs, getString(R.string.pref_key_net_monitor));
    }

    @Override
    public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(mPreferencesChangeListener);
        super.onDestroy();
    }
}

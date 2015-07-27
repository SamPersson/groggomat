package org.altekamereren.groggomat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import org.jetbrains.anko.toast

/**
 * A BroadcastReceiver that notifies of important Wi-Fi p2p events.
 */
public class WiFiDirectBroadcastReceiver(val manager : WifiP2pManager, val channel : WifiP2pManager.Channel, val activity : MainActivity) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                activity.toast("Wifi P2P is enabled")
            } else {
                activity.toast("Wifi P2P is disabled")
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            manager.requestPeers(channel, { peers ->
                activity.peersReceived(peers.getDeviceList().toArrayList())
            })
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            manager.requestConnectionInfo(channel, { info ->
                if(info != null) {
                    activity.connected(info)
                }
            })
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            // Respond to this device's wifi state changing
        }
    }
}
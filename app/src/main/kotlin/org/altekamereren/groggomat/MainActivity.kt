package org.altekamereren.groggomat

import android.app.Activity
import android.app.DialogFragment
import android.app.ListActivity
import android.app.ListFragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.DataSetObserver
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.p2p.*
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import android.support.v7.appcompat
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import org.jetbrains.anko.*
import com.fasterxml.jackson.module.kotlin.*

import kotlinx.android.synthetic.activity_main.*
import org.jetbrains.anko.db.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.Future

val PORT = 14156

data class DeviceLatestKryss(val device:String, val latestKryss:Long)
data class KryssPair(val my:DeviceLatestKryss, val their:DeviceLatestKryss?)
data class SendKryss(val id:Long?, val device:String, val type:Int, val count:Int, val time:Long, val kamerer:Int, val replaces_id:Long?, val replaces_device:String?) {
    companion object {
        val table = "Kryss k"
        val selectList = arrayOf("ifnull(real_id, _id)", "device", "type", "count", "time", "kamerer", "replaces_id", "replaces_device")
        val parser = rowParser { id:Long, device:String, type:Int, count:Int, time:Long, kamerer:Int, replaces_id:Any?, replaces_device:Any? ->
            SendKryss(id, device, type, count, time, kamerer, if(replaces_id is Long) replaces_id else null, if(replaces_device is String) replaces_device else null)
        }
    }
    public fun insert(db: SQLiteDatabase) {
        if(id != null) {
            if (replaces_id != null && replaces_device != null) {
                db.insert("Kryss",
                        "real_id" to id,
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replaces_id,
                        "replaces_device" to replaces_device
                )
            } else {
                db.insert("Kryss",
                        "real_id" to id,
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer
                )
            }
        } else {
            if (replaces_id != null && replaces_device != null) {
                db.insert("Kryss",
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replaces_id,
                        "replaces_device" to replaces_device
                )
            } else {
                db.insert("Kryss",
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer
                )
            }
        }
    }
}

public class MainActivity : Activity(), AnkoLogger {
    var intentFilter : IntentFilter = IntentFilter()
    var receiver: WiFiDirectBroadcastReceiver? = null
    var deviceId: String = ""

    data class Kryss(var kamerer:Int, var type:Int, var count:Int)


    override fun onCreate(savedInstanceState: Bundle?) {
        super<Activity>.onCreate(savedInstanceState)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId","")
        if(deviceId == "") {
            deviceId = UUID.randomUUID().toString()
            val editor = prefs.edit()
            editor.putString("deviceId", deviceId)
            editor.commit()
        }

        calculateKryss()

        intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, getMainLooper(), null)
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, KamererListFragment())
                .commit()

        startServer()
    }

    private fun calculateKryss() {
        database.use {
            val kryssParser = rowParser { kamerer: Int, type: Int, count: Int -> Kryss(kamerer, type, count) }
            for (kryss in select("Kryss k", "kamerer", "type", "sum(count)").where("not exists (select * from Kryss r where r.replaces_id = ifnull(k.real_id, k._id) and r.replaces_device = k.device)").groupBy("kamerer, type").parseList(kryssParser)) {
                Kamerer.kamerers[kryss.kamerer].kryss[kryss.type] = kryss.count
            }
        }
    }

    override fun onDestroy() {
        super<Activity>.onDestroy()
        serverTask?.cancel(true)
    }

    override fun onResume() {
        super<Activity>.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super<Activity>.onPause()
        unregisterReceiver(receiver)
    }

    fun syncKryss(outputStream:OutputStream, inputStream:InputStream) {
        database.use {
            val myLatestKryss =
                    select("Kryss", "device", "ifnull(max(real_id)", "max(_id))")
                    .groupBy("device")
                    .parseList(rowParser({device:String, id:Long -> DeviceLatestKryss(device, id)}))

            val mapper = jacksonObjectMapper()
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)

            info("Sending ${myLatestKryss}")
            mapper.writeValue(outputStream, myLatestKryss)
            outputStream.flush()

            val jsonParser = mapper.getFactory().createParser(inputStream)
            val theirLatestKryss = jsonParser.readValueAs<List<DeviceLatestKryss>>(object: TypeReference<List<DeviceLatestKryss>>(){})
            info("Received ${theirLatestKryss}")

            val kryssPairs = myLatestKryss.map { my -> KryssPair(my,theirLatestKryss.firstOrNull({their -> their.device == my.device}) ) }

            val newerKryss = kryssPairs.filter { p -> p.their == null || p.my.latestKryss > p.their.latestKryss }.map { p -> DeviceLatestKryss(p.my.device, p.their?.latestKryss ?: 0) }

            val kryssToSend = newerKryss.flatMap { newer ->
                select(SendKryss.table, *SendKryss.selectList)
                        .where("device = {device} and ifnull(real_id, _id) > {latestKryss}",
                                "device" to newer.device,
                                "latestKryss" to newer.latestKryss)
                        .parseList(SendKryss.parser)
            }

            info("Sending ${kryssToSend.size()} kryss")
            mapper.writeValue(outputStream, kryssToSend)

            val theirKryss = jsonParser.readValueAs<List<SendKryss>>(object: TypeReference<List<SendKryss>>(){})
            info("Received ${theirKryss.size()} kryss")
            for(kryss in theirKryss) {
                kryss.insert(this)
            }
            info("Sync complete")
        }
    }

    private var serverTask: Future<Unit>? = null

    fun startServer() {
        serverTask = async {
            ServerSocket(PORT).use { serverSocket ->
                info("Server listening on port $PORT")
                while (serverTask?.isCancelled() == false) {
                    try {
                        serverSocket.accept().use { client ->
                            client.setSoTimeout(1000)

                            info("Client connected, syncing")
                            syncKryss(client.getOutputStream(), client.getInputStream())
                        }
                        uiThread {
                            toast("Sync done as server")
                            calculateKryss()
                            updateKryssLists()
                        }
                    } catch(e: Exception) {
                        if(e !is InterruptedException){
                            error(e.toString())
                            uiThread {
                                toast("Error syncing: $e")
                            }
                        }
                    }
                }
            }
        }
    }

    fun startClient(host:String) {
        async {
            try{
                info("Starting Client")
                Socket().use { socket ->
                    socket.bind(null)
                    socket.connect(InetSocketAddress(host, PORT), 500)
                    socket.setSoTimeout(1000)
                    info("Connected to server")
                    syncKryss(socket.getOutputStream(), socket.getInputStream())
                }
                uiThread {
                    toast("Sync done as client")
                    calculateKryss()
                    updateKryssLists()
                }
            } catch(e:Exception) {
                error(e.toString())
                uiThread {
                    toast("Error syncing: $e")
                }
            }

        }
    }

    private var menu: Menu? = null

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        this.menu = menu
        updateMenu()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item!!.getItemId()

        val wifiP2pInfo = wifiP2pInfo
        val receiver = receiver
        if(id == R.id.action_connect && receiver != null) {
            if (wifiP2pInfo?.groupFormed == true) {
                //Disconnect
                receiver.manager.removeGroup(receiver.channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                    }

                    override fun onFailure(reason: Int) {
                        toast("Failed to disconenct: $reason")
                    }
                })
            } else {
                searchingForPeers = true
                toast("Searching for peers")
                receiver.manager.discoverPeers(receiver.channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                    }

                    override fun onFailure(reason: Int) {
                        searchingForPeers = false
                        toast("Failed to discover peers: $reason")
                    }
                })
            }
        }

        if (id == R.id.action_sync && wifiP2pInfo != null) {
            startClient(wifiP2pInfo.groupOwnerAddress.getHostAddress())
            return true
        }

        return super<Activity>.onOptionsItemSelected(item)
    }

    private fun connectToDevice(d: WifiP2pDevice) {
        val config = WifiP2pConfig();
        config.deviceAddress = d.deviceAddress;
        receiver?.manager?.connect(receiver?.channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                toast("Connected to ${d.deviceAddress}")
            }

            override fun onFailure(reason: Int) {
                toast("Failed to connect: $reason")
            }
        })
    }

    fun updateKryssLists() {
        calculateKryss()
        val fragment = getFragmentManager().findFragmentById(android.R.id.content)
        if(fragment is KamererListFragment) {
            (fragment.getListAdapter() as ArrayAdapter<*>).notifyDataSetChanged()
        } else if(fragment is KamererFragment) {
            fragment.updateData()
        }
    }

    private var searchingForPeers: Boolean = false

    fun peersReceived(peers: List<WifiP2pDevice>) {
        if(searchingForPeers) {
            searchingForPeers = false
            if (peers.size() >= 0) {
                selector("Which peer?", peers.map { d -> d.deviceName }.toArrayList(), { i ->
                    connectToDevice(peers[i])
                })
            } else {
                toast("No peers found")
            }
        }
    }

    private var wifiP2pInfo: WifiP2pInfo? = null
    fun connected(info: WifiP2pInfo) {
        this.wifiP2pInfo = info
        updateMenu()
        if(info.groupFormed){
            toast("Connected")
        } else {
            toast("Disconnected")
        }
        if(info.groupFormed && !info.isGroupOwner) {
            startClient(info.groupOwnerAddress.getHostAddress())
        }
    }

    fun updateMenu() {
        val wifiP2pInfo = wifiP2pInfo
        if(wifiP2pInfo != null) {
            menu?.findItem(R.id.action_connect)?.setTitle(if (wifiP2pInfo.groupFormed) "Koppla från" else "Anslut")
            menu?.findItem(R.id.action_sync)?.setVisible(wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner)
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                menu?.findItem(R.id.action_sync)?.setTitle("Synka ${wifiP2pInfo.groupOwnerAddress.getHostAddress()}")
            }
        }
    }
}

data class KryssType(val name:String, val color:Int, val alcohol:Double) {
    companion object {
        val types = arrayOf(
                KryssType("Svag", 0xff8e9103.toInt(), 0.2),
                KryssType("Vanlig", 0xff906500.toInt(), 0.4),
                KryssType("Delüx", 0xff051e76.toInt(), 0.4),
                KryssType("Mat", 0xff962000.toInt(), 0.0)
        )
    }
}

class Kamerer(val name:String) {
    companion object {
        val kamerers = arrayOf("Felicia Ardenmark Strand", "Jules Hanley", "Jesper Hasselquist", "Damir Basic Knezevic", "Douglas Clifford", "Jens Ogniewski", "Johan Levin", "Philip Jönsson", "Annica Ericsson", "Hanna Ekström", "Peder Andersson", "Peter Swartling", "Johan Ruuskanen", "Pontus Persson", "Sam Persson", "Gustaf Malmberg", "Oskar Fransén", "Philip Ljungkvist", "Tobias Petersen", "Viktoria Alm", "Rebecka Erntell", "Christine Persson", "Joakim Arnsby", "Lukas Arnsby", "Olov Ferm")
                .sortBy({n -> n})
                .map({n -> Kamerer(n)})
    }

    var alcohol = 0.0
    val kryss = Array(KryssType.types.size(), { i -> 0 })
    val weight = 60
    val man = true
}


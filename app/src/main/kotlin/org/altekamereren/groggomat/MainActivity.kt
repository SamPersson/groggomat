package org.altekamereren.groggomat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.LongSparseArray
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.interceptors.LogRequestAsCurlInterceptor
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.coroutines.awaitString
import jxl.Workbook
import jxl.WorkbookSettings
import jxl.write.DateTime
import jxl.write.Label
import jxl.write.Number
import kotlinx.coroutines.*
import org.jetbrains.anko.*
import org.jetbrains.anko.db.insert
import org.jetbrains.anko.db.rowParser
import org.jetbrains.anko.db.select
import org.jetbrains.anko.db.update
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import kotlin.math.max

const val PORT = 14156

data class DeviceLatestKryss(val device:String, val latestKryss:Long)
data class KryssPair(val my:DeviceLatestKryss?, val their:DeviceLatestKryss?)
data class Kryss(val id:Long?, val device:String, val type:Int, val count:Int, val time:Long, val kamerer:Long, val replacesId:Long?, val replacesDevice:String?) {
    var alcohol:Double = 0.0

    companion object {
        const val table = "Kryss k"
        val selectList = arrayOf("ifnull(real_id, _id)", "device", "type", "count", "time", "kamerer", "replaces_id", "replaces_device")
        val parser = rowParser { id:Long, device:String, type:Int, count:Int, time:Long, kamerer:Long, replacesId:Any?, replacesDevice:Any? ->
            Kryss(id, device, type, count, time, kamerer, replacesId as? Long, replacesDevice as? String)
        }
    }

    fun insert(db: SQLiteDatabase) : Kryss {
        if(id != null) {
            if (replacesId != null && replacesDevice != null) {
                db.insert("Kryss",
                        "real_id" to id,
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replacesId,
                        "replaces_device" to replacesDevice
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
            return this
        } else {
            val id = if (replacesId != null && replacesDevice != null) {
                db.insert("Kryss",
                        "device" to device,
                        "type" to type,
                        "count" to count,
                        "time" to time,
                        "kamerer" to kamerer,
                        "replaces_id" to replacesId,
                        "replaces_device" to replacesDevice
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
            return Kryss(id, device, type, count, time, kamerer, replacesId, replacesDevice)
        }
    }
}

class Kamerer(val id: Long, val name: String, var weight: Double?, val male: Boolean, var updated: Long) {
    var alcohol:Double = 0.0
    val kryss = Array(KryssType.types.size) { 0 }
    fun update(db: SQLiteDatabase) {
        val weight = weight
        if(weight != null) {
            db.update("Kamerer",
                    "name" to name,
                    "weight" to weight,
                    "male" to male,
                    "updated" to updated)
                    .whereArgs("_id = {id}", "id" to id)
                    .exec()
        } else {
            db.update("Kamerer",
                    "name" to name,
                    "male" to if(male) 1 else 0,
                    "updated" to updated)
                    .whereArgs("_id = {id}", "id" to id)
                    .exec()
        }
    }

    fun alcoholDissipation(initialAlcohol:Double, dt:Long) : Double {
        return max(0.0, initialAlcohol - (if (male) 0.15 else 0.17) * dt / 3600000)
    }

    fun bloodAlcoholIncreaseAfterDrink(drinkAlcohol:Double): Double? {
        return weight?.let { weight ->
            0.806 * drinkAlcohol * 25.0 * 1.2  / ((if (male) 0.58 else 0.49) * weight)
        }
    }
}


class MainActivity : FragmentActivity(), CoroutineScope by MainScope(), AnkoLogger {
    private var intentFilter : IntentFilter = IntentFilter()
    private var receiver: WiFiDirectBroadcastReceiver? = null
    var deviceId: String = ""

    private val SERVER = "https://groggoserver.azurewebsites.net"
    private val TAG = "2019" // Also update app name
    private val VERSION = "groggomat $TAG"

    private var updater: ScheduledFuture<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        deviceId = prefs.getString("deviceId","") ?: ""
        if(deviceId == "") {
            deviceId = UUID.randomUUID().toString()
            val editor = prefs.edit()
            editor.putString("deviceId", deviceId)
            editor.apply()
        }

        loadKamererer()
        loadKryss()

//        intentFilter = IntentFilter()
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
//        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
//
//        val manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
//        val channel = manager.initialize(this, mainLooper, null)
//        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, KamererListFragment())
                .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel() // CoroutineScope.cancel
    }

    data class IdAndDevice(val id:Long, val device:String)

    var kryssCache:MutableList<Kryss> = ArrayList()

    private fun loadKryss() {
        database.use {
            info("Loading kryss")
            val time = System.currentTimeMillis()
            val rows = select("Kryss k", *Kryss.selectList).parseList(Kryss.parser)
            val replacedRows = HashMap<IdAndDevice, Kryss>()
            for(row in rows) {
                if(row.replacesId != null && row.replacesDevice != null) {
                    val replaces = IdAndDevice(row.replacesId, row.replacesDevice)
                    if(replacedRows.containsKey(replaces)) {
                        // Pick one device to win
                        if(row.device > replacedRows[replaces]!!.device) {
                            replacedRows[replaces] = row
                        }
                    } else {
                        replacedRows[replaces] = row
                    }
                }
            }

            // Skip replacing rows that were not picked, then filter replaced rows.
            kryssCache = (rows.filter { r -> r.replacesId == null} + replacedRows.values).filter { r -> !replacedRows.containsKey(IdAndDevice(r.id as Long, r.device))}.toMutableList()
            info("Time to load kryss: ${System.currentTimeMillis() - time}")
        }
        calculateKryss()
    }

    private fun calculateKryss() {
        database.use {
            for(kamerer in kamererer.values) {
                for(i in kamerer.kryss.indices) {
                    kamerer.kryss[i] = 0
                }
                kamerer.alcohol = 0.0
            }

            info("Calculating kryss")
            val time = System.currentTimeMillis()
            for(kamererKryss in kryssCache.groupBy { it.kamerer }) {
                val kamerer = kamererer[kamererKryss.key]!!
                var alcohol = 0.0
                var lastTime = 0L
                for(kryss in kamererKryss.value.sortedBy { it.time }) {
                    kamerer.kryss[kryss.type] += kryss.count
                    if (kamerer.weight != null) {
                        val kryssType = KryssType.types[kryss.type]
                        val remainingAlcohol = kamerer.alcoholDissipation(alcohol, kryss.time - lastTime)
                        val newAlcohol = remainingAlcohol + (kamerer.bloodAlcoholIncreaseAfterDrink(kryssType.alcohol*kryss.count) ?: 0.0)
                        alcohol = newAlcohol
                        kryss.alcohol = alcohol
                    }
                    lastTime = kryss.time
                }
                if(kamerer.weight != null) {
                    kamerer.alcohol = kamerer.alcoholDissipation(alcohol, time - lastTime)
                }
            }
            info("Time to calc kryss: ${System.currentTimeMillis() - time}")
        }
    }

    val kamererer = LongSparseArray<Kamerer>()
    private fun loadKamererer() {
        kamererer.clear()
        database.use {
            val kamererParser = rowParser { id: Long, name: String, weight: Any?, male: Long, updated:Long -> Kamerer(id, name, if(weight is Double) weight else null, male > 0, updated) }
            for (kamerer in select("Kamerer", "_id", "name", "weight", "male", "updated").parseList(kamererParser)) {
                kamererer.put(kamerer.id, kamerer)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startServer()
    }

    override fun onStop() {
        super.onStop()
        serverTask?.cancel(true)
    }

    override fun onResume() {
        super.onResume()
        if (receiver != null) registerReceiver(receiver, intentFilter)
        val sch = Executors.newScheduledThreadPool(1) as ScheduledThreadPoolExecutor
        updater = sch.scheduleAtFixedRate({ doAsync { uiThread { updateKryssLists(false) }} }, 60, 60, TimeUnit.SECONDS)
    }

    override fun onPause() {
        super.onPause()
        if (receiver != null) unregisterReceiver(receiver)
        updater?.cancel(true)
    }

    private val syncing = Semaphore(1, true)

    private fun syncKryss(outputStream:OutputStream, inputStream:InputStream) {
        syncing.acquire()
        try {
            database.use {
                val myLatestKryss =
                        select("Kryss", "device", "max(ifnull(real_id, _id))")
                                .groupBy("device")
                                .parseList(rowParser { device: String, id: Long -> DeviceLatestKryss(device, id) })

                val mapper = jacksonObjectMapper()
                mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
                mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE

                val protocol = VERSION
                mapper.writeValue(outputStream, protocol)
                outputStream.flush()
                val theirProtocol = mapper.readValue<String>(inputStream)
                if (protocol != theirProtocol) {
                    throw Exception("Unknown protocol: $theirProtocol")
                }

                info("Sending $myLatestKryss")
                mapper.writeValue(outputStream, myLatestKryss)
                outputStream.flush()

                val jsonParser = mapper.factory.createParser(inputStream)
                val theirLatestKryss = jsonParser.readValueAs<List<DeviceLatestKryss>>(object : TypeReference<List<DeviceLatestKryss>>() {})
                info("Received $theirLatestKryss")

                val kryssPairs = myLatestKryss.map { my -> KryssPair(my, theirLatestKryss.firstOrNull { their -> their.device == my.device }) }

                val newerKryss = kryssPairs.filter { p -> p.their == null || p.my!!.latestKryss > p.their.latestKryss }.map { p -> DeviceLatestKryss(p.my!!.device, p.their?.latestKryss ?: 0) }

                val kryssToSend = newerKryss.flatMap { newer ->
                    select(Kryss.table, *Kryss.selectList)
                            .whereArgs("device = {device} and ifnull(real_id, _id) > {latestKryss}",
                                    "device" to newer.device,
                                    "latestKryss" to newer.latestKryss)
                            .parseList(Kryss.parser)
                }

                info("Sending ${kryssToSend.size} kryss")
                mapper.writeValue(outputStream, kryssToSend)
                outputStream.flush()

                val theirKryss = jsonParser.readValueAs<List<Kryss>>(object : TypeReference<List<Kryss>>() {})
                info("Received ${theirKryss.size} kryss")

                info("Sending ${kamererer.size()} kamererer")
                mapper.writeValue(outputStream, kamererer.values.toList())
                outputStream.flush()

                val theirKamererer = jsonParser.readValueAs<List<Kamerer>>(object : TypeReference<List<Kamerer>>() {})
                info("Received ${theirKamererer.size} kamererer")

                info("Storing updates")

                for (kryss in theirKryss) {
                    kryss.insert(this)
                }
                for (kamerer in theirKamererer) {
                    val myKamerer = kamererer[kamerer.id]
                    if (kamerer.updated > myKamerer.updated) {
                        myKamerer.weight = kamerer.weight
                        myKamerer.updated = kamerer.updated
                        myKamerer.update(this)
                    }
                }

                info("Sync complete")
            }
        } finally {
            syncing.release()
        }
    }

    val mapper: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            .configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false)

    private inline fun <reified T : Any> jacksonDeserializerOf() = object : ResponseDeserializable<T> {
        override fun deserialize(reader: Reader): T? {
            return mapper.readValue(reader)
        }

        override fun deserialize(content: String): T? {
            return mapper.readValue(content)
        }

        override fun deserialize(bytes: ByteArray): T? {
            return mapper.readValue(bytes)
        }

        override fun deserialize(inputStream: InputStream): T? {
            return mapper.readValue(inputStream)
        }
    }

    private suspend fun syncOnlineServer() {
        val manager = FuelManager()
        manager.addRequestInterceptor(LogRequestAsCurlInterceptor)

        syncing.acquire()
        try {
            val myLatestKryss = database.use {
                select("Kryss", "device", "max(ifnull(real_id, _id))")
                        .groupBy("device")
                        .parseList(rowParser { device: String, id: Long -> DeviceLatestKryss(device, id) })
            }

            val theirLatestKryss = Fuel.get("$SERVER/$TAG/devices").awaitObject(jacksonDeserializerOf<List<DeviceLatestKryss>>())
            info("Received $theirLatestKryss")

            val kryssPairs = (myLatestKryss.map { my -> KryssPair(my, theirLatestKryss.firstOrNull { their -> their.device == my.device }) }
                    + theirLatestKryss.map { their -> KryssPair(myLatestKryss.firstOrNull { my -> their.device == my.device }, their) }.filter { it.my == null })

            val newerKryss = kryssPairs
                    .filter { p -> p.my != null && (p.their == null || p.my.latestKryss > p.their.latestKryss) }
                    .map { p -> DeviceLatestKryss(p.my!!.device, p.their?.latestKryss ?: 0) }

            val olderKryss = kryssPairs
                    .filter { p -> p.their != null && (p.my == null || p.my.latestKryss < p.their.latestKryss) }
                    .map { p -> DeviceLatestKryss(p.their!!.device, p.my?.latestKryss ?: 0) }

            for (older in olderKryss) {
                val theirKryss = Fuel.get("$SERVER/$TAG/devices/${older.device}/kryss?newerThan=${older.latestKryss}").awaitObject(jacksonDeserializerOf<List<Kryss>>())
                info("Received ${theirKryss.size} kryss for device: ${older.device}")
                for (kryss in theirKryss) {
                    database.use {
                        kryss.insert(this)
                    }
                }
            }

            val kryssToSend = database.use {
                newerKryss.flatMap { newer ->
                    select(Kryss.table, *Kryss.selectList)
                            .whereArgs("device = {device} and ifnull(real_id, _id) > {latestKryss}",
                                    "device" to newer.device,
                                    "latestKryss" to newer.latestKryss)
                            .parseList(Kryss.parser)
                }
            }

            if (kryssToSend.any()) {
                info("Sending ${kryssToSend.size} kryss")
                val body = mapper.writeValueAsString(kryssToSend)
                val request = Fuel.put("$SERVER/$TAG/kryss").body(body)
                request.headers["Content-Type"] = "application/json"
                request.awaitString()
            }

            val theirKamererer = Fuel.get("$SERVER/$TAG/kamererer").awaitObject(jacksonDeserializerOf<List<Kamerer>>())
            info("Received ${theirKamererer.size} kamererer")

            for (kamerer in theirKamererer) {
                val myKamerer = kamererer[kamerer.id]
                if (kamerer.updated > myKamerer.updated) {
                    myKamerer.weight = kamerer.weight
                    myKamerer.updated = kamerer.updated
                    database.use {
                        myKamerer.update(this)
                    }
                }
            }

            for (myKamerer in kamererer.values) {
                if (!theirKamererer.any { it.id == myKamerer.id && it.updated >= myKamerer.updated}) {
                    val body = mapper.writeValueAsString(myKamerer)
                    val request = Fuel.put("$SERVER/$TAG/kamererer").body(body)
                    request.headers["Content-Type"] = "application/json"
                    request.awaitString()
                }
            }

            info("Sync complete")
        } catch(e: Exception) {
            error("Failed to sync", e)
            withContext(Dispatchers.Main) {
                toast("Sync failed: $e")
            }
            return
        } finally {
            syncing.release()
        }

        withContext(Dispatchers.Main) {
            toast("Sync complete")
            loadKamererer()
            updateKryssLists(true)
        }
    }

    private var serverTask: Future<Unit>? = null

    private fun startServer() {
        serverTask = doAsync {
            ServerSocket(PORT).use { serverSocket ->
                info("Server listening on port $PORT")
                while (serverTask?.isCancelled == false) {
                    try {
                        serverSocket.accept().use { client ->
                            client.soTimeout = 5*1000

                            info("Client connected, syncing")
                            syncKryss(client.outputStream, client.inputStream)
                        }
                        uiThread {
                            toast("Sync done as server")
                            loadKamererer()
                            updateKryssLists(true)
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

    private fun startClient(host:String) {
        doAsync {
            try{
                info("Starting Client")
                Socket().use { socket ->
                    socket.bind(null)
                    socket.connect(InetSocketAddress(host, PORT), 500)
                    socket.soTimeout = 10000
                    info("Connected to server")
                    syncKryss(socket.outputStream, socket.inputStream)
                }
                uiThread {
                    toast("Sync done as client")
                    loadKamererer()
                    updateKryssLists(true)
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
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu
        updateMenu()
        return true
    }

    private val PERMISSIONS_REQUEST_STORAGE: Int = 1

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

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
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1);
                }
                else {
                    receiver.manager.discoverPeers(
                        receiver.channel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                            }

                            override fun onFailure(reason: Int) {
                                searchingForPeers = false
                                toast("Failed to discover peers: $reason")
                            }
                        })
                }
            }

            return true
        }

        if (id == R.id.action_sync && wifiP2pInfo != null) {
            startClient(wifiP2pInfo.groupOwnerAddress.hostAddress)
            return true
        }

        if (id == R.id.action_export) {
            exportData()

            return true
        }

        if(id == R.id.action_stats) {
            val newFragment = StatsFragment()
            supportFragmentManager.beginTransaction().replace(android.R.id.content, newFragment).addToBackStack(null).commit()
            return true
        }

        if(id == R.id.action_sync_online) {
            val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.getNetworkCapabilities(cm.activeNetwork)
            if(activeNetwork?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                toast("Needs internet connection!")
            } else {
                toast("Syncing to online server")
                launch(Dispatchers.IO)  {
                    syncOnlineServer()
                }
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    exportData()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            }
        }

        return super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private val openExportDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // take persistable Uri Permission for future use
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            this.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val preferences = this.getPreferences(Context.MODE_PRIVATE)
            preferences.edit().putString("exportStorageUri", uri.toString()).apply()

            DocumentFile.fromTreeUri(this, uri)?.let {
                exportData(it)
            }

        }
    }

    private fun exportData() {
        val preferences = this.getPreferences(Context.MODE_PRIVATE)
        preferences.getString("exportStorageUri", null)?.let { uri ->
            val documentFile = DocumentFile.fromTreeUri(this, Uri.parse(uri));
            if (documentFile != null && documentFile.canWrite()) {
                exportData(documentFile)
                return
            }
        }

        openExportDir.launch(null);
    }

    private fun exportData(dir: DocumentFile) {
        val date = SimpleDateFormat("yyyyMMdd-hhmmss", Locale.US).format(Date())

        val excelFile = dir.createFile("application/ms-excel", "$date.xls")
        this.contentResolver.openFileDescriptor(excelFile!!.uri, "w")?.use { pfd ->
            FileOutputStream(pfd.fileDescriptor).use { excelOutputStream ->
                val wbSettings = WorkbookSettings()
                wbSettings.locale = Locale("en", "EN")
                val workbook = Workbook.createWorkbook(excelOutputStream, wbSettings)
                val sheet = workbook.createSheet("Kryss", 0)
                val cols = arrayOf("kamerer", "time", "type", "count")
                for (i in cols.indices) {
                    sheet.addCell(Label(i, 0, cols[i]))
                }
                for (i in kryssCache.indices) {
                    val kryss = kryssCache[i]
                    sheet.addCell(Label(0, i + 1, kamererer[kryss.kamerer]!!.name))
                    sheet.addCell(DateTime(1, i + 1, Date(kryss.time)))
                    sheet.addCell(Label(2, i + 1, KryssType.types[kryss.type].name))
                    sheet.addCell(Number(3, i + 1, kryss.count.toDouble()))
                }

                val sheetKamerer = workbook.createSheet("Kamererer", 1)
                val colsKamerer = arrayOf("kamerer", "sex", "weight") + KryssType.types.map { (name) -> name }
                for (i in colsKamerer.indices) {
                    sheetKamerer.addCell(Label(i, 0, colsKamerer[i]))
                }

                var row = 0
                for (kamerer in kamererer.values.sortedBy { it.name }) {
                    row++
                    sheetKamerer.addCell(Label(0, row, kamerer.name))
                    sheetKamerer.addCell(Label(1, row, if (kamerer.male) "man" else "woman"))
                    val w = kamerer.weight
                    if (w != null) {
                        sheetKamerer.addCell(Number(2, row, w))
                    }
                    for (i in KryssType.types.indices) {
                        sheetKamerer.addCell(Number(3 + i, row, kamerer.kryss[i].toDouble()))
                    }
                }

                workbook.write()
                workbook.close()

                toast("Wrote data to ${excelFile.name}")
            }
        }

        val csvFile = dir.createFile("text/csv", "$date-raw.csv")
        this.contentResolver.openFileDescriptor(csvFile!!.uri, "w")?.use { pfd ->
            OutputStreamWriter(FileOutputStream(pfd.fileDescriptor)).use { writer ->
                writer.write("id, device, type, count, time, kamerer, replaces_id, replaces_device\n")
                database.use {
                    for (kryss in select("Kryss", *Kryss.selectList).parseList(Kryss.parser)) {
                        writer.write("${kryss.id}, ${kryss.device}, ${kryss.type}, ${kryss.count}, ${kryss.time}, ${kryss.kamerer}, ${kryss.replacesId}, ${kryss.replacesDevice}\n")
                    }
                }
            }
        }

        /*val readableFile = File(dir, "$date-readable.csv")
        OutputStreamWriter(FileOutputStream(File(dir, "$date-readable.csv"))).use { writer ->
            writer.write("kamerer, time, type, count\n")
            database.use {
                for (kryss in select("Kryss k", *SmallKryss.selectList).where("not exists (select * from Kryss r where r.replacesId = ifnull(k.real_id, k._id) and r.replacesDevice = k.device)").parseList(SmallKryss.parser)) {
                    writer.write("${kamererer[kryss.kamerer].name}, ${Date(kryss.time)}, ${KryssType.types[kryss.type].name}, ${kryss.count}\n")
                }
            }
        }*/
    }

    private fun connectToDevice(d: WifiP2pDevice) {
        val config = WifiP2pConfig()
        config.deviceAddress = d.deviceAddress
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1);
            return
        }

        receiver?.manager?.connect(receiver?.channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                toast("Connected to ${d.deviceAddress}")
            }

            override fun onFailure(reason: Int) {
                toast("Failed to connect: $reason")
            }
        })
    }

    fun updateKryssLists(reload:Boolean) {
        if(reload)
            loadKryss()
        else
            calculateKryss()

        val fragment = supportFragmentManager.findFragmentById(android.R.id.content)
        info("updateKryssLists: $reload, $fragment")
        if(fragment is KamererListFragment) {
            fragment.updateData()
        } else if(fragment is KamererFragment) {
            fragment.updateData()
        }
    }

    private var searchingForPeers: Boolean = false

    fun peersReceived(peers: List<WifiP2pDevice>) {
        if(searchingForPeers) {
            searchingForPeers = false
            if (peers.size >= 0) {
                selector("Which peer?", peers.map { d -> d.deviceName }.toList()) { _, i ->
                    connectToDevice(peers[i])
                }
            } else {
                toast("No peers found")
            }
        }
    }

    private var wifiP2pInfo: WifiP2pInfo? = null
    fun connected(info: WifiP2pInfo) {
        if(info.groupFormed){
            toast("Connected")
            if(!info.isGroupOwner) {
                if(syncing.availablePermits() > 0)
                    startClient(info.groupOwnerAddress.hostAddress)
            }
        } else if(wifiP2pInfo?.groupFormed == true) {
            toast("Disconnected")
        }
        this.wifiP2pInfo = info
        updateMenu()
    }

    private fun updateMenu() {
        val wifiP2pInfo = wifiP2pInfo
        if(wifiP2pInfo != null) {
            menu?.findItem(R.id.action_connect)?.isVisible = true
            menu?.findItem(R.id.action_connect)?.title = if (wifiP2pInfo.groupFormed) "Koppla från" else "Anslut"
            menu?.findItem(R.id.action_sync)?.isVisible = wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                menu?.findItem(R.id.action_sync)?.title = "Synka ${wifiP2pInfo.groupOwnerAddress.hostAddress}"
            }
        }
        else {
            menu?.findItem(R.id.action_connect)?.isVisible = false
        }
    }
}

data class KryssType(val name:String, val description:String, val color:Int, val alcohol:Double) {
    companion object {
        val types = arrayOf(
                KryssType("Svag", "4 cl 17% sprit", 0xff8e9103.toInt(), 0.17),
                KryssType("Vanlig", "4 cl 40% sprit\n1 burk öl/cider\n~2 dl vin", 0xff906500.toInt(), 0.4),
                KryssType("Fin", "4 cl 40% finsprit", 0xff051e76.toInt(), 0.4),
                KryssType("Övrigt", "Kryss utan sprit", 0xff962000.toInt(), 0.0)
        )
    }
}

val <E> LongSparseArray<E>.values: Sequence<E>
    get() {
        val t = this
        return sequence {
            val size = t.size()
            for (i in 0 until size) {
                if (size != t.size()) throw ConcurrentModificationException()
                yield(t.valueAt(i))
            }
        }
    }

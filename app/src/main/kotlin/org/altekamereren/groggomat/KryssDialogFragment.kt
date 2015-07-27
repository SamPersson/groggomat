package org.altekamereren.groggomat

import android.app.DialogFragment
import android.app.Fragment
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.support.v7.appcompat

import kotlinx.android.synthetic.kryss_dialog.view.*
import org.jetbrains.anko.ctx
import org.jetbrains.anko.db.ManagedSQLiteOpenHelper
import org.jetbrains.anko.db.select
import org.jetbrains.anko.enabled

public class KryssDialogFragment() : DialogFragment() {
    val kryss = Array(KryssType.types.size(), {i->0})

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.kryss_dialog, container, false)
        val kamererId = getArguments().getInt("kamerer")
        val kamerer = Kamerer.kamerers[kamererId]
        val replaces_id:Long? = if(getArguments().getLong("replaces_id", -1L) == -1L) null else getArguments().getLong("replaces_id")
        val replaces_device:String? = getArguments().getString("replaces_device")
        var replaceKryss:SendKryss? = null

        val buttons = arrayOf(v.weak, v.strong, v.delux, v.food)

        if(replaces_id != null && replaces_device != null) {
            replaceKryss = database.use {
                select(SendKryss.table, *SendKryss.selectList)
                    .where("ifnull(real_id, _id) = {replaces_id} and device = {replaces_device}", "replaces_id" to replaces_id, "replaces_device" to replaces_device)
                    .parseOpt(SendKryss.parser)
            }
            if(replaceKryss != null) {
                for (i in buttons.indices) {
                    if (i != replaceKryss.type) buttons[i].enabled = false
                    else kryss[i] = replaceKryss.count
                }
            }
        }

        v.title.setText("Kryssa för ${kamerer.name}")
        v.kryssa.setOnClickListener {
            /*for(i in kryss.indices){
                kamerer.kryss[i] += kryss[i]
            }*/
            getDialog().dismiss();
            val storeKryss = kryss.indices.filter { i -> kryss[i] > 0 || i == replaceKryss?.type }
                    .map { i -> SendKryss(null, (ctx as MainActivity).deviceId, i, kryss[i], replaceKryss?.time ?: System.currentTimeMillis(), kamererId, replaceKryss?.id, replaceKryss?.device) }.toTypedArray()

            database.use {
                for(k in storeKryss) {
                    k.insert(this)
                }
            }

            (ctx as MainActivity).updateKryssLists()
        }
        v.kryssa.enabled = false

        for(i in buttons.indices){
            buttons[i].setBackgroundColor(KryssType.types[i].color)
            buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}")

            buttons[i].setOnClickListener {
                kryss[i]++;
                buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}")
                v.kryssa.enabled = true
            }

            buttons[i].setOnLongClickListener {
                kryss[i] = 0;
                buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}");
                v.kryssa.enabled = replaceKryss != null || kryss.any { n -> n > 0 }
                true
            }
        }

        return v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(DialogFragment.STYLE_NORMAL, appcompat.R.style.Theme_AppCompat_Dialog)
    }
}


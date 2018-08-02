package org.altekamereren.groggomat

import android.app.DialogFragment
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.Button
import android.widget.TextView
import android.support.v7.appcompat.R as AR

import kotlinx.android.synthetic.main.kryss_dialog.view.*
import org.jetbrains.anko.*
import org.jetbrains.anko.db.*

public class KryssDialogFragment() : DialogFragment() {
    val kryss = Array(KryssType.types.size, {_->0})

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.kryss_dialog, container, false)
        val kamererId = arguments.getLong("kamerer")
        val kamerer = (ctx as MainActivity).kamererer[kamererId]!!
        val replaces_id:Long? = if(arguments.getLong("replaces_id", -1L) == -1L) null else arguments.getLong("replaces_id")
        val replaces_device:String? = arguments.getString("replaces_device")
        var replaceKryss: Kryss? = null

        val buttons = arrayOf<Button>(v.weak, v.strong, v.delux, v.food)
        val descriptions = arrayOf<TextView>(v.weakText, v.strongText, v.deluxText, v.foodText)

        if(replaces_id != null && replaces_device != null) {
            replaceKryss = database.use {
                select(Kryss.table, *Kryss.selectList)
                    .whereArgs("ifnull(real_id, _id) = {replaces_id} and device = {replaces_device}", "replaces_id" to replaces_id, "replaces_device" to replaces_device)
                    .parseOpt(Kryss.parser)
            }
            if(replaceKryss != null) {
                for (i in buttons.indices) {
                    if (i != replaceKryss.type) buttons[i].isEnabled = false
                    else kryss[i] = replaceKryss.count
                }
            }
        }

        v.title.text = "Kryssa för ${kamerer.name}"
        v.kryssa.setOnClickListener {
            /*for(i in kryss.indices){
                kamerer.kryss[i] += kryss[i]
            }*/

            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

            var a = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    view.alpha = 1.0f-interpolatedTime;
                }
            }

            a.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {dialog.dismiss();}
                override fun onAnimationStart(animation: Animation?) {}
            })
            a.duration = 500;
            v.startAnimation(a);


            val storeKryss = kryss.indices.filter { i -> kryss[i] > 0 || i == replaceKryss?.type }
                    .map { i -> Kryss(null, (ctx as MainActivity).deviceId, i, kryss[i], replaceKryss?.time ?: System.currentTimeMillis(), kamererId, replaceKryss?.id, replaceKryss?.device) }.toTypedArray()

            database.use {
                for(k in storeKryss) {
                    val stored = k.insert(this)
                    (ctx as MainActivity).kryssCache.add(stored)
                }
            }

            (ctx as MainActivity).updateKryssLists(replaces_id != null)
        }
        v.kryssa.isEnabled = false

        for(i in buttons.indices){
            if(android.os.Build.VERSION.SDK_INT >= 21) {
                buttons[i].background.setTint(KryssType.types[i].color)
            }
            else {
                buttons[i].setBackgroundColor(KryssType.types[i].color)
            }
            buttons[i].text = "${KryssType.types[i].name}: ${kryss[i]}"

            buttons[i].setOnClickListener {
                kryss[i]++;
                buttons[i].text = "${KryssType.types[i].name}: ${kryss[i]}"
                v.kryssa.isEnabled = true
            }

            buttons[i].setOnLongClickListener {
                kryss[i] = 0;
                buttons[i].text = "${KryssType.types[i].name}: ${kryss[i]}";
                v.kryssa.isEnabled = replaceKryss != null || kryss.any { n -> n > 0 }
                true
            }

            descriptions[i].text = KryssType.types[i].description
        }

        return v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //setStyle(DialogFragment.STYLE_NORMAL, AR.style.Theme_AppCompat_Dialog)
    }
}



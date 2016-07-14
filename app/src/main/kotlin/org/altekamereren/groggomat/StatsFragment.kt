package org.altekamereren.groggomat

import android.app.Fragment
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import org.jetbrains.anko.*
import java.text.DateFormat
import java.util.*

public class StatsFragment : Fragment() {
    data class KamererValue<T>(val kamerer:Kamerer, val value:T, val description:String? = null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainActivity = (ctx as MainActivity)

        val dateFormat = DateFormat.getInstance()

        var statsView: TextView? = null
        val view = ctx.verticalLayout {
            isVerticalScrollBarEnabled = true
            val fromDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                setText(dateFormat.format(Date(Date().time -24*3600*1000)))
            }
            val toDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                setText(dateFormat.format(Date()))
            }
            linearLayout {
                button("Kryss (alla typer)") {
                    onClick {
                        val from = dateFormat.parse(fromDate.text.toString())
                        val to = dateFormat.parse(toDate.text.toString())

                        statsView!!.text = mainActivity.kamererer.values
                                .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from.time && kryss.time < to.time }.sumBy { kryss -> kryss.count }) }
                                .sortedByDescending { kv -> kv.value }
                                .map { kv -> "${kv.value}\t${kv.kamerer.name}" }.joinToString("\n")
                    }
                }
                button("Alkohol (cl %40 sprit)") {
                    onClick {
                        val from = dateFormat.parse(fromDate.text.toString())
                        val to = dateFormat.parse(toDate.text.toString())

                        statsView!!.text = mainActivity.kamererer.values
                                .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from.time && kryss.time < to.time }.sumByDouble { kryss -> kryss.count*KryssType.types[kryss.type].alcohol*10 }.toInt()) }
                                .sortedByDescending { kv -> kv.value }
                                .map { kv -> "${kv.value}\t${kv.kamerer.name}" }.joinToString("\n")
                    }
                }
                button("Max alcohol") {
                    onClick {
                        val from = dateFormat.parse(fromDate.text.toString())
                        val to = dateFormat.parse(toDate.text.toString())

                        statsView!!.text = mainActivity.kamererer.values
                                .filter { it.weight != null }
                                .map { k ->
                                    val maxAlcoholKryss = mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from.time && kryss.time < to.time }.maxBy { kryss -> kryss.alcohol };
                                    KamererValue (k, maxAlcoholKryss?.alcohol, dateFormat.format(Date(maxAlcoholKryss?.time ?: 0)))
                                }
                                .sortedByDescending { kv -> kv.value }
                                .map { kv -> "${kv.value}\t${kv.description}\t${kv.kamerer.name}" }
                                .joinToString("\n")
                    }
                }
            }
            statsView = textView {
            }.lparams(width=matchParent, height=wrapContent)
        }

        return view
    }
}
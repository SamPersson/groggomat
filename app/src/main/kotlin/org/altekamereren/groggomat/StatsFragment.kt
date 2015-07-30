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
    data class KamererValue<T>(val kamerer:Kamerer, val value:T)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainActivity = (ctx as MainActivity)

        val dateFormat = DateFormat.getInstance()

        var statsView: TextView
        val view = verticalLayout {
            val fromDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                text = dateFormat.format(Date(Date().getTime()-24*3600*1000))
            }
            val toDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                text = dateFormat.format(Date())
            }
            linearLayout {
                button("Kryss (alla typer)") {
                    onClick {
                        val from = dateFormat.parse(fromDate.text.toString())
                        val to = dateFormat.parse(toDate.text.toString())

                        statsView.text = mainActivity.kamererer.values()
                                .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from.getTime() && kryss.time < to.getTime() }.sumBy { kryss -> kryss.count }) }
                                .sortDescendingBy { kv -> kv.value }
                                .map { kv -> "${kv.value}\t${kv.kamerer.name}" }.join("\n")
                    }
                }
                button("Alkohol (cl %40 sprit)") {
                    onClick {
                        val from = dateFormat.parse(fromDate.text.toString())
                        val to = dateFormat.parse(toDate.text.toString())

                        statsView.text = mainActivity.kamererer.values()
                                .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from.getTime() && kryss.time < to.getTime() }.sumByDouble { kryss -> kryss.count*KryssType.types[kryss.type].alcohol*10 }.toInt()) }
                                .sortDescendingBy { kv -> kv.value }
                                .map { kv -> "${kv.value}\t${kv.kamerer.name}" }.join("\n")
                    }
                }
            }
            statsView = textView {

            }.layoutParams(width=matchParent, height=wrapContent)
        }

        return view
    }
}
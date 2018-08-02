package org.altekamereren.groggomat

import android.app.Fragment
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.util.*


public class StatsFragment : Fragment() {
    data class KamererValue<T>(val kamerer:Kamerer, val value:T, val description:String? = null)

    private val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.UK)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainActivity = (ctx as MainActivity)

        var statsView: TextView? = null
        val view = ctx.verticalLayout {
            isVerticalScrollBarEnabled = true
            val fromDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                setText(LocalDateTime().withTime(6,0, 0, 0).toString(dateFormat))
            }
            val toDate = editText {
                inputType = InputType.TYPE_CLASS_DATETIME
                setText(LocalDateTime().plusDays(1).withTime(6,0, 0, 0).toString(dateFormat))
            }
            linearLayout {
                button("Kryss (alla typer)") {
                    onClick {
                        val from = tryParse(fromDate.text.toString())?.toDateTime()?.millis
                        val to = tryParse(toDate.text.toString())?.toDateTime()?.millis
                        when {
                            from == null -> toast("Invalid date: ${fromDate.text}")
                            to == null -> toast("Invalid date: ${toDate.text}")
                            else ->
                                statsView!!.text = mainActivity.kamererer.values
                                    .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from && kryss.time < to }.sumBy { kryss -> kryss.count }) }
                                    .sortedByDescending { kv -> kv.value }
                                    .map { kv -> "%5d  %s".format(kv.value, kv.kamerer.name) }.joinToString("\n")
                        }
                    }
                }
                button("Alkohol (cl %40 sprit)") {
                    onClick {
                        val from = tryParse(fromDate.text.toString())?.toDateTime()?.millis
                        val to = tryParse(toDate.text.toString())?.toDateTime()?.millis
                        when {
                            from == null -> toast("Invalid date: ${fromDate.text}")
                            to == null -> toast("Invalid date: ${toDate.text}")
                            else ->
                                statsView!!.text = mainActivity.kamererer.values
                                    .map { k -> KamererValue(k, mainActivity.kryssCache.filter { kryss -> kryss.kamerer == k.id && kryss.time >= from && kryss.time < to }.sumByDouble { kryss -> kryss.count * KryssType.types[kryss.type].alcohol * 10 }) }
                                    .sortedByDescending { kv -> kv.value }
                                    .map { kv -> "%#8.2f    %s".format(kv.value, kv.kamerer.name) }.joinToString("\n")
                        }
                    }
                }
                button("Max alkohol") {
                    onClick {
                        val from = tryParse(fromDate.text.toString())?.toDateTime()?.millis
                        val to = tryParse(toDate.text.toString())?.toDateTime()?.millis
                        when {
                            from == null -> toast("Invalid date: ${fromDate.text}")
                            to == null -> toast("Invalid date: ${toDate.text}")
                            else ->
                                statsView!!.text = mainActivity.kamererer.values
                                    .filter { it.weight != null }
                                    .map { k ->
                                        val kamererKryss = mainActivity.kryssCache.filter { it.kamerer == k.id }.toList();
                                        val maxAlcoholKryss = kamererKryss.filter { it.time >= from && it.time < to }.maxBy { it.alcohol }
                                        val remainingAlcohol = kamererKryss.filter { it.time < from }.maxBy { it.time }
                                                ?.let { previousKryss -> k.alcoholDissipation(previousKryss.alcohol, from - previousKryss.time) }
                                        if (remainingAlcohol != null && (maxAlcoholKryss == null || remainingAlcohol > maxAlcoholKryss.alcohol)) {
                                            KamererValue(k, remainingAlcohol, DateTime(from).toString(dateFormat))
                                        } else {
                                            KamererValue(k, maxAlcoholKryss?.alcohol, DateTime(maxAlcoholKryss?.time
                                                    ?: 0).toString(dateFormat))
                                        }
                                    }
                                    .filter { kv -> kv.value != null && kv.value > 0 }
                                    .sortedByDescending { kv -> kv.value }
                                    .map { kv -> "%22s  %10.4f  %s".format(kv.description, kv.value, kv.kamerer.name) }
                                    .joinToString("\n")
                        }
                    }
                }
            }
            statsView = textView {
                typeface = Typeface.MONOSPACE
            }.lparams(width=matchParent, height=wrapContent)
        }

        return view
    }

    private fun tryParse(v: String) : LocalDateTime?
    {
        return try {
            dateFormat.parseLocalDateTime(v)
        } catch (e:IllegalArgumentException) {
            null
        }
    }
}
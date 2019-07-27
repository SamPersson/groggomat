package org.altekamereren.groggomat

import androidx.fragment.app.Fragment
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk27.coroutines.onClick
import org.jetbrains.anko.sdk27.coroutines.onEditorAction
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.toast
import org.jetbrains.anko.support.v4.withArguments
import java.text.SimpleDateFormat
import java.util.*

class KamererFragment : Fragment()
{
    class ListAdapter(context: Context, val kryss:MutableList<Kryss>) : ArrayAdapter<Kryss>(context, -1, kryss) {
        private inline fun <T: Any> view(crossinline f: AnkoContext<*>.() -> T): T {
            var view: T? = null
            context.UI { view = f() }
            return view!!
        }

        private val dateFormat = SimpleDateFormat("yyyyMMdd hh:mm:ss", Locale.US)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val k = kryss[position]

            return view {
                verticalLayout {
                    textView {
                        text = "${dateFormat.format(Date(k.time))} ${k.device} ${k.id} ${KryssType.types[k.type].name} ${k.count}"
                        padding = dip(5)
                    }
                    if(k.replacesId != null && k.replacesDevice != null) {
                        textView {
                            textColor = 0xffff0000.toInt()
                            text = "Replaces: ${k.replacesDevice} ${k.replacesId}"
                            padding = dip(5)
                        }
                    }
                }
            }
        }
    }

    private var listAdapter: ListAdapter? = null

    fun updateData() {
        listAdapter?.kryss?.clear()
        listAdapter?.kryss?.addAll(fetchKryss(arguments!!.getLong("kamerer")))
        listAdapter?.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val kamererId = arguments!!.getLong("kamerer")
        val kamerer = (ctx as MainActivity).kamererer[kamererId] ?: throw Exception()

        val kryss = ArrayList(fetchKryss(kamererId))

        listAdapter = ListAdapter(ctx, kryss)
        val listAdapter = listAdapter

        fun parseDouble(s:String?):Double? {
            return try {
                java.lang.Double.parseDouble(s ?: "")
            } catch(e:NumberFormatException) {
                null
            }
        }

        return ctx.verticalLayout {
            linearLayout {
                textView {
                    textSize = 24f
                    text = kamerer.name
                    padding = dip(5)
                }
                button("Sätt vikt") {
                    hint = "Vikt i kilo"
                    padding = dip(5)
                    onClick {
                        var dialog : DialogInterface? = null
                        //val weightAlert:AlertDialogBuilder
                        val weightAlert = alert {
                            title = "Skriv in din vikt"
                            customView {
                                var weightInput:EditText? = null
                                weightInput = editText {
                                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                                    onEditorAction { _, actionId, _ ->
                                        if(actionId and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_DONE) {
                                            val d = parseDouble(weightInput!!.text.toString())
                                            if(d != null) {
                                                kamerer.weight = d
                                                kamerer.updated = System.currentTimeMillis()
                                                database.use {
                                                    kamerer.update(this)
                                                }
                                                dialog!!.dismiss()
                                                (ctx as MainActivity).updateKryssLists(false)
                                            }
                                            else {
                                                toast("Invalid weight")
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                }
                                weightInput.requestFocus()
                            }
                        }
                        dialog = weightAlert.show()
                        //dialog.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    }
                }
            }
            listView {
                adapter = listAdapter
                setOnItemClickListener { _, _, position, _ ->
                    val ft = fragmentManager!!.beginTransaction()
                    val prev = fragmentManager!!.findFragmentByTag("dialog")
                    if (prev != null) {
                        ft.remove(prev)
                    }
                    ft.addToBackStack(null)

                    // Create and show the dialog.
                    val newFragment = KryssDialogFragment().withArguments(
                            "kamerer" to kamererId,
                            "replaces_id" to kryss[position].id!!,
                            "replaces_device" to kryss[position].device)
                    newFragment.show(ft, "dialog")
                }
            }
        }
    }

    private fun fetchKryss(kamererId: Long): List<Kryss> {
        return (ctx as MainActivity).kryssCache
                .filter { kryss -> kryss.kamerer == kamererId}
                .sortedByDescending { kryss -> kryss.time}
    }

}
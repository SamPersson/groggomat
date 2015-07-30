package org.altekamereren.groggomat

import android.app.AlertDialog
import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.SparseBooleanArray
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView
import org.jetbrains.anko.db.SqlOrderDirection
import org.jetbrains.anko.db.select
import java.text.SimpleDateFormat
import java.util.*

public class KamererFragment : Fragment()
{
    public class ListAdapter(context: Context, val kryss:MutableList<Kryss>) : ArrayAdapter<Kryss>(context, -1, kryss) {
        public inline fun <T: Any> view(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: UiHelper.() -> T): T {
            var view: T? = null
            getContext().UI { view = f() }
            return view!!
        }

        val dateFormat = SimpleDateFormat("yyyyMMdd hh:mm:ss")

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            val k = kryss[position];

            var view = view {
                verticalLayout {
                    textView {
                        text = "${dateFormat.format(Date(k.time))} ${k.device} ${k.id} ${KryssType.types[k.type].name} ${k.count}"
                        padding = dip(5)
                    }
                    if(k.replaces_id != null && k.replaces_device != null) {
                        textView {
                            textColor = 0xffff0000.toInt()
                            text = "Replaces: ${k.replaces_device} ${k.replaces_id}"
                            padding = dip(5)
                        }
                    }
                }
            }
            return view;
        }
    }

    var listAdapter: ListAdapter? = null

    public fun updateData() {
        listAdapter?.kryss?.clear()
        listAdapter?.kryss?.addAll(fetchKryss(getArguments().getLong("kamerer")))
        listAdapter?.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val kamererId = getArguments().getLong("kamerer")
        val kamerer = (ctx as MainActivity).kamererer[kamererId]

        val kryss = ArrayList(fetchKryss(kamererId))

        listAdapter = ListAdapter(ctx, kryss)
        val listAdapter = listAdapter

        fun parseDouble(s:String?):Double? {
            try {
                return java.lang.Double.parseDouble(s ?: "")
            } catch(e:NumberFormatException) {
                return null
            }
        }

        val view = verticalLayout {
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
                        var weightAlert:AlertDialogBuilder
                        var dialog: AlertDialog
                        weightAlert = alert {
                            title("Skriv in din vikt")
                            customView {
                                var weightInput:EditText
                                weightInput = editText {
                                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                                    onEditorAction { textView, actionId, keyEvent ->
                                        if(actionId and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_DONE) {
                                            val d = parseDouble(weightInput.text.toString())
                                            if(d != null) {
                                                kamerer.weight = d
                                                kamerer.updated = System.currentTimeMillis()
                                                database.use {
                                                    kamerer.update(this)
                                                }
                                                dialog.dismiss()
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
                        dialog = weightAlert.getBuilder().create()
                        dialog.show()
                        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    }
                }
            }
            listView {
                adapter = listAdapter
                setOnItemClickListener { adapterView, view, position, id ->
                    val ft = getFragmentManager().beginTransaction();
                    val prev = getFragmentManager().findFragmentByTag("dialog");
                    if (prev != null) {
                        ft.remove(prev);
                    }
                    ft.addToBackStack(null);

                    // Create and show the dialog.
                    val newFragment = KryssDialogFragment().withArguments(
                            "kamerer" to kamererId,
                            "replaces_id" to kryss[position].id!!,
                            "replaces_device" to kryss[position].device);
                    newFragment.show(ft, "dialog");
                }
            }
        }

        return view
    }

    private fun fetchKryss(kamererId: Long): List<Kryss> {
        return (ctx as MainActivity).kryssCache
                .filter({kryss -> kryss.kamerer == kamererId})
                .sortDescendingBy({kryss -> kryss.time})
    }

}
package org.altekamereren.groggomat

import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ListView
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView
import org.jetbrains.anko.db.select
import java.util.*

public class KamererFragment : Fragment()
{
    public class ListAdapter(context: Context, val kryss:MutableList<SendKryss>) : ArrayAdapter<SendKryss>(context, -1, kryss) {
        public inline fun <T: Any> view(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: UiHelper.() -> T): T {
            var view: T? = null
            getContext().UI { view = f() }
            return view!!
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            val k = kryss[position];

            var view = view {
                verticalLayout {
                    textView {
                        text = "${Date(k.time)} ${k.device} ${k.id} ${KryssType.types[k.type].name} ${k.count}"
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
        listAdapter?.kryss?.addAll(fetchKryss(getArguments().getInt("kamerer")))
        listAdapter?.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val kamererId = getArguments().getInt("kamerer")
        val kamerer = Kamerer.kamerers[kamererId]

        val kryss = fetchKryss(kamererId)

        listAdapter = ListAdapter(ctx, kryss)
        val listAdapter = listAdapter

        val view = verticalLayout {
            textView {
                textSize = 24f
                text = kamerer.name
                padding = dip(5)
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

    private fun fetchKryss(kamererId: Int): ArrayList<SendKryss> {
        val kryss = database.use {
            select(SendKryss.table, *SendKryss.selectList)
                    .where("kamerer = {kamerer} and not exists (select * from Kryss r where r.replaces_id = ifnull(k.real_id, k._id) and r.replaces_device = k.device)", "kamerer" to kamererId)
                    .parseList(SendKryss.parser)
                    .toArrayList()
        }
        return kryss
    }

}
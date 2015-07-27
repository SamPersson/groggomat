package org.altekamereren.groggomat

import android.app.Activity
import android.app.Fragment
import android.app.ListFragment
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import org.jetbrains.anko.*
import org.jetbrains.anko.db.*
import java.util
import java.util.*

public class KamererListFragment : ListFragment() {


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        getListView().setOnItemLongClickListener { adapterView, view, position, id ->
            val ft = getFragmentManager().beginTransaction();
            val prev = getFragmentManager().findFragmentByTag("kamerer");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            // Create and show the dialog.
            val newFragment = KamererFragment().withArguments("kamerer" to position);
            getFragmentManager().beginTransaction().replace(android.R.id.content, newFragment).addToBackStack(null).commit()
            true
        }
    }

    override fun onAttach(activity: Activity?) {
        super.onAttach(activity)

        setListAdapter(object: ArrayAdapter<Kamerer>(activity, -1, Kamerer.kamerers){
            public inline fun <T: Any> view(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: UiHelper.() -> T): T {
                var view: T? = null
                getContext().UI { view = f() }
                return view!!
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
                val kamerer = Kamerer.kamerers[position];

                val view = view {
                   linearLayout {
                        textView {
                            text = kamerer.name
                            textSize = 16f
                            typeface = Typeface.create("", Typeface.BOLD)
                        }.layoutParams(width = 0) {
                            margin=dip(10)
                            weight=1f
                            gravity= Gravity.CENTER_VERTICAL
                        }

                        for(i in kamerer.kryss.indices) {
                            textView {
                                text = kamerer.kryss[i].toString()
                                textSize = 16f
                                typeface = Typeface.create("", Typeface.BOLD)
                                backgroundColor = Color.parseColor(KryssType.types[i].color)
                                padding = dip(10)
                            }.layoutParams(width = wrapContent) {}
                        }
                    }
                }

                return view
            }

            override fun isEnabled(position: Int): Boolean {
                return true;
            }
        })
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        val ft = getFragmentManager().beginTransaction();
        val prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        val newFragment = KryssDialogFragment().withArguments("kamerer" to position);
        newFragment.show(ft, "dialog");
    }
}

public class KamererFragment : Fragment()
{
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val position = getArguments().getInt("kamerer")
        val kamerer = Kamerer.kamerers[position]

        val kryss = database.use {
            select("Kryss", "ifnull(real_id, _id)", "device", "type", "count", "time", "kamerer")
                    .where("kamerer = {kamerer}", "kamerer" to position)
                    .parseList(SendKryss.parser)
        }

        val listAdapter = object : ArrayAdapter<SendKryss>(ctx, -1, kryss) {
            val selectedItems = SparseBooleanArray()

            public inline fun <T: Any> view(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) f: UiHelper.() -> T): T {
                var view: T? = null
                getContext().UI { view = f() }
                return view!!
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
                val k = kryss[position];

                val view = view {
                    linearLayout {
                        backgroundColor = if(selectedItems[position]) 0x20ffffff else 0x00000000
                        textView {
                            text = Date(k.time).toString()
                            padding = dip(5)
                        }
                        textView {
                            text = k.device.toString()
                            padding = dip(5)
                        }
                        textView {
                            text = k.id.toString()
                            padding = dip(5)
                        }
                        textView {
                            text = KryssType.types[k.type].name
                            padding = dip(5)
                        }
                        textView {
                            text = k.count.toString()
                            padding = dip(5)
                        }
                    }
                }
                return view;
            }
        }

        val view = verticalLayout {
            editText {
                hint = "Name"
                textSize = 24f
                text = kamerer.name
            }
            listView {
                choiceMode = AbsListView.CHOICE_MODE_MULTIPLE_MODAL
                setMultiChoiceModeListener(object : AbsListView.MultiChoiceModeListener {
                    override fun onItemCheckedStateChanged(mode: ActionMode?, position: Int, id: Long, checked: Boolean) {
                        listAdapter.selectedItems.put(position, !listAdapter.selectedItems[position])
                        listAdapter.notifyDataSetChanged()
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
                        if(item.getItemId() == R.id.delete) {
                            // Calls getSelectedIds method from ListViewAdapter Class
                            // Captures all selected ids with a loop
                            for (i in listAdapter.selectedItems.size()-1 downTo 0) {
                                if (listAdapter.selectedItems.valueAt(i)) {
                                    val k = kryss[listAdapter.selectedItems.keyAt(i)]
                                    // Remove selected items following the ids
                                    listAdapter.remove(k);
                                }
                            }
                            // Close CAB
                            mode?.finish();
                            return true;
                        }
                        return false;
                    }

                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        mode.getMenuInflater().inflate(R.menu.delete_kryss, menu);
                        return true;
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        listAdapter.selectedItems.clear()
                        listAdapter.notifyDataSetChanged()
                    }
                })
                adapter = listAdapter
            }
        }



        return view
    }

}
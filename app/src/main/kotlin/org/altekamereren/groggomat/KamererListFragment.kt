package org.altekamereren.groggomat

import android.app.Activity
import androidx.fragment.app.ListFragment
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import org.jetbrains.anko.*
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.withArguments
import java.util.*

class KamererListFragmentUI(private val kamerer: Kamerer = Kamerer(1,"Test", 50.0, true, 0)) : AnkoComponent<Activity> {
    override fun createView(ui: AnkoContext<Activity>) = with(ui) {
        linearLayout {
            textView {
                text = kamerer.name
                textSize = 16f
                typeface = Typeface.create("", Typeface.BOLD)
            }.lparams(width = 0) {
                width=0
                margin=dip(10)
                weight=1f
                gravity= Gravity.CENTER_VERTICAL
            }

            if(kamerer.weight != null) {
                textView {
                    text = String.format("%.2f", kamerer.alcohol)
                    textSize = 16f
                    padding = dip(10)
                }.lparams(width = wrapContent, height = matchParent)
            }

            for(i in kamerer.kryss.indices) {
                textView {
                    text = kamerer.kryss[i].toString()
                    textSize = 16f
                    typeface = Typeface.create("", Typeface.BOLD)
                    backgroundColor = KryssType.types[i].color
                    padding = dip(10)
                }.lparams(width = wrapContent, height = matchParent) {}
            }
        }
    }
}

class KamererListFragment : ListFragment() {

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        listView.setOnItemLongClickListener { _, _, _, id ->
            val newFragment = KamererFragment().withArguments("kamerer" to id)
            fragmentManager!!.beginTransaction().replace(android.R.id.content, newFragment).addToBackStack(null).commit()
            true
        }
    }

    private var kamerererByName: List<Kamerer> = ArrayList()

    fun updateData() {
        kamerererByName = (ctx as MainActivity).kamererer.values.toList().sortedBy {it.name}
        (listAdapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        kamerererByName = (ctx as MainActivity).kamererer.values.toList().sortedBy {it.name}

        listAdapter = object: ArrayAdapter<Kamerer>(context, -1, kamerererByName){
            /*inline fun <T: Any> view(crossinline f: AnkoContext<*>.() -> T): T {
                var view: T? = null
                context.UI { view = f() }
                return view!!
            }*/

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return KamererListFragmentUI(kamerererByName[position]).createView(AnkoContext.create(ctx,requireActivity()))
            }

            override fun getItemId(position: Int): Long {
                return kamerererByName[position].id
            }

            override fun hasStableIds(): Boolean {
                return true
            }

            override fun isEnabled(position: Int): Boolean {
                return true
            }
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val ft = fragmentManager!!.beginTransaction()
        val prev = fragmentManager!!.findFragmentByTag("dialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        // Create and show the dialog.
        val newFragment = KryssDialogFragment().withArguments("kamerer" to id)
        newFragment.show(ft, "dialog")
    }
}


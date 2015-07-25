package org.altekamereren.groggomat

import android.app.Activity
import android.app.DialogFragment
import android.app.ListActivity
import android.app.ListFragment
import android.content.Context
import android.database.DataSetObserver
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import android.support.v7.appcompat
import org.jetbrains.anko.*

import kotlinx.android.synthetic.activity_main.*
import org.jetbrains.anko.db.*
import java.util.*


public class MainActivity : Activity(), AnkoLogger {

    data class Kryss(var kamerer:Int, var type:Int, var count:Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super<Activity>.onCreate(savedInstanceState)

        database.use {
            val kryssParser = rowParser { kamerer:Int, type:Int, count:Int -> Kryss(kamerer, type, count)}
            for(kryss in select("Kryss", "kamerer", "type", "sum(count)").groupBy("kamerer, type").parseList(kryssParser)) {
                Kamerer.kamerers[kryss.kamerer].kryss[kryss.type] += kryss.count
            }
        }

        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, KamererListFragment())
                .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item!!.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true
        }

        return super<Activity>.onOptionsItemSelected(item)
    }

    fun onKryssa(kamerer:Kamerer, kryss:Array<Int>) {
        val list = getFragmentManager().findFragmentById(android.R.id.content) as KamererListFragment
        (list.getListAdapter() as ArrayAdapter<*>).notifyDataSetInvalidated()

        //async {
            val result = database.use {
                for(i in kryss.indices.filter { i -> kryss[i] > 0 }) {
                    DbKryss(_id=0,
                            device = "device",
                            real_id = null,
                            type = i,
                            count = kryss[i],
                            time = System.currentTimeMillis(),
                            kamerer = Kamerer.kamerers.indexOf(kamerer)
                    ).insert(this)
                }
            }
        //}
    }

    public class KamererListFragment : ListFragment() {



        override fun onAttach(activity: Activity?) {
            super.onAttach(activity)

            setListAdapter(object:ArrayAdapter<Kamerer>(activity, -1, Kamerer.kamerers){
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
                                gravity=Gravity.CENTER_VERTICAL
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

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
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


}

data class KryssType(val name:String, val color:String) {
    companion object {
        val types = arrayOf(KryssType("Svag", "#ff8e9103"), KryssType("Vanlig", "#ff906500"), KryssType("Delüx", "#ff051e76"), KryssType("Mat", "#ff962000"))
    }
}

class Kamerer(val name:String) {
    companion object {
        val kamerers = arrayOf("Felicia Ardenmark Strand", "Jules Hanley", "Jesper Hasselquist", "Damir Basic Knezevic", "Douglas Clifford", "Jens Ogniewski", "Johan Levin", "Philip Jönsson", "Annica Ericsson", "Hanna Ekström", "Peder Andersson", "Peter Swartling", "Johan Ruuskanen", "Pontus Persson", "Sam Persson", "Gustaf Malmberg", "Oskar Fransén", "Philip Ljungkvist", "Tobias Petersen", "Viktoria Alm", "Rebecka Erntell", "Christine Persson", "Joakim Arnsby", "Lukas Arnsby", "Olov Ferm")
                .sortBy({n -> n})
                .map({n -> Kamerer(n)})
    }

    val kryss = Array(KryssType.types.size(), { i -> 0 })
}

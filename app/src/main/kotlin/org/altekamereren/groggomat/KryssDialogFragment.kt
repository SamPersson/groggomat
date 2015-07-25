package org.altekamereren.groggomat

import android.app.DialogFragment
import android.app.Fragment
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.support.v7.appcompat

import kotlinx.android.synthetic.kryss_dialog.view.*
import org.jetbrains.anko.db.ManagedSQLiteOpenHelper
import org.jetbrains.anko.enabled

public class KryssDialogFragment() : DialogFragment() {
    val kryss = Array(KryssType.types.size(), {i->0})

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.kryss_dialog, container, false)
        val kamerer = Kamerer.kamerers[getArguments().getInt("kamerer")]
        v.title.setText("Kryssa för ${kamerer.name}")
        v.kryssa.setOnClickListener {
            for(i in kryss.indices){
                kamerer.kryss[i] += kryss[i]
            }
            getDialog().dismiss();
            (getActivity() as MainActivity).onKryssa(kamerer, kryss)
        }
        v.kryssa.enabled = false

        val buttons = arrayOf(v.weak, v.strong, v.delux, v.food)

        for(i in buttons.indices){
            buttons[i].setBackgroundColor(Color.parseColor(KryssType.types[i].color))
            buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}")

            buttons[i].setOnClickListener {
                kryss[i]++;
                buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}")
                v.kryssa.enabled = true
            }

            buttons[i].setOnLongClickListener {
                kryss[i] = 0;
                buttons[i].setText("${KryssType.types[i].name}: ${kryss[i]}");
                v.kryssa.enabled = kryss.any { n -> n > 0 }
                true
            }
        }

        return v
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(DialogFragment.STYLE_NORMAL, appcompat.R.style.Theme_AppCompat_Dialog)
    }
}


package org.altekamereren.groggomat

import androidx.fragment.app.Fragment
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.*
import org.jetbrains.anko.db.*

class MyDatabaseOpenHelper(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "MyDatabase", null, 10), AnkoLogger{

    companion object {
        private var instance: MyDatabaseOpenHelper? = null

        @Synchronized
        fun getInstance(ctx: Context): MyDatabaseOpenHelper {
            if (instance == null) {
                instance = MyDatabaseOpenHelper(ctx.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        info("DB onCreate")
        db.createTable("Kryss", true,
                "_id" to INTEGER + PRIMARY_KEY,
                "device" to TEXT + NOT_NULL,
                "real_id" to INTEGER ,
                "type" to INTEGER + NOT_NULL,
                "count" to INTEGER + NOT_NULL,
                "time" to INTEGER + NOT_NULL,
                "kamerer" to INTEGER + NOT_NULL,
                "replaces_id" to INTEGER,
                "replaces_device" to TEXT)

        db.createTable("Kamerer", true,
                "_id" to INTEGER + PRIMARY_KEY,
                "name" to TEXT + NOT_NULL,
                "weight" to REAL,
                "male" to INTEGER + NOT_NULL,
                "updated" to INTEGER + NOT_NULL)

        /*val r = Random()
        for(i in 0..9999) {
            Kryss(i.toLong(), "test", r.nextInt(4), 1 + r.nextInt(4), System.currentTimeMillis() - r.nextInt(1000*3600*24*10), r.nextInt(25).toLong(), if(r.nextInt(30)==0 && i > 100) (i - r.nextInt(99) - 1).toLong() else null, "test")
                .insert(db)
        }*/

        val men = arrayOf(
                "Anders \"Taggen\" Rosenqvist",
                "Christoffer Svensson",
                "Damir Basic Knezevic",
                "David Ohlin",
                "David Wahlqvist ",
                "Douglas Clifford",
                "Erik Löfquist",
                "Gustaf Bergström",
                "Hjalmar Lind",
                "Jens Ogniewski",
                "Jesper Hasselquist",
                "Johan Ruuskanen",
                "Ludvig Hagmar",
                "Mattias Lilja",
                "Niklas Dichter",
                "Oskar Fransén",
                "Peder Anderson",
                "Philip Ljungkvist",
                "Pontus Persson ",
                "Robert Hansen Jagrelius",
                "Sam Persson",
                "Simon Susnjevic",
                "Svante Rosenlind",
                "Tobias Alex-Petersen",
                "Victor Pihl",
                "Viktor Hjertenstein",
                "Övrig man 1",
                "Övrig man 2")

        val women = arrayOf(
                "Carolina Svensson",
                "Elin Johansson",
                "Elin Svensson",
                "Ester Randahl",
                "Frida Nilsson",
                "Hanna Ekström",
                "Övrig kvinna 1",
                "Övrig kvinna 2")

        for(i in men.indices) {
            db.insert("Kamerer", "_id" to i, "name" to men[i], "male" to 1, "updated" to 1/*, "weight" to r.nextDouble()*60 + 50*/)
        }
        for(i in women.indices) {
            db.insert("Kamerer", "_id" to i+men.size, "name" to women[i], "male" to 0, "updated" to 1/*, "weight" to r.nextDouble()*40 + 40*/)
        }

        /*db.execSQL("create index kryss_replaces on Kryss(replaces_id, replaces_device) where replaces_id is not null or replaces_device is not null")
        db.execSQL("create index real_id on Kryss(real_id, device) where real_id is not null")
        db.execSQL("create index kamerer_list on Kryss(kamerer, time desc)")*/

        info("DB created")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        info("DB onUpgrade")
        db.dropTable("Kryss", true)
        db.dropTable("Kamerer", true)
        onCreate(db)
    }
}

// Access properties for Context (you could use it in Activity, Service etc.)
val Context.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(applicationContext)
// Access property for Fragment
val Fragment.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(activity!!.applicationContext)


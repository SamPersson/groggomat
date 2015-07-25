package org.altekamereren.groggomat

import android.app.Fragment
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.jetbrains.anko.*
import org.jetbrains.anko.db.*

class MyDatabaseOpenHelper(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "MyDatabase", null, 2), AnkoLogger{

    companion object {
        private var instance: MyDatabaseOpenHelper? = null

        synchronized fun getInstance(ctx: Context): MyDatabaseOpenHelper {
            if (instance == null) {
                instance = MyDatabaseOpenHelper(ctx.getApplicationContext())
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        info("DB onCreate")
        db.createTable("Kryss", true,
                "_id" to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                "device" to TEXT + NOT_NULL,
                "real_id" to INTEGER ,
                "type" to INTEGER + NOT_NULL,
                "count" to INTEGER + NOT_NULL,
                "time" to INTEGER + NOT_NULL,
                "kamerer" to INTEGER + NOT_NULL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        info("DB onUpgrade")
        db.dropTable("Kryss", true)
        onCreate(db)
    }
}

// Access properties for Context (you could use it in Activity, Service etc.)
val Context.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(getApplicationContext())
// Access property for Fragment
val Fragment.database: MyDatabaseOpenHelper
    get() = MyDatabaseOpenHelper.getInstance(getActivity().getApplicationContext())

data class DbKryss(val _id : Long, val device : String, val real_id : Long?, val type:Int, val count:Int, val time:Long, val kamerer:Int) {
    public fun insert(db: SQLiteDatabase) {
        if(real_id != null) {
            db.insert("Kryss",
                    "device" to device,
                    "real_id" to real_id,
                    "type" to type,
                    "count" to count,
                    "time" to time,
                    "kamerer" to kamerer
            )
        } else {
            db.insert("Kryss",
                    "device" to device,
                    "type" to type,
                    "count" to count,
                    "time" to time,
                    "kamerer" to kamerer
            )
        }
    }
}
package com.jehutyno.yomikata.repository.local

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.jehutyno.yomikata.util.updateBDD
import org.jetbrains.anko.db.ManagedSQLiteOpenHelper
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by valentin on 07/10/2016.
 */
class SQLiteHelper(var context: Context) : ManagedSQLiteOpenHelper(context, SQLiteHelper.DATABASE_NAME, null, SQLiteHelper.DATABASE_VERSION) {

    private lateinit var database: SQLiteDatabase
    private var flag: Boolean = false

    companion object {
        val DATABASE_NAME = "yomikataz.db"
        val DATABASE_VERSION = 12

        private var instance: SQLiteHelper? = null

        @Synchronized
        fun getInstance(ctx: Context): SQLiteHelper {
            if (instance == null) {
                instance = SQLiteHelper(ctx.applicationContext)
            }
            return instance!!
        }
    }

    /**
     * Creates a empty database on the system and rewrites it with your own
     * database.
     */
    @Throws(IOException::class)
    fun createDataBase() {
        val dbExist = checkDataBase()
        if (dbExist) {
            // do nothing - database already exist
        } else {
            this.readableDatabase
            try {
                copyDataBase()
                readableDatabase.version = DATABASE_VERSION
            } catch (e: IOException) {
                e.printStackTrace()
                throw Error("Error copying database")
            }
        }
    }

    @Throws(IOException::class)
    fun forceDatabaseReset() {
        this.readableDatabase
        try {
            copyDataBase()
        } catch (e: IOException) {
            throw Error("Error copying database")
        }
    }

    /**
     * Check if the database already exist to avoid re-copying the file each
     * time you open the application.
     *
     * @return true if it exists, false if it doesn't
     */
    private fun checkDataBase(): Boolean {
        var checkDB: SQLiteDatabase? = null
        try {
            val myPath = context.getString(com.jehutyno.yomikata.R.string.db_path) + DATABASE_NAME
            checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            e.printStackTrace()
        }
        checkDB?.close()

        return checkDB != null
    }

    /**
     * Copies your database from your local assets-folder to the just created
     * empty database in the system folder, from where it can be accessed and
     * handled. This is done by transfering bytestream.
     */
    @Throws(IOException::class)
    private fun copyDataBase() {
        // Open your local db as the input stream
        val myInput = context.assets.open(DATABASE_NAME)
        // Path to the just created empty db
        val outFileName = context.getString(com.jehutyno.yomikata.R.string.db_path) + DATABASE_NAME
        // Open the empty db as the output stream
        val myOutput = FileOutputStream(outFileName)
        // transfer bytes from the inputfile to the outputfile
        val buffer = ByteArray(1024)
        while (myInput.read(buffer) > 0) {
            myOutput.write(buffer)
            Log.d("#DB", "writing>>")
        }

        // Close the streams
        myOutput.flush()
        myOutput.close()
        myInput.close()
    }

    @Throws(SQLException::class)
    fun openDataBase() {
        // Open the database
        val myPath = context.getString(com.jehutyno.yomikata.R.string.db_path) + DATABASE_NAME
        SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Synchronized override fun close() {
        flag = false
        database.close()
        super.close()
    }

    override fun onCreate(database: SQLiteDatabase) {

    }

    @Throws(SQLException::class)
    fun open(): SQLiteDatabase {
        if (!flag) {
            try {
                createDataBase()
            } catch (ioe: IOException) {
                throw Error("Unable to create database")
            }

            try {
                openDataBase()
                database = writableDatabase
                flag = true
            } catch (sqle: SQLException) {
                throw sqle
            }
        }

        return database
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.disableWriteAheadLogging()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        context.updateBDD(db, "", oldVersion)
    }
}

// Access property for Context
val Context.database: SQLiteHelper
    get() = SQLiteHelper.getInstance(applicationContext)
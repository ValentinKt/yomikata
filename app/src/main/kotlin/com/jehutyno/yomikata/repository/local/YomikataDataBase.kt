package com.jehutyno.yomikata.repository.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jehutyno.yomikata.util.updateOldDBtoVersion12
import java.io.File


@Database(entities = [RoomKanjiSolo::class, RoomQuiz::class, RoomSentences::class,
                      RoomStatEntry::class, RoomWords::class, RoomQuizWord::class,
                      RoomRadicals::class],
          version = 13, exportSchema = true)
abstract class YomikataDataBase : RoomDatabase() {
    abstract fun kanjiSoloDao(): KanjiSoloDao
    abstract fun quizDao(): QuizDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun statsDao(): StatsDao
    abstract fun updateDao(): UpdateDao
    abstract fun wordDao(): WordDao

    companion object {
        private const val DATABASE_FILE_NAME = "yomikataz.db"
        private var INSTANCE: YomikataDataBase? = null
        fun getDatabase(context: Context): YomikataDataBase {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE =
                        Room.databaseBuilder(context, YomikataDataBase::class.java, DATABASE_FILE_NAME)
                            .createFromAsset(DATABASE_FILE_NAME)
                            .allowMainThreadQueries()   // TODO: remove this after using coroutines/livedata
                            .addMigrations(MIGRATION_8_9, MIGRATION_11_12(context), MIGRATION_12_13)
                            .build()
                }
            }
            return INSTANCE!!
        }

        /**
         * Overwrite database
         *
         * Overwrites the current database with a new database
         *
         * @param context Context
         * @param externalDatabasePath The absolute path to an external database which will
         * replace the currently loaded database
         */
        @Synchronized
        fun overwriteDatabase(context: Context, externalDatabasePath: String) {
            // make sure current database is closed
            INSTANCE!!.close()

            // make backup
            // TODO()

            val externalDatabaseFile = File(externalDatabasePath)
            val currentDatabaseFile = context.getDatabasePath(DATABASE_FILE_NAME)
            externalDatabaseFile.copyTo(currentDatabaseFile, overwrite = true)

            INSTANCE = null
        }

        ///////// DEFINE MIGRATIONS /////////
        // do not use values or constants that may be changed externally

        // migrate from anko sqlite to room
        val MIGRATION_12_13 = object : Migration(12, 13) {

            // drop and recreate tables in order to make types more consistent
            override fun migrate(database: SupportSQLiteDatabase) {
                // quiz     isSelected is a boolean, but Room stores it as an INTEGER in the db
                database.execSQL("""
                    CREATE TABLE NEW_quiz (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name_en TEXT NOT NULL,
                    name_fr TEXT NOT NULL,
                    category INTEGER NOT NULL,
                    isSelected INTEGER NOT NULL DEFAULT (0)
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_quiz ( _id, name_en, name_fr, category, isSelected )
                    SELECT                 _id, name_en, name_fr, category, isSelected
                    FROM quiz
                        
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE quiz")
                database.execSQL("ALTER TABLE NEW_quiz RENAME TO quiz")

                // words
                database.execSQL("""
                    CREATE TABLE NEW_words (
                      _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      japanese TEXT NOT NULL,
                      english TEXT NOT NULL,
                      french TEXT NOT NULL,
                      reading TEXT NOT NULL,
                      level INTEGER NOT NULL DEFAULT (0),
                      count_try INTEGER NOT NULL DEFAULT (0),
                      count_success INTEGER NOT NULL DEFAULT (0),
                      count_fail INTEGER NOT NULL DEFAULT (0),
                      is_kana INTEGER NOT NULL,
                      repetition INTEGER NOT NULL DEFAULT (-1),
                      points INTEGER NOT NULL DEFAULT (0),
                      base_category INTEGER NOT NULL,
                      isSelected INTEGER NOT NULL DEFAULT (0),
                      sentence_id INTEGER NOT NULL DEFAULT (-1)
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_words (
                        _id, japanese, english, french, reading, level, count_try,
                        count_success, count_fail, is_kana, repetition, points,
                        base_category, isSelected, sentence_id
                    )
                    SELECT 
                        _id, japanese, english, french, reading, level, count_try,
                        count_success, count_fail, is_kana, repetition, points,
                        base_category, isSelected, sentence_id 
                    FROM words
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE words")
                database.execSQL("ALTER TABLE NEW_words RENAME TO words")

                // quiz word
                database.execSQL("""
                    CREATE TABLE NEW_quiz_word (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        quiz_id INTEGER NOT NULL,
                        word_id INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_quiz_word ( _id, quiz_id, word_id )
                    SELECT                      _id, quiz_id, word_id
                    FROM quiz_word
                    """.trimIndent()
                )
                database.execSQL("""DROP TABLE quiz_word""")
                database.execSQL("""ALTER TABLE NEW_quiz_word RENAME TO quiz_word""")

                // stat entry
                database.execSQL("""
                    CREATE TABLE NEW_stat_entry (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        "action" INTEGER NOT NULL,
                        associatedId INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        result INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_stat_entry ( _id, "action", associatedId, date, result )
                    SELECT                       _id, "action", associatedId, date, result
                    FROM stat_entry
                    """.trimIndent()
                )
                database.execSQL("""DROP TABLE stat_entry""")
                database.execSQL("""ALTER TABLE NEW_stat_entry RENAME TO stat_entry""")

                // kanji solo
                database.execSQL("""
                    CREATE TABLE NEW_kanji_solo (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kanji TEXT NOT NULL,
                        strokes INTEGER NOT NULL,
                        en TEXT NOT NULL,
                        fr TEXT NOT NULL,
                        kunyomi TEXT NOT NULL,
                        onyomi TEXT NOT NULL,
                        radical TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_kanji_solo ( 
                                    _id, kanji, strokes, en, fr, kunyomi, onyomi, radical
                                )
                    SELECT          _id, kanji, strokes, en, fr, kunyomi, onyomi, radical
                    FROM kanji_solo
                    """.trimIndent()
                )
                database.execSQL("""DROP TABLE kanji_solo""")
                database.execSQL("""ALTER TABLE NEW_kanji_solo RENAME TO kanji_solo""")

                // radicals
                database.execSQL("""
                    CREATE TABLE NEW_radicals (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        strokes INTEGER NOT NULL,
                        radical TEXT NOT NULL,
                        reading TEXT NOT NULL,
                        en TEXT NOT NULL,
                        fr TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_radicals ( _id, strokes, radical, reading, en, fr )
                    SELECT                     _id, strokes, radical, reading, en, fr
                    FROM radicals
                """.trimIndent()
                )
                database.execSQL("""DROP TABLE radicals""")
                database.execSQL("""ALTER TABLE NEW_radicals RENAME TO radicals""")

                // sentences
                database.execSQL("""
                    CREATE TABLE NEW_sentences (
                        _id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        jap TEXT NOT NULL,
                        en TEXT NOT NULL,
                        fr TEXT NOT NULL,
                        level INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("""
                    INSERT INTO NEW_sentences (_id, jap, en, fr, level )
                    SELECT                     _id, jap, en, fr, level
                    FROM sentences
                """.trimIndent()
                )
                database.execSQL("""DROP TABLE sentences""")
                database.execSQL("""ALTER TABLE NEW_sentences RENAME TO sentences""")
            }

        }

        @Suppress("ClassName")
        private class MIGRATION_11_12(val context: Context) : Migration(11, 12) {

            // This performs all migrations from 1 to 12 (excluding the operations in
            // MIGRATION_8_9. The database is synchronized with a static copy of version 12
            override fun migrate(database: SupportSQLiteDatabase) {
                updateOldDBtoVersion12(database, context)
            }

        }

        @Suppress("ClassName")
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            // STEPS
            // 1. Add sentences table (did not exist before version 9)
            // 2. Alter the words table: new column = sentenceId (all others preserved)
            // 3. A̶d̶d̶ ̶a̶l̶l̶ ̶o̶f̶ ̶t̶h̶e̶ ̶n̶e̶w̶ ̶s̶e̶n̶t̶e̶n̶c̶e̶s̶ This is now done when migrating from 11 -> 12
            override fun migrate(database: SupportSQLiteDatabase) {
                // STEP 1
                val queryCreateSentencesTable = "CREATE TABLE sentences (\n" +
                        "   _id         INTEGER     PRIMARY KEY,\n" +
                        "   jap         TEXT,\n" +
                        "   en          TEXT,\n" +
                        "   fr          TEXT,\n" +
                        "   level       INTEGER\n" +
                        ");"

                database.execSQL(queryCreateSentencesTable)

                // STEP 2
                val queryRenameOldTable = "ALTER TABLE words RENAME TO OLD_words"

                val queryCreateNewTable = "CREATE TABLE words (\n" +
                        "    _id           INTEGER      PRIMARY KEY AUTOINCREMENT,\n" +
                        "    japanese      TEXT         NOT NULL,\n" +
                        "    english       TEXT         NOT NULL,\n" +
                        "    french        TEXT         NOT NULL,\n" +
                        "    reading       TEXT         NOT NULL,\n" +
                        "    level         INTEGER,\n" +
                        "    count_try     INTEGER,\n" +
                        "    count_success INTEGER,\n" +
                        "    count_fail    INTEGER,\n" +
                        "    is_kana       BOOLEAN,\n" +
                        "    repetition    INTEGER (-1),\n" +
                        "    points        INTEGER,\n" +
                        "    base_category INTEGER,\n" +
                        "    isSelected    INTEGER      DEFAULT (0),\n" +
                        "    sentence_id   INTEGER      DEFAULT (-1) \n" +
                        ");"

                val queryInsertOldIntoNew = "INSERT INTO words " +
                        "(_id, japanese, english, french, reading, level, count_try, count_success, " +
                        "       count_fail, is_kana, repetition, points, base_category, isSelected) " +
                        "SELECT _id, japanese, english, french, reading, level, count_try, count_success, " +
                        "       count_fail, is_kana, repetition, points, base_category, isSelected " +
                        "FROM OLD_words"

                val queryDropOldTable = "DROP TABLE OLD_words"

                database.use {
                    it.execSQL(queryRenameOldTable)
                    it.execSQL(queryCreateNewTable)
                    it.execSQL(queryInsertOldIntoNew)
                    it.execSQL(queryDropOldTable)
                }
            }
        }

    }

}

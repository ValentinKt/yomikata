package com.jehutyno.yomikata.repository.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.jehutyno.yomikata.dao.WordDao
import com.jehutyno.yomikata.model.Word
import com.jehutyno.yomikata.repository.WordRepository
import com.jehutyno.yomikata.repository.database.RoomQuizWord
import com.jehutyno.yomikata.repository.database.RoomWords
import com.jehutyno.yomikata.util.Category
import com.jehutyno.yomikata.util.HiraganaUtils
import com.jehutyno.yomikata.util.Level
import com.jehutyno.yomikata.util.QuizType
import com.jehutyno.yomikata.util.getLevelFromPoints
import com.jehutyno.yomikata.util.getNextLevel
import kotlinx.coroutines.flow.map
import java.security.InvalidParameterException


/**
 * Created by jehutyno on 08/10/2016.
 */
class WordSource(private val wordDao: WordDao) : WordRepository {

    override suspend fun getAllWords(): List<Word> {
        return wordDao.getAllWords().map { it.toWord() }
    }

    override fun getWords(quizId: Long) : Flow<List<Word>> {
        val roomWordsList = wordDao.getWords(quizId)
        return roomWordsList.map { list ->
            list.map {
                it.toWord()
            }
        }
    }

    override fun getWords(quizIds: LongArray) : Flow<List<Word>> {
        val roomWordsList = wordDao.getWords(quizIds)
        return roomWordsList.map { list ->
            list.map {
                it.toWord()
            }
        }
    }

    /**
     * Get words by level
     *
     * @param quizIds Ids of the quiz of the returned words
     * @param level The level of the word. If level = null, words of any level are returned
     * @return Flow of List of Words with the specified level in a quiz of the given quizIds
     */
    override fun getWordsByLevel(quizIds: LongArray, level: Level?) : Flow<List<Word>> {
        if (level == null) {
            return getWords(quizIds)
        }

        val levelsArray = arrayListOf(level)

        val roomWordsList = wordDao.getWordsByLevels(quizIds, levelsArray.map { it.level }.toIntArray())
        return roomWordsList.map { list ->
            list.map {
                it.toWord()
            }
        }
    }

    override suspend fun getWordsByRepetition(wordIds: LongArray, repetition: Int, limit: Int): ArrayList<Word> {
        return wordDao.getWordsByRepetition(wordIds, repetition, limit).map { it.toWord() } as ArrayList<Word>
    }

    override suspend fun getWordsByMinRepetition(wordIds: LongArray, minRepetition: Int, limit: Int): ArrayList<Word> {
        return wordDao.getWordsByMinRepetition(wordIds, minRepetition, limit).map { it.toWord() } as ArrayList<Word>
    }

    override suspend fun getRandomWords(
        wordId: Long,
        answer: String,
        wordSize: Int,
        limit: Int,
        quizType: QuizType
    ): ArrayList<Word> {
        val wordsTableName = "words" // CHANGE THIS IF WORDS TABLE NAME IS CHANGED
        val column = when (quizType) {
            QuizType.TYPE_PRONUNCIATION -> "${wordsTableName}.reading"
            QuizType.TYPE_PRONUNCIATION_QCM -> "${wordsTableName}.reading"
            QuizType.TYPE_AUDIO -> "${wordsTableName}.japanese"
            QuizType.TYPE_EN_JAP -> "${wordsTableName}.japanese"
            QuizType.TYPE_JAP_EN -> "${wordsTableName}.japanese"
            else -> "${wordsTableName}.japanese"
        }

        val wordIds = mutableListOf<Long>()
        var tryWordSize = wordSize
        while (wordIds.size <= 1 && tryWordSize > 0) {
            wordIds += wordDao.getWordsOfSizeRelatedTo(wordId, tryWordSize)
            tryWordSize--   // get smaller sizes in case there are no other words of the same size
        }

        // use @RawQuery since column names cannot be inserted in @Query by Room
        val rawQuery = "SELECT * FROM words " +
                       "WHERE _id IN (${wordIds.joinToString(",")}) " +
                       "AND $column != (?) " +
                       "GROUP BY $column ORDER BY RANDOM() LIMIT ?"
        var supportSQLiteQuery = SimpleSQLiteQuery(rawQuery, arrayOf<Any>(answer, limit))

        val roomWordsList = wordDao.getRandomWords(supportSQLiteQuery).toMutableList()

        // try two more times to make sure size is at least 3
        for (i in 1..2) {
            if (roomWordsList.size < limit) {
                supportSQLiteQuery = SimpleSQLiteQuery(rawQuery, arrayOf<Any>(answer, limit - roomWordsList.size))
                val extraRoomWordsList = wordDao.getRandomWords(supportSQLiteQuery)
                roomWordsList += extraRoomWordsList
            }
        }

        return roomWordsList.map { it.toWord() } as ArrayList<Word>
    }

    override fun searchWords(searchString: String) : Flow<List<Word>> {
        val hiragana = HiraganaUtils.toHiragana(searchString)
        val roomWordsList = wordDao.searchWords(searchString, hiragana)
        return roomWordsList.map { list ->
            list.map {
                it.toWord()
            }
        }
    }

    override suspend fun isWordInQuiz(wordId: Long, quizId: Long): Boolean {
        return wordDao.isWordInQuiz(wordId, quizId)
    }

    override suspend fun isWordInQuizzes(wordId: Long, quizIds: Array<Long>): ArrayList<Boolean> {
        val isInQuizArrayList: ArrayList<Boolean> = arrayListOf()
        for (quizId in quizIds)
            isInQuizArrayList.add(isWordInQuiz(wordId, quizId))

        return isInQuizArrayList
    }

    override suspend fun getWordById(wordId: Long): Word {
        return wordDao.getWordById(wordId)!!.toWord()
    }

    override fun getWordsByIds(wordIds: LongArray): Flow<List<Word>> {
        return wordDao.getWordsByIds(wordIds).map { lst ->
            lst.map { it.toWord() }
        }
    }

    override suspend fun deleteAllWords() {
        wordDao.deleteAllWords()
    }

    override suspend fun deleteWord(wordId: Long) {
        val roomWord = wordDao.getWordById(wordId)
        if (roomWord != null)
            wordDao.deleteWord(roomWord)
    }

    /**
     * Update word points
     *
     * Updates the words Points and associated Level
     */
    override suspend fun updateWordPoints(wordId: Long, points: Int) {
        wordDao.updateWordLevelAndPoints(wordId, getLevelFromPoints(points).level, points)
    }

    override suspend fun updateWordLevel(wordId: Long, level: Level) {
        wordDao.updateWordLevel(wordId, level.level)
    }

    /**
     * Update word points
     *
     * Updates the words Points and associated Level
     */
    override suspend fun updateWordPoints(wordIds: LongArray, points: IntArray) {
        if (wordIds.size != points.size) {
            throw InvalidParameterException("wordIds and points Array sizes do not match: " +
                    "${wordIds.size} != ${points.size}")
        }
        val levels = points.map { getLevelFromPoints(it).level }.toIntArray()
        wordDao.updateWordLevelAndPoints(wordIds, levels, points)
    }

    override suspend fun updateWordRepetition(wordId: Long, repetition: Int) {
        wordDao.updateWordRepetition(wordId, repetition)
    }

    override suspend fun decreaseWordsRepetition(wordIds: LongArray) {
        wordDao.decreaseWordsRepetition(wordIds)
    }

    override suspend fun updateWord(updateWord: Word, word: Word?) {
        if (word != null) {
            val newWord = Word(
                word.id,
                updateWord.japanese,
                updateWord.english,
                updateWord.french,
                updateWord.reading,
                word.level,
                word.countSuccess,
                word.countFail,
                word.isKana,
                word.repetition,
                word.points,
                word.baseCategory,
                word.isSelected,
                updateWord.sentenceId
            )
            wordDao.updateWord(RoomWords.from(newWord))
        } else {
            val newWord = Word(
                0, updateWord.japanese, updateWord.english, updateWord.french,
                updateWord.reading, Level.LOW, 0, 0,
                0, 0, 0, Category.HOME, 0, 0
            )
            wordDao.updateWord(RoomWords.from(newWord))
        }
    }

    override suspend fun updateWordProgression(updateWord: Word, word: Word) {
        val newWord = Word(
            word.id,
            word.japanese,
            word.english,
            word.french,
            word.reading,
            updateWord.level,
            updateWord.countSuccess,
            updateWord.countFail,
            word.isKana,
            updateWord.repetition,
            updateWord.points,
            word.baseCategory,
            updateWord.isSelected,
            word.sentenceId
        )
        wordDao.updateWord(RoomWords.from(newWord))
    }

    override suspend fun addQuizWord(quizId: Long, wordId: Long) {
        wordDao.addQuizWord(RoomQuizWord(quizId, wordId))
    }

    override suspend fun incrementFail(wordId: Long) {
        wordDao.incrementFail(wordId)
    }

    override suspend fun incrementSuccess(wordId: Long) {
        wordDao.incrementSuccess(wordId)
    }

}

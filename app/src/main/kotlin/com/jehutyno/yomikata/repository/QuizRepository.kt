package com.jehutyno.yomikata.repository

import com.jehutyno.yomikata.model.Quiz
import com.jehutyno.yomikata.util.Category
import com.jehutyno.yomikata.util.Level
import kotlinx.coroutines.flow.Flow


/**
 * Created by valentin on 27/09/2016.
 */
interface QuizRepository {
    fun getQuiz(category: Category): Flow<List<Quiz>>
    suspend fun getQuiz(quizId: Long): Quiz?
    suspend fun saveQuiz(quizName: String, category: Category) : Long
    suspend fun deleteAllQuiz()
    suspend fun deleteQuiz(quizId:Long)
    suspend fun updateQuizName(quizId: Long, quizName: String)
    suspend fun updateQuizSelected(quizId: Long, isSelected: Boolean)
    suspend fun addWordToQuiz(wordId: Long, quizId: Long)
    suspend fun deleteWordFromQuiz(wordId: Long, quizId: Long)
    suspend fun addWordsToQuiz(wordIds: LongArray, quizId: Long)
    suspend fun deleteWordsFromQuiz(wordIds: LongArray, quizId: Long)
    fun countWordsForLevel(quizIds: LongArray, level: Level): Flow<Int>
    fun countWordsForQuizzes(quizIds: LongArray): Flow<Int>
}

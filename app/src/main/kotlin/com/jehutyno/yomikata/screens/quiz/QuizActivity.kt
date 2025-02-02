package com.jehutyno.yomikata.screens.quiz

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.window.OnBackInvokedDispatcher
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.jehutyno.yomikata.R
import com.jehutyno.yomikata.YomikataZKApplication
import com.jehutyno.yomikata.repository.WordRepository
import com.jehutyno.yomikata.util.Extras
import com.jehutyno.yomikata.util.Level
import com.jehutyno.yomikata.util.Prefs
import com.jehutyno.yomikata.util.QuizStrategy
import com.jehutyno.yomikata.util.QuizType
import com.jehutyno.yomikata.util.addOrReplaceFragment
import com.jehutyno.yomikata.util.getParcelableArrayListExtraHelper
import com.jehutyno.yomikata.util.getParcelableArrayListHelper
import com.jehutyno.yomikata.util.getSerializableExtraHelper
import com.jehutyno.yomikata.util.getSerializableHelper
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.di.bind
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.cancelButton
import splitties.alertdialog.appcompat.okButton
import splitties.alertdialog.appcompat.titleResource


class QuizActivity : AppCompatActivity(), DIAware {

    companion object : KLogging()

    // kodein
    override val di by di()
    private val subDI by DI.lazy {
        extend(di)
        bind<QuizContract.Presenter>() with factory {
            view: QuizContract.View ->
            QuizPresenter (
                instance(), instance(), instance(), instance(), view,
                wordIds, quizStrategy, quizTypes,
                instance(arg = lifecycleScope), instance(), lifecycleScope
            )
        }
    }

    private lateinit var quizFragment: QuizFragment

    private lateinit var wordIds: LongArray
    private lateinit var quizStrategy: QuizStrategy
    private var level: Level? = null
    private lateinit var quizTypes: ArrayList<QuizType>


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase, YomikataZKApplication.viewPump))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        AppCompatDelegate.setDefaultNightMode(pref.getInt(Prefs.DAY_NIGHT_MODE.pref, AppCompatDelegate.MODE_NIGHT_YES))
        setContentView(R.layout.activity_quiz)
        if(resources.getBoolean(R.bool.portrait_only)){
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_clear_orange_24dp)
            setDisplayHomeAsUpEnabled(true)
            title = intent.getStringExtra(Extras.EXTRA_QUIZ_TITLE)
        }

        if (savedInstanceState != null) {
            //Restore the fragment's instance
            quizFragment = supportFragmentManager.getFragment(savedInstanceState, "quizFragment") as QuizFragment
            wordIds = savedInstanceState.getLongArray("word_ids")?: longArrayOf()

            quizStrategy = savedInstanceState.getSerializableHelper("quiz_strategy", QuizStrategy::class.java)!!
            level = savedInstanceState.getSerializableHelper("level", Level::class.java)

            quizTypes = savedInstanceState.getParcelableArrayListHelper("quiz_types", QuizType::class.java)?: arrayListOf()
        } else {
            wordIds = intent.getLongArrayExtra(Extras.EXTRA_WORD_IDS) ?: longArrayOf()

            // wordIds is empty, check if quizIds was set, and retrieve the corresponding words
            if (wordIds.isEmpty()) {
                val quizIds = intent.getLongArrayExtra(Extras.EXTRA_QUIZ_IDS) ?: longArrayOf()
                runBlocking {
                    wordIds = di.direct.instance<WordRepository>().getWords(quizIds).first()
                        .map{ it.id }.toLongArray()
                }
            }

            quizStrategy = intent.getSerializableExtraHelper(Extras.EXTRA_QUIZ_STRATEGY, QuizStrategy::class.java)!!

            level = intent.getSerializableExtraHelper(Extras.EXTRA_LEVEL, Level::class.java)

            quizTypes = intent.getParcelableArrayListExtraHelper(Extras.EXTRA_QUIZ_TYPES, QuizType::class.java) ?: arrayListOf()

            val bundle = Bundle()
            bundle.putLongArray(Extras.EXTRA_WORD_IDS, wordIds)
            bundle.putSerializable(Extras.EXTRA_QUIZ_STRATEGY, quizStrategy)
            bundle.putParcelableArrayList(Extras.EXTRA_QUIZ_TYPES, quizTypes)

            quizFragment = QuizFragment(subDI)
            quizFragment.arguments = bundle
        }
        addOrReplaceFragment(R.id.container_content, quizFragment)

        fun askToQuitSession() {
            alertDialog(getString(R.string.quit_quiz)) {
                okButton { finish() }
                cancelButton()
                setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_BACK)
                        finish()
                    true
                }
            }.show()
        }

        // set back button: ask if user wants to quit out of session
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                askToQuitSession()
            }
        } else {
            onBackPressedDispatcher.addCallback(this /* lifecycle owner */) {
                askToQuitSession()
            }
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //Save the fragment's instance
        supportFragmentManager.putFragment(outState, "quizFragment", quizFragment)
        outState.putLongArray("word_ids", wordIds)
        outState.putSerializable("quiz_strategy", quizStrategy)
        outState.putSerializable("level", level)
        outState.putParcelableArrayList("quiz_types", quizTypes)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                alertDialog {
                    titleResource = R.string.quit_quiz
                    okButton { finish() }
                    cancelButton()
                }.show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}

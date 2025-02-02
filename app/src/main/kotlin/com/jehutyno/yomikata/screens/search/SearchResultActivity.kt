package com.jehutyno.yomikata.screens.search

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.jehutyno.yomikata.R
import com.jehutyno.yomikata.YomikataZKApplication
import com.jehutyno.yomikata.util.Prefs
import com.jehutyno.yomikata.util.addOrReplaceFragment
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import org.kodein.di.DIAware
import org.kodein.di.android.di


class SearchResultActivity : AppCompatActivity(), DIAware {

    // kodein
    override val di by di()

    private lateinit var searchResultFragment : SearchResultFragment


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase, YomikataZKApplication.viewPump))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        AppCompatDelegate.setDefaultNightMode(pref.getInt(Prefs.DAY_NIGHT_MODE.pref, AppCompatDelegate.MODE_NIGHT_YES))
        setContentView(R.layout.activity_search)

        if(resources.getBoolean(R.bool.portrait_only)){
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setSupportActionBar(findViewById(R.id.toolbar))

        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_orange_24dp)
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.search_title)
        }

        searchResultFragment = if (savedInstanceState != null) {
            //Restore the fragment's instance
            supportFragmentManager.getFragment(savedInstanceState, "searchResultFragment") as SearchResultFragment
        } else {
            SearchResultFragment(di)
        }
        addOrReplaceFragment(R.id.container_content, searchResultFragment)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //Save the fragment's instance
        supportFragmentManager.putFragment(outState, "searchResultFragment", searchResultFragment)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // Open the navigation drawer when the home icon is selected from the toolbar.
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


}

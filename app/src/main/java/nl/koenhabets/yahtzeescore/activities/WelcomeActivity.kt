package nl.koenhabets.yahtzeescore.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import nl.koenhabets.yahtzeescore.introduction.IntroGameFragment
import nl.koenhabets.yahtzeescore.introduction.IntroMultiFragment
import nl.koenhabets.yahtzeescore.introduction.IntroNameFragment

class WelcomeActivity : AppIntro() {
    private lateinit var multiFragment: IntroMultiFragment
    private lateinit var nameFragment: IntroNameFragment
    private lateinit var gameFragment: IntroGameFragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        gameFragment = IntroGameFragment.newInstance()
        multiFragment = IntroMultiFragment.newInstance()
        nameFragment = IntroNameFragment.newInstance()

        addSlide(gameFragment)
        addSlide(multiFragment)
        addSlide(nameFragment)
        showStatusBar(true)
        isWizardMode = true
        supportActionBar?.hide()

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBarsInsets.top, 0, systemBarsInsets.bottom)
            insets
        }

        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        save()
    }

    override fun onPageSelected(position: Int) {
        super.onPageSelected(position)
        Log.i("page", "page selected")
        if (::multiFragment.isInitialized) {
            if (!multiFragment.multiplayerEnabled() && position == 2) {
                save()
            }
        }
    }

    fun save() {
        val name = nameFragment.getName()
        val multiplayer = multiFragment.multiplayerEnabled()
        val game = gameFragment.getGame()

        val sharedPref = getSharedPreferences("nl.koenhabets.yahtzeescore", MODE_PRIVATE)
        val edit = sharedPref.edit()
        if (multiplayer) {
            edit.putString("name", name)
        }
        edit.putBoolean("multiplayer", multiplayer)
        edit.putBoolean("multiplayerAsked", multiplayer)
        edit.putBoolean("welcomeShown", true)
        edit.putString("game", game.toString())
        edit.apply()
        val myIntent = Intent(this, MainActivity::class.java)
        this.startActivity(myIntent)
        finish()
    }
}
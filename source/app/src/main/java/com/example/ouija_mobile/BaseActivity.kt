package com.example.ouija_mobile

import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity that applies the user-selected theme before inflation.
 * All activities should extend this instead of AppCompatActivity.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun setContentView(layoutResID: Int) {
        applySelectedTheme()
        super.setContentView(layoutResID)
    }

    private fun applySelectedTheme() {
        val sm = SessionManager(this)
        val themeId = when (sm.getTheme()) {
            "Theme.Ouija.Light"     -> R.style.Theme_Ouija_Light
            "Theme.Ouija.Midnight"  -> R.style.Theme_Ouija_Midnight
            "Theme.Ouija.Forest"    -> R.style.Theme_Ouija_Forest
            "Theme.Ouija.Rose"      -> R.style.Theme_Ouija_Rose
            "Theme.Ouija.Solarized" -> R.style.Theme_Ouija_Solarized
            else                    -> R.style.Theme_Ouija
        }
        setTheme(themeId)
    }
}

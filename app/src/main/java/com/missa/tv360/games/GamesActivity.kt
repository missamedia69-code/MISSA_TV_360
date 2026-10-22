package com.missa.tv360.games

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.missa.tv360.DeviceForm
import com.missa.tv360.R
import com.missa.tv360.ui.SystemBarInsets

/** Hôte des jeux : rangées D-pad sur TV, liste tactile sur téléphone/tablette. */
class GamesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val television = DeviceForm.isTelevision(this)
        setTheme(if (television) R.style.Theme_MissaTv else R.style.Theme_MissaTv_Mobile)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)
        if (!television) SystemBarInsets.apply(findViewById(R.id.games_root))
        findViewById<Button>(R.id.games_back).setOnClickListener { finish() }
        if (savedInstanceState == null) {
            val fragment = if (television) GamesFragment.newInstance() else MobileGamesFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.games_container, fragment)
                .commitNow()
        }
    }
}

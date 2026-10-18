package com.missa.tv360.games

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.missa.tv360.R

/**
 * Ecran « Jeux video » — heberge [GamesFragment].
 *
 * Pourquoi une Activity dediee : le Browse principal ouvre cet ecran. Or un
 * [androidx.leanback.app.BrowseSupportFragment] place sur une back stack
 * n'ajoute pas son fragment enfant de lignes (comportement Leanback connu) et
 * la grille de jeux resterait vide. En donnant a l'ecran sa propre Activity,
 * le fragment est ajoute par commitNow() — exactement comme le Browse
 * principal — et le retour cle « back » revient naturellement au catalogue.
 */
class GamesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.games_container, GamesFragment.newInstance())
                .commitNow()
        }
    }
}

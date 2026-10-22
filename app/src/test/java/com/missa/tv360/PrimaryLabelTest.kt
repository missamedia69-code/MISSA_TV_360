package com.missa.tv360

import com.missa.tv360.data.ContentKind
import com.missa.tv360.ui.primaryLabelRes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le bouton principal de la fiche détail change de libellé selon le contenu :
 * direct → « Regarder en direct », audio → « Écouter », sinon → « Lire ».
 */
class PrimaryLabelTest {

    @Test
    fun primaryLabel_isWatchLiveForLiveContent() {
        assertEquals(R.string.action_watch_live, primaryLabelRes(ContentKind.LIVE_TV))
        assertEquals(R.string.action_watch_live, primaryLabelRes(ContentKind.LIVE_SPORT))
    }

    @Test
    fun primaryLabel_isListenForAudioContent() {
        assertEquals(R.string.action_listen, primaryLabelRes(ContentKind.RADIO))
        assertEquals(R.string.action_listen, primaryLabelRes(ContentKind.PODCAST))
        assertEquals(R.string.action_listen, primaryLabelRes(ContentKind.MUSIC))
    }

    @Test
    fun primaryLabel_isPlayOtherwise() {
        assertEquals(R.string.action_play, primaryLabelRes(ContentKind.MOVIE))
        assertEquals(R.string.action_play, primaryLabelRes(ContentKind.SERIES))
        assertEquals(R.string.action_play, primaryLabelRes(ContentKind.SHORT))
        assertEquals(R.string.action_play, primaryLabelRes(ContentKind.GAME))
    }
}

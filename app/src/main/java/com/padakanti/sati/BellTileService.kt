package com.padakanti.sati

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** A Quick Settings tile that turns the bell on or off. */
class BellTileService : TileService() {
    override fun onStartListening() = update()

    override fun onClick() {
        val store = Store(this)
        store.enabled = !store.enabled
        // Turning the bell on again also ends any pause.
        if (store.enabled) store.pausedUntil = 0
        Alarms.reschedule(this)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val store = Store(this)
        tile.state = if (store.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = Status.line(this, store)
        tile.updateTile()
    }
}

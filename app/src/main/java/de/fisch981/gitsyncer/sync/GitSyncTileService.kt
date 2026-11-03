package de.fisch981.gitsyncer.sync

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import de.fisch981.gitsyncer.R

@RequiresApi(Build.VERSION_CODES.N)
class GitSyncTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.quick_settings_tile_label)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Synchronisiert..."
        tile.updateTile()

        SyncScheduler.triggerOneShotSync(applicationContext)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val currentTile = qsTile ?: return@postDelayed
            currentTile.state = Tile.STATE_INACTIVE
            currentTile.label = getString(R.string.quick_settings_tile_label)
            currentTile.updateTile()
        }, 2000)
    }
}

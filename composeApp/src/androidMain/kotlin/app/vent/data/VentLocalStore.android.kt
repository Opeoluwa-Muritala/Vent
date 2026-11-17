package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "demo_state")
internal data class DemoStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val confirmedSeat: String,
    val checkedIn: Boolean,
    val organizerMode: Boolean,
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Dao
internal interface DemoStateDao {
    @Query("SELECT * FROM demo_state WHERE id = 1 LIMIT 1")
    suspend fun load(): DemoStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: DemoStateEntity)
}

@Database(entities = [DemoStateEntity::class], version = 1, exportSchema = false)
internal abstract class VentDatabase : RoomDatabase() {
    abstract fun demoStateDao(): DemoStateDao
}

private class RoomVentLocalStore(private val dao: DemoStateDao) : VentLocalStore {
    override suspend fun load(): VentLocalState = dao.load()?.let {
        VentLocalState(it.confirmedSeat, it.checkedIn, it.organizerMode)
    } ?: VentLocalState()

    override suspend fun save(state: VentLocalState) {
        dao.save(DemoStateEntity(confirmedSeat = state.confirmedSeat, checkedIn = state.checkedIn, organizerMode = state.organizerMode))
    }
}

@Composable
actual fun rememberVentLocalStore(): VentLocalStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val database = Room.databaseBuilder(context, VentDatabase::class.java, "vent-demo.db").build()
        RoomVentLocalStore(database.demoStateDao())
    }
}

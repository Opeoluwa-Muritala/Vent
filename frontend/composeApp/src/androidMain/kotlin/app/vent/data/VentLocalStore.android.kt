package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Entity(tableName = "demo_state")
internal data class DemoStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val confirmedSeat: String,
    val checkedIn: Boolean,
    val organizerMode: Boolean,
    @ColumnInfo(defaultValue = "'{}'") val settings: String = "{}",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
internal interface DemoStateDao {
    @Query("SELECT * FROM demo_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<DemoStateEntity?>

    @Query("SELECT * FROM demo_state WHERE id = 1 LIMIT 1")
    suspend fun load(): DemoStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: DemoStateEntity)
}

@Database(entities = [DemoStateEntity::class], version = 2, exportSchema = false)
internal abstract class VentDatabase : RoomDatabase() {
    abstract fun demoStateDao(): DemoStateDao
}

private class RoomVentLocalStore(
    private val dao: DemoStateDao,
) : VentLocalStore {
    override fun changes(): Flow<VentLocalState> =
        dao.observe().filterNotNull().map {
            VentLocalState(it.confirmedSeat, it.checkedIn, it.organizerMode, Json.decodeFromString<SummitSettings>(it.settings))
        }

    override suspend fun load(): VentLocalState =
        dao.load()?.let {
            VentLocalState(it.confirmedSeat, it.checkedIn, it.organizerMode, Json.decodeFromString<SummitSettings>(it.settings))
        } ?: VentLocalState()

    override suspend fun save(state: VentLocalState) {
        dao.save(
            DemoStateEntity(
                confirmedSeat = state.confirmedSeat,
                checkedIn = state.checkedIn,
                organizerMode = state.organizerMode,
                settings = Json.encodeToString(state.settings),
            ),
        )
    }
}

@Composable
actual fun rememberVentLocalStore(account: String): VentLocalStore {
    val context = LocalContext.current.applicationContext
    return remember(context, account) {
        val name =
            if (account.isEmpty()) {
                "vent-demo.db"
            } else {
                "vent-account-${java.util.UUID.nameUUIDFromBytes(
                    account.encodeToByteArray(),
                )}.db"
            }
        val database =
            Room
                .databaseBuilder(context, VentDatabase::class.java, name)
                .addMigrations(
                    object : Migration(1, 2) {
                        override fun migrate(db: SupportSQLiteDatabase) {
                            db.execSQL("ALTER TABLE demo_state ADD COLUMN settings TEXT NOT NULL DEFAULT '{}'")
                        }
                    },
                ).build()
        RoomVentLocalStore(database.demoStateDao())
    }
}

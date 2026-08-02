package ir.keyvanadili.noghteyab.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** Populates the table with sensible defaults the first time it's empty. */
    suspend fun seedDefaultsIfEmpty(defaults: List<String>) {
        if (count() == 0) {
            defaults.forEach { insert(CategoryEntity(name = it)) }
        }
    }
}

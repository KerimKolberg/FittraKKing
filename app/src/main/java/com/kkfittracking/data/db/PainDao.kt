package com.kkfittracking.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PainDao {
    @Query("SELECT * FROM tendon_pain WHERE deletedAt IS NULL ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TendonPainEntity>>

    @Query(
        """
        SELECT * FROM tendon_pain
        WHERE tendon = :tendon AND date = :date AND moment = :moment AND deletedAt IS NULL
        LIMIT 1
        """,
    )
    suspend fun get(tendon: String, date: LocalDate, moment: String): TendonPainEntity?

    @Insert
    suspend fun insert(entry: TendonPainEntity)

    @Update
    suspend fun update(entry: TendonPainEntity)

    @Query("UPDATE tendon_pain SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

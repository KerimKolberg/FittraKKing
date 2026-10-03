package com.kkfittracking.data

import com.kkfittracking.data.db.PainDao
import com.kkfittracking.data.db.TendonPainEntity
import com.kkfittracking.model.PainEntry
import com.kkfittracking.model.PainMoment
import com.kkfittracking.model.Tendon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** The tendon pain log. */
class PainRepository(
    private val dao: PainDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** All ratings, newest first; rows with an unknown tendon or moment (from a newer version) are left out. */
    val entries: Flow<List<PainEntry>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toEntry() } }

    /** Rates [tendon] at [moment] on [date], replacing an earlier rating of the same moment. */
    suspend fun rate(tendon: Tendon, date: LocalDate, moment: PainMoment, score: Int, note: String = "") {
        val time = now()
        val value = score.coerceIn(0, 10)
        val existing = dao.get(tendon.name, date, moment.name)
        if (existing != null) {
            dao.update(existing.copy(score = value, note = note, updatedAt = time))
        } else {
            dao.insert(
                TendonPainEntity(
                    id = newId(), date = date, tendon = tendon.name, moment = moment.name, score = value, note = note,
                    createdAt = time, updatedAt = time,
                ),
            )
        }
    }

    suspend fun delete(id: String) = dao.softDelete(id, now())
}

private fun TendonPainEntity.toEntry(): PainEntry? {
    val tendon = Tendon.entries.firstOrNull { it.name == tendon } ?: return null
    val moment = PainMoment.entries.firstOrNull { it.name == moment } ?: return null
    return PainEntry(id, date, tendon, moment, score, note)
}

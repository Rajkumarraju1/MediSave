package com.pralayakaveri.medisave.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicines: List<MedicineEntity>)

    @Query("""
        SELECT * FROM medicines 
        WHERE normalizedSalt = :salt 
        AND strength = :strength 
        ORDER BY 
        CASE WHEN packSize IS NOT NULL AND packSize > 0 THEN (price * 1.0 / packSize) ELSE price END ASC
    """)
    fun getAlternativesBySalt(salt: String, strength: String): Flow<List<MedicineEntity>>
    
    @Query("SELECT * FROM medicines WHERE brandName LIKE '%' || :query || '%' COLLATE NOCASE")
    suspend fun searchBrand(query: String): List<MedicineEntity>
    
    @Query("SELECT COUNT(*) FROM medicines")
    suspend fun getCount(): Int
}

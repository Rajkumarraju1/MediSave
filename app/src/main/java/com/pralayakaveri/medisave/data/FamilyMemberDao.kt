package com.pralayakaveri.medisave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members")
    fun observeAllMembers(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members")
    suspend fun getAllMembers(): List<FamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: FamilyMemberEntity)

    @Update
    suspend fun update(member: FamilyMemberEntity)

    @Query("SELECT * FROM family_members WHERE id = :id LIMIT 1")
    suspend fun getMemberById(id: String): FamilyMemberEntity?

    @Delete
    suspend fun delete(member: FamilyMemberEntity)

    @Query("UPDATE family_members SET profileId = :newProfileId WHERE profileId = :oldProfileId")
    suspend fun migrateProfileId(oldProfileId: String, newProfileId: String)
}

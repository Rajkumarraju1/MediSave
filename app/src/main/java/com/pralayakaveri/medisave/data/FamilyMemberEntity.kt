package com.pralayakaveri.medisave.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "family_members")
data class FamilyMemberEntity(
    @PrimaryKey val id: String,
    val name: String,
    val age: String,
    val relation: String,
    val condition: String,
    val profileId: String = "primary"
)

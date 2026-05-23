package com.pralayakaveri.medisave.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val name: String,
    val phone: String,
    val email: String,
    val age: String,
    val gender: String,
    val conditions: List<String>,
    val language: String,
    val connectionCode: String = ""
)

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    fun getPrimaryUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getPrimaryUser(): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clearAll()
}

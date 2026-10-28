package com.ouor.mycoupon.data

import androidx.room.AutoMigration
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun reviewToString(m: Map<String, List<String>>): String = Json.encodeToString(m)
    @TypeConverter fun stringToReview(s: String): Map<String, List<String>> = Json.decodeFromString(s)

    @TypeConverter fun boxesToString(m: Map<String, List<Double>>): String = Json.encodeToString(m)
    @TypeConverter fun stringToBoxes(s: String): Map<String, List<Double>> = Json.decodeFromString(s)

    @TypeConverter fun datesToString(l: List<LocalDate>): String = l.joinToString(",")
    @TypeConverter fun stringToDates(s: String): List<LocalDate> = if (s.isEmpty()) emptyList() else s.split(",").map(LocalDate::parse)

    @TypeConverter fun intsToString(l: List<Int>?): String? = l?.joinToString(",")
    @TypeConverter fun stringToInts(s: String?): List<Int>? = s?.let { if (it.isEmpty()) emptyList() else it.split(",").map(String::toInt) }
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupon ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Coupon>>

    @Query("SELECT * FROM coupon")
    suspend fun all(): List<Coupon>

    @Query("SELECT * FROM coupon WHERE id = :id")
    fun observe(id: Long): Flow<Coupon?>

    @Query("SELECT * FROM coupon WHERE id = :id")
    suspend fun get(id: Long): Coupon?

    // A2-08: enum values as arguments, so the converter names them (no string literal to drift)
    @Query("SELECT * FROM coupon WHERE status = :status")
    suspend fun active(status: CouponStatus = CouponStatus.ACTIVE): List<Coupon>

    @Query("SELECT * FROM coupon WHERE codeKey = :key")
    suspend fun byCodeKey(key: String): List<Coupon>

    @Query("SELECT * FROM coupon WHERE imageHash = :hash")
    suspend fun byImageHash(hash: String): List<Coupon>

    @Query("SELECT DISTINCT brand FROM coupon WHERE brand IS NOT NULL ORDER BY brand")
    fun observeBrands(): Flow<List<String>>

    @Insert suspend fun insert(c: Coupon): Long
    @Update suspend fun update(c: Coupon)
    @Delete suspend fun delete(c: Coupon)
    @Query("DELETE FROM coupon") suspend fun deleteAll()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_log WHERE couponId = :couponId ORDER BY at DESC, id DESC")
    fun observe(couponId: Long): Flow<List<UsageLog>>

    @Query("SELECT * FROM usage_log WHERE couponId = :couponId ORDER BY at DESC, id DESC")
    suspend fun list(couponId: Long): List<UsageLog>

    @Query("SELECT * FROM usage_log")
    suspend fun all(): List<UsageLog>

    @Insert suspend fun insert(l: UsageLog): Long
    @Delete suspend fun delete(l: UsageLog)
}

@Dao
interface ScanDao {
    /** settled images: everything except found-but-not-yet-reviewed coupons (S9 offers those again after a restart) */
    @Query("SELECT * FROM scan_log WHERE result != :pending")
    suspend fun settled(pending: ScanResult = ScanResult.COUPON): List<ScanLog>

    @Query("SELECT * FROM scan_log WHERE mediaId = :id")
    suspend fun get(id: Long): ScanLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(l: ScanLog)

    @Query("DELETE FROM scan_log") suspend fun deleteAll()
}

/**
 * Schema v1 is frozen in app/schemas (code review A-01). Never use destructive migration: a schema change bumps the version
 * and ships N.json + a Migration (or AutoMigration) + a MigrationTest case (docs/decisions.md).
 */
@Database(
    entities = [Coupon::class, UsageLog::class, ScanLog::class],
    version = 3,
    exportSchema = true,
    // v2 (cycle 2): coupon.authCode (C2-19), coupon.givenTo (C2-25), both nullable TEXT, so existing rows keep every value.
    // v3 (code review 2, A2-01): MIGRATION_2_3 below
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coupons(): CouponDao
    abstract fun usage(): UsageDao
    abstract fun scans(): ScanDao

    companion object {
        /**
         * v3 (A2-01, before the first release): image paths become file names (SQLite has no basename: rtrim drops the name's
         * characters, leaving the folder, which replace() then removes), and scan_log gets dateAdded (0 = unknown, A-12).
         */
        private val V3 = listOf(
            "UPDATE coupon SET imagePath = replace(imagePath, rtrim(imagePath, replace(imagePath, '/', '')), ''), " +
                "thumbPath = replace(thumbPath, rtrim(thumbPath, replace(thumbPath, '/', '')), '')",
            "ALTER TABLE scan_log ADD COLUMN dateAdded INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = V3.forEach(db::execSQL)
            override fun migrate(connection: SQLiteConnection) = V3.forEach { connection.execSQL(it) }
        }
    }
}

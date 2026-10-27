package com.ouor.mycoupon.data

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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

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

    @Query("SELECT * FROM coupon WHERE status = 'ACTIVE'")
    suspend fun active(): List<Coupon>

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
    /** settled images: everything except found-but-not-yet-reviewed coupons (those are re-offered after a restart) */
    @Query("SELECT mediaId FROM scan_log WHERE result != 'COUPON'")
    suspend fun settledIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(l: ScanLog)

    @Query("DELETE FROM scan_log") suspend fun deleteAll()
}

@Database(entities = [Coupon::class, UsageLog::class, ScanLog::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coupons(): CouponDao
    abstract fun usage(): UsageDao
    abstract fun scans(): ScanDao
}

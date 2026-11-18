package com.arash.daftar

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * همه‌ی ردیف‌ها حذف واقعی نمی‌شوند (deleted=true) تا قابل برگشت باشند.
 * dirty=true یعنی این تغییر هنوز روی Supabase بکاپ نشده است.
 */
@Entity(tableName = "customers", indices = [Index("dirty")])
data class Customer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

object Kind {
    const val DEBT = "debt"
    const val PAYMENT = "payment"
}

@Entity(tableName = "txs", indices = [Index("customerId"), Index("dirty")])
data class Tx(
    @PrimaryKey val id: String,
    val customerId: String,
    /** debt یا payment */
    val kind: String,
    /** تومان */
    val amount: Long,
    val note: String = "",
    /** تاریخ ثبت بدهی یا پرداخت */
    val date: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true
)

/** یک ردیف صفحه‌ی اصلی */
data class CustomerSummary(
    val id: String,
    val name: String,
    val debts: Long,
    val paid: Long,
    val firstDebt: Long?,
    val lastDebt: Long?
) {
    val balance: Long get() = debts - paid
}

@Dao
interface DaftarDao {
    @Query(
        """
        SELECT c.id AS id, c.name AS name,
          COALESCE(SUM(CASE WHEN t.kind = 'debt' THEN t.amount ELSE 0 END), 0) AS debts,
          COALESCE(SUM(CASE WHEN t.kind = 'payment' THEN t.amount ELSE 0 END), 0) AS paid,
          MIN(CASE WHEN t.kind = 'debt' THEN t.date END) AS firstDebt,
          MAX(CASE WHEN t.kind = 'debt' THEN t.date END) AS lastDebt
        FROM customers c
        LEFT JOIN txs t ON t.customerId = c.id AND t.deleted = 0
        WHERE c.deleted = 0
        GROUP BY c.id
        """
    )
    fun summaries(): Flow<List<CustomerSummary>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun customerFlow(id: String): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun customer(id: String): Customer?

    @Query("SELECT * FROM customers WHERE deleted = 0")
    suspend fun allCustomers(): List<Customer>

    @Query("SELECT * FROM txs WHERE customerId = :customerId AND deleted = 0 ORDER BY date DESC, updatedAt DESC")
    fun txsOf(customerId: String): Flow<List<Tx>>

    @Query("SELECT * FROM txs WHERE id = :id")
    suspend fun tx(id: String): Tx?

    @Query("SELECT * FROM txs WHERE customerId = :customerId")
    suspend fun allTxsOf(customerId: String): List<Tx>

    @Upsert
    suspend fun upsertCustomer(c: Customer)

    @Upsert
    suspend fun upsertTx(t: Tx)

    @Upsert
    suspend fun upsertTxs(t: List<Tx>)

    // ---------- همگام‌سازی

    @Query("SELECT * FROM customers WHERE dirty = 1")
    suspend fun dirtyCustomers(): List<Customer>

    @Query("SELECT * FROM txs WHERE dirty = 1")
    suspend fun dirtyTxs(): List<Tx>

    @Query("UPDATE customers SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markCustomerClean(id: String, updatedAt: Long)

    @Query("UPDATE txs SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markTxClean(id: String, updatedAt: Long)

    @Query("SELECT (SELECT COUNT(*) FROM customers WHERE dirty = 1) + (SELECT COUNT(*) FROM txs WHERE dirty = 1)")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun customerCount(): Int
}

@Database(entities = [Customer::class, Tx::class], version = 1, exportSchema = false)
abstract class DaftarDb : RoomDatabase() {
    abstract fun dao(): DaftarDao

    companion object {
        @Volatile
        private var instance: DaftarDb? = null

        fun get(ctx: Context): DaftarDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, DaftarDb::class.java, "daftar.db")
                .build().also { instance = it }
        }
    }
}

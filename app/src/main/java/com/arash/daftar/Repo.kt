package com.arash.daftar

import android.content.Context

/** همه‌ی تغییرها از اینجا رد می‌شوند تا بعد از هر تغییر بکاپ Supabase شروع شود */
class Repo(private val ctx: Context) {
    val dao: DaftarDao = DaftarDb.get(ctx).dao()

    private fun now() = System.currentTimeMillis()

    private fun changed() = Sync.request(ctx)

    /** مشتری با همین اسم (بدون حساسیت به ی/ک و فاصله) یا یک مشتری جدید */
    suspend fun findOrCreateCustomer(rawName: String): Customer {
        val name = Fa.cleanName(rawName)
        val key = Fa.norm(name)
        dao.allCustomers().firstOrNull { Fa.norm(it.name) == key }?.let { return it }
        val t = now()
        val c = Customer(id = newId(), name = name, createdAt = t, updatedAt = t)
        dao.upsertCustomer(c)
        return c
    }

    suspend fun addDebt(customerName: String, amount: Long, note: String, date: Long): String {
        val c = findOrCreateCustomer(customerName)
        addTx(c.id, Kind.DEBT, amount, note, date)
        return c.id
    }

    suspend fun addTx(customerId: String, kind: String, amount: Long, note: String, date: Long) {
        dao.upsertTx(
            Tx(
                id = newId(), customerId = customerId, kind = kind, amount = amount,
                note = note.trim(), date = date, updatedAt = now()
            )
        )
        changed()
    }

    suspend fun editTx(t: Tx, amount: Long, note: String, date: Long) {
        dao.upsertTx(t.copy(amount = amount, note = note.trim(), date = date, updatedAt = now(), dirty = true))
        changed()
    }

    suspend fun setTxDeleted(id: String, deleted: Boolean) {
        val t = dao.tx(id) ?: return
        dao.upsertTx(t.copy(deleted = deleted, updatedAt = now(), dirty = true))
        changed()
    }

    /** اگر اسم جدید مال مشتری دیگری باشد false برمی‌گرداند */
    suspend fun renameCustomer(id: String, newName: String): Boolean {
        val name = Fa.cleanName(newName)
        if (name.isEmpty()) return false
        val key = Fa.norm(name)
        if (dao.allCustomers().any { it.id != id && Fa.norm(it.name) == key }) return false
        val c = dao.customer(id) ?: return false
        dao.upsertCustomer(c.copy(name = name, updatedAt = now(), dirty = true))
        changed()
        return true
    }

    suspend fun setCustomerNote(id: String, note: String) {
        val c = dao.customer(id) ?: return
        dao.upsertCustomer(c.copy(note = note.trim(), updatedAt = now(), dirty = true))
        changed()
    }

    /** حذف مشتری همراه با همه‌ی تراکنش‌هایش (قابل برگشت) */
    suspend fun setCustomerDeleted(id: String, deleted: Boolean) {
        val c = dao.customer(id) ?: return
        // تراکنش‌ها دست نمی‌خورند؛ با حذف مشتری از جمع‌ها بیرون می‌روند و با برگرداندنش برمی‌گردند
        dao.upsertCustomer(c.copy(deleted = deleted, updatedAt = now(), dirty = true))
        changed()
    }
}

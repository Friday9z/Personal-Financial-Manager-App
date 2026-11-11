package com.pfm.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** ไฟล์ใบเสร็จเก็บในพื้นที่ส่วนตัวของแอป (filesDir/receipts) ไม่ส่งออกนอกเครื่องเอง */
object Receipts {
    fun dir(ctx: Context) = File(ctx.filesDir, "receipts").apply { mkdirs() }
    fun file(ctx: Context, name: String?): File? = name?.let { File(dir(ctx), File(it).name) }?.takeIf { it.exists() }
}

/**
 * Backup = ไฟล์ .zip ประกอบด้วย pfm.db + receipts/*  (ไม่รวม PIN — PIN อยู่ใน Keystore ของเครื่องนี้)
 * Restore = แทนที่ฐานข้อมูลทั้งหมด แล้วรีสตาร์ทแอป
 */
object Backup {
    private const val DB_ENTRY = "pfm.db"
    private const val REC_PREFIX = "receipts/"
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    fun export(ctx: Context, db: AppDatabase, dest: Uri) {
        // รวม WAL ลงไฟล์หลักก่อนคัดลอก
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val dbFile = ctx.getDatabasePath(AppDatabase.FILE_NAME)
        ctx.contentResolver.openOutputStream(dest, "w")!!.use { os ->
            ZipOutputStream(os).use { z ->
                z.putNextEntry(ZipEntry(DB_ENTRY)); dbFile.inputStream().use { it.copyTo(z) }; z.closeEntry()
                Receipts.dir(ctx).listFiles()?.filter { it.isFile }?.forEach { f ->
                    z.putNextEntry(ZipEntry(REC_PREFIX + f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
                }
            }
        }
    }

    /** คืนข้อความ error หรือ null เมื่อสำเร็จ (ผู้เรียกต้องรีสตาร์ทแอปหลังสำเร็จ) */
    fun restore(ctx: Context, src: Uri): String? {
        val tmp = File(ctx.cacheDir, "restore_tmp").apply { deleteRecursively(); mkdirs() }
        var hasDb = false
        try {
            ctx.contentResolver.openInputStream(src)?.use { ins ->
                ZipInputStream(ins).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        // ป้องกัน zip-slip: รับเฉพาะชื่อที่กำหนด และใช้เฉพาะชื่อไฟล์ล้วน
                        val name = e.name
                        val out: File? = when {
                            name == DB_ENTRY -> File(tmp, DB_ENTRY).also { hasDb = true }
                            name.startsWith(REC_PREFIX) && !e.isDirectory ->
                                File(File(tmp, "receipts").apply { mkdirs() }, File(name).name)
                            else -> null
                        }
                        out?.outputStream()?.use { z.copyTo(it) }
                        e = z.nextEntry
                    }
                }
            } ?: return "เปิดไฟล์ไม่ได้"
            if (!hasDb) return "ไฟล์นี้ไม่ใช่ไฟล์สำรองของแอป"
            val head = ByteArray(16)
            File(tmp, DB_ENTRY).inputStream().use { if (it.read(head) < 16) return "ไฟล์ฐานข้อมูลเสียหาย" }
            if (!head.contentEquals(SQLITE_MAGIC)) return "ไฟล์ฐานข้อมูลไม่ถูกต้อง"

            AppDatabase.closeAndReset()
            val target = ctx.getDatabasePath(AppDatabase.FILE_NAME)
            File(target.path + "-wal").delete(); File(target.path + "-shm").delete()
            File(tmp, DB_ENTRY).copyTo(target, overwrite = true)

            val rd = Receipts.dir(ctx)
            rd.listFiles()?.forEach { it.delete() }
            File(tmp, "receipts").listFiles()?.forEach { it.copyTo(File(rd, it.name), overwrite = true) }
            return null
        } catch (e: Exception) {
            return "กู้คืนไม่สำเร็จ: ${e.message}"
        } finally {
            tmp.deleteRecursively()
        }
    }
}

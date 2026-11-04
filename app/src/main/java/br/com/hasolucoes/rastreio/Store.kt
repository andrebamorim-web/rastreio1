package br.com.hasolucoes.rastreio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Point(
    val ts: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float,
    val battery: Int,
    val mock: Boolean
)

data class Event(val ts: Long, val type: String, val detail: String)

/** Banco local, na área privada do app (o vendedor não tem acesso). */
class Store private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, "rastreio.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE points(ts INTEGER NOT NULL, lat REAL, lon REAL, acc REAL, bat INTEGER, mock INTEGER)")
        db.execSQL("CREATE INDEX idx_points_ts ON points(ts)")
        db.execSQL("CREATE TABLE events(ts INTEGER NOT NULL, type TEXT, detail TEXT)")
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts)")
        db.execSQL("CREATE TABLE sent(day TEXT PRIMARY KEY, ts INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addPoint(p: Point) {
        writableDatabase.insert("points", null, ContentValues().apply {
            put("ts", p.ts); put("lat", p.lat); put("lon", p.lon)
            put("acc", p.acc); put("bat", p.battery); put("mock", if (p.mock) 1 else 0)
        })
    }

    fun addEvent(type: String, detail: String = "") {
        writableDatabase.insert("events", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("type", type); put("detail", detail)
        })
    }

    fun points(from: Long, to: Long): List<Point> =
        readableDatabase.rawQuery(
            "SELECT ts, lat, lon, acc, bat, mock FROM points WHERE ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            val out = ArrayList<Point>(c.count)
            while (c.moveToNext()) {
                out += Point(c.getLong(0), c.getDouble(1), c.getDouble(2), c.getFloat(3), c.getInt(4), c.getInt(5) == 1)
            }
            out
        }

    fun events(from: Long, to: Long): List<Event> =
        readableDatabase.rawQuery(
            "SELECT ts, type, detail FROM events WHERE ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            val out = ArrayList<Event>(c.count)
            while (c.moveToNext()) out += Event(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")
            out
        }

    fun lastPointTs(): Long? =
        readableDatabase.rawQuery("SELECT MAX(ts) FROM points", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }

    fun isSent(day: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM sent WHERE day = ?", arrayOf(day)).use { it.moveToFirst() }

    fun markSent(day: String) {
        writableDatabase.insertWithOnConflict("sent", null, ContentValues().apply {
            put("day", day); put("ts", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun purge(before: Long) {
        val db = writableDatabase
        db.delete("points", "ts < ?", arrayOf(before.toString()))
        db.delete("events", "ts < ?", arrayOf(before.toString()))
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(ctx.applicationContext).also { instance = it }
            }
    }
}

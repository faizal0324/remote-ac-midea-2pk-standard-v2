package id.midea.remote

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * Protokol IR Midea (48 bit, 38 kHz).
 * Dasar: library open source IRremoteESP8266 (ir_Midea.cpp).
 * Format pesan (urutan kirim, MSB dulu): [A1|tipe] [power/sleep/fan/mode] [suhu] [timer mati] [sensor] [checksum]
 * Setiap pesan dikirim 2x; kali kedua semua bit dibalik.
 */
object MideaIr {
    const val CARRIER = 38000
    private const val HDR = 4480
    private const val BIT_MARK = 560
    private const val ONE_SPACE = 1680
    private const val ZERO_SPACE = 560
    private const val GAP = 5600

    enum class Mode(val bits: Int) { COOL(0), DRY(1), AUTO(2), FAN(4) }
    enum class Fan(val bits: Int) { AUTO(0), LOW(1), MED(2), HIGH(3) }

    data class State(
        val power: Boolean = false,
        val mode: Mode = Mode.COOL,
        val fan: Fan = Fan.AUTO,
        val tempC: Int = 25,
        val sleep: Boolean = false,
        val offTimerMin: Int = 0
    )

    // Perintah khusus (toggle) dari library
    const val SWING = 0xA201FFFFFF7CL
    const val ECO = 0xA202FFFFFF7EL
    const val LIGHT = 0xA208FFFFFF75L
    const val TURBO = 0xA209FFFFFF74L
    const val SELF_CLEAN = 0xA20DFFFFFF70L
    const val QUIET_ON = 0xA212FFFFFF6EL
    const val QUIET_OFF = 0xA213FFFFFF6FL

    fun fromLong(v: Long): IntArray = IntArray(6) { i -> ((v shr (40 - 8 * i)) and 0xFF).toInt() }

    private fun rev8(x: Int): Int {
        var r = 0
        var v = x
        repeat(8) { r = (r shl 1) or (v and 1); v = v shr 1 }
        return r
    }

    private fun checksum(b: IntArray): Int {
        var sum = 0
        for (i in 0..4) sum += rev8(b[i])
        return rev8((256 - (sum and 0xFF)) and 0xFF)
    }

    fun commandBytes(s: State): IntArray {
        val b = IntArray(6)
        b[0] = 0xA1 // header 10100 + tipe "command" 001
        b[1] = (if (s.power) 0x80 else 0) or (if (s.sleep) 0x40 else 0) or (s.fan.bits shl 3) or s.mode.bits
        b[2] = 0x40 or (s.tempC.coerceIn(17, 30) - 17) // Celsius
        val halfHours = (s.offTimerMin.coerceIn(0, 1440)) / 30
        b[3] = if (halfHours == 0) 0xFF else (0x81 or ((halfHours - 1) shl 1))
        b[4] = 0xFF
        b[5] = checksum(b)
        return b
    }

    /** Ubah 6 byte menjadi pola mark/space (mikrodetik) untuk ConsumerIrManager. */
    fun pattern(bytes: IntArray): IntArray {
        val out = ArrayList<Int>()
        for (phase in 0..1) {
            out.add(HDR); out.add(HDR)
            for (byte in bytes) {
                val v = if (phase == 0) byte else (byte.inv() and 0xFF)
                for (i in 7 downTo 0) {
                    out.add(BIT_MARK)
                    out.add(if (((v shr i) and 1) == 1) ONE_SPACE else ZERO_SPACE)
                }
            }
            out.add(BIT_MARK)
            if (phase == 0) out.add(GAP)
        }
        return out.toIntArray()
    }
}

class IrSender(context: Context) {
    private val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean get() = manager?.hasIrEmitter() == true

    fun send(bytes: IntArray): Boolean = try {
        val m = manager
        if (m != null && m.hasIrEmitter()) {
            m.transmit(MideaIr.CARRIER, MideaIr.pattern(bytes))
            true
        } else false
    } catch (e: Exception) {
        false
    }
}

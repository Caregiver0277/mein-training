package de.beispiel.meintraining.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Der Ton am Ende einer Pause: zwei kurze, ansteigende Glockenschläge.
 *
 * Er wird gerechnet statt abgespielt. Eine Tondatei wäre ein Fremdkörper im Projekt – sie
 * müsste beschafft, lizenziert und mitgeliefert werden –, während ein halbe Sekunde langer
 * Zweiklang aus zwei Sinusschwingungen mit Hüllkurve besteht und in Millisekunden entsteht. So
 * lässt sich auch prüfen, was da eigentlich klingt: [chimeSamples] ist reines Kotlin und ohne
 * Gerät zu testen.
 *
 * Angemeldet wird der Ton als Wecker (USAGE_ALARM), genau wie das Vibrieren daneben: Im Studio
 * steht das Handy meistens auf lautlos, und ein Klingelton wäre dort nicht zu hören. Liegt ein
 * Kopfhörer an, geht der Ton dorthin – siehe [headphoneOutput].
 */
object RestTimerSound {

    /**
     * Spielt den Ton und kehrt erst zurück, wenn er verklungen ist.
     *
     * Das Warten gehört dazu: Der Aufrufer ist ein Wecker-Empfänger, und wenn der sich beim
     * System abmeldet, darf sein Prozess beendet werden – mitten im Ton.
     */
    suspend fun play(context: Context) {
        val samples = chime
        // Ein abgebrochener Ton ist ärgerlich, ein Absturz beim Klingeln wäre schlimmer: Kann
        // das Gerät die Spur nicht anlegen – belegt, kein Ausgang, Hersteller-Eigenheit –,
        // bleibt es beim Vibrieren.
        val track = runCatching { buildTrack(samples) }.getOrNull() ?: return
        try {
            // Erst den Ausgang festlegen, dann starten: Ein Wechsel während des Spielens würde
            // den Ton nur zerhacken.
            headphoneOutput(context)?.let { track.setPreferredDevice(it) }
            track.play()
            delay(samples.size * MILLIS_PER_SECOND / SAMPLE_RATE + TAIL_MILLIS)
        } catch (throwable: IllegalStateException) {
            // play() wirft, wenn die Spur zwischenzeitlich ungültig geworden ist.
        } finally {
            // Ohne Freigeben bliebe je Klingeln ein Stück Audiospeicher des Systems belegt.
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    /**
     * Die fertigen Abtastwerte, einmal gerechnet.
     *
     * Der Empfänger wird für jeden Wecker neu angelegt, das Objekt hier bleibt: Läuft die App,
     * kostet jedes weitere Klingeln keine Rechnung mehr. Ist der Prozess zwischendurch beendet
     * worden, entstehen sie eben neu – ein knappes Zehntausend Sinuswerte, unter einer
     * Millisekunde.
     */
    private val chime: ShortArray by lazy { chimeSamples(SAMPLE_RATE) }

    /**
     * Eine Spur, die den ganzen Ton auf einmal aufnimmt (MODE_STATIC).
     *
     * Das ist der Unterschied zum sonst üblichen Nachschieben in Häppchen: Der Ton steht
     * vollständig im Puffer, bevor er beginnt, und kann deshalb nicht ins Stocken geraten,
     * wenn das Gerät gerade beschäftigt ist – und beschäftigt ist es, weil im selben Moment
     * das Vibrieren anläuft und die App aus dem Schlaf kommt.
     */
    private fun buildTrack(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * BYTES_PER_SAMPLE)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        val written = track.write(samples, 0, samples.size)
        if (written < samples.size) {
            track.release()
            throw IllegalStateException("Nur $written von ${samples.size} Werten geschrieben.")
        }
        return track
    }

    /**
     * Der angeschlossene Kopfhörer, falls es einen gibt.
     *
     * Ohne diese Wahl entscheidet die Klangpolitik des Geräts, wohin ein Wecker geht, und die
     * schickt ihn gern zusätzlich oder ausschließlich auf den Lautsprecher – im Studio also
     * dorthin, wo ihn alle außer dem Trainierenden hören. Kommt kein Kopfhörer zurück, bleibt
     * es bei der Vorgabe des Geräts.
     *
     * Der erste passende Ausgang genügt: Mehr als einen Kopfhörer gleichzeitig gibt es in der
     * Praxis nicht, und wenn doch, ist jeder davon besser als der Lautsprecher.
     */
    private fun headphoneOutput(context: Context): AudioDeviceInfo? {
        val manager = context.getSystemService(AudioManager::class.java) ?: return null
        return runCatching {
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            HEADPHONE_TYPES.firstNotNullOfOrNull { type ->
                outputs.firstOrNull { it.type == type }
            }
        }.getOrNull()
    }

    /**
     * Alles, was man sich ins Ohr steckt oder aufsetzt – in der Reihenfolge, in der danach
     * gesucht wird. Kabel zuerst, weil es angeschlossen nur ist, wer es auch trägt; Bluetooth
     * über Musikwiedergabe (A2DP), weil dieselben Hörer daneben als Freisprecheinrichtung
     * gemeldet werden, und dieser Kanal steht nur während eines Telefonats offen.
     *
     * Die Werte sind Konstanten und werden beim Übersetzen eingesetzt; auf älteren
     * Android-Fassungen meldet das Gerät die neueren Arten schlicht nie.
     */
    private val HEADPHONE_TYPES = listOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID
    )

    private const val BYTES_PER_SAMPLE = 2

    /** Zugabe nach dem letzten Abtastwert, damit die Spur nicht im Ausklingen abgeräumt wird. */
    private const val TAIL_MILLIS = 120L
}

/**
 * Rechnet den Zweiklang aus: je Ton eine Sinusschwingung mit weicher Hüllkurve, dazwischen
 * eine kurze Pause.
 *
 * Die Hüllkurve ist der Grund, dass es nach einer Glocke klingt und nicht nach einem Piepser:
 * Ein hart ein- und ausgeschalteter Sinus knackt an beiden Enden, weil die Schwingung
 * mittendrin abreißt. Deshalb steigt sie kurz an und klingt anschließend aus.
 *
 * Herausgegeben werden 16-Bit-Werte in einem Kanal – dieselbe Form, die [AudioTrack] erwartet.
 */
internal fun chimeSamples(sampleRate: Int): ShortArray {
    require(sampleRate > 0) { "Abtastrate muss positiv sein, war $sampleRate." }

    val toneLength = (sampleRate * TONE_MILLIS / MILLIS_PER_SECOND).toInt()
    val gapLength = (sampleRate * GAP_MILLIS / MILLIS_PER_SECOND).toInt()
    // Zwischen zwei Tönen liegt je eine Pause, hinter dem letzten keine.
    val samples = ShortArray(toneLength * TONE_HZ.size + gapLength * (TONE_HZ.size - 1))

    var offset = 0
    TONE_HZ.forEachIndexed { index, frequency ->
        val radiansPerSample = 2.0 * PI * frequency / sampleRate
        for (position in 0 until toneLength) {
            val value = sin(radiansPerSample * position) *
                envelopeAt(position, toneLength) * PEAK_AMPLITUDE
            samples[offset + position] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        offset += toneLength
        if (index < TONE_HZ.lastIndex) offset += gapLength
    }
    return samples
}

/**
 * Lautstärke innerhalb eines Tones: kurz anschwellen, dann ausklingen.
 *
 * Das Ausklingen fällt anfangs schneller als linear ([DECAY_SHAPE] über 1), damit der Ton
 * nicht wie eine Sirene stehen bleibt, sondern wie ein angeschlagenes Metall verhallt.
 */
private fun envelopeAt(position: Int, length: Int): Double {
    val attack = (length * ATTACK_FRACTION).toInt().coerceAtLeast(1)
    if (position < attack) return position.toDouble() / attack
    val remaining = 1.0 - (position - attack).toDouble() / (length - attack).coerceAtLeast(1)
    return remaining.coerceAtLeast(0.0).pow(DECAY_SHAPE)
}

/** Abtastrate; 44,1 kHz beherrscht jedes Gerät ohne Umrechnung. */
private const val SAMPLE_RATE = 44_100

/**
 * Die beiden Töne: A5 und D6.
 *
 * Eine reine Quarte nach oben – der Vierklang jeder Türklingel. Hoch genug, um sich gegen
 * Studiolärm durchzusetzen, und weit genug von den tiefen Frequenzen entfernt, die ein
 * Kopfhörer je nach Sitz verschluckt.
 */
private val TONE_HZ = doubleArrayOf(880.0, 1174.7)

private const val TONE_MILLIS = 170L
private const val GAP_MILLIS = 60L
private const val MILLIS_PER_SECOND = 1000L

/** Anteil des Tones, in dem er anschwillt – kurz genug, um trotzdem als Anschlag zu wirken. */
private const val ATTACK_FRACTION = 0.02

private const val DECAY_SHAPE = 1.6

/**
 * Höchster Ausschlag, mit Abstand zur Aussteuerungsgrenze. Voll ausgesteuert übersteuert der
 * Ton auf manchen Geräten hörbar; die Lautstärke regelt ohnehin der Wecker-Regler.
 */
private const val PEAK_AMPLITUDE = 0.75

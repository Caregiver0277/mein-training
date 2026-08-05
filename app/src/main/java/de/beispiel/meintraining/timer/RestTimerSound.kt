package de.beispiel.meintraining.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Der Ton am Ende einer Pause: zweimal ein ansteigender Zweiklang, gut eine Sekunde lang.
 *
 * Er wird gerechnet statt abgespielt. Eine Tondatei wäre ein Fremdkörper im Projekt – sie
 * müsste beschafft, lizenziert und mitgeliefert werden –, während der Zweiklang aus vier
 * Schwingungen mit Hüllkurve besteht und in Millisekunden entsteht. So lässt sich auch prüfen,
 * was da eigentlich klingt: [chimeSamples] ist reines Kotlin und ohne Gerät zu testen.
 *
 * **Auf Lautheit gebaut, nicht auf Schönheit.** Im Studio ging der Ton unter, und der Pegel war
 * dabei nie das Problem – er reizt die 16 Bit aus. Was fehlte, war Energie. Vier Stellschrauben
 * arbeiten daran, jede an einer anderen Stelle: [PEAK_AMPLITUDE] am Ausschlag, [saturate] an
 * der Form der Welle, [envelopeAt] an ihrem Verlauf über die Zeit und [loudnessBoost] jenseits
 * der Aussteuerungsgrenze. Zusammen ist der Ton ein Vielfaches dessen, was er war; der
 * Glockencharakter ist dabei einem Signalton gewichen.
 *
 * Angemeldet wird der Ton als Wecker (USAGE_ALARM), genau wie das Vibrieren daneben: Im Studio
 * steht das Handy meistens auf lautlos, und ein Klingelton wäre dort nicht zu hören. Liegt ein
 * Kopfhörer an, geht der Ton dorthin – siehe [headphoneOutput].
 *
 * Die Obergrenze setzt weiterhin der Wecker-Regler des Geräts. Steht der niedrig, hilft hier
 * nichts davon.
 */
object RestTimerSound {

    /**
     * Spielt den Ton und kehrt erst zurück, wenn er verklungen ist.
     *
     * Das Warten gehört dazu: Der Aufrufer ist ein Wecker-Empfänger, und wenn der sich beim
     * System abmeldet, darf sein Prozess beendet werden – mitten im Ton.
     *
     * [volume] ist der Regler aus den Einstellungen, 0 bis 1. Er wirkt auf die Spur und nicht
     * auf die Abtastwerte: Die sind einmal gerechnet und werden geteilt (siehe [chime]) – jede
     * Änderung des Reglers müsste sie sonst neu rechnen. Ganz unten bleibt der Ton weg, statt
     * eine Spur für Stille aufzuziehen.
     */
    suspend fun play(context: Context, volume: Float = 1f) {
        val gain = chimeGain(volume)
        if (gain <= 0f) return

        val samples = chime
        // Ein abgebrochener Ton ist ärgerlich, ein Absturz beim Klingeln wäre schlimmer: Kann
        // das Gerät die Spur nicht anlegen – belegt, kein Ausgang, Hersteller-Eigenheit –,
        // bleibt es beim Vibrieren.
        val track = runCatching { buildTrack(samples) }.getOrNull() ?: return
        val boost = loudnessBoost(track)
        try {
            // Erst den Ausgang festlegen, dann starten: Ein Wechsel während des Spielens würde
            // den Ton nur zerhacken.
            headphoneOutput(context)?.let { track.setPreferredDevice(it) }
            track.setVolume(gain)
            track.play()
            delay(samples.size * MILLIS_PER_SECOND / SAMPLE_RATE + TAIL_MILLIS)
        } catch (throwable: IllegalStateException) {
            // play() wirft, wenn die Spur zwischenzeitlich ungültig geworden ist.
        } finally {
            // Ohne Freigeben bliebe je Klingeln ein Stück Audiospeicher des Systems belegt.
            // Der Effekt zuerst, er hängt an der Sitzung der Spur.
            runCatching { boost?.release() }
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    /**
     * Hebt den Ton über die Aussteuerungsgrenze hinaus.
     *
     * Das ist der einzige Weg, der noch übrig ist: Die Abtastwerte reizen die 16 Bit bereits
     * aus (siehe [PEAK_AMPLITUDE]), und [AudioTrack.setVolume] kann nur dämpfen, nicht anheben.
     * Der [LoudnessEnhancer] sitzt hinter der Spur in der Klangkette des Systems und verstärkt
     * dort, wo noch Luft ist – er begrenzt dabei selbst, statt die Spitzen abzuschneiden.
     *
     * Ob es ihn gibt, entscheidet das Gerät: Der Effekt ist Teil des Herstellerpakets und fehlt
     * auf manchen Fassungen. Fehlt er, bleibt es beim Ton, wie er berechnet wurde – deshalb
     * [runCatching] und kein Aufgeben.
     */
    private fun loudnessBoost(track: AudioTrack): LoudnessEnhancer? = runCatching {
        LoudnessEnhancer(track.audioSessionId).apply {
            setTargetGain(BOOST_MILLIBEL)
            enabled = true
        }
    }.getOrNull()

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

    /**
     * Wie weit der [LoudnessEnhancer] anhebt, in Millibel – 1000 sind 10 Dezibel, also gut das
     * Dreifache an Schalldruck.
     *
     * Kein Wunschwert, sondern eine Obergrenze: Der Effekt gibt so viel, wie die Klangkette des
     * Geräts noch hergibt, und begrenzt selbst, sobald es eng wird. Höher anzusetzen brächte auf
     * einem Handylautsprecher nichts als Verzerrung.
     */
    private const val BOOST_MILLIBEL = 1000
}

/**
 * Rechnet den Reglerstand in einen Verstärkungsfaktor um.
 *
 * Nicht eins zu eins: Das Ohr hört Lautstärke etwa logarithmisch, ein linear angelegter Regler
 * fühlt sich deshalb im oberen Drittel wie eine einzige Stufe an und ganz unten wie ein Sprung
 * ins Stumme. Das Quadrat (siehe [GAIN_SHAPE]) verteilt die Wahrnehmung über den ganzen Weg –
 * halber Regler klingt dann tatsächlich etwa halb so laut.
 *
 * Ausgeschlossen wird nur, was der Regler ohnehin nicht liefert: Werte außerhalb von 0 bis 1
 * werden gekappt, damit [AudioTrack.setVolume] nichts vorgelegt bekommt, was es ablehnt.
 */
internal fun chimeGain(volume: Float): Float =
    volume.coerceIn(0f, 1f).toDouble().pow(GAIN_SHAPE).toFloat()

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
            val value = saturate(sin(radiansPerSample * position)) *
                envelopeAt(position, toneLength) * PEAK_AMPLITUDE
            samples[offset + position] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        offset += toneLength
        if (index < TONE_HZ.lastIndex) offset += gapLength
    }
    return samples
}

/**
 * Drückt den Sinus gegen eine weiche Wand.
 *
 * Der Grund, warum ein reiner Sinus leise wirkt, obwohl er voll aussteuert: Er ist die meiste
 * Zeit weit unter seiner Spitze. Was das Ohr hört, ist aber nicht die Spitze, sondern die
 * Energie im Mittel – und die liegt beim Sinus rund 3 Dezibel darunter. Der Tangens hyperbolicus
 * schiebt alles außer den Nulldurchgängen nach oben: Die Kurve wird kantiger, ohne dass die
 * Spitze steigt.
 *
 * Zwei Gewinne auf einmal. Der erste ist die Energie. Der zweite sind die Obertöne, die dabei
 * entstehen – bei 880 Hz liegen sie um 1,8 und 2,6 kHz, und genau dort hört ein Mensch am besten;
 * derselbe Schalldruck wirkt dort deutlich lauter als beim Grundton.
 *
 * Geteilt wird durch `tanh(DRIVE)`, den größten Wert, den die Funktion hier annehmen kann. Damit
 * bleibt das Ergebnis sicher zwischen -1 und 1, und [PEAK_AMPLITUDE] behält seine Bedeutung als
 * höchster Ausschlag – ohne diese Teilung wäre die Grenze eine Behauptung statt einer Zusage.
 */
private fun saturate(value: Double): Double = tanh(DRIVE * value) / tanh(DRIVE)

/**
 * Lautstärke innerhalb eines Tones: kurz anschwellen, stehen bleiben, am Ende ausklingen.
 *
 * Die Haltephase ist der größte Einzelgewinn an Lautheit, und sie kostet nichts an Ausschlag.
 * Vorher fiel der Ton vom ersten Augenblick an ab und war nach einem Drittel seiner Zeit schon
 * halb verschwunden – gemessen über den ganzen Ton blieb weniger als die Hälfte der möglichen
 * Energie übrig. Jetzt steht er über [SUSTAIN_FRACTION] seiner Länge voll da.
 *
 * Damit klingt er weniger nach angeschlagenem Metall und mehr nach einem Signal. Das ist der
 * Preis: Ein Ton, der sofort verhallt, ist der schönere – und der, den im Studio niemand hört.
 *
 * Das Ausklingen am Schluss bleibt, und es fällt anfangs schneller als linear ([DECAY_SHAPE]
 * über 1). Ohne das Ausklingen bräche die Schwingung mittendrin ab, und das knackt.
 */
private fun envelopeAt(position: Int, length: Int): Double {
    val attack = (length * ATTACK_FRACTION).toInt().coerceAtLeast(1)
    if (position < attack) return position.toDouble() / attack
    // Mindestens ein Wert hinter dem Anschwellen, sonst bliebe bei sehr kurzen Tönen kein Platz
    // zum Ausklingen und die Rechnung darunter teilte durch nichts.
    val hold = (length * SUSTAIN_FRACTION).toInt().coerceIn(attack, length - 1)
    if (position < hold) return 1.0
    val remaining = 1.0 - (position - hold).toDouble() / (length - hold).coerceAtLeast(1)
    return remaining.coerceAtLeast(0.0).pow(DECAY_SHAPE)
}

/** Abtastrate; 44,1 kHz beherrscht jedes Gerät ohne Umrechnung. */
private const val SAMPLE_RATE = 44_100

/**
 * Die Töne: A5 und D6, zweimal.
 *
 * Eine reine Quarte nach oben – der Vierklang jeder Türklingel. Hoch genug, um sich gegen
 * Studiolärm durchzusetzen, und weit genug von den tiefen Frequenzen entfernt, die ein
 * Kopfhörer je nach Sitz verschluckt.
 *
 * Das zweite Paar ist keine Verzierung. Ein Geräusch unter etwa einer Fünftelsekunde wirkt
 * leiser, als es gemessen ist – das Ohr mittelt über ein Zeitfenster, und was kürzer ist, füllt
 * es nicht aus. Dazu kommt der praktische Teil: Der einzelne Zweiklang ging im ersten Moment
 * unter, in dem daneben das Vibrieren anläuft. Zweimal ist schwerer zu überhören als einmal.
 */
private val TONE_HZ = doubleArrayOf(880.0, 1174.7, 880.0, 1174.7)

private const val TONE_MILLIS = 220L
private const val GAP_MILLIS = 55L
private const val MILLIS_PER_SECOND = 1000L

/** Anteil des Tones, in dem er anschwillt – kurz genug, um trotzdem als Anschlag zu wirken. */
private const val ATTACK_FRACTION = 0.02

/** Anteil des Tones, der voll ausgesteuert stehen bleibt – siehe [envelopeAt]. */
private const val SUSTAIN_FRACTION = 0.6

private const val DECAY_SHAPE = 1.6

/**
 * Wie hart der Sinus in die Sättigung gefahren wird – siehe [saturate].
 *
 * 3,0 ist die Mitte zwischen zwei Enden: Gegen 0 bliebe die reine Sinuskurve und mit ihr das
 * Lautstärkeproblem, weit darüber käme ein Rechteck heraus, und das klingt nach billigem
 * Weckton. Hier ist die Kurve deutlich kantig, der Zweiklang aber noch als Zweiklang zu hören.
 */
private const val DRIVE = 3.0

/** Krümmung des Lautstärkereglers – siehe [chimeGain]. */
private const val GAIN_SHAPE = 2.0

/**
 * Höchster Ausschlag, knapp unter der Aussteuerungsgrenze.
 *
 * Vorher lag er bei 0,75 und ließ Abstand nach oben. 0,975 ist fast alles, was geht – darüber
 * schneidet die 16-Bit-Grenze die Spitzen ab, und eine beschnittene Welle knarzt.
 *
 * Zugleich ist es die kleinste der vier Stellschrauben: Ausschlag mal 1,3 sind gut zwei
 * Dezibel. Dass der Ton am Ende ein Vielfaches lauter ist, liegt an den drei anderen – hier ist
 * schlicht nichts mehr zu holen.
 *
 * Eingehalten wird die Grenze von selbst: [saturate] liefert höchstens 1, die Hüllkurve
 * höchstens 1, und das Produkt aus beiden mal diesem Wert kann sie deshalb nicht überschreiten.
 */
private const val PEAK_AMPLITUDE = 0.975

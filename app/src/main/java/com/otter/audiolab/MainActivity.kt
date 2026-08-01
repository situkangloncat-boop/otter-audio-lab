package com.otter.audiolab

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * OTTER AUDIO LAB
 * ================================================================
 * Aplikasi pengubah bagian-bagian lagu secara otomatis: Tempo, Pitch,
 * Volume, Bass, Mid, Treble, Reverb, Echo/Delay, Panning, Kompresi,
 * Distorsi, Fade In/Out, Loop, Trim, dan mode Vokal/Instrumen
 * (best-effort, bukan AI source separation).
 *
 * Mesin pemroses: FFmpeg (lewat FFmpegKit, fork community karena versi
 * resmi arthenica sudah pensiun). Semua efek dibangun jadi satu filter
 * chain FFmpeg lalu dieksekusi sekali proses.
 * ================================================================
 */

data class EffectParam(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val unit: String,
)

class MainActivity : AppCompatActivity() {

    // ------------------------------------------------------------
    // DAFTAR EFEK (tambah/kurangi item di sini kalau mau ubah UI)
    // ------------------------------------------------------------
    private val EFFECTS = listOf(
        EffectParam("tempo", "Tempo (kecepatan lagu)", 50f, 200f, 100f, "%"),
        EffectParam("pitch", "Nada (Pitch / Key)", -12f, 12f, 0f, "semitone"),
        EffectParam("volume", "Volume", -20f, 20f, 0f, "dB"),
        EffectParam("bass", "Bass", -20f, 20f, 0f, "dB"),
        EffectParam("mid", "Mid", -20f, 20f, 0f, "dB"),
        EffectParam("treble", "Treble", -20f, 20f, 0f, "dB"),
        EffectParam("reverb", "Reverb", 0f, 100f, 0f, "%"),
        EffectParam("echo", "Echo / Delay", 0f, 100f, 0f, "%"),
        EffectParam("pan", "Panning (kiri <-> kanan)", -100f, 100f, 0f, ""),
        EffectParam("compress", "Kompresi", 0f, 100f, 0f, "%"),
        EffectParam("distortion", "Distorsi / Saturasi", 0f, 100f, 0f, "%"),
        EffectParam("fadeIn", "Fade In", 0f, 10f, 0f, "detik"),
        EffectParam("fadeOut", "Fade Out", 0f, 10f, 0f, "detik"),
        EffectParam("loopCount", "Loop (ulang lagu)", 0f, 5f, 0f, "x"),
    )

    private val effectValues = mutableMapOf<String, Float>()
    private val valueLabels = mutableMapOf<String, TextView>()

    private lateinit var effectsContainer: LinearLayout
    private lateinit var txtFileName: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerVocal: Spinner
    private lateinit var editTrimStart: EditText
    private lateinit var editTrimEnd: EditText
    private lateinit var btnProcess: Button
    private lateinit var btnPlay: Button
    private lateinit var btnSave: Button

    private var inputFile: File? = null
    private var outputFile: File? = null
    private var inputDurationSec: Double = 0.0
    private var mediaPlayer: MediaPlayer? = null

    private val pickAudioLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) copyPickedAudioToCache(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        effectsContainer = findViewById(R.id.effectsContainer)
        txtFileName = findViewById(R.id.txtFileName)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        spinnerVocal = findViewById(R.id.spinnerVocal)
        editTrimStart = findViewById(R.id.editTrimStart)
        editTrimEnd = findViewById(R.id.editTrimEnd)
        btnProcess = findViewById(R.id.btnProcess)
        btnPlay = findViewById(R.id.btnPlay)
        btnSave = findViewById(R.id.btnSave)

        buildEffectSliders()

        spinnerVocal.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Tidak diubah", "Kurangi Vokal (mode Karaoke)", "Perkuat Vokal / Tengah")
        )

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener { resetAllEffects() }

        btnProcess.setOnClickListener { processAudio() }
        btnPlay.setOnClickListener { playOutput() }
        btnSave.setOnClickListener { shareOutput() }

        btnProcess.isEnabled = false
        btnPlay.isEnabled = false
        btnSave.isEnabled = false
    }

    // ------------------------------------------------------------
    // UI: bangun 1 baris (label + nilai + SeekBar) per efek
    // ------------------------------------------------------------
    private fun buildEffectSliders() {
        val steps = 1000
        for (effect in EFFECTS) {
            effectValues[effect.key] = effect.default

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 20, 0, 0)
            }

            val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val labelView = TextView(this).apply {
                text = effect.label
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val valueView = TextView(this).apply {
                text = formatValue(effect, effect.default)
                setTextColor(0xFF66BB6A.toInt())
            }
            valueLabels[effect.key] = valueView
            labelRow.addView(labelView)
            labelRow.addView(valueView)

            val seekBar = SeekBar(this).apply {
                max = steps
                progress = valueToProgress(effect, effect.default, steps)
            }
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progressToValue(effect, progress, steps)
                    effectValues[effect.key] = value
                    valueLabels[effect.key]?.text = formatValue(effect, value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            row.addView(labelRow)
            row.addView(seekBar)
            effectsContainer.addView(row)
        }
    }

    private fun valueToProgress(effect: EffectParam, value: Float, steps: Int): Int {
        val ratio = (value - effect.min) / (effect.max - effect.min)
        return (ratio * steps).roundToInt().coerceIn(0, steps)
    }

    private fun progressToValue(effect: EffectParam, progress: Int, steps: Int): Float {
        val ratio = progress.toFloat() / steps
        return effect.min + ratio * (effect.max - effect.min)
    }

    private fun formatValue(effect: EffectParam, value: Float): String {
        val v = if (value == value.roundToInt().toFloat()) value.roundToInt().toString()
                else String.format("%.1f", value)
        return "$v${effect.unit}"
    }

    private fun resetAllEffects() {
        effectsContainer.removeAllViews()
        valueLabels.clear()
        buildEffectSliders()
        spinnerVocal.setSelection(0)
        editTrimStart.setText("")
        editTrimEnd.setText("")
        txtStatus.text = "Semua efek direset ke default."
    }

    // ------------------------------------------------------------
    // Ambil file audio yang dipilih user -> salin ke cache lokal
    // (FFmpegKit butuh path file, bukan content:// Uri langsung)
    // ------------------------------------------------------------
    private fun copyPickedAudioToCache(uri: Uri) {
        try {
            val displayName = queryDisplayName(uri) ?: "input_audio"
            val ext = displayName.substringAfterLast('.', "mp3")
            val dest = File(cacheDir, "otter_input.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            inputFile = dest
            outputFile = null
            txtFileName.text = "File: $displayName"
            btnPlay.isEnabled = false
            btnSave.isEnabled = false

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(dest.absolutePath)
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            inputDurationSec = durMs / 1000.0
            retriever.release()

            btnProcess.isEnabled = true
            txtStatus.text = "Siap diproses. Durasi: ${"%.1f".format(inputDurationSec)} detik."
        } catch (e: Exception) {
            txtStatus.text = "Gagal membaca file: ${e.message}"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
        }
        return uri.lastPathSegment
    }

    // ------------------------------------------------------------
    // BANGUN FILTER CHAIN FFMPEG DARI SEMUA NILAI SLIDER
    // ------------------------------------------------------------
    private fun buildAtempoChain(ratio: Double): String {
        var r = ratio
        val filters = mutableListOf<String>()
        while (r > 2.0) { filters.add("atempo=2.0"); r /= 2.0 }
        while (r < 0.5) { filters.add("atempo=0.5"); r /= 0.5 }
        filters.add("atempo=%.4f".format(r))
        return filters.joinToString(",")
    }

    private fun buildFilterChain(vocalMode: Int, effectiveDuration: Double): String {
        val filters = mutableListOf<String>()
        val v = effectValues

        // --- Tempo + Pitch (digabung supaya independen satu sama lain) ---
        val tempoRatio = (v["tempo"] ?: 100f) / 100.0
        val pitchSemitone = (v["pitch"] ?: 0f).toDouble()
        val pitchRatio = 2.0.pow(pitchSemitone / 12.0)
        if (pitchSemitone != 0.0) {
            filters.add("asetrate=44100*${"%.6f".format(pitchRatio)}")
            filters.add("aresample=44100")
            filters.add(buildAtempoChain(tempoRatio / pitchRatio))
        } else if (tempoRatio != 1.0) {
            filters.add(buildAtempoChain(tempoRatio))
        }

        // --- EQ 3-band: Bass / Mid / Treble ---
        val bass = v["bass"] ?: 0f
        val mid = v["mid"] ?: 0f
        val treble = v["treble"] ?: 0f
        if (bass != 0f) filters.add("bass=g=${"%.1f".format(bass)}")
        if (mid != 0f) filters.add("equalizer=f=1000:width_type=o:width=1:g=${"%.1f".format(mid)}")
        if (treble != 0f) filters.add("treble=g=${"%.1f".format(treble)}")

        // --- Kompresi ---
        val compress = v["compress"] ?: 0f
        if (compress > 0f) {
            val ratio = 1.0 + (compress / 100.0) * 15.0
            val threshold = -5.0 - (compress / 100.0) * 25.0
            filters.add("acompressor=threshold=${"%.1f".format(threshold)}dB:ratio=${"%.1f".format(ratio)}:attack=20:release=250:makeup=2")
        }

        // --- Distorsi / Saturasi ---
        val distortion = v["distortion"] ?: 0f
        if (distortion > 0f) {
            val threshold = (1.0 - (distortion / 100.0) * 0.9).coerceIn(0.05, 1.0)
            filters.add("asoftclip=type=tanh:threshold=${"%.3f".format(threshold)}")
        }

        // --- Panning ---
        val pan = v["pan"] ?: 0f
        if (pan != 0f) {
            val p = (pan / 100.0).coerceIn(-1.0, 1.0)
            filters.add("stereotools=balance_in=${"%.2f".format(p)}")
        }

        // --- Vokal / Instrumen (best-effort, bukan AI separation) ---
        when (vocalMode) {
            1 -> filters.add("pan=stereo|c0=c0-c1|c1=c1-c0") // kurangi vokal tengah (karaoke)
            2 -> filters.add("stereotools=mlev=2.0")          // perkuat sinyal tengah (biasanya vokal)
        }

        // --- Reverb (approx, via multi-tap echo) ---
        val reverb = v["reverb"] ?: 0f
        if (reverb > 0f) {
            val d1 = (reverb / 100.0 * 0.6).coerceIn(0.05, 0.6)
            filters.add("aecho=0.8:0.9:60|150|280:${"%.2f".format(d1)}|${"%.2f".format(d1 * 0.7)}|${"%.2f".format(d1 * 0.5)}")
        }

        // --- Echo / Delay ---
        val echo = v["echo"] ?: 0f
        if (echo > 0f) {
            val delayMs = (200 + echo * 8).roundToInt()
            val decay = (echo / 100.0 * 0.6).coerceIn(0.05, 0.6)
            filters.add("aecho=0.8:0.88:$delayMs:${"%.2f".format(decay)}")
        }

        // --- Volume ---
        val volume = v["volume"] ?: 0f
        if (volume != 0f) filters.add("volume=${"%.1f".format(volume)}dB")

        // --- Fade In / Out ---
        val fadeIn = v["fadeIn"] ?: 0f
        val fadeOut = v["fadeOut"] ?: 0f
        if (fadeIn > 0f) filters.add("afade=t=in:st=0:d=${"%.2f".format(fadeIn)}")
        if (fadeOut > 0f) {
            val start = (effectiveDuration - fadeOut).coerceAtLeast(0.0)
            filters.add("afade=t=out:st=${"%.2f".format(start)}:d=${"%.2f".format(fadeOut)}")
        }

        // --- Loop ---
        val loopCount = (v["loopCount"] ?: 0f).roundToInt()
        if (loopCount > 0) {
            val sizeSamples = (effectiveDuration * 44100).toLong().coerceAtLeast(1L)
            filters.add("aloop=loop=$loopCount:size=$sizeSamples")
        }

        return if (filters.isEmpty()) "anull" else filters.joinToString(",")
    }

    // ------------------------------------------------------------
    // PROSES AUDIO LEWAT FFMPEGKIT
    // ------------------------------------------------------------
    private fun processAudio() {
        val input = inputFile ?: return
        val vocalMode = spinnerVocal.selectedItemPosition
        val trimStart = editTrimStart.text.toString().toDoubleOrNull()
        val trimEnd = editTrimEnd.text.toString().toDoubleOrNull()

        val effectiveDuration = if (trimStart != null && trimEnd != null && trimEnd > trimStart) {
            trimEnd - trimStart
        } else {
            inputDurationSec
        }

        val filterChain = buildFilterChain(vocalMode, effectiveDuration)
        val out = File(cacheDir, "otter_output_${System.currentTimeMillis()}.m4a")
        outputFile = out

        val trimArgs = if (trimStart != null && trimEnd != null && trimEnd > trimStart) {
            "-ss $trimStart -to $trimEnd"
        } else ""

        val command = "-y -i \"${input.absolutePath}\" $trimArgs -filter:a \"$filterChain\" -c:a aac -b:a 192k \"${out.absolutePath}\""

        progressBar.visibility = View.VISIBLE
        txtStatus.text = "Memproses audio..."
        btnProcess.isEnabled = false
        btnPlay.isEnabled = false
        btnSave.isEnabled = false

        FFmpegKit.executeAsync(command) { session ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                btnProcess.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    txtStatus.text = "Selesai! File siap diputar / disimpan."
                    btnPlay.isEnabled = true
                    btnSave.isEnabled = true
                } else {
                    val logDetail = try {
                        session.failStackTrace ?: session.allLogsAsString.takeLast(800)
                    } catch (e: Exception) {
                        "Tidak ada detail log."
                    }
                    txtStatus.text = "Gagal (kode: ${session.returnCode}).\nPerintah: $command\n\nLog:\n$logDetail"
                }
            }
        }
    }

    private fun playOutput() {
        val out = outputFile ?: return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(out.absolutePath)
            setOnCompletionListener { it.release() }
            prepare()
            start()
        }
        txtStatus.text = "Memutar hasil..."
    }

    private fun shareOutput() {
        val out = outputFile ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Simpan / Bagikan hasil audio"))
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}

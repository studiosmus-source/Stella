package com.studiosmus.stella

import android.Manifest
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.studiosmus.stella.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var savedImagePath: String? = null
    private var cachedWeather: WeatherData? = null

    // Secret: 7 taps on the title within 3 seconds opens the debug panel
    private var tapCount   = 0
    private var lastTapMs  = 0L

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handleImageUri(it) } }

    private val legacyPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImageUri(it) } }

    private val storagePerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) legacyPicker.launch("image/*") }

    private val locationPerm = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) loadWeatherAsync()
        else binding.tvWeatherStatus.text = getString(R.string.weather_unavailable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getSharedPreferences(StellaWallpaperService.PREFS, MODE_PRIVATE)
            .getString(StellaWallpaperService.KEY_BG, null)
            ?.let { savedImagePath = it; updatePreview(it) }

        binding.btnPickImage.setOnClickListener { pickImage() }
        binding.btnSetWallpaper.setOnClickListener { setLiveWallpaper() }
        binding.btnSetWallpaper.isEnabled = savedImagePath != null

        // Secret tap sequence: 7 taps on title within 3 s → debug panel
        binding.tvTitle.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapMs > 3000L) tapCount = 0
            lastTapMs = now
            if (++tapCount >= 7) { tapCount = 0; showDebugDialog() }
        }

        requestLocationAndLoad()
    }

    private fun pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
                legacyPicker.launch("image/*")
            else storagePerm.launch(perm)
        }
    }

    private fun handleImageUri(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { copyToInternal(uri) }
            if (path != null) {
                savedImagePath = path
                getSharedPreferences(StellaWallpaperService.PREFS, MODE_PRIVATE)
                    .edit().putString(StellaWallpaperService.KEY_BG, path).apply()
                sendBroadcast(Intent(StellaWallpaperService.ACTION_BG_CHANGED))
                updatePreview(path)
                binding.btnSetWallpaper.isEnabled = true
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.image_error), Toast.LENGTH_SHORT).show()
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun copyToInternal(uri: Uri): String? = try {
        val file = File(filesDir, "wallpaper_bg.jpg")
        contentResolver.openInputStream(uri)?.use { i ->
            FileOutputStream(file).use { o -> i.copyTo(o) }
        }
        file.absolutePath
    } catch (_: Exception) { null }

    private fun updatePreview(path: String) {
        val seed = System.currentTimeMillis() / 60_000
        val bmp = WeatherRenderer.render(path, cachedWeather, seed)
        binding.imgPreview.setImageBitmap(bmp)
    }

    private fun requestLocationAndLoad() {
        val perm = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            loadWeatherAsync()
        else locationPerm.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun loadWeatherAsync() {
        binding.tvWeatherStatus.text = getString(R.string.loading_weather)
        lifecycleScope.launch {
            val data = WeatherFetcher.fetch(this@MainActivity)
            cachedWeather = data
            if (data != null) {
                binding.tvWeatherStatus.text = "${data.temperatureCelsius.toInt()}° — ${data.condition.label}"
                savedImagePath?.let { updatePreview(it) }
            } else {
                binding.tvWeatherStatus.text = getString(R.string.weather_unavailable)
            }
        }
    }

    private fun setLiveWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(packageName, StellaWallpaperService::class.java.name)
            )
        }
        startActivity(intent)
    }

    private fun showDebugDialog() {
        val prefs = getSharedPreferences(StellaWallpaperService.PREFS, MODE_PRIVATE)

        val condLabels = arrayOf("Auto (reale)") +
            WeatherCondition.values().map { it.label }.toTypedArray()
        val tofdLabels = arrayOf("Auto (reale)") +
            TimeOfDay.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                .toTypedArray()

        val savedCond = prefs.getString(StellaWallpaperService.KEY_DEBUG_COND, null)
        val savedTofd = prefs.getString(StellaWallpaperService.KEY_DEBUG_TOFD, null)
        val condSel = WeatherCondition.values().indexOfFirst { it.name == savedCond }
            .let { if (it < 0) 0 else it + 1 }
        val tofdSel = TimeOfDay.values().indexOfFirst { it.name == savedTofd }
            .let { if (it < 0) 0 else it + 1 }

        val ctx = this
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 32, 60, 8)
        }

        val tvCond = TextView(ctx).apply { text = "Condizione meteo"; textSize = 13f }
        val spinCond = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, condLabels)
            setSelection(condSel)
        }
        val tvTofd = TextView(ctx).apply {
            text = "Orario del giorno"; textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        val spinTofd = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, tofdLabels)
            setSelection(tofdSel)
        }

        layout.addView(tvCond);  layout.addView(spinCond)
        layout.addView(tvTofd); layout.addView(spinTofd)

        AlertDialog.Builder(ctx)
            .setTitle("Debug meteo")
            .setView(layout)
            .setPositiveButton("Applica") { _, _ ->
                val newCond = if (spinCond.selectedItemPosition == 0) null
                    else WeatherCondition.values()[spinCond.selectedItemPosition - 1].name
                val newTofd = if (spinTofd.selectedItemPosition == 0) null
                    else TimeOfDay.values()[spinTofd.selectedItemPosition - 1].name
                prefs.edit().also { ed ->
                    if (newCond != null) ed.putString(StellaWallpaperService.KEY_DEBUG_COND, newCond)
                    else ed.remove(StellaWallpaperService.KEY_DEBUG_COND)
                    if (newTofd != null) ed.putString(StellaWallpaperService.KEY_DEBUG_TOFD, newTofd)
                    else ed.remove(StellaWallpaperService.KEY_DEBUG_TOFD)
                }.apply()
                sendBroadcast(Intent(StellaWallpaperService.ACTION_DEBUG_CHANGED))
                savedImagePath?.let { updatePreview(it) }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}

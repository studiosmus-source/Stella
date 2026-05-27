package com.studiosmus.livingweather

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.studiosmus.livingweather.databinding.ActivityConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WeatherConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var savedImagePath: String? = null
    private var cachedWeather: WeatherData? = null

    // ─── Image picker (Android 13+ photo picker, no permission needed) ────────
    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { handleImageUri(it) } }

    // ─── Fallback picker for Android < 13 ────────────────────────────────────
    private val legacyPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleImageUri(it) } }

    // ─── Storage permission request ───────────────────────────────────────────
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openLegacyPicker() else showPermissionError()
    }

    // ─── Location permission request ─────────────────────────────────────────
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) loadWeatherAsync()
        else {
            binding.tvWeatherStatus.text = getString(R.string.weather_unavailable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding.btnPickImage.setOnClickListener { openImagePicker() }
        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnSave.isEnabled = false

        requestLocationAndLoadWeather()
    }

    private fun requestLocationAndLoadWeather() {
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val hasLocation = ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) {
            loadWeatherAsync()
        } else {
            locationPermissionLauncher.launch(arrayOf(fine, coarse))
        }
    }

    private fun openImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
                openLegacyPicker()
            } else {
                permissionLauncher.launch(perm)
            }
        }
    }

    private fun openLegacyPicker() = legacyPicker.launch("image/*")

    private fun showPermissionError() {
        Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
    }

    private fun handleImageUri(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { copyUriToInternal(uri) }
            if (path != null) {
                savedImagePath = path
                updatePreview(path)
                binding.btnSave.isEnabled = true
            } else {
                Toast.makeText(
                    this@WeatherConfigActivity,
                    getString(R.string.image_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun copyUriToInternal(uri: Uri): String? {
        return try {
            val file = File(filesDir, "widget_bg_$widgetId.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Exception) { null }
    }

    private fun updatePreview(path: String) {
        val seed = System.currentTimeMillis() / 60_000
        val bmp = WeatherRenderer.render(path, cachedWeather, seed)
        binding.imgPreview.setImageBitmap(bmp)
        binding.imgPreview.visibility = View.VISIBLE
    }

    private fun loadWeatherAsync() {
        binding.tvWeatherStatus.text = getString(R.string.loading_weather)
        lifecycleScope.launch {
            val data = WeatherFetcher.fetch(this@WeatherConfigActivity)
            cachedWeather = data
            if (data != null) {
                binding.tvWeatherStatus.text =
                    "${data.temperatureCelsius.toInt()}° – ${data.condition.label}"
                savedImagePath?.let { updatePreview(it) }
            } else {
                binding.tvWeatherStatus.text = getString(R.string.weather_unavailable)
            }
        }
    }

    private fun saveAndFinish() {
        val path = savedImagePath ?: return

        val prefs = getSharedPreferences(WeatherWidgetProvider.PREFS, MODE_PRIVATE)
        prefs.edit().putString(WeatherWidgetProvider.KEY_BG + widgetId, path).apply()

        cachedWeather?.let { WeatherWidgetProvider.saveWeather(prefs, widgetId, it) }

        val manager = AppWidgetManager.getInstance(this)
        WeatherWidgetProvider.updateWidget(this, manager, widgetId)
        WeatherWidgetProvider.scheduleVisualUpdates(this, widgetId)
        WeatherWidgetProvider.scheduleWeatherFetch(this)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

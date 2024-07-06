package org.hyperskill.photoeditor

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.security.Permissions
import java.util.jar.Manifest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_EXTERNAL_STORAGE: Int = 0
    private lateinit var currentImage: ImageView
    private lateinit var btnGallery:Button
    private lateinit var btnSave:Button
    private lateinit var slBrightness:Slider
    private lateinit var slContrast:Slider
    private lateinit var slSaturation:Slider
    private lateinit var slGamma:Slider
    private lateinit var actualBitmap: Bitmap
    private var brightnessApplied:Boolean = false
    private var avgBrightness:Int = 0

    private val activityResultLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photoUri = result.data?.data ?: return@registerForActivityResult
                // code to update ivPhoto with loaded image
                actualBitmap = uriToBitmap(photoUri)
                currentImage.setImageBitmap(actualBitmap)
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        //do not change this line
        actualBitmap = createBitmap()
        currentImage.setImageBitmap(createBitmap())
    }
    private var lastJob: Job? = null
    private fun onSliderChanges(slider: Slider, value: Float, fromUser:Boolean){
        lastJob?.cancel()

        lastJob = CoroutineScope(Dispatchers.IO).launch{
            var bitmapModified = actualBitmap
            val brightenCopyDeferred:   Deferred<Bitmap> = this.async{
                bitmapModified.modifyRGB(if (slider.id == R.id.slBrightness) value else slBrightness.value)
        }
            bitmapModified = brightenCopyDeferred.await()

            val contrastCopyDeferred:   Deferred<Bitmap> = this.async{
                bitmapModified.modifyContrast(if (slider.id == R.id.slContrast) value else slContrast.value)
            }
            bitmapModified = contrastCopyDeferred.await()
            val saturationCopyDeferred:   Deferred<Bitmap> = this.async{
                bitmapModified.modifySaturation(if (slider.id == R.id.slSaturation) value else slSaturation.value)
            }
            bitmapModified = saturationCopyDeferred.await()
            val gammaCopyDeferred:   Deferred<Bitmap> = this.async{
                bitmapModified.modifyGamma(if (slider.id == R.id.slGamma) value else slGamma.value)
            }
            bitmapModified = gammaCopyDeferred.await()

            ensureActive()

            runOnUiThread {
                currentImage.setImageBitmap(bitmapModified)
            }
        }
    }
    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
        btnGallery = findViewById(R.id.btnGallery)
        slBrightness = findViewById(R.id.slBrightness)
        slContrast = findViewById(R.id.slContrast)
        slSaturation = findViewById(R.id.slSaturation)
        slGamma = findViewById(R.id.slGamma)
        btnSave = findViewById(R.id.btnSave)
        btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }
        slBrightness.addOnChangeListener { slider, value, fromUser ->
            onSliderChanges(slider, value, fromUser)
        }
        slContrast.addOnChangeListener { slider, value, fromUser ->
           onSliderChanges(slider,value, fromUser)
        }
        slSaturation.addOnChangeListener { slider, value, fromUser ->
           onSliderChanges(slider, value, fromUser)
        }
        slGamma.addOnChangeListener { slider, value, fromUser ->
          onSliderChanges(slider, value, fromUser)
        }
        btnSave.setOnClickListener {
            requestPermission()
        }
    }
    private fun hasPermission(manifestPermission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.checkSelfPermission(manifestPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            PermissionChecker.checkSelfPermission(this, manifestPermission) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun requestPermission(){
        when {
            hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                saveImage()
            }

            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                ActivityCompat.requestPermissions(this, listOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray(), REQUEST_CODE_READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun saveImage() {
        ContentValues().apply {
            val bitmap: Bitmap = currentImage.drawable.toBitmap()
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            values.put(MediaStore.Images.ImageColumns.WIDTH, bitmap.width)
            values.put(MediaStore.Images.ImageColumns.HEIGHT, bitmap.height)

            val uri = this@MainActivity.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return

            contentResolver.openOutputStream(uri).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                btnSave.callOnClick()
            } else {
                Toast.makeText(this, "Songs cannot be loaded without permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Bitmap.modifyRGB(value:Float):Bitmap{
        val newBitmap = this.copy(Bitmap.Config.RGB_565, true)

        for (x:Int in 0..newBitmap.width){
            for (y:Int in 0.. newBitmap.height){

                if (y >= newBitmap.height || x >= newBitmap.width) break
                val pixel = newBitmap.getPixel(x, y)

                var red = Color.red(pixel)
                var blue = Color.blue(pixel)
                var green = Color.green(pixel)
                val base = 255f
                red = min(base, max(0f, red + value)).toInt()
                blue = min(base, max(0f, blue + value)).toInt()
                green = min(base, max(0f, green + value)).toInt()

                avgBrightness += (red + blue + green)

                newBitmap.setPixel(x,y, Color.rgb(red, green, blue))
            }
        }
        avgBrightness /= (newBitmap.width * newBitmap.height * 3)
        return newBitmap
    }

    private fun Bitmap.modifySaturation(value: Float):Bitmap{
        val newBitmap = this.copy(Bitmap.Config.RGB_565, true)

        for (x:Int in 0..newBitmap.width){
            for (y:Int in 0.. newBitmap.height){

                if (y >= newBitmap.height || x >= newBitmap.width) break
                val pixel = newBitmap.getPixel(x, y)

                var red = Color.red(pixel)
                var blue = Color.blue(pixel)
                var green = Color.green(pixel)
                val alpha = ((255 + value) / (255 - value)).toDouble()
                val base = 255.0
                val avgRGB = (red + blue + green) / 3
                red = min(base, max(0.0, alpha * (red - avgRGB) + avgRGB)).toInt()
                blue = min(base, max(0.0, alpha * (blue - avgRGB) + avgRGB)).toInt()
                green = min(base, max(0.0, alpha * (green - avgRGB) + avgRGB)).toInt()

                newBitmap.setPixel(x,y, Color.rgb(red, green, blue))
            }
        }
        avgBrightness /= (newBitmap.width * newBitmap.height * 3)
        return newBitmap
    }

    private fun Bitmap.modifyGamma(value: Float):Bitmap{
        val newBitmap = this.copy(Bitmap.Config.RGB_565, true)

        for (x:Int in 0..newBitmap.width){
            for (y:Int in 0.. newBitmap.height){

                if (y >= newBitmap.height || x >= newBitmap.width) break
                val pixel = newBitmap.getPixel(x, y)

                var red = Color.red(pixel)
                var blue = Color.blue(pixel)
                var green = Color.green(pixel)
                val base = 255f
                val min = 0f
                val newred = 255 * ((red.toFloat()/ base).pow(value))
                val newblue = 255 * ((blue.toFloat()/ base).pow(value))
                val newgreen = 255 * ((green.toFloat()/ base).pow(value))
                red = min(base, max(min, newred)).toInt()
                blue = min(base, max(min, newblue)).toInt()
                green = min(base, max(min, newgreen)).toInt()

                newBitmap.setPixel(x,y, Color.rgb(red, green, blue))
            }
        }
        avgBrightness /= (newBitmap.width * newBitmap.height * 3)
        return newBitmap
    }


    private fun Bitmap.modifyContrast(value:Float):Bitmap{
        val newBitmap = this.copy(Bitmap.Config.RGB_565, true)

        for (x:Int in 0..newBitmap.width){
            for (y:Int in 0.. newBitmap.height){
                if (y >= newBitmap.height || x >= newBitmap.width) break
                val pixel = newBitmap.getPixel(x, y)

                var red = Color.red(pixel)
                var blue = Color.blue(pixel)
                var green = Color.green(pixel)
                val baseAlpha =  Color.alpha(pixel)
                val alpha = ((255 + value) / (255 - value)).toDouble()
                val base = 255.0
                red = min(base, max(0.0, alpha * (red - avgBrightness) + avgBrightness)).toInt()
                blue = min(base, max(0.0, alpha * (blue - avgBrightness) + avgBrightness)).toInt()
                green = min(base, max(0.0, alpha * (green - avgBrightness) + avgBrightness)).toInt()

                newBitmap.setPixel(x,y, Color.rgb(red, green, blue))
            }
        }
        return newBitmap
    }
    @Throws(FileNotFoundException::class)
    private fun uriToBitmap(uri: Uri): Bitmap {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }
    // do not change this function
    fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x+y) % 100 + 120

                pixels[index] = Color.rgb(R,G,B)

            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }
}
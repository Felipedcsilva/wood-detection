package com.app.wood_detection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.app.wood_detection.databinding.ActivityImageBinding
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageBinding
    private var bitmap: Bitmap? = null
    private var yolov5TFLiteDetector: Yolov5TFLiteDetector? = null
    private var boxPaint = Paint()
    private var textPain = Paint()

    private val IMAGE_PICK = 100

    private var imagePath: String? = null
    private var imageName: String? = null
    private var mProfileFile: File? = null

    private var isFromGallery: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        yolov5TFLiteDetector = Yolov5TFLiteDetector()
        yolov5TFLiteDetector?.modelFile = "wood_detection_v1.1.tflite"
        yolov5TFLiteDetector?.initialModel(this)

        boxPaint.strokeWidth = 5f
        boxPaint.style = Paint.Style.STROKE
        boxPaint.color = Color.GREEN

        textPain.textSize = 50f
        textPain.color = Color.GREEN
        textPain.style = Paint.Style.FILL

        binding.predict.setOnClickListener {
            predict()
        }

        binding.capture.setOnClickListener {
            isFromGallery = false
            checkPermission()
        }
        binding.gallery.setOnClickListener {
            isFromGallery = true
            val intent = Intent()
            intent.setAction(Intent.ACTION_PICK)
            intent.setType("image/*")
            startActivityForResult(intent, IMAGE_PICK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK && data != null) {
            val uri = data.data
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                binding.imageView.setImageBitmap(bitmap)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun Bitmap.rotate(degrees: Float) =
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)

    private fun predict() {

        if (bitmap == null) {
            Toast.makeText(this, "Please capture image.", Toast.LENGTH_SHORT).show()
            return
        }

        val recognitions = yolov5TFLiteDetector?.detect(bitmap)
        val mutableBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = mutableBitmap?.let {
            Canvas(it)
        }

        var totalWoodCount = 0

        if (recognitions != null) {
            for (recognition in recognitions) {
                if (recognition.confidence > 0.5) {
                    totalWoodCount++
                    val location = recognition.location
                    val radius = (location.right - location.left) / 2
                    canvas?.drawCircle(location.centerX(), location.centerY(), radius, boxPaint)
                }
            }
        }

        if (!isFromGallery) {
            binding.imageView.setImageBitmap(mutableBitmap?.rotate(90f))
        } else {
            binding.imageView.setImageBitmap(mutableBitmap)
        }

        binding.tvWooCount.text = "Total wood count: $totalWoodCount"
    }


    private fun checkPermission() {
        Dexter.withContext(this)
            .withPermission(Manifest.permission.CAMERA)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(permissionGrantedResponse: PermissionGrantedResponse) {
                    dispatchTakePictureIntent()
                }

                override fun onPermissionDenied(permissionDeniedResponse: PermissionDeniedResponse) {
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionRequest: PermissionRequest,
                    permissionToken: PermissionToken
                ) {
                    permissionToken.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun dpToPixel(dp: Int): Int {
        val r = resources
        val px =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), r.displayMetrics)
        return px.toInt()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.putExtra("outputX", dpToPixel(1024))
        takePictureIntent.putExtra("outputY", dpToPixel(1024))

        var photoFile: File? = null
        try {
            photoFile = createImageFile()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        if (photoFile != null) {
            val photoURI = FileProvider.getUriForFile(
                this, BuildConfig.APPLICATION_ID + ".provider",
                photoFile
            )
            mProfileFile = photoFile
            mProfileFile?.let { profileFile ->
                imagePath = profileFile.absolutePath
                imagePath?.let { imgPath ->
                    imageName = imgPath.substring(imgPath.lastIndexOf("/") + 1)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    imageCaptureLauncher.launch(takePictureIntent)

                }
            }
        }

        return
    }

    private fun Context.createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val mFileName = "JPEG_" + timeStamp + "_"
        val storageDir: File? = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(mFileName, ".jpg", storageDir)
    }

    private var imageCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val bitMapOption = BitmapFactory.Options()
                bitMapOption.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imagePath, bitMapOption)

                bitmap = BitmapFactory.decodeFile(imagePath, bitMapOption)

                mProfileFile?.let { mFile ->
                    bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, mFile.toUri())
                    binding.imageView.setImageURI(mFile.toUri())
                }
            }
        }
}
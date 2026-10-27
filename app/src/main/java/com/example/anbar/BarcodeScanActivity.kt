package com.example.anbar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.annotation.OptIn
import com.example.anbar.databinding.ActivityBarcodeScanBinding
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * اسکنر حرفه‌ای بارکد و QR — پشتیبانی از همه فرمت‌های رایج
 */
class BarcodeScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScanBinding
    private lateinit var repository: InventoryRepository
    private lateinit var scanner: BarcodeScanner
    private var camera: Camera? = null
    private var torchOn = false
    private val scanned = AtomicBoolean(false)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        repository = InventoryRepository(this)

        // همه فرمت‌های رایج بارکد + QR
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC
            )
            .build()
        scanner = BarcodeScanning.getClient(options)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnTorch.setOnClickListener { toggleTorch() }
        binding.btnManualCode.setOnClickListener { showManualEntry() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            // حتی بدون دوربین، ورود دستی ممکن است
            showManualEntry()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val analysis = try {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(1280, 720))
                        .build()
                } catch (_: Exception) {
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                }

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                binding.tvScanStatus.text = "دوربین آماده — بارکد را روبه‌رو بگیرید"
            } catch (e: Exception) {
                binding.tvScanStatus.text = "خطای دوربین"
                Toast.makeText(this, "خطای دوربین: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "فلش در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        binding.btnTorch.text = if (torchOn) "🔦 خاموش" else "🔦 نور"
    }

    private fun showManualEntry() {
        val et = EditText(this).apply {
            hint = "کد بارکد یا QR"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("ورود دستی کد")
            .setView(et)
            .setPositiveButton("جستجو") { _, _ ->
                val code = et.text?.toString()?.trim().orEmpty()
                if (code.isNotEmpty()) onBarcodeFound(code)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (scanned.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (scanned.get() || barcodes.isEmpty()) return@addOnSuccessListener
                for (barcode in barcodes) {
                    val raw = barcode.rawValue?.trim()
                    if (!raw.isNullOrEmpty()) {
                        if (scanned.compareAndSet(false, true)) {
                            runOnUiThread {
                                binding.tvScanStatus.text = "پیدا شد!"
                                feedbackSuccess()
                                onBarcodeFound(raw)
                            }
                        }
                        break
                    }
                }
            }
            .addOnFailureListener {
                // ignore frame errors
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun feedbackSuccess() {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                .startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (_: Exception) { }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(80)
                }
            }
        } catch (_: Exception) { }
    }

    private fun onBarcodeFound(code: String) {
        val found = repository.findByBarcode(code)
        if (found != null) {
            val (product, variant) = found
            val name = buildString {
                append(product.name)
                if (variant != null) append(" · ${variant.name}")
            }
            val stock = repository.getInventoryRows().filter { it.productId == product.id }
                .sumOf { it.quantity }
            val price = repository.priceFor(product.id, "RETAIL")
            val msg = buildString {
                append(name)
                append("\nکد: $code")
                append("\nموجودی: ${FormatUtils.qty(stock)} ${product.unit}")
                if (price > 0) append("\nقیمت خرده‌فروشی: ${FormatUtils.money(price)}")
            }
            val options = arrayOf(
                "⬇️  ورود به انبار",
                "💳  فروش (صندوق)",
                "💰  تنظیم قیمت",
                "⬆️  خروج از انبار"
            )
            AlertDialog.Builder(this)
                .setTitle("اسکن هوشمند")
                .setMessage(msg)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openTransaction(true, product.id, code)
                        1 -> {
                            startActivity(
                                Intent(this, PosActivity::class.java)
                                    .putExtra("product_id", product.id)
                                    .putExtra("barcode", code)
                            )
                            finish()
                        }
                        2 -> showPriceDialog(product.id, product.name)
                        3 -> openTransaction(false, product.id, code)
                    }
                }
                .setNegativeButton("بستن") { _, _ ->
                    scanned.set(false)
                    binding.tvScanStatus.text = "آماده اسکن مجدد"
                }
                .setOnCancelListener {
                    scanned.set(false)
                    binding.tvScanStatus.text = "آماده اسکن مجدد"
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("کالا یافت نشد")
                .setMessage("کد «$code» در انبار ثبت نشده.\nمی‌خواهید کالای جدید اضافه کنید؟")
                .setPositiveButton("افزودن کالا") { _, _ ->
                    startActivity(
                        Intent(this, AddProductActivity::class.java)
                            .putExtra("prefill_barcode", code)
                    )
                    finish()
                }
                .setNegativeButton("اسکن دوباره") { _, _ ->
                    scanned.set(false)
                    binding.tvScanStatus.text = "آماده اسکن مجدد"
                }
                .show()
        }
    }

    private fun showPriceDialog(productId: Long, productName: String) {
        val current = repository.getProductPrices()[productId]
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 16, 40, 8)
        }
        fun priceField(hint: String, value: Double) = android.widget.EditText(this).apply {
            this.hint = hint
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (value > 0) setText(value.toLong().toString())
        }.also { layout.addView(it) }
        val etRetail = priceField("خرده‌فروشی", current?.retail ?: 0.0)
        val etWholesale = priceField("عمده", current?.wholesale ?: 0.0)
        val etColleague = priceField("همکار", current?.colleague ?: 0.0)
        val etVip = priceField("VIP", current?.vip ?: 0.0)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("قیمت · $productName")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                repository.setProductPrice(
                    ProductPrice(
                        productId = productId,
                        retail = etRetail.text.toString().toDoubleOrNull() ?: 0.0,
                        wholesale = etWholesale.text.toString().toDoubleOrNull() ?: 0.0,
                        vip = etVip.text.toString().toDoubleOrNull() ?: 0.0,
                        colleague = etColleague.text.toString().toDoubleOrNull() ?: 0.0
                    )
                )
                Toast.makeText(this, "قیمت ذخیره شد", Toast.LENGTH_SHORT).show()
                scanned.set(false)
                binding.tvScanStatus.text = "آماده اسکن مجدد"
            }
            .setNegativeButton("لغو") { _, _ ->
                scanned.set(false)
                binding.tvScanStatus.text = "آماده اسکن مجدد"
            }
            .show()
    }

    private fun openTransaction(isEntry: Boolean, productId: Long, barcode: String) {
        startActivity(
            Intent(this, TransactionActivity::class.java)
                .putExtra("is_entry", isEntry)
                .putExtra("product_id", productId)
                .putExtra("barcode", barcode)
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            scanner.close()
        } catch (_: Exception) { }
        cameraExecutor.shutdown()
    }
}

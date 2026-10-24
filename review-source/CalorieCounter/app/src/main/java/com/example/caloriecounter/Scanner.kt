package com.example.caloriecounter

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun Scanner(onCode:(String)->Unit,onCancel:()->Unit) {
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current
 var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted=it }
 val executor=remember { Executors.newSingleThreadExecutor() }
 val scanner=remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13,Barcode.FORMAT_EAN_8,Barcode.FORMAT_UPC_A,Barcode.FORMAT_UPC_E).build()) }
 val preview=remember { PreviewView(context) };val delivered=remember { AtomicBoolean(false) }
 var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
 var error by remember { mutableStateOf("") }
 val currentOnCode by rememberUpdatedState(onCode)
 DisposableEffect(permitted) {
  var active=true
  var ownedPreview:Preview?=null
  var ownedAnalysis:ImageAnalysis?=null
  if(permitted) {
   val future=ProcessCameraProvider.getInstance(context)
   future.addListener({
    if(active) runCatching {
     val p=future.get();provider=p
     val pv=Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) };ownedPreview=pv
     val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();ownedAnalysis=analysis
     analysis.setAnalyzer(executor) { proxy ->
      val image=proxy.image
      if(image==null || delivered.get()) proxy.close() else scanner.process(InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees))
       .addOnSuccessListener { list -> list.firstNotNullOfOrNull { it.rawValue }?.let { code -> if(active && delivered.compareAndSet(false,true)) currentOnCode(code) } }
       .addOnCompleteListener { proxy.close() }
     }
     p.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,pv,analysis)
    }.onFailure { error=it.message ?: "Camera unavailable" }
   },ContextCompat.getMainExecutor(context))
  }
  onDispose { active=false;ownedAnalysis?.clearAnalyzer();val uses=listOfNotNull(ownedPreview,ownedAnalysis).toTypedArray();if(uses.isNotEmpty()) provider?.unbind(*uses) }
 }
 DisposableEffect(Unit) { onDispose { executor.shutdown();scanner.close() } }
 Column(Modifier.fillMaxSize()) {
  TextButton(onClick=onCancel) { Text("Back") }
  if(!permitted) Button(onClick={ launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
  if(error.isNotBlank()) Text(error)
  if(permitted) AndroidView(factory={ preview },modifier=Modifier.weight(1f).fillMaxWidth())
 }
}

package br.com.cosmiceffect.pdf_renderer

import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.NonNull
import androidx.core.content.FileProvider
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.benjinus.pdfium.PdfiumSDK
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/** PdfRendererPlugin */
class PdfRendererPlugin : FlutterPlugin, MethodCallHandler {
    private val pdfReaderMap = HashMap<String, PdfiumSDK?>()
    private var workerThread: HandlerThread? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(flutterPluginBinding.binaryMessenger, "br.com.cosmiceffect.pdf_renderer")
        // MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "br.com.cosmiceffect.pdf_renderer")
        channel.setMethodCallHandler(PdfRendererPlugin())
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "br.com.cosmiceffect.pdf_renderer")
            channel.setMethodCallHandler(PdfRendererPlugin())
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "createPdfDocumentHandler" -> onCreatePdfDocumentHandler(call, result)
            "getPageCount" -> onGetPageCount(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            workerThread?.quitSafely()
        } else {
            workerThread?.quit()
        }

        workerThread = null

        pdfReaderMap.forEach { entry ->
            entry.value?.closeDocument()
        }
        pdfReaderMap.clear()
    }

    private fun onDismissHandler(call: MethodCall, result: Result) {
        val handlerId: String? = call.arguments as? String
        if (handlerId == null) {
            result.error("IOException", "Handler ID required", "Handler ID argument wasn't provided")
            return
        }

        pdfReaderMap[handlerId]?.closeDocument()
        pdfReaderMap.remove(handlerId)

        result.success(true)
    }

    private fun onGetPageCount(call: MethodCall, result: Result) {
        val handlerId: String? = call.arguments as? String
        if (handlerId == null) {
            result.error("IOException", "Handler ID required", "Handler ID argument wasn't provided")
            return
        }

        val sdk = pdfReaderMap[handlerId]
        if (sdk == null) {
            result.error("IOException", "PDF handler not initialized", "There is no PDF handler for this ID")
            return
        }

        if (workerThread == null) {
            workerThread = HandlerThread("PdfWorker").also { it.start() }
        }
        workerThread?.run {
            val workerHandler = Handler(looper)
            val mainHandler = Handler(Looper.getMainLooper())

            workerHandler.post {
                val count = sdk.pageCount
                mainHandler.post {
                    result.success(count)
                }
            }
        }
    }

    private fun onCreatePdfDocumentHandler(call: MethodCall, result: Result) {
        val absoluteFilePath: String? = call.arguments as? String

        if (absoluteFilePath == null) {
            result.error("IOException", "File path argument is required", "File path argument wasn't provided")
            return
        }

        val pdfFile = File(absoluteFilePath)
        if (!pdfFile.canRead()) {
            result.error("IOException", "File can't be read", "File at path [${pdfFile.absolutePath}] can't be read")
            return
        }


        val handlerId = UUID.randomUUID().toString()
        var sdk: PdfiumSDK? = null
        try {
            val fd = FileProvider().openFile(Uri.fromFile(pdfFile), "r")
            sdk = PdfiumSDK()
            sdk.newDocument(fd, null)
            pdfReaderMap[handlerId] = sdk

            result.success(handlerId)
        } catch (e: Exception) {
            try {
                sdk?.closeDocument()
            } catch (ignored: Exception) {
            }

            pdfReaderMap.remove(handlerId)

            result.error("IOException", "Error opening PDF file", e.message)
        }
    }
}


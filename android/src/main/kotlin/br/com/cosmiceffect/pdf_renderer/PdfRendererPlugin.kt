package br.com.cosmiceffect.pdf_renderer

import android.content.Context
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import androidx.annotation.NonNull
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.HashMap

class PdfRendererPlugin : FlutterPlugin {

    companion object {
        private var handler: PdfRendererPluginHandler? = null

        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "br.com.cosmiceffect.pdf_renderer")
            channel.setMethodCallHandler(PdfRendererPluginHandler(registrar))
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        handler?.dismiss()
        handler = null
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(flutterPluginBinding.binaryMessenger, "br.com.cosmiceffect.pdf_renderer")
        handler = PdfRendererPluginHandler(flutterPluginBinding).also {
            channel.setMethodCallHandler(it)
        }
    }
}

class PdfRendererPluginHandler : MethodCallHandler {
    private var binding: FlutterPlugin.FlutterPluginBinding? = null
    private var registrar: Registrar? = null
    private val pdfReaderMap = HashMap<String, PdfDocument?>()
    private var workerThread: HandlerThread? = null
    private var pdfCore: PdfiumCore? = null

    constructor(binding: FlutterPlugin.FlutterPluginBinding?) {
        this.binding = binding
    }

    constructor(registrar: Registrar?) {
        this.registrar = registrar
    }

    companion object {
        const val CACHE_PATH = "pdfium_cache"
    }

    fun dismiss() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            workerThread?.quitSafely()
        } else {
            workerThread?.quit()
        }

        workerThread = null

        pdfCore?.let { core ->
            val handlers = pdfReaderMap.keys
            for (handlerId in handlers) {
                val document = pdfReaderMap.remove(handlerId)
                document?.let {
                    core.closeDocument(it)
                }
            }
        }

        pdfCore = null

        try {
            getContext()?.let { context ->
                if (context.externalCacheDir?.isDirectory == true) {
                    val pdfCachePath = File(context.externalCacheDir, CACHE_PATH)
                    pdfCachePath.deleteRecursively()
                }
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "createPdfDocumentHandler" -> onCreatePdfDocumentHandler(call, result)
            "getPageCount" -> onGetPageCount(call, result)
            "openPage" -> onGetPage(call, result)
            "closeDocument" -> onDismissHandler(call, result)
            else -> result.notImplemented()
        }
    }

    private fun getContext(): Context? {
        return binding?.applicationContext ?: registrar?.context()?.applicationContext
    }

    private fun onDismissHandler(call: MethodCall, result: Result) {
        val handlerId: String? = call.arguments as? String
        if (handlerId == null) {
            result.error("Exception", "Handler ID required", "Handler ID argument wasn't provided")
            return
        }

        val document = pdfReaderMap.remove(handlerId)
        document?.let {
            pdfCore?.closeDocument(it)
        }

        Log.d("pdf_renderer", "Dismissed PDF renderer handler")
        result.success(true)
    }

    private fun onGetPage(call: MethodCall, result: Result) {
        val context = getContext()
        if (context == null) {
            result.error("Exception", "Plugin not attached to a context", "Failed to obtain current context from plugin")
            return
        }

        val args: List<*>? = call.arguments as? List<*>

        val handlerId = args?.get(0) as? String
        if (handlerId == null) {
            result.error("Exception", "Handler ID required", "Handler ID argument wasn't provided")
            return
        }

        val pageIndex = args[1] as? Int
        if (pageIndex == null) {
            result.error("Exception", "Page Index required", "Page index argument wasn't provided")
            return
        }

        val document = pdfReaderMap[handlerId]
        if (document == null) {
            result.error("Exception", "PDF handler not initialized", "There is no PDF handler for this ID")
            return
        }

        workerThread = (workerThread ?: HandlerThread("PdfWorker").also { it.start() }).also { worker ->
            val mainHandler = Handler(Looper.getMainLooper())
            val workerHandler = Handler(worker.looper)

            workerHandler.post {
                var cachedPagePath: String? = null

                pdfCore?.let { core ->
                    val framebuffer: Bitmap?
                    try {
                        if (!pdfReaderMap.containsKey(handlerId)) {
                            // Handles the dismissal of open document while worker is trying to open a page
                            return@let
                        }

                        core.openPage(document, pageIndex)

                        if (!pdfReaderMap.containsKey(handlerId)) {
                            return@let
                        }

                        val size = core.getPageSize(document, pageIndex)

                        if (!pdfReaderMap.containsKey(handlerId)) {
                            return@let
                        }

                        framebuffer = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.RGB_565)
                        core.renderPageBitmap(document, framebuffer, pageIndex, 0, 0, size.width, size.height)
                    }catch (e: Throwable) {
                        return@let
                    }

                    var pdfCachePath: File? = null
                    if (context.externalCacheDir?.isDirectory == true) {
                        pdfCachePath = File(context.externalCacheDir, CACHE_PATH).also {
                            it.mkdirs()
                        }
                    }

                    pdfCachePath?.let { cacheDir ->
                        if (cacheDir.isDirectory) {
                            val cachePageName = UUID.randomUUID().toString()
                            val cachedPageFile = File(cacheDir, "${cachePageName}.jpg")
                            FileOutputStream(cachedPageFile).use { output ->
                                framebuffer?.compress(Bitmap.CompressFormat.JPEG, 100, output)
                            }
                            cachedPagePath = cachedPageFile.absolutePath
                        }
                    }
                }

                mainHandler.post {
                    if (cachedPagePath != null) {
                        result.success(cachedPagePath)
                    } else {
                        result.error("IOError", "Could not generate image for selected page", "Error writing image for selected page")
                    }
                }
            }
        }
    }

    private fun onGetPageCount(call: MethodCall, result: Result) {
        val handlerId: String? = call.arguments as? String
        if (handlerId == null) {
            result.error("Exception", "Handler ID required", "Handler ID argument wasn't provided")
            return
        }

        val document = pdfReaderMap[handlerId]
        if (document == null) {
            result.error("Exception", "PDF handler not initialized", "There is no PDF handler for this ID")
            return
        }


        workerThread = (workerThread ?: HandlerThread("PdfWorker").also { it.start() }).also { worker ->
            val mainHandler = Handler(Looper.getMainLooper())
            val workerHandler = Handler(worker.looper)

            workerHandler.post {
                val count = pdfCore?.getPageCount(document) ?: 0
                mainHandler.post {
                    result.success(count)
                }
            }
        }
    }

    private fun onCreatePdfDocumentHandler(call: MethodCall, result: Result) {
        val context = getContext()
        if (context == null) {
            result.error("Exception", "Plugin not attached to a context", "Failed to obtain current context from plugin")
            return
        }

        val absoluteFilePath: String? = call.arguments as? String

        if (absoluteFilePath == null) {
            result.error("Exception", "File path argument is required", "File path argument wasn't provided")
            return
        }

        val pdfFile = File(absoluteFilePath)
        if (!pdfFile.canRead()) {
            result.error("Exception", "File can't be read", "File at path [${pdfFile.absolutePath}] can't be read")
            return
        }

        pdfCore = pdfCore ?: PdfiumCore(context)
        val core = pdfCore
        if (core == null) {
            result.error("Exception", "Could not initialize PDF reader", "Could not initialize PDF reader")
            return
        }

        workerThread = (workerThread ?: HandlerThread("PdfWorker").also { it.start() }).also { worker ->
            val mainHandler = Handler(Looper.getMainLooper())
            val workerHandler = Handler(worker.looper)

            workerHandler.post {
                val documentHandlerId = UUID.randomUUID().toString()
                var document: PdfDocument? = null
                try {
                    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    document = core.newDocument(fd)
                    pdfReaderMap[documentHandlerId] = document

                    mainHandler.post {
                        Log.d("pdf_renderer", "Created new PDF document handler")
                        result.success(documentHandlerId)
                    }
                } catch (e: Exception) {
                    try {
                        document?.let {
                            core.closeDocument(document)
                        }
                    } catch (ignored: Exception) {
                    }

                    document = null
                    pdfReaderMap.remove(documentHandlerId)

                    mainHandler.post {
                        Log.d("pdf_renderer", "Error creating PDF document handler")
                        result.error("Exception", e.message, "Error opening PDF file")
                    }
                }
            }
        }
    }
}
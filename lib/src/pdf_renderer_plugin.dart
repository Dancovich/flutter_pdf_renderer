import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

import '../pdf_renderer.dart';
import 'pdf_entities.dart';

class PdfRenderer {
  static const MethodChannel _channel =
      const MethodChannel('br.com.cosmiceffect.pdf_renderer');

  Future<PdfDocument> openDocument(File pdfFile) async {
    if (pdfFile?.existsSync() != true) {
      throw FileSystemException('PDF document not found', pdfFile?.path);
    }

    String handlerId;

    try {
      handlerId = await _channel.invokeMethod<String>(
          'createPdfDocumentHandler', pdfFile.absolute.path);
    } on PlatformException catch (_) {
      handlerId = null;
    }

    if (handlerId?.isNotEmpty ?? false) {
      return PdfDocumentHandler(handlerId).createDocument();
    }

    return null;
  }
}

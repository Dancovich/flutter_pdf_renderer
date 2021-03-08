import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../pdf_renderer.dart';
import 'pdf_entities.dart';

class PdfRenderer {
  static const MethodChannel _channel =
      const MethodChannel('br.com.cosmiceffect.pdf_renderer');

  static Future<PdfDocument?> openDocument(File pdfFile) async {
    if (!pdfFile.existsSync()) {
      throw FileSystemException('PDF document not found', pdfFile.path);
    }

    String? handlerId;

    try {
      handlerId = await _channel.invokeMethod<String>(
          'createPdfDocumentHandler', pdfFile.absolute.path);
    } on PlatformException catch (e) {
      if (!kReleaseMode) {
        log(
          'Could not create document handler',
          error: e,
          name: 'pdf_renderer',
        );
      }

      handlerId = null;
    }

    if (handlerId?.isNotEmpty ?? false) {
      return PdfDocumentHandler(handlerId!).createDocument();
    }

    return null;
  }
}

class PdfDocumentHandler {
  PdfDocumentHandler(this.id);

  final String id;

  PdfDocument createDocument() {
    return PdfDocumentImpl._(this);
  }

  Future<int> get pageCount async =>
      await PdfRenderer._channel.invokeMethod('getPageCount', this.id);

  Future<void> closeDocument() async {
    await PdfRenderer._channel.invokeMethod('closeDocument', this.id);
  }

  Future<String?> openPage(int pageIndex) => PdfRenderer._channel
      .invokeMethod('openPage', <dynamic>[this.id, pageIndex]);
}

class PdfDocumentImpl extends PdfDocument {
  PdfDocumentImpl._(this._handler);

  final PdfDocumentHandler _handler;

  bool _isOpen = true;

  @override
  Future<PdfPage?> getPage(int pageIndex) async {
    if (!_isOpen) {
      throw ResourceClosedException("Can't use a document after it's closed");
    }

    try {
      final path = await _handler.openPage(pageIndex);

      if (path != null) {
        final file = File(path);
        if (file.existsSync()) {
          return PdfPageImpl._(file);
        }
      }
    } on PlatformException catch (e) {
      if (!kReleaseMode) {
        print('$e');
      }
    }

    return null;
  }

  @override
  Future<int> get pageCount => _isOpen
      ? _handler.pageCount
      : throw ResourceClosedException("Can't use a document after it's closed");

  @override
  Future<void> close() {
    _isOpen = false;
    return _handler.closeDocument();
  }
}

class PdfPageImpl extends PdfPage {
  PdfPageImpl._(this._path);

  final File _path;

  @override
  File get path => _path;
}

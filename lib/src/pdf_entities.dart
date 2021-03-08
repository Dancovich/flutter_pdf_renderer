import 'dart:io';

abstract class PdfDocument {
  Future<int> get pageCount;

  Future<PdfPage?> getPage(int pageIndex);

  Future<void> close();
}

abstract class PdfPage {
  File get path;
}

class ResourceClosedException implements Exception {
  ResourceClosedException([this.message]);

  final message;
}

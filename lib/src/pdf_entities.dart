class PdfDocument {
  PdfDocument._(this._handler);

  final PdfDocumentHandler _handler;
}

class PdfPage {}

class PdfDocumentHandler {
  PdfDocumentHandler(this.id);

  final String id;

  PdfDocument createDocument() {
    return PdfDocument._(this);
  }
}

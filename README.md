# flutter_pdf_renderer

PDF renderer plugin for Android API 16 and above that uses Pdfium
through [PdfiumAndroid](https://github.com/barteksc/PdfiumAndroid).

At the moment this plugin only works on Android.

## Installing

Declare the dependency in your pubspec.yaml file
```yaml
dependencies:
  # ...
  flutter_pdf_renderer: ^0.0.1
```

For help getting started with Flutter, view the online [documentation](https://flutter.io/).

## Getting Started

Import the package

```dart
import 'package:flutter_pdf_renderer/pdf_renderer.dart';
```

Fetch an instance of PdfDocument for a file

```dart
final document = await PdfRenderer.openDocument('/path/to/some/file.pdf');
```

Count how many pages it has and render pages

```dart
int pageCount = await document.pageCount;
PdfPage currentPage;

void loadFirstPage() {
    document.getPage(0).then((page) {
        setState(() {
            currentPage = page;
        });
    });
}

Widget build(BuildContext context) {
    return currentPage != null
        ? Center(child: Image.file(currentPage.path))
        : Center(Text('Page could not be loaded'));
}
```

For a more in-depth example check the `example` folder.
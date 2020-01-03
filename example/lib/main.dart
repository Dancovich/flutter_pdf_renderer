import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:path_provider/path_provider.dart';
import 'package:flutter_pdf_renderer/pdf_renderer.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  Future<PdfDocument> documentFuture;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    documentFuture = DefaultAssetBundle.of(context)
        .load('assets/sample.pdf')
        .then((byteData) async {
      final folder = await getTemporaryDirectory();
      int randomNumber = max(0, Random().nextInt(48392));
      final cachedFile = File('${folder.absolute.path}/$randomNumber.pdf');

      await cachedFile.writeAsBytes(
        byteData.buffer
            .asUint8List(byteData.offsetInBytes, byteData.lengthInBytes),
        mode: FileMode.write,
        flush: true,
      );

      return PdfRenderer.openDocument(cachedFile);
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: <Widget>[
            FutureBuilder<int>(
              future: documentFuture?.then((document) => document.pageCount),
              builder: (context, snapshot) {
                switch (snapshot.connectionState) {
                  case ConnectionState.done:
                    return Text('Document has ${snapshot.data} pages');
                  default:
                    return Text('Wait for document to open');
                }
              },
            ),
            Expanded(
              child: FutureBuilder<PdfPage>(
                future: documentFuture?.then((document) => document.getPage(0)),
                builder: (context, snapshot) {
                  switch (snapshot.connectionState) {
                    case ConnectionState.done:
                      final page = snapshot.data;
                      return page != null
                          ? Image.file(page.path)
                          : Text('Could not load page 1');
                    default:
                      return Text('Waiting for page to load');
                  }
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    debugPrint("Disposing document");
    documentFuture?.then((document) => document?.close());
    super.dispose();
  }
}

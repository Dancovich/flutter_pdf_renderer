import Flutter
import UIKit

public class SwiftPdfRendererPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "br.com.cosmiceffect.pdf_renderer", binaryMessenger: registrar.messenger())
    let instance = SwiftPdfRendererPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}

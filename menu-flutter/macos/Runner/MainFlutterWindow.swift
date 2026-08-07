import Cocoa
import FlutterMacOS

class MainFlutterWindow: NSWindow {
  override func awakeFromNib() {
    let flutterViewController = FlutterViewController()
    self.contentViewController = flutterViewController

    // 模拟手机屏幕：iPhone 标准尺寸 390×844（逻辑点），固定不可缩放，居中显示
    let phoneSize = NSSize(width: 390, height: 844)
    let screenFrame = NSScreen.main?.frame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
    let origin = NSPoint(
      x: screenFrame.midX - phoneSize.width / 2,
      y: screenFrame.midY - phoneSize.height / 2
    )
    self.styleMask.remove(.resizable) // 固定窗口，更像手机
    self.setFrame(NSRect(origin: origin, size: phoneSize), display: true)

    RegisterGeneratedPlugins(registry: flutterViewController)

    super.awakeFromNib()
  }
}

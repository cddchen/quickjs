import SwiftUI

@MainActor
@Observable
final class LatexConverterViewModel {
    var inputText: String = """
    已知 $E = mc^2$，求根公式：
    $$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}$$
    """
    var outputText: String = ""
    var metricInfo: String = "等待转换..."
    var isEngineReady: Bool = false
    var isConverting: Bool = false
    var errorMessage: String? = nil

    private var engine: QuickJsEngine?

    func setup() {
        Task {
            let qjs = QuickJsEngine { [weak self] tag, duration in
                Task { @MainActor in
                    self?.metricInfo = "JS 回调 -> \(tag), 耗时: \(String(format: "%.2f", duration)) ms"
                }
            }
            do {
                // 将重型 JS 初始化调度到后台线程池
                try await Task.detached(priority: .userInitiated) {
                     try qjs.initializePipeline()
                }.value
                self.engine = qjs
                self.isEngineReady = true
            } catch {
                self.errorMessage = error.localizedDescription
            }
        }
    }

    func convert() {
        guard let engine, !inputText.isEmpty, isEngineReady else { return }
        isConverting = true
        errorMessage = nil

        let rawInput = inputText
        Task{
            let result = await Task.detached(priority: .userInitiated) {
                engine.convert(text: rawInput)
            }.value
            self.outputText = result
            self.isConverting = false
        }
    }
}
import Foundation

final class QuickJsEngine: NSObject, @unchecked Sendable {
    private let bridge: QuickJsBridge
    private let adapter: MetricAdapter

    init(metricHandler: @escaping (String, Double) -> Void) {
        self.bridge = QuickJsBridge()
        self.adapter = MetricAdapter(handler: metricHandler)
        super.init()

        self.bridge.metricDelegate = adapter
        objc_setAssociatedObject(self, "metric_adapter", adapter, .OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }

    func initializePipeline() throws {
        // 1. 从 Bundle 读取 Katex 源码
        guard let url = Bundle.main.url(forResource: "katex.min", withExtension: "js"),
        let katexCode = try? String(contentsOf: url, encoding: .utf8) else {
            throw NSError(domain: "QuickJsEngine", code: -1, userInfo: [NSLocalizedDescriptionKey: "katex.min.js 未找到"])
        }

        _ = bridge.evaluateScript("var window = globalThis; \(katexCode)", filename: "katex.min.js")

        // 2. 注入正则匹配和转换函数
        let script = """
        function renderAllLatexToMathMl(fullText) {
            var start = Date.now();
            var regex = /(\\$\\$[\\s\\S]*?\\$\\$|\\$[^\\$\\n]+?\\$)/g;
            var count = 0;
            var res = fullText.replace(regex, function(match) {
                count++;
                var isBlock = match.startsWith("$$");
                var raw = isBlock ? match.slice(2, -2) : match.slice(1, -1);
                try {
                    return katex.renderToString(raw.trim(), {
                        displayMode: isBlock,
                        output: "mathml",
                        throwOnError: false
                    });
                } catch(e) {
                    return match;
                }
            });
            if (typeof NativeBridge !== 'undefined') {
                NativeBridge.onMetric("RenderLatex(count=" + count + ")", Date.now() - start);
            }
            return res;
        }
        """
        _ = bridge.evaluateScript(script, filename: "pipeline.js")
    }

    func convert(text: String) -> String {
        // JSON 序列化保证安全转义
        guard let data = try? JSONSerialization.data(withJSONObject: [text]),
        let jsonArray = String(data: data, encoding: .utf8) else {
            return text
        }
        let escapedArg = String(jsonArray.dropFirst().dropLast())
        return bridge.evaluateScript("renderAllLatexToMathMl(\(escapedArg))", filename: "<eval>") ?? ""
    }
}

// 代理转闭包适配器
private final class MetricAdapter: NSObject, QuickJsMetricDelegate {
    private let handler: (String, Double) -> Void
    init(handler: @escaping (String, Double) -> Void) {
        self.handler = handler
    }
    func onMetric(withTag tag: String, durationMs: Double) {
        handler(tag, durationMs);
    }
}
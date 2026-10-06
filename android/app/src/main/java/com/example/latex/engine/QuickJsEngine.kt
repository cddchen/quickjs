package com.example.latex.engine

import android.content.Context
import com.example.latex.converter.MathMlPlainTextConverter
import java.io.Closeable

interface QuickJsMetricListener {
    fun onMetric(tag: String, durationMs: Double)
}

class QuickJsEngine(listener: QuickJsMetricListener? = null): Closeable {
    private  var nativeHandle: Long = 0
    
    init {
        System.loadLibrary("quickjs_bridge")
        nativeHandle = nativeInit(listener)
    }
    
    fun initLatexPipeline(context: Context) {
        // 1. 加载 Katex 基础运行库
        val katexJs = context.assets.open("katex.min.js").bufferedReader().use { it.readText() }
        eval("var window = globalThis; $katexJs")
        
        // 2. 注入核心公式转换函数，完成后调用 NativeBridge 反调 Native
        eval("""
            function renderAllLatexToMathMl(fullText) {
                var start = Date.now();
                // 匹配 $$...$$ 块级公式与 $...$ 行内公式
                var regex = /(\$\$[\s\S]*?\$\$|\$[^\$\n]+?\$)/g;
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
                    } catch (e) {
                        return match;
                    }
                });
                
                // 回调 Native 汇报耗时与统计
                if (typeof NativeBridge !== 'undefined') {
                    NativeBridge.onMetric("RenderLatex(count=" + count + ")", Date.now() - start);
                }
                return res;
            }
        """.trimIndent())
    }
    
    fun convertText(text: String): String {
        // 对文本做安全转义并求值
        val escaped = org.json.JSONObject.quote(text)
        val mathMlText = eval("renderAllLatexToMathMl($escaped)")
        
        return MathMlPlainTextConverter.convertFullText(mathMlText)
    }
    
    fun eval(script: String): String = nativeEval(nativeHandle, script)
    
    override fun close() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }
    
    private external fun nativeInit(listener: QuickJsMetricListener?): Long
    private external fun nativeEval(handle: Long, script: String): String
    private external fun nativeRelease(handle: Long)
}
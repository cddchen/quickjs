package com.example.latex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.latex.engine.QuickJsEngine
import com.example.latex.engine.QuickJsMetricListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConverterUiState(
    val inputText: String = """已知 ${'$'}E = mc^2${'$'}，求根公式：
${'$'}${'$'}x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}${'$'}${'$'}""",
    val outputText: String = "",
    val metricInfo: String = "等待转换...",
    val isEngineReady: Boolean = false,
    val isConverting: Boolean = false,
    val errorMessage: String? = null
)

class LatexConverterViewModel(application: Application): AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()
    
    private var engine: QuickJsEngine? = null
    
    init {
        initEngine()
    }
    
    private fun initEngine() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val qjs = QuickJsEngine(object : QuickJsMetricListener {
                    override fun onMetric(tag: String, durationMs: Double) {
                        // JS 回调 Native: 更新性能和统计信息
                        _uiState.update {
                            it.copy(metricInfo = "JS 回调 -> $tag, 耗时: ${String.format("%.2f", durationMs)} ms")
                        }
                    }
                })
                qjs.initLatexPipeline(getApplication())
                engine = qjs
                _uiState.update { it.copy(isEngineReady = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "QuickJS 初始化失败: ${e.localizedMessage}")
                }
            }
        }
    }
    
    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }
    
    fun convert() {
        val currentEngine = engine
        val textToConvert = _uiState.value.inputText
        if (currentEngine == null || textToConvert.isBlank()) return 
        
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, errorMessage = null) }
            try {
                // 调度到后台 Default 线程进行 JS 执行
                val result = withContext(Dispatchers.Default) {
                    currentEngine.convertText(textToConvert)
                }
                _uiState.update { it.copy(outputText = result, isConverting = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        errorMessage = "转换出错: ${e.localizedMessage}",
                            isConverting = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine?.close()
        engine = null
    }
}
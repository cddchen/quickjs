package com.example.latex.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatexConverterScreen(viewModel: LatexConverterViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QuickJS Latex -> MathML") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 输入框
            Text(text = "输入文本（包含Latex公式）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("请输入 Markdownwn / Latex 混合内容...") },
                singleLine = false
            )
            
            // 转换触发按钮及性能信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = viewModel::convert,
                    enabled = state.isEngineReady && !state.isConverting
                ) {
                    if (state.isConverting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("解析中...")
                    } else {
                        Text(if (state.isEngineReady) "执行转换" else "引擎加载中...")
                    }
                }
                
                // 展示 JS 回调 Native 拿到的 Metric
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = state.metricInfo,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 异常提示
            state.errorMessage?.let { err ->
                Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            
            // 输出框
            Text(text = "输出结果: ", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = state.outputText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().height(260.dp),
                placeholder = { Text("转换结果将显示在此处...")}
            )
        }
    }
}
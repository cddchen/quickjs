import SwiftUI

struct ContentView: View {
    @State private var viewModel = LatexConverterViewModel()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("输入文本（包含Latex公式）").font(.headline)

                    TextEditor(text: $viewModel.inputText)
                        .frame(height: 120)
                        .padding(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.secondary.opacity(0.3)))

                    HStack {
                        Button(action: viewModel.convert) {
                            if viewModel.isConverting {
                                ProgressView().controlSize(.small)
                            } else {
                                Text(viewModel.isEngineReady ? "执行转换" : "引擎加载中...")
                            }
                        }
                            .buttonStyle(.borderedProminent)
                            .disabled(!viewModel.isEngineReady || viewModel.isConverting)

                        Spacer()

                        Text(viewModel.metricInfo)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.secondary.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }

                    if let error = viewModel.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }

                    Text("输出结果：")
                        .font(.headline)
                    
                    TextEditor(text: .constant(viewModel.outputText))
                        .frame(height: 240)
                        .padding(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.secondary.opacity(0.3)))
                }
                .padding()
            }
            .navigationTitle("QuickJS iOS Demo")
        }
        .onAppear {
            viewModel.setup()
        }
    }
}

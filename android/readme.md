[原始文本 + LaTeX]
       ↓ (1. QuickJS + KaTeX：将公式解析为 MathML XML 结构)
[文本 + MathML DOM 树]
       ↓ (2. Kotlin 递归 AST 处理器：映射分数/根号/上标)
[Unicode 纯文本（如 E = mc²，(-b ± √(b² - 4ac))/(2a)）]
package com.hrm.latex.renderer.export

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.hrm.latex.parser.LatexParser
import com.hrm.latex.parser.ParseDiagnostic
import com.hrm.latex.parser.model.LatexNode
import com.hrm.latex.parser.visitor.BaseLatexVisitor
import com.hrm.latex.renderer.font.loadKatexFonts
import com.hrm.latex.renderer.layout.LatexRenderer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexFontFamilies
import com.hrm.latex.renderer.model.LatexTheme
import com.hrm.latex.renderer.model.toContext
import kotlin.math.ceil

/** SVG 尺寸和真实文字基线使用同一轮测量结果，供外部行内排版消费。 */
data class JvmLatexSvg(
    val bytes: ByteArray,
    val width: Float,
    val height: Float,
    val baseline: Float,
)

/** 服务器侧入口：直接复用原测量／绘图管线，不创建窗口或 Composition。 */
class JvmLatexRenderer private constructor(
    private val fontFamilies: LatexFontFamilies,
    private val density: Density,
    private val textMeasurer: TextMeasurer,
) {
    private val parser = LatexParser()

    @Synchronized
    fun renderSvg(
        source: String,
        fontSize: Float = 16f,
        color: Int = 0xff000000.toInt(),
        isDarkTheme: Boolean = false,
    ): JvmLatexSvg {
        require(source.isNotBlank()) { "公式不能为空" }
        require(fontSize.isFinite() && fontSize > 0f) { "公式字号必须为有限正数" }
        val parsed = parser.parseWithDiagnostics(source)
        parsed.diagnostics.firstOrNull { it.severity != ParseDiagnostic.Severity.INFO }?.let {
            throw IllegalArgumentException("公式解析失败: ${it.message}")
        }
        val document = parsed.document
        document.accept(CompleteFormulaValidator)
        require(document.children.isNotEmpty()) { "公式未生成可绘制内容" }
        val config = LatexConfig(fontSize = fontSize.sp, theme = LatexTheme.light(color = Color(color)))
        val context = config.toContext(isDarkTheme, fontFamilies)
        val measured = LatexRenderer.measure(document.children, context, textMeasurer, density)
        require(measured.canvasWidth.isFinite() && measured.canvasWidth > 0f &&
            measured.canvasHeight.isFinite() && measured.canvasHeight > 0f) { "公式绘图尺寸无效" }
        val width = ceil(measured.canvasWidth)
        val height = ceil(measured.canvasHeight)
        val bytes = requireNotNull(renderToSvg(width, height, true, false) { canvas ->
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(width, height)) {
                with(LatexRenderer) { draw(measured, Color.Transparent) }
            }
        }) { "公式 SVG 编码失败" }
        return JvmLatexSvg(bytes, width, height, measured.verticalPadding + measured.layout.baseline)
    }

    companion object {
        /** 显式字体可直接使用；内置字体加载失败时原样抛出，调用方不会得到替代字体结果。 */
        suspend fun create(fontFamilies: LatexFontFamilies? = null): JvmLatexRenderer {
            val density = Density(1f)
            val fonts = fontFamilies ?: loadKatexFonts()
            return JvmLatexRenderer(
                fonts,
                density,
                TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr),
            )
        }
    }
}

private object CompleteFormulaValidator : BaseLatexVisitor<Unit>() {
    override fun defaultVisit(node: LatexNode) = Unit
    override fun visitCommand(node: LatexNode.Command): Unit =
        throw IllegalArgumentException("不支持的公式命令: \\${node.name}")
    override fun visitError(node: LatexNode.Error): Unit =
        throw IllegalArgumentException("公式解析失败: ${node.message}")
}

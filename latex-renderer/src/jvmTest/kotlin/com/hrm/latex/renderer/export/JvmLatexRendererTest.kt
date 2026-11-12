package com.hrm.latex.renderer.export

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmLatexRendererTest {
    private val config = 32f

    @Test
    fun subscriptKeepsTheParentBaselineAndAddsDescent() {
        val plain = renderer.renderSvg("x", config)
        val subscript = renderer.renderSvg("x_2", config)
        assertEquals(plain.baseline, subscript.baseline, 0.1f)
        assertTrue(subscript.height - subscript.baseline > plain.height - plain.baseline)
        assertFailsWith<IllegalArgumentException> { renderer.renderSvg(" ", config) }
    }

    @Test
    fun casesPaintExpressionBeforeCondition() {
        val svg = renderer.renderSvg("\\begin{cases}\\textcolor{red}{x}&\\textcolor{blue}{y}\\end{cases}", config)
        Data.makeFromBytes(svg.bytes).use { data ->
            SVGDOM(data).use { dom ->
                Surface.makeRasterN32Premul(svg.width.toInt(), svg.height.toInt()).use { surface ->
                    surface.canvas.clear(Color.WHITE)
                    dom.setContainerSize(svg.width, svg.height)
                    dom.render(surface.canvas)
                    surface.makeImageSnapshot().use { image ->
                        Bitmap().use { bitmap ->
                            assertTrue(bitmap.allocN32Pixels(image.width, image.height))
                            assertTrue(image.readPixels(bitmap))
                            var redX = 0L
                            var blueX = 0L
                            var redCount = 0
                            var blueCount = 0
                            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                                val color = bitmap.getColor(x, y)
                                if (Color.getR(color) > 180 && Color.getG(color) < 80 && Color.getB(color) < 80) {
                                    redX += x; redCount++
                                }
                                if (Color.getB(color) > 180 && Color.getG(color) < 80 && Color.getR(color) < 80) {
                                    blueX += x; blueCount++
                                }
                            }
                            assertTrue(redCount > 0 && blueCount > 0, "表达式与条件都必须实际绘出")
                            assertTrue(redX.toDouble() / redCount < blueX.toDouble() / blueCount,
                                "分段函数的表达式必须位于条件左侧")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val renderer by lazy { runBlocking { JvmLatexRenderer.create() } }
    }
}

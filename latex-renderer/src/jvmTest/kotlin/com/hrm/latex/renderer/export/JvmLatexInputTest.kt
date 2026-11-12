package com.hrm.latex.renderer.export

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmLatexInputTest {
    @Test
    fun rejectsInvalidFormulaWithoutContaminatingTheNextRender() = runBlocking {
        val renderer = JvmLatexRenderer.create()
        val expected = renderer.renderSvg("x_2")
        for (source in listOf(" ", "\\unknownTavoloCommand", "\\require{missingPackage}x", "\\frac{1}{")) {
            assertFailsWith<IllegalArgumentException>(source) { renderer.renderSvg(source) }
            val actual = renderer.renderSvg("x_2")
            assertContentEquals(expected.bytes, actual.bytes)
            assertEquals(expected.baseline, actual.baseline)
        }
    }
}

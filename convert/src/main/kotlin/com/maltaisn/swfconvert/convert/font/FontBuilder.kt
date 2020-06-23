/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.swfconvert.convert.font

import com.maltaisn.swfconvert.core.shape.PathElement.ClosePath
import com.maltaisn.swfconvert.core.shape.PathElement.QuadTo
import com.maltaisn.swfconvert.core.text.BaseFont
import com.maltaisn.swfconvert.core.text.GlyphData
import org.apache.logging.log4j.kotlin.logger
import org.doubletype.ossa.Engine
import org.doubletype.ossa.adapter.EContour
import org.doubletype.ossa.adapter.EContourPoint
import org.doubletype.ossa.module.GlyphFile
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Based on ffdec, doubletype code was taken there too.
 * [https://github.com/jindrapetrik/jpexs-decompiler/blob/bf2a413725c09eecded4e8f42af4487ecd1842a5/libsrc/ttf/src/fontastic/Fontastic.java]
 * Modifications made to this code include custom whitespace validation.
 */
internal class FontBuilder @Inject constructor(
    private val doubletypeEngine: Engine
) {

    private val logger = logger()

    /**
     * Build a TTF file for a [font] instance, setting the [BaseFont.fontFile] field.
     * @param destination Directory to create the file in.
     * @param tempDir Used to output temporary files generated by doubletype.
     */
    fun buildFont(font: BaseFont, destination: File, tempDir: File) {
        // Make sure font name is a valid file name because doubletype uses temp files.
        val name = validateFilename(font.name)
        if (name != font.name) {
            logger.debug { "Renamed font '${font.name}' to valid filename '$name'" }
        }

        // Build font
        doubletypeEngine.buildNewTypeface(name, tempDir)
        configureTypeface(font, name)
        createFontGlyphs(font)
        doubletypeEngine.buildTrueType()

        // Move file from temp dir to destination.
        val ttfFile = File(tempDir, "$name.ttf")
        val outFile = File(destination, "$name.ttf")
        ttfFile.renameTo(outFile)

        // Set file on font
        font.fontFile = outFile
    }

    private fun configureTypeface(font: BaseFont, name: String) {
        doubletypeEngine.typeface.let {
            // No scaling is done between the intermediate representation EM square and the one
            // used by doubletype. So make sure they are the same.
            assert(it.em == GlyphData.EM_SQUARE_SIZE.toDouble())

            it.ascender = font.metrics.ascent.toDouble().coerceIn(0.0, it.em)
            it.descender = font.metrics.descent.toDouble().coerceIn(0.0, it.em)

            it.fontFamilyName = name
            it.version = TYPEFACE_VERSION
            it.license = TYPEFACE_LICENSE
            it.author = TYPEFACE_AUTHOR
            it.copyrightYear = TYPEFACE_COPYRIGHT
            it.creationDate = TYPEFACE_DATE
            it.modificationDate = TYPEFACE_DATE
        }
    }

    private fun createFontGlyphs(font: BaseFont) {
        // Add glyphs to typeface
        for (glyph in font.glyphs) {
            val code = glyph.char.toLong()
            val data = glyph.data
            doubletypeEngine.checkUnicodeBlock(code)
            val glyphFile = doubletypeEngine.addNewGlyph(code)
            glyphFile.advanceWidth = data.advanceWidth.roundToInt()
            createGlyphFileContours(glyphFile, data)
        }
        doubletypeEngine.typeface.addRequiredGlyphs()
    }

    private fun createGlyphFileContours(glyphFile: GlyphFile, data: GlyphData) {
        for (contour in data.contours) {
            val econtour = EContour()
            econtour.type = EContour.k_quadratic
            for (e in contour.elements) {
                if (e is ClosePath) {
                    continue
                }
                val epoint = EContourPoint(e.x.toDouble(), e.y.toDouble(), true)
                if (e is QuadTo) {
                    econtour.addContourPoint(EContourPoint(e.cx.toDouble(), e.cy.toDouble(), false))
                }
                // Note: epoint.controlPoint1 only works partially--sometimes it isn't added correctly.
                econtour.addContourPoint(epoint)
            }
            glyphFile.addContour(econtour)
        }
    }

    private fun validateFilename(filename: String) =
        filename.replace(INVALID_FILENAME_CHARS_PATTERN, "")

    companion object {
        private val INVALID_FILENAME_CHARS_PATTERN = """[/\\:*?"<>|]""".toRegex()

        // These metadata values aren't found in SWF files, so generic ones are used instead.
        private val TYPEFACE_DATE = Date(946684800000) // 2000-01-01 00:00:00 UTC
        private const val TYPEFACE_COPYRIGHT = "2000"
        private const val TYPEFACE_LICENSE = ""
        private const val TYPEFACE_VERSION = "1.0"
        private const val TYPEFACE_AUTHOR = "unknown"
    }
}

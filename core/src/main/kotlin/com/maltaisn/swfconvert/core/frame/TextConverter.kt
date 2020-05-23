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

package com.maltaisn.swfconvert.core.frame

import com.flagstone.transform.text.StaticTextTag
import com.flagstone.transform.text.TextSpan
import com.maltaisn.swfconvert.core.config.Configuration
import com.maltaisn.swfconvert.core.conversionError
import com.maltaisn.swfconvert.core.font.data.Font
import com.maltaisn.swfconvert.core.font.data.FontId
import com.maltaisn.swfconvert.core.font.data.FontScale
import com.maltaisn.swfconvert.core.font.data.GlyphData
import com.maltaisn.swfconvert.core.frame.data.FrameObject
import com.maltaisn.swfconvert.core.frame.data.GroupObject
import com.maltaisn.swfconvert.core.frame.data.ShapeObject
import com.maltaisn.swfconvert.core.frame.data.TextObject
import com.maltaisn.swfconvert.core.image.CompositeColorTransform
import com.maltaisn.swfconvert.core.image.data.Color
import com.maltaisn.swfconvert.core.shape.path.Path
import com.maltaisn.swfconvert.core.shape.path.PathElement
import com.maltaisn.swfconvert.core.toAffineTransformOrIdentity
import com.maltaisn.swfconvert.core.toColor
import java.awt.geom.AffineTransform
import kotlin.math.absoluteValue


/**
 * Converts SWF text tags to [TextObject] intermediate representation.
 */
internal class TextConverter(private val fileIndex: Int,
                             private val fonts: Map<FontId, Font>,
                             private val config: Configuration) {

    private lateinit var textTag: StaticTextTag
    private lateinit var colorTransform: CompositeColorTransform

    // Text span style
    private var font: Font? = null
    private var fontSize: Float? = null
    private var color: Color? = null
    private var offsetX = 0f
    private var offsetY = 0f


    fun parseText(textTag: StaticTextTag,
                  colorTransform: CompositeColorTransform): List<FrameObject> {
        this.textTag = textTag
        this.colorTransform = colorTransform

        // Reset styles
        font = null
        fontSize = null
        color = null
        offsetX = 0f
        offsetY = 0f

        // Make sure font scale parameters are all the same
        // This is important because the same transform group is used for all spans.
        var lastScale: FontScale? = null
        for (span in textTag.spans) {
            if (span.identifier != null) {
                val fontId = FontId(fileIndex, span.identifier)
                val font = fonts[fontId] ?: error("Unknown font ID")
                val scale = font.metrics.scale
                conversionError(lastScale == null || scale == lastScale) {
                    "Different scale fonts in same text tag"
                }
                lastScale = scale
            }
        }
        if (lastScale == null) {
            // No fonts, so no text.
            return emptyList()
        }

        // Create text transform
        // The Y scale is negated because glyphs in fonts are upright, and they must be flipped
        // since frame is already flipped, the same way images are flipped.
        // Then, the transform is scaled except for translation components.
        val tagTr = textTag.transform.toAffineTransformOrIdentity()
        val usx = lastScale.unscaleX.toDouble()
        val usy = -lastScale.unscaleY.toDouble()
        val transform = AffineTransform(
                tagTr.scaleX * usx, tagTr.shearY * usx,
                tagTr.shearX * usy, tagTr.scaleY * usy,
                tagTr.translateX, tagTr.translateY)

        var textObjects = mutableListOf<FrameObject>()
        val objects: MutableList<FrameObject> = if (transform.isIdentity) {
            textObjects
        } else {
            // Transform could not be extracted to translate and scale, so wrap objects in transform group.
            val group = GroupObject.Transform(textTag.identifier, transform)
            textObjects = group.objects
            mutableListOf(group)
        }

        // Parse text spans
        for (span in textTag.spans) {
            textObjects.add(parseTextSpan(span) ?: continue)
        }
        if (textObjects.isEmpty()) {
            // No text spans with content, so no text.
            return emptyList()
        }

        // Add text bounds rectangle if needed
        if (config.main.drawTextBounds) {
            val bounds = textTag.bounds
            val boundsRect = Path(listOf(PathElement.Rectangle(
                    bounds.minX.toFloat(), bounds.minY.toFloat(),
                    bounds.width.toFloat(), bounds.height.toFloat())),
                    lineStyle = config.main.debugLineStyle)
            objects += ShapeObject(textTag.identifier, listOf(boundsRect))
        }

        return objects
    }

    private fun parseTextSpan(span: TextSpan): TextObject? {
        parseTextSpanStyle(span)
        val font = this.font!!
        val fontSize = this.fontSize!!
        val scale = font.metrics.scale

        var xPos = offsetX
        val yPos = offsetY

        if (fontSize == 0f) {
            // No height, text will be invisible.
            return null
        }

        // Get span text and glyph offsets
        val glyphEntries = span.characters

        var ignoreCustomPos = true
        val textSb = StringBuilder(glyphEntries.size)
        val glyphOffsets = mutableListOf<Float>()

        // Replace all leading whitespace with a X offset.
        var start = 0
        for (glyphEntry in glyphEntries) {
            val glyph = font.getGlyph(glyphEntry)
            if (!glyph.isWhitespace) {
                break
            }
            xPos += glyphEntry.advance / scale.unscaleX
            start++
        }

        // Important! Although not very clearly from SWF specification, 
        // drawing text actually changes the X offset.
        offsetX += glyphEntries.sumByDouble { (it.advance / scale.unscaleX).toDouble() }.toFloat()

        if (start == glyphEntries.size) {
            // Only whitespaces, no text object to create.
            return null
        }

        // Find last index, ignoring trailing whitespace.
        var end = glyphEntries.size
        for (glyphEntry in glyphEntries.asReversed()) {
            val glyph = font.getGlyph(glyphEntry)
            if (!glyph.isWhitespace) {
                break
            }
            end--
        }

        // Add text span glyphs to list
        for (i in start until end) {
            val glyphEntry = glyphEntries[i]
            val glyph = font.getGlyph(glyphEntry)
            var charStr = glyph.char.toString()

            // Get char default advance.
            val defaultAdvance = try {
                glyph.data.advanceWidth
            } catch (e: IllegalArgumentException) {
                // Glyph isn't in typeface!
                if (!glyph.isWhitespace) {
                    throw e
                }
                // If char is a whitespace, just replace with space.
                charStr = " "
                GlyphData.WHITESPACE_ADVANCE_WIDTH
            }

            // Add char to list
            textSb.append(charStr)

            // Add advance width difference to list
            if (i != glyphEntries.lastIndex) {
                // Find difference with default advance width, in glyph space units.
                val actualAdvance = glyphEntry.advance.toFloat() / (scale.unscaleX * fontSize) * GlyphData.EM_SQUARE_SIZE
                val diff = actualAdvance - defaultAdvance
                glyphOffsets += diff
                if (diff.absoluteValue >= CUSTOM_POSITIONING_THRESHOLD) {
                    ignoreCustomPos = false
                }
            }
        }
        if (ignoreCustomPos || glyphOffsets.size == 1) {
            glyphOffsets.clear()
        }

        // Note: SWF spec talks about the offset being relative to the glyph "reference point", i.e
        // the starting point of the path. It doesn't seem necessary to take it into account.

        return TextObject(textTag.identifier, xPos, yPos, fontSize,
                color!!, font, textSb.toString(), glyphOffsets)
    }

    private fun parseTextSpanStyle(span: TextSpan) {
        // Font
        if (span.identifier != null) {
            val fontId = FontId(fileIndex, span.identifier)
            this.font = fonts[fontId] ?: error("Unknown font ID")
        }
        val font = this.font
        conversionError(font != null) { "No font specified" }

        // Font size
        if (span.height != null) {
            fontSize = span.height.toFloat()
        }
        conversionError(fontSize != null) { "No font size specified" }

        // Text color
        if (span.color != null) {
            color = colorTransform.transform(span.color.toColor())
        }
        conversionError(color != null) { "No text color specified" }

        // Offsets
        // Although SWF specification says that unspecified offset is like setting it to 0,
        // this is not true, offset is only set when specified. Also, offsets are not additive.
        val scale = font.metrics.scale
        if (span.offsetX != null) {
            offsetX = span.offsetX.toFloat() / scale.unscaleX
        }
        if (span.offsetY != null) {
            offsetY = span.offsetY.toFloat() / -scale.unscaleY
        }
    }

    companion object {
        /**
         * Value under which custom glyph advance differences are ignored.
         * This value is in glyph space units.
         */
        private const val CUSTOM_POSITIONING_THRESHOLD = 5f
    }

}

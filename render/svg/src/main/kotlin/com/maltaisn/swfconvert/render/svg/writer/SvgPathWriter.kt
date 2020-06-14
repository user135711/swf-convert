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

package com.maltaisn.swfconvert.render.svg.writer

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.pow
import kotlin.math.roundToInt


/**
 * Class used to write SVG paths with numbers using a certain [precision].
 * Path strings are also optimized to reduce size.
 */
internal class SvgPathWriter(private val precision: Int) {

    init {
        // float is used internally so precision beyond 5 might produce rounding errors.
        require(precision in 0..5) { "Precision must be between 0 and 5." }
    }

    private val numberFmt = DecimalFormat().apply {
        maximumFractionDigits = precision
        decimalFormatSymbols = DecimalFormatSymbols().apply {
            isGroupingUsed = false
            decimalSeparator = '.'
        }
    }

    private val precisionMult = 10f.pow(precision)

    private val sb = StringBuilder()

    private val absoluteSb = StringBuilder()
    private val relativeSb = StringBuilder()

    private var lastCommand = '_'
    private var lastNumberStr = ""

    private var moveToX = 0f
    private var moveToY = 0f
    private var currentX = 0f
    private var currentY = 0f


    fun moveTo(x: Float, y: Float) {
        moveToX = x.roundToPrecision()
        moveToY = y.roundToPrecision()
        appendShortestCommand('M', x, y,
                getFormattedValues(x, y),
                getFormattedValues(x - currentX, y - currentY))
    }

    fun lineTo(x: Float, y: Float) {
        val absVals = getFormattedValues(x, y)
        val relVals = getFormattedValues(x - currentX, y - currentY)

        val rxStr = relVals.first()
        val ryStr = relVals.last()
        val rxIsZero = rxStr == "0"
        val ryIsZero = ryStr == "0"

        when {
            rxIsZero && ryIsZero -> {
                // Invisible line, don't write it.
                return
            }
            rxIsZero -> {
                // X didn't change from current position
                appendShortestCommand('V', x, y,
                        arrayOf(absVals.last()), arrayOf(relVals.last()))
            }
            ryIsZero -> {
                // Y didn't change from current position
                appendShortestCommand('H', x, y,
                        arrayOf(absVals.first()), arrayOf(relVals.first()))
            }
            else -> appendShortestCommand('L', x, y, absVals, relVals)
        }
    }

    fun quadTo(c1x: Float, c1y: Float, x: Float, y: Float) {
        val relVals = getFormattedValues(c1x - currentX, c1y - currentY,
                x - currentX, y - currentY)
        if (relVals[2] == "0" && relVals[3] == "0") {
            // Invisible quad curve, don't write it.
            return
        }
        appendShortestCommand('Q', x, y,
                getFormattedValues(c1x, c1y, x, y),
                relVals)
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        val relVals = getFormattedValues(c1x - currentX, c1y - currentY,
                c2x - currentX, c2y - currentY,
                x - currentX, y - currentY)
        if (relVals[4] == "0" && relVals[5] == "0") {
            // Invisible cubic curve, don't write it.
            return
        }
        appendShortestCommand('C', x, y,
                getFormattedValues(c1x, c1y, c2x, c2y, x, y),
                relVals)
    }

    fun closePath() {
        sb.append('Z')
        lastCommand = 'Z'
        currentX = moveToX
        currentY = moveToY
    }

    private fun getFormattedValues(vararg values: Float) = Array(values.size) { i ->
        // Format value to string.
        // Remove leading zero if value is -1 < n < 1 and n != 0
        val value = values[i]
        var str = numberFmt.format(value)
        if (str == "-0") {
            str = "0"
        }
        if (value in -1.0..1.0 && '.' in str) {
            str = str.replaceFirst("0", "")
        }
        str
    }

    private fun appendShortestCommand(command: Char, x: Float, y: Float,
                                      absoluteValues: Array<String>,
                                      relativeValues: Array<String>) {
        val absCommand = command.toUpperCase()
        val relCommand = command.toLowerCase()

        absoluteSb.clear()
        absoluteSb.appendCommand(absCommand, absoluteValues)

        relativeSb.clear()
        relativeSb.appendCommand(relCommand, relativeValues)

        lastCommand = if (relativeSb.length < absoluteSb.length) {
            sb.append(relativeSb)
            lastNumberStr = relativeValues.last()
            currentX += (x - currentX).roundToPrecision()
            currentY += (y - currentY).roundToPrecision()
            relCommand
        } else {
            sb.append(absoluteSb)
            lastNumberStr = absoluteValues.last()
            currentX = x.roundToPrecision()
            currentY = y.roundToPrecision()
            absCommand
        }
    }

    private fun Float.roundToPrecision() = (this * precisionMult).roundToInt() / precisionMult

    private fun StringBuilder.appendCommand(command: Char,
                                            values: Array<String>) {
        // Append command char
        var lastNbStr = lastNumberStr
        if (!(lastCommand == command && command != 'M' && command != 'm'
                        || lastCommand == 'M' && command == 'L'
                        || lastCommand == 'm' && command == 'l')) {
            // - Command can be omitted if previous command is the same, except 'move to'.
            // - 'line to' command can be omitted if placed immediately after a 'move to' command.
            append(command)
            lastNbStr = ""
        }

        // Append all values for that command
        for (value in values) {
            // Append space separator if value has no sign '-' or decimal point '.'
            // to separate it from previous value, and if value isn't the first after command.
            if (lastNbStr != "" && '-' !in value
                    && ('.' !in lastNbStr || !value.startsWith('.'))) {
                append(' ')
            }

            // Append value.
            append(value)

            lastNbStr = value
        }
    }

    override fun toString() = sb.toString()

}

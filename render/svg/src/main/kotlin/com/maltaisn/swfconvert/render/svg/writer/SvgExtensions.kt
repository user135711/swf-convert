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


/**
 * Append a list of space separated [values] to [this] StringBuilder.
 */
internal fun StringBuilder.appendValuesList(vararg values: Float) {
    for (value in values) {
        this.append(NUMBER_FMT.format(value))
        this.append(' ')
    }
    if (values.isNotEmpty()) {
        this.deleteCharAt(this.length - 1)
    }
}

private val NUMBER_FMT = DecimalFormat().apply {
    isGroupingUsed = false
    decimalFormatSymbols = DecimalFormatSymbols().apply {
        decimalSeparator = '.'
    }
}

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

package com.maltaisn.swfconvert.core.shape.data

import com.flagstone.transform.datatype.Color
import com.flagstone.transform.fillstyle.FillStyle
import com.flagstone.transform.linestyle.CapStyle
import com.flagstone.transform.linestyle.JoinStyle
import com.flagstone.transform.linestyle.LineStyle1
import com.flagstone.transform.linestyle.LineStyle2


data class WLineStyle(val color: Color,
                      val width: Int,
                      val capStyle: CapStyle?,
                      val joinStyle: JoinStyle?,
                      val miterLimit: Int,
                      val fillStyle: FillStyle?) {

    constructor(style: LineStyle1) : this(style.color, style.width, null,
            null, 0, null)

    constructor(style: LineStyle2) : this(style.color, style.width, style.startCap,
            style.joinStyle, style.miterLimit, style.fillStyle)
    
}
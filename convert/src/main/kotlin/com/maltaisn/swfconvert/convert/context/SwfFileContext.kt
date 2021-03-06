/*
 * Copyright (C) 2020 Nicolas Maltais
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.
 */

package com.maltaisn.swfconvert.convert.context

import java.io.File

/**
 * Context for a SWF [file] at index [fileIndex] being converted.
 */
internal class SwfFileContext(
    parent: ConvertContext?,
    val file: File,
    val fileIndex: Int
) : ConvertContext(parent) {

    override val description: String
        get() = "file #$fileIndex ($file)"

}

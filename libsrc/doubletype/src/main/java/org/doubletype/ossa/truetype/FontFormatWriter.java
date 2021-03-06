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
package org.doubletype.ossa.truetype;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * @author e.e
 */
public class FontFormatWriter {

    protected DataOutputStream m_buffer;

    protected ByteArrayOutputStream m_bytes;

    private int m_offset;

    public FontFormatWriter() {
        init();
    }

    protected void init() {
        m_bytes = new ByteArrayOutputStream();
        m_buffer = new DataOutputStream(m_bytes);
        m_offset = 0;
    }

    public void write() throws IOException {
    }

    public byte[] toByteArray() {
        return m_bytes.toByteArray();
    }

    /**
     * Size of buffer in bytes.
     *
     * @return size of buffer in bytes.
     */
    public int size() {
        return m_bytes.size();
    }

    public void reset() {
        m_bytes.reset();
    }

    protected void writeFixed32(double a_value) throws IOException {
        final int k_denom = 16384;

        short mantissa = (short) Math.floor(a_value);
        int fraction = (int) ((a_value - mantissa) * k_denom);
        if (fraction > k_denom) {
            fraction = 0;
            mantissa++;
        }

        m_buffer.writeShort(mantissa);
        m_buffer.writeShort(fraction);
    }

    protected void writeUInt16(int a_value) throws IOException {
        writeInt16((short) (0xffff & a_value));
    }

    protected void writeInt16(int a_value) throws IOException {
        m_buffer.writeShort((short) a_value);
    }

    protected void writeFWord(int a_value) throws IOException {
        writeInt16(a_value);
    }

    protected void writeUFWord(int a_value) throws IOException {
        writeUInt16(a_value);
    }

    protected void writeUInt32(long a_value) throws IOException {
        writeInt32((int) (0xffffffff & a_value));
    }

    protected void writeInt32(int a_value) throws IOException {
        m_buffer.writeInt(a_value);
    }

    protected void writeUInt8(int a_byte) throws IOException {
        m_buffer.writeByte(a_byte);
    }

    protected void writeTag(String a_value) throws IOException {
        String s = a_value + "    ";

        int i;
        for (i = 0; i < 4; i++) {
            writeUInt8(s.charAt(i));
        }
    }

    protected void writeLongDateTime(Date a_date) throws IOException {
        long sec = a_date.getTime() / 1000;
        sec += (1970 - 1904) * 365 * 24 * 60 * 60;
        m_buffer.writeLong(sec);
    }

    protected String getTag() {
        throw new RuntimeException("unimplemnted call to getTag");
    }

    protected long getCheckSum() {
        long retval = 0;
        byte[] bytes = toByteArray();

        for (int i = 0; i < bytes.length / 4; i++) {
            long n = 0;
            for (int j = 0; j < 4; j++) {
                n += bytes[4 * i + j] << ((4 - j) * 8);
            } // for j
            retval += n;
        }

        return retval;
    }

    protected void pad() throws IOException {
        int align = 4;
        int numOfPad = align - m_bytes.size() % align;
        if (numOfPad == align) {
            return;
        }

        for (int i = 0; i < numOfPad; i++) {
            writeUInt8(0);
        }
    }

    public int getOffset() {
        return m_offset;
    }

    public void setOffset(int a_value) {
        m_offset = a_value;
    }
}

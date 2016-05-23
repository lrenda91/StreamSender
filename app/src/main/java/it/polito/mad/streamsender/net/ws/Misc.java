/*
 * Copyright (C) 2015-2016 Neo Visionaries Inc.
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
package it.polito.mad.streamsender.net.ws;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Collection;

import static it.polito.mad.streamsender.net.ws.WebSocketOpcode.*;


class Misc
{
    private static final SecureRandom sRandom = new SecureRandom();


    private Misc()
    {
    }


    /**
     * Get a UTF-8 byte array representation of the given string.
     */
    public static byte[] getBytesUTF8(String string)
    {
        if (string == null)
        {
            return null;
        }

        try
        {
            return string.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // This never happens.
            return null;
        }
    }


    /**
     * Convert a UTF-8 byte array into a string.
     */
    public static String toStringUTF8(byte[] bytes)
    {
        if (bytes == null)
        {
            return null;
        }

        return toStringUTF8(bytes, 0, bytes.length);
    }


    /**
     * Convert a UTF-8 byte array into a string.
     */
    public static String toStringUTF8(byte[] bytes, int offset, int length)
    {
        if (bytes == null)
        {
            return null;
        }

        try
        {
            return new String(bytes, offset, length, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // This never happens.
            return null;
        }
        catch (IndexOutOfBoundsException e)
        {
            return null;
        }
    }


    /**
     * Fill the given buffer with random bytes.
     */
    public static byte[] nextBytes(byte[] buffer)
    {
        sRandom.nextBytes(buffer);

        return buffer;
    }


    /**
     * Create a buffer of the given size filled with random bytes.
     */
    public static byte[] nextBytes(int nBytes)
    {
        byte[] buffer = new byte[nBytes];

        return nextBytes(buffer);
    }


    /**
     * Convert a WebSocket opcode into a string representation.
     */
    public static String toOpcodeName(int opcode)
    {
        switch (opcode)
        {
            case CONTINUATION:
                return "CONTINUATION";

            case TEXT:
                return "TEXT";

            case BINARY:
                return "BINARY";

            case CLOSE:
                return "CLOSE";

            case PING:
                return "PING";

            case PONG:
                return "PONG";

            default:
                break;
        }

        if (0x1 <= opcode && opcode <= 0x7)
        {
            return String.format("DATA(0x%X)", opcode);
        }

        if (0x8 <= opcode && opcode <= 0xF)
        {
            return String.format("CONTROL(0x%X)", opcode);
        }

        return String.format("0x%X", opcode);
    }


    /**
     * Read a line from the given stream.
     */
    public static String readLine(InputStream in, String charset) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (true)
        {
            // Read one byte from the stream.
            int b = in.read();

            // If the end of the stream was reached.
            if (b == -1)
            {
                if (baos.size() == 0)
                {
                    // No more line.
                    return null;
                }
                else
                {
                    // The end of the line was reached.
                    break;
                }
            }

            if (b == '\n')
            {
                // The end of the line was reached.
                break;
            }

            if (b != '\r')
            {
                // Normal character.
                baos.write(b);
                continue;
            }

            // Read one more byte.
            int b2 = in.read();

            // If the end of the stream was reached.
            if (b2 == -1)
            {
                // Treat the '\r' as a normal character.
                baos.write(b);

                // The end of the line was reached.
                break;
            }

            // If '\n' follows the '\r'.
            if (b2 == '\n')
            {
                // The end of the line was reached.
                break;
            }

            // Treat the '\r' as a normal character.
            baos.write(b);

            // Append the byte which follows the '\r'.
            baos.write(b2);
        }

        // Convert the byte array to a string.
        return baos.toString(charset);
    }


    /**
     * Find the minimum value from the given array.
     */
    public static int min(int[] values)
    {
        int min = Integer.MAX_VALUE;

        for (int i = 0; i < values.length; ++i)
        {
            if (values[i] < min)
            {
                min = values[i];
            }
        }

        return min;
    }


    /**
     * Find the maximum value from the given array.
     */
    public static int max(int[] values)
    {
        int max = Integer.MIN_VALUE;

        for (int i = 0; i < values.length; ++i)
        {
            if (max < values[i])
            {
                max = values[i];
            }
        }

        return max;
    }


    public static String join(Collection<?> values, String delimiter)
    {
        StringBuilder builder = new StringBuilder();

        join(builder, values, delimiter);

        return builder.toString();
    }


    private static void join(StringBuilder builder, Collection<?> values, String delimiter)
    {
        boolean first = true;

        for (Object value : values)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                builder.append(delimiter);
            }

            builder.append(value.toString());
        }
    }
}

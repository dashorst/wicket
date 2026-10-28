/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.core.util.objects.statedelta;

import java.io.OutputStream;
import java.util.zip.CRC32C;

/**
 * An output stream that keeps no bytes: it only digests and counts what is written to it.
 */
final class HashingOutputStream extends OutputStream
{
	private final CRC32C crc = new CRC32C();

	private long count;

	@Override
	public void write(int b)
	{
		crc.update(b);
		count++;
	}

	@Override
	public void write(byte[] b, int off, int len)
	{
		crc.update(b, off, len);
		count += len;
	}

	/**
	 * @return the digest of everything written since the last call, after which it starts over
	 */
	long take()
	{
		long digest = (crc.getValue() << 32) ^ count;
		crc.reset();
		count = 0;
		return digest;
	}

	/**
	 * @return the number of bytes written since the last {@link #take()}
	 */
	long count()
	{
		return count;
	}
}

/*
 * Copyright (c) 2016 Vladimir L. Shabanov <virlof@gmail.com>
 *
 * Licensed under the Underdark License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://underdark.io/LICENSE.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.underdark.util.nslogger;

import android.util.Log;

import java.nio.charset.Charset;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.LogRecord;

/**
 * A class that encapsulates a log message and can produce a binary representation
 * to send to the desktop NSLogger viewer. Methods are provided to gradually build
 * the various parts of the messsage. Once building is complete, a call to the
 * getBytes() method returns a full-fledged binary message to send to the desktop
 * viewer.
 */
public final class LogMessage
{
	static Charset stringsCharset = Charset.forName("utf-8");

	// Constants for the "part key" field
	static final int
			PART_KEY_MESSAGE_TYPE = 0,		// defines the type of message (see LOGMSG_TYPE_*)
			PART_KEY_TIMESTAMP_S = 1,	// "seconds" component of timestamp
			PART_KEY_TIMESTAMP_MS = 2,		// milliseconds component of timestamp (optional, mutually exclusive with PART_KEY_TIMESTAMP_US)
			PART_KEY_TIMESTAMP_US = 3,		// microseconds component of timestamp (optional, mutually exclusive with PART_KEY_TIMESTAMP_MS)
			PART_KEY_THREAD_ID = 4,
			PART_KEY_TAG = 5,
			PART_KEY_LEVEL = 6,
			PART_KEY_MESSAGE = 7,
			PART_KEY_IMAGE_WIDTH = 8,		// messages containing an image should also contain a part with the image size
			PART_KEY_IMAGE_HEIGHT = 9,		// (this is mainly for the desktop viewer to compute the cell size without having to immediately decode the image)
			PART_KEY_MESSAGE_SEQ = 10,		// the sequential number of this message which indicates the order in which messages are generated
			PART_KEY_FILENAME = 11,			// when logging, message can contain a file name
			PART_KEY_LINENUMBER = 12,		// as well as a line number
			PART_KEY_FUNCTIONNAME = 13;		// and a function or method name

	// Constants for parts in LOGMSG_TYPE_CLIENTINFO
	static final int
			PART_KEY_CLIENT_NAME = 20,
			PART_KEY_CLIENT_VERSION = 21,
			PART_KEY_OS_NAME = 22,
			PART_KEY_OS_VERSION = 23,
			PART_KEY_CLIENT_MODEL = 24,		// For iPhone, device model (i.e 'iPhone', 'iPad', etc)
			PART_KEY_UNIQUEID = 25;			// for remote device identification, part of LOGMSG_TYPE_CLIENTINFO

	// Area starting at which you may define your own constants
	static final int
			PART_KEY_USER_DEFINED = 100;

	// Constants for the "partType" field
	static final int
			PART_TYPE_STRING = 0,			// Strings are stored as UTF-8 data
			PART_TYPE_BINARY = 1,			// A block of binary data
			PART_TYPE_INT16 = 2,
			PART_TYPE_INT32 = 3,
			PART_TYPE_INT64 = 4,
			PART_TYPE_IMAGE = 5;			// An image, stored in PNG format

	// Data values for the PART_KEY_MESSAGE_TYPE parts
	static final int
			LOGMSG_TYPE_LOG = 0,			// A standard log message
			LOGMSG_TYPE_BLOCKSTART = 1,		// The start of a "block" (a group of log entries)
			LOGMSG_TYPE_BLOCKEND = 2,		// The end of the last started "block"
			LOGMSG_TYPE_CLIENTINFO = 3,		// Information about the client app
			LOGMSG_TYPE_DISCONNECT = 4,		// Pseudo-message on the desktop side to identify client disconnects
			LOGMSG_TYPE_MARK = 5;			// Pseudo-message that defines a "mark" that users can place in the log flow

	// Instance variables
	private byte[] data;
	private int dataUsed;
	private final int sequenceNumber;
	private short numParts;

	// Flushing support
	private ReentrantLock doneLock;
	private Condition doneCondition;

	/**
	 * Create a new binary log record from an existing LogRecord instance
	 * @param record		the LogRecord instance we want to send
	 * @param seq			the message's sequence number
	 */
	public LogMessage(LogRecord record, int seq)
	{
		sequenceNumber = seq;
		String msg = record.getMessage();
		data = new byte[msg.length() + 64];			// gross approximation
		dataUsed = 6;
		addTimestamp(record.getMillis());
		addInt32(LOGMSG_TYPE_LOG, PART_KEY_MESSAGE_TYPE);
		addInt32(sequenceNumber, PART_KEY_MESSAGE_SEQ);
		addString(Long.toString(record.getThreadID()), PART_KEY_THREAD_ID);
		addInt16(record.getLevel().intValue(), PART_KEY_LEVEL);
		addString(record.getSourceClassName() + "." + record.getSourceMethodName(), PART_KEY_FUNCTIONNAME);
		addString(record.getMessage(), PART_KEY_MESSAGE);
	}

	/**
	 * Prepare an empty log message that can be filled
	 * @param messageType		the message type (i.e. LOGMSG_TYPE_LOG)
	 * @param seq				the message's sequence number
	 */
	public LogMessage(int messageType, int seq)
	{
		sequenceNumber = seq;
		data = new byte[256];
		dataUsed = 6;
		addInt32(messageType, PART_KEY_MESSAGE_TYPE);
		addInt32(sequenceNumber, PART_KEY_MESSAGE_SEQ);
		addTimestamp(0);
		addThreadID(Thread.currentThread().getId());
	}

	/**
	 * Obtain this message's sequence number (should be a unique number that orders messages sent)
	 * @return the sequence number
	 */
	public int getSequenceNumber()
	{
		return sequenceNumber;
	}

	/**
	 * Internally used to indicate that the client thread will wait for this
	 * message to have been sent before continuing
	 */
	protected void prepareForFlush()
	{
		doneLock = new ReentrantLock();
		doneCondition = doneLock.newCondition();
		doneLock.lock();
	}

	/**
	 * Internally used by the client thread to wait for the logging thread
	 * to have successfully sent the message. Thread is blocked in the
	 * meantime.
	 */
	protected void waitFlush()
	{
		if(doneLock == null)
			return;

		try {
			if (NSLogger.debugLogger)
				Log.v("NSLogger", String.format("waiting for flush of message %d", sequenceNumber));
			doneCondition.await();
		} catch (InterruptedException e) {
			// nothing here
		} finally {
			doneLock.unlock();
		}
	}

	/**
	 * Internally used by the logging thread to mark this message as "flushed"
	 * (sent to the desktop viewer or written to the log file)
	 */
	protected void markFlushed()
	{
		if (doneLock != null)
		{
			if (NSLogger.debugLogger)
				Log.v("NSLogger", String.format("marking message %d as flushed", sequenceNumber));
			doneLock.lock();
			doneCondition.signal();
			doneLock.unlock();
		}
	}

	/**
	 * Return the generated binary message
	 *
	 * @return bytes to send to desktop NSLogger
	 */
	byte[] getBytes()
	{
		int size = dataUsed - 4;
		data[0] = (byte) (size >> 24);
		data[1] = (byte) (size >> 16);
		data[2] = (byte) (size >> 8);
		data[3] = (byte) size;
		data[4] = (byte) (numParts >> 8);
		data[5] = (byte) numParts;

		if (dataUsed == data.length)
			return data;

		byte[] b = new byte[dataUsed];
		System.arraycopy(data, 0, b, 0, dataUsed);
		data = null;
		return b;
	}

	void addTimestamp(long ts)
	{
		if (ts == 0)
			ts = System.currentTimeMillis();
		addInt64(ts / 1000, PART_KEY_TIMESTAMP_S);
		addInt16((int) (ts % 1000), PART_KEY_TIMESTAMP_MS);
	}

	void addThreadID(long threadID)
	{
		String s = null;
		// If the thread is part if our thread group, we can extract its
		// name. Otherwise, just use a thread number
		Thread t = Thread.currentThread();
		if (t.getId() == threadID)
			s = t.getName();
		else
		{
			Thread array[] = new Thread[Thread.activeCount()];
			Thread.enumerate(array);
			for (Thread th : array)
			{
				if (th.getId() == threadID)
				{
					s = t.getName();
					break;
				}
			}
		}
		if (s == null || s.isEmpty())
			s = Long.toString(threadID);
		addString(s, PART_KEY_THREAD_ID);
	}

	private void grow(int nBytes)
	{
		final int n = data.length;
		if (n >= dataUsed + nBytes)
			return;

		byte b[] = new byte[Math.max(n + n / 2, dataUsed + nBytes + 64)];
		System.arraycopy(data, 0, b, 0, dataUsed);
		data = b;
	}

	public void addInt16(int value, int key)
	{
		grow(4);
		int n = dataUsed;
		data[n++] = (byte)key;
		data[n++] = (byte)PART_TYPE_INT16;
		data[n++] = (byte)(value >> 8);
		data[n++] = (byte)value;
		dataUsed = n;
		numParts++;
	}

	public void addInt32(int value, int key)
	{
		grow(6);
		int n = dataUsed;
		data[n++] = (byte)key;
		data[n++] = (byte)PART_TYPE_INT32;
		data[n++] = (byte)(value >> 24);
		data[n++] = (byte)(value >> 16);
		data[n++] = (byte)(value >> 8);
		data[n++] = (byte)value;
		dataUsed = n;
		numParts++;
	}

	public void addInt64(long value, int key)
	{
		grow(10);
		int n = dataUsed;
		data[n++] = (byte)key;
		data[n++] = (byte)PART_TYPE_INT64;
		data[n++] = (byte)(value >> 56);
		data[n++] = (byte)(value >> 48);
		data[n++] = (byte)(value >> 40);
		data[n++] = (byte)(value >> 32);
		data[n++] = (byte)(value >> 24);
		data[n++] = (byte)(value >> 16);
		data[n++] = (byte)(value >> 8);
		data[n++] = (byte)value;
		dataUsed = n;
		numParts++;
	}

	public void addBytes(int key, int type, byte[] bytes)
	{
		final int l = bytes.length;
		grow(l + 6);
		int n = dataUsed;
		data[n++] = (byte)key;
		data[n++] = (byte)type;
		data[n++] = (byte)(l >> 24);
		data[n++] = (byte)(l >> 16);
		data[n++] = (byte)(l >> 8);
		data[n++] = (byte)l;
		System.arraycopy(bytes, 0, data, n, l);
		dataUsed = n + l;
		numParts++;
	}

	public void addString(String s, int key)
	{
		byte[] sb = s.getBytes(stringsCharset);
		addBytes(key, PART_TYPE_STRING, sb);
	}

	public void addBinaryData(byte[] d, int key)
	{
		addBytes(key, PART_TYPE_BINARY, d);
	}

	public void addImageData(byte[] img, int key)
	{
		addBytes(key, PART_TYPE_IMAGE, img);
	}
} // LogMessage
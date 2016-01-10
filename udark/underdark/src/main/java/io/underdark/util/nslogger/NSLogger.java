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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import io.netty.buffer.ByteBuf;
import io.underdark.util.dispatch.DispatchQueue;

public class NSLogger
{
	static final boolean debugLogger = false;

	private static final int logMessageSizeMax = 512 * 1024;

	private enum State
	{
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		CLOSED
	}

	private boolean flushMessages = false;

	private volatile State state = State.DISCONNECTED;

	private final AtomicInteger nextSequenceNumber = new AtomicInteger(0);

	private Deque<LogMessage> messages = new ArrayDeque<>();

	private DispatchQueue queue = new DispatchQueue("NSLogger Output");
	private boolean useSSL = true;

	private String host;
	private int port;

	private Socket socket;
	private OutputStream outputStream;

	public NSLogger()
	{
		super();
	}

	public NSLogger(Context context)
	{
		super();
		loadClientInfo(context.getApplicationContext());
	}

	public void shutdown()
	{
		if(state != State.CONNECTED)
			return;

		try
		{
			socket.close();
		}
		catch (IOException ex)
		{
		}
	}

	public void loadClientInfo(Context context)
	{
		if (debugLogger)
			Log.v("underdark", "NSLogger Pushing client info to front of queue");

		final LogMessage lm = new LogMessage(LogMessage.LOGMSG_TYPE_CLIENTINFO, nextSequenceNumber.getAndIncrement());
		lm.addString(Build.MANUFACTURER + " " + Build.MODEL, LogMessage.PART_KEY_CLIENT_MODEL);
		lm.addString("Android", LogMessage.PART_KEY_OS_NAME);
		lm.addString(Build.VERSION.RELEASE, LogMessage.PART_KEY_OS_VERSION);

		String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
		if(androidId == null)
			androidId = "";
		lm.addString(androidId, LogMessage.PART_KEY_UNIQUEID);

		ApplicationInfo ai = context.getApplicationInfo();
		String appName = ai.packageName;
		if (appName == null)
		{
			appName = ai.processName;
			if (appName == null)
			{
				appName = ai.taskAffinity;
				if (appName == null)
					appName = ai.toString();
			}
		}
		lm.addString(appName, LogMessage.PART_KEY_CLIENT_NAME);

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				messages.addFirst(lm);
			}
		});
	}

	private synchronized void onChannelClosed()
	{
		// Any thread.
		this.state = state.CLOSED;
	}

	public void connect(final String host, final int port)
	{
		this.host = host;
		this.port = port;
		this.state = State.CONNECTING;

		queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				connectImpl();
			}
		});
	} // connect

	private void connectImpl()
	{
		// Output thread

		try
		{
			socket = new Socket(host, port);
		}
		catch (UnknownHostException e)
		{
			Log.w("underdark", String.format("nslogger connect failed (unknown host) to %s:%d", host, port));
			NSLogger.this.state = State.CLOSED;
			return;
		}
		catch (IOException ex)
		{
			Log.w("underdark", "nslogger connect failed to " + host + ":" + port, ex);
			NSLogger.this.state = State.CLOSED;
			return;
		}

		Log.v("underdark", "nslogger base socket connected");

		if(useSSL)
		{
			SSLSocketFactory sf = SSLCertificateSocketFactory.getInsecure(5000, null);
			SSLSocket sslSocket;
			try
			{
				sslSocket = (SSLSocket) sf.createSocket(socket, host, port, true);
			} catch (IOException ex)
			{
				Log.w("underdark", "nslogger ssl socket failed to " + host + ":" + port, ex);
				NSLogger.this.state = State.CLOSED;
				try
				{
					socket.close();
				}
				catch (IOException ioex)
				{
				}

				return;
			}

			sslSocket.setUseClientMode(true);
			socket = sslSocket;
		}

		try
		{
			outputStream = socket.getOutputStream();
		}
		catch (IOException ex)
		{
			NSLogger.this.state = State.CLOSED;
			try
			{
				socket.close();
			}
			catch (IOException ioex)
			{
			}

			return;
		}

		NSLogger.this.state = State.CONNECTED;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				processMessages();
			}
		});
	} // connectImpl

	/**
	 * Log a message with full information (if provided)
	 * @param filename		the filename (or class name), or null
	 * @param method		the method that emitted the message, or null
	 * @param tag			a tag attributed to the message, or empty string or null
	 * @param level			a level larger than 0
	 * @param message		the message to send
	 */
	public final void log(String filename, int lineNumber, String method, String tag, int level, String message)
	{
		final LogMessage lm = new LogMessage(LogMessage.LOGMSG_TYPE_LOG, nextSequenceNumber.getAndIncrement());
		lm.addInt16(level, LogMessage.PART_KEY_LEVEL);
		if (filename != null)
		{
			lm.addString(filename, LogMessage.PART_KEY_FILENAME);
			if (lineNumber != 0)
				lm.addInt32(lineNumber, LogMessage.PART_KEY_LINENUMBER);
		}
		if (method != null)
			lm.addString(method, LogMessage.PART_KEY_FUNCTIONNAME);
		if (tag != null && !tag.isEmpty())
			lm.addString(tag, LogMessage.PART_KEY_TAG);
		lm.addString(message, LogMessage.PART_KEY_MESSAGE);

		log(lm);
	} // log

	/**
	 * Log a message, attributing a tag and level to the message
	 * @param tag			a tag attributed to the message, or empty string or null
	 * @param level			a level larger than 0
	 * @param message		the message to send
	 */
	public final void log(String tag, int level, String message)
	{
		log(null, 0, null, tag, level, message);
	}

	public final void log(String tag, int level, String message, Throwable throwable)
	{
		if(throwable != null)
			message = message + " Exception: " + throwable.toString();

		log(null, 0, null, tag, level, message);
	}

	/**
	 * Log a message with no tag and default level (0)
	 * @param message		the message to send
	 */
	public final void log(String message)
	{
		log(null, 0, message);
	}

	/**
	 * Log a message that you built yourself
	 * @param lm LogMessage
	 */
	public final void log(LogMessage lm)
	{
		if(flushMessages)
			lm.prepareForFlush();

		addMessage(lm);
	}

	private void addMessage(final LogMessage message)
	{
		if(state == State.CLOSED)
			return;

		queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				messages.add(message);
				if (state != State.CONNECTED)
					message.markFlushed();

				processMessages();
			}
		});

		message.waitFlush();
	} // addMessage()

	private void processMessages()
	{
		// Output thread.
		if(state != State.CONNECTED)
			return;

		try
		{
			LogMessage message;
			while ((message = messages.poll()) != null)
			{
				byte[] data = message.getBytes();
				outputStream.write(data);
				message.markFlushed();
			} // while
		}
		catch (Exception ex)
		{
			state = State.CLOSED;
		}
	} // processMessages()
} // NSLogger

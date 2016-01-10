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

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class NSLoggerAdapter extends org.slf4j.helpers.MarkerIgnoringBase
{
	private static final boolean logCatLogging = true;

	private final int LEVEL_DEBUG = 3;
	private final int LEVEL_INFO = 2;
	private final int LEVEL_WARN = 1;
	private final int LEVEL_ERROR = 0;

	private final String tag;
	public NSLogger logger;

	NSLoggerAdapter(String tag)
	{
		this.tag = tag;
	}

	private String tupleToString(String message, Throwable throwable)
	{
		return message + (throwable == null ? "" : throwable.toString());
	}

	@Override
	public boolean isTraceEnabled()
	{
		return false;
	}

	@Override
	public void trace(String msg)
	{
	}

	@Override
	public void trace(String format, Object arg)
	{
	}

	@Override
	public void trace(String format, Object arg1, Object arg2)
	{
	}

	@Override
	public void trace(String format, Object... arguments)
	{
	}

	@Override
	public void trace(String msg, Throwable t)
	{
	}

	@Override
	public boolean isDebugEnabled()
	{
		return true;
	}

	@Override
	public void debug(String msg)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_DEBUG, msg);

		if(logCatLogging)
			Log.d(tag, msg);
	}

	@Override
	public void debug(String format, Object arg)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg);
		logger.log(null, LEVEL_DEBUG, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.d(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void debug(String format, Object arg1, Object arg2)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		logger.log(null, LEVEL_DEBUG, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.d(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void debug(String format, Object... arguments)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
		logger.log(null, LEVEL_DEBUG, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.d(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void debug(String msg, Throwable t)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_DEBUG, msg, t);

		if(logCatLogging)
			Log.d(tag, msg, t);
	}

	@Override
	public boolean isInfoEnabled()
	{
		return true;
	}

	@Override
	public void info(String msg)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_INFO, msg);

		if(logCatLogging)
			Log.i(tag, msg);
	}

	@Override
	public void info(String format, Object arg)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg);
		logger.log(null, LEVEL_INFO, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.i(tag, tupleToString(ft.getMessage(), ft.getThrowable()));
	}

	@Override
	public void info(String format, Object arg1, Object arg2)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		logger.log(null, LEVEL_INFO, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.i(tag, tupleToString(ft.getMessage(), ft.getThrowable()));
	}

	@Override
	public void info(String format, Object... arguments)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
		logger.log(null, LEVEL_INFO, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.i(tag, tupleToString(ft.getMessage(), ft.getThrowable()));
	}

	@Override
	public void info(String msg, Throwable t)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_INFO, msg, t);

		if(logCatLogging)
			Log.i(tag, tupleToString(msg, t));
	}

	@Override
	public boolean isWarnEnabled()
	{
		return true;
	}

	@Override
	public void warn(String msg)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_WARN, msg);

		if(logCatLogging)
			Log.w(tag, msg);
	}

	@Override
	public void warn(String format, Object arg)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg);
		logger.log(null, LEVEL_WARN, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.w(tag, tupleToString(ft.getMessage(), ft.getThrowable()));
	}

	@Override
	public void warn(String format, Object... arguments)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
		logger.log(null, LEVEL_WARN, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.w(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void warn(String format, Object arg1, Object arg2)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		logger.log(null, LEVEL_WARN, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.w(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void warn(String msg, Throwable t)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_WARN, msg, t);

		if(logCatLogging)
			Log.w(tag, tupleToString(msg, t) );
	}

	@Override
	public boolean isErrorEnabled()
	{
		return true;
	}

	@Override
	public void error(String msg)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_ERROR, msg);

		if(logCatLogging)
			Log.e(tag, msg);
	}

	@Override
	public void error(String format, Object arg)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg);
		logger.log(null, LEVEL_ERROR, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.e(tag, tupleToString(ft.getMessage(), ft.getThrowable()));
	}

	@Override
	public void error(String format, Object arg1, Object arg2)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
		logger.log(null, LEVEL_ERROR, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.e(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void error(String format, Object... arguments)
	{
		if(logger == null)
			return;

		FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
		logger.log(null, LEVEL_ERROR, ft.getMessage(), ft.getThrowable());

		if(logCatLogging)
			Log.e(tag, tupleToString(ft.getMessage(), ft.getThrowable()) );
	}

	@Override
	public void error(String msg, Throwable t)
	{
		if(logger == null)
			return;

		logger.log(null, LEVEL_ERROR, msg, t);

		if(logCatLogging)
			Log.e(tag, tupleToString(msg, t) );
	}
} // NSLoggerAdapter

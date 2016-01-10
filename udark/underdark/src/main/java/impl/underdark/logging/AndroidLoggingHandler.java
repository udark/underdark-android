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

package impl.underdark.logging;

import android.util.Log;
import java.util.logging.*;
import java.util.logging.Logger;

/**
 * Make JUL work on Android.
 */
public class AndroidLoggingHandler extends Handler {

	public static void reset(Handler rootHandler) {
		Logger rootLogger = LogManager.getLogManager().getLogger("");
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			rootLogger.removeHandler(handler);
		}
		LogManager.getLogManager().getLogger("").addHandler(rootHandler);
	}

	@Override
	public void close() {
	}

	@Override
	public void flush() {
	}

	@Override
	public void publish(LogRecord record) {
		if (!super.isLoggable(record))
			return;

		String name = record.getLoggerName();
		int maxLength = 30;
		String tag = name.length() > maxLength ? name.substring(name.length() - maxLength) : name;

		try {
			int level = getAndroidLevel(record.getLevel());
			Log.println(level, tag, record.getMessage());
			if (record.getThrown() != null) {
				Log.println(level, tag, Log.getStackTraceString(record.getThrown()));
			}
		} catch (RuntimeException e) {
			Log.e("AndroidLoggingHandler", "Error logging message.", e);
		}
	}

	static int getAndroidLevel(Level level) {
		int value = level.intValue();
		if (value >= 1000) {
			return Log.ERROR;
		} else if (value >= 900) {
			return Log.WARN;
		} else if (value >= 800) {
			return Log.INFO;
		} else {
			return Log.DEBUG;
		}
	}
} // AndroidLoggingHandler

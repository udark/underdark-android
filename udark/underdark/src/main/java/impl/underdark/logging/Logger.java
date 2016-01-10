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

import org.slf4j.LoggerFactory;

import io.underdark.Underdark;

// http://stackoverflow.com/questions/4561345/how-to-configure-java-util-logging-on-android

public class Logger
{
	public static boolean log = false;
	private static org.slf4j.Logger logger = null;

	public static void setEnabled(boolean enabled)
	{
		if(log == enabled)
			return;

		if(enabled)
			logger = LoggerFactory.getLogger("underdark");
		else
			logger = null;

		log = enabled;
	}

	public static void debug(String format, Object ... args)
	{
		if(!log)
			return;

		logger.debug(format, args);
	}

	public static void info(String format, Object ... args)
	{
		if(!log)
			return;

		logger.info(format, args);
	}

	public static void warn(String format, Object ... args)
	{
		if(!log)
			return;

		logger.warn(format, args);
	}

	public static void error(String format, Object ... args)
	{
		if(!log)
			return;

		logger.error(format, args);
	}
} // Logger

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

import org.slf4j.ILoggerFactory;

public class NSLoggerFactory implements ILoggerFactory
{
	static Object lock = new Object();
	static NSLoggerAdapter adapter;

	private String tag;

	public NSLoggerFactory(String tag)
	{
		this.tag = tag;
	}

	@Override
	public NSLoggerAdapter getLogger(String name)
	{
		synchronized (lock)
		{
			if(adapter == null)
			{
				adapter = new NSLoggerAdapter(tag);
			}
		}

		return adapter;
	}
}

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

package io.underdark.transport;

import java.util.EnumSet;
import java.util.Set;

public enum TransportKind
{
	WIFI(1<<0),
	BLUETOOTH(1<<1);

	private final long value;

	TransportKind(long value)
	{
		this.value = value;
	}

	long getValue()
	{
		return value;
	}

	static EnumSet<TransportKind> getTransportKinds(long value)
	{
		EnumSet transports = EnumSet.noneOf(TransportKind.class);
		for(TransportKind kind : TransportKind.values())
		{
			long kindValue = kind.value;
			if ( (kindValue & value) == kindValue )
			{
				transports.add(kindValue);
			}
			}

		return transports;
	}


	static long getTransportKindValue(Set<TransportKind> kinds)
	{
		long value=0;

		for(TransportKind kind : kinds)
		{
			value |= kind.getValue();
		}

		return value;
	}
} // TransportKind

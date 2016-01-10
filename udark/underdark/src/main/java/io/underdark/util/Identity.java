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

package io.underdark.util;

import java.util.Random;

public class Identity
{
	public static long generateNodeId()
	{
		long nodeId;
		do
		{
			nodeId = new Random().nextLong();
		} while (nodeId == 0);

		if(nodeId < 0)
			nodeId = -nodeId;

		return nodeId;
	}
} // Identity

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

/**
 * Class for the connection objects with discovered remote devices.
 * All methods and properties of this class must be accessed
 * only on the delegate handler of corresponding {@link Transport}.
 */
public interface Link
{
	/**
	 * nodeId of remote device
	 * @return nodeId of remote device
	 */
	long getNodeId();

	int getPriority();

	/**
	 * Disconnects remote device after all pending output frame have been sent to it.
	 */
	void disconnect();

	/**
	 * Sends bytes to remote device as single atomic frame.
	 * @param frameData bytes to send.
	 */
	void sendFrame(byte[] frameData);
} // Link

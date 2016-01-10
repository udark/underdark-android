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

import android.app.Activity;

/**
 * {@link Transport} callback listener.
 * All methods of this interface will be called on {@link Transport}'s handler.
 */
public interface TransportListener
{
	interface ActivityCallback
	{
		void accept(Activity activity);
	}

	/**
	 * Called when transport needs application activity for its function.
	 * @param transport transport that requests activity.
	 * @param callback callback to send activity. Can be called back on any thread.
	 */
	void transportNeedsActivity(Transport transport, ActivityCallback callback);

	/**
	 * Called when transport discovered new device and established connection with it.
	 * @param transport transport instance that discovered the device
	 * @param link connection object to discovered device
	 */
	void transportLinkConnected(Transport transport, Link link);

	/**
	 * Called when connection to device is closed explicitly from either side
	 * or because device is out of range.
	 * @param transport transport instance that lost the device
	 * @param link connection object to disconnected device
	 */
	void transportLinkDisconnected(Transport transport, Link link);

	/**
	 * Called when new data frame is received from remote device.
	 * @param transport transport instance that connected to the device
	 * @param link connection object for the device
	 * @param frameData frame data received from remote device
	 * @see Link#sendFrame(byte[])
	 */
	void transportLinkDidReceiveFrame(Transport transport, Link link, byte[] frameData);
}

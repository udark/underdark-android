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

package io.underdark;

import android.content.Context;
import android.os.Handler;

import java.util.EnumSet;

import impl.underdark.logging.Logger;
import impl.underdark.transport.nsd.NsdTransport;
import io.underdark.transport.Transport;
import io.underdark.transport.TransportKind;
import io.underdark.transport.TransportListener;
import impl.underdark.transport.aggregate.AggTransport;
import impl.underdark.transport.bluetooth.BtTransport;

public class Underdark
{
	private Underdark()
	{
	}

	public static void configureLogging(boolean enabled)
	{
		Logger.setEnabled(enabled);
	}

	/**
	 * Configures aggregate {@link Transport} that supports communication through given protocols.
	 * It must be started via {@link Transport#start()} before use.
	 * @param appId identifier for current application. Must be same across all devices.
	 * @param nodeId globally unique identifier of curent device.
	 * @param listener listener for transport events.
	 * @param handler handler which will be used to dispatch listener callbacks.
	 *                Supply null if you want to receive callbacks on main thread.
	 * @param context application context.
	 * @param transportKinds 0 or more enum values from {@link TransportKind}
	 *                       for communication protocols.
	 * @return transport that uses given listener and communicates via specified protocols.
	 * All methods of returned {@link Transport} object must be called via supplied handler
	 * or its execute() method.
	 */
	public static Transport configureTransport(
			int appId,
			long nodeId,
			TransportListener listener,
			Handler handler,
			Context context,
			EnumSet<TransportKind> transportKinds
			)
	{
		if(appId < 0)
			appId = -appId;

		AggTransport transport = new AggTransport(nodeId, listener, handler, context);

		if(transportKinds.contains(TransportKind.WIFI))
		{
			Transport nsdTransport = new NsdTransport(
					appId, nodeId, transport, transport.queue, context);
			transport.addTransport(nsdTransport);
		}

		if(transportKinds.contains(TransportKind.BLUETOOTH))
		{
			Transport btTransport = new BtTransport(
					appId, nodeId, transport, transport.queue, context);
			transport.addTransport(btTransport);
		}

		return transport;
	}
} // Underdark

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

package impl.underdark.transport.bluetooth.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import impl.underdark.logging.Logger;
import impl.underdark.transport.bluetooth.BtTransport;
import io.underdark.util.dispatch.DispatchQueue;

// Bluetooth multiple connections: http://stackoverflow.com/q/7198487/1449965

public class BtServer
{
	public interface Listener
	{
		void onChannelsListeningChanged();
		void onSocketAccepted(BluetoothSocket socket, String uuid);
	}

	final String serviceName;
	final List<String> uuids;
	private int portsCountMax;
	private BluetoothAdapter adapter;
	Executor pool;
	private Listener listener;
	DispatchQueue queue;

	private boolean running;

	private Map<String, BtPort> ports = new HashMap<>();            // UUID to BtPort
	private Map<BluetoothSocket, BtPort> sockets = new HashMap<>();
	private List<Integer> channelsListening = new ArrayList<>();

	public BtServer(
			int appId,
			List<String> uuids,
			int portsCountMax,
			BluetoothAdapter adapter,
			Executor pool,
			Listener listener,
			DispatchQueue queue)
	{
		// Transport queue.
		this.serviceName = "underdark1-app" + appId;
		this.uuids = new ArrayList<>(uuids);
		this.portsCountMax = Math.min(portsCountMax, uuids.size());

		this.adapter = adapter;
		this.pool = pool;
		this.listener = listener;
		this.queue = queue;
	}

	public boolean isRunning()
	{
		return running;
	}

	public void start()
	{
		// Transport queue.
		if(running)
			return;

		running = true;

		Logger.debug("bt listening started");

		for(int i = 0; i < portsCountMax; ++i)
		{
			final BtPort port = new BtPort(this, uuids.get(i));
			this.ports.put(port.getUuid(), port);
			port.listen();
		}
	} // start()

	public void stop()
	{
		// Transport queue.
		if(!running)
			return;

		running = false;

		Logger.debug("bt listening stopped");

		for(BtPort port : ports.values())
		{
			port.close();
		}
		ports.clear();
	}

	public List<Integer> getChannelsListening()
	{
		// Transport queue.
		return new ArrayList<>(channelsListening);
	}

	//region BtPort Callback
	void onPortListeningError(BtPort port)
	{
		ports.remove(port.getUuid());
	}

	void onPortListeningCanceled(BtPort port)
	{
		if(!running)
			return;

		port.listen();
	}

	void onPortListening(BtPort port)
	{
		if(!running)
			return;

		channelsListening.add(port.getChannel());
		listener.onChannelsListeningChanged();
	}

	void onPortConnected(BtPort port, BluetoothSocket socket)
	{
		channelsListening.remove(Integer.valueOf(port.getChannel()));
		sockets.put(socket, port);
		listener.onSocketAccepted(socket, port.getUuid());
	}

	void onPortDisconnected(BtPort port)
	{
		if(!running)
			return;

		port.listen();
	}

	public void onSocketDisconnected(final BluetoothSocket socket)
	{
		if(socket == null)
			return;

		// Transport queue.
		BtPort port = sockets.remove(socket);
		if(port == null)
			return;

		port.onSocketDisconnect();
	}
	//endregion
} // BtServer

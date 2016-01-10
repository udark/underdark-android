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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import impl.underdark.logging.Logger;
import impl.underdark.transport.bluetooth.BtUtils;

public class BtPort
{
	public final BtServer server;
	private final String uuid;
	private int channel;
	private BluetoothServerSocket serverSocket;
	private BluetoothSocket socket;

	public BtPort(BtServer server, String uuid)
	{
		this.server = server;
		this.uuid = uuid;
	}

	public String getUuid()
	{
		return uuid;
	}

	public int getChannel()
	{
		return channel;
	}

	public void close()
	{
		try
		{
			if (serverSocket != null)
				serverSocket.close();

			serverSocket = null;

			if(socket != null)
				socket.close();

			socket = null;
		}
		catch (IOException ex)
		{
			Logger.warn("bt unlistening to uuid failed", ex);
		}
	}

	public void listen()
	{
		listenUuids();
		//listenChannels();
	}

	private void listenUuids()
	{
		try
		{
			this.serverSocket =
					BluetoothAdapter.getDefaultAdapter()
							.listenUsingInsecureRfcommWithServiceRecord(
									server.serviceName,
									UUID.fromString(uuid));
		} catch (IOException ex)
		{
			Logger.error("bt listening failed for uuid {}", uuid);
			server.onPortListeningError(this);
			return;
		}

		this.channel = server.uuids.indexOf(uuid);

		server.onPortListening(this);

		server.pool.execute(new Runnable()
		{
			@Override
			public void run()
			{
				accept(BtPort.this, serverSocket);
			}
		});
	} // listenUuids()

	private void listenChannels()
	{
		List<Integer> busyChannels = server.getChannelsListening();

		List<Integer> channelsToTry = new ArrayList<>();
		for(int channelNum = 2; channelNum <= BtUtils.channelNumberMax; ++channelNum)
			channelsToTry.add(channelNum);

		channelsToTry.removeAll(busyChannels);

		for(Integer channelNum : channelsToTry)
		{
			try
			{
				this.serverSocket =
						BtHacks.listenUsingInsecureRfcommOn(
								BluetoothAdapter.getDefaultAdapter(),
								channelNum
						);

				//this.serverSocket =	InsecureBluetooth.listenUsingRfcomm(channelNum, false);
			}
			catch (Throwable ex)
			{
				continue;
			}

			this.channel = channelNum;

			break;
		} // for

		if(this.serverSocket == null)
		{
			Logger.error("bt listening failed to find free channel");
			server.onPortListeningError(this);
			return;
		}

		server.onPortListening(this);

		server.pool.execute(new Runnable()
		{
			@Override
			public void run()
			{
				accept(BtPort.this, serverSocket);
			}
		});
	} // listenChannels

	public void onSocketDisconnect()
	{
		this.socket = null;
		server.onPortDisconnected(this);
	}

	private void onAccept(BluetoothServerSocket oldServerSocket, BluetoothSocket clientSocket)
	{
		try
		{
			//Logger.debug("bt server closing serverSocket for uuid {}", port.uuid);
			oldServerSocket.close();
		}
		catch (IOException ex)
		{
			Logger.warn("bt server socket close failed.", ex);
		}

		if(this.serverSocket != oldServerSocket)
		{
			// Port closed.
			try
			{
				clientSocket.close();
			}
			catch (IOException ex)
			{
			}
			return;
		}

		this.serverSocket = null;
		this.socket = clientSocket;

		server.onPortConnected(BtPort.this, socket);
	} // onAccept()

	private static void accept(final BtPort port, final BluetoothServerSocket serverSocket)
	{
		// Accept thread.

		BluetoothSocket socket = null;

		while(socket == null)
		{
			try
			{
				Logger.debug("bt listening channel {} uuid {}", port.getChannel(), port.getUuid());
				socket = serverSocket.accept();
				Logger.debug("bt accept channel {} device '{}' {}",
						port.getChannel(),
						socket.getRemoteDevice() == null ? null : socket.getRemoteDevice().getName(),
						socket.getRemoteDevice() == null ? null : socket.getRemoteDevice().getAddress()
				);
			}
			catch (IOException ex)
			{
				Logger.error("bt accept() failed for uuid {}", port.getUuid(), ex);

				port.server.queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						port.server.onPortListeningCanceled(port);
					}
				});

				return;
			}
		} // while

		final BluetoothSocket finalSocket = socket;

		port.server.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				port.onAccept(serverSocket, finalSocket);
			}
		});
	} // accept()
} // BtPort

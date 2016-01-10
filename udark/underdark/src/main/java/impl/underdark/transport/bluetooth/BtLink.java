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

package impl.underdark.transport.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import impl.underdark.protobuf.Frames;
import impl.underdark.transport.bluetooth.server.BtHacks;
import io.netty.buffer.ByteBuf;
import impl.underdark.logging.Logger;
import io.netty.buffer.Unpooled;
import io.underdark.transport.Link;
import io.underdark.util.dispatch.DispatchQueue;
import io.underdark.Config;

// getBluetoothService() called with no BluetoothManagerCallback:
// https://code.google.com/p/android/issues/detail?id=41415

public class BtLink implements Link
{
	public enum State
	{
		CONNECTING,
		CONNECTED,
		DISCONNECTED
	}

	private BtTransport transport;
	private boolean client;
	public BluetoothSocket socket;
	private BluetoothDevice device;

	private long nodeId;

	private String uuidChannel;
	private int rfcommChannel = -1;

	private List<String> uuids;
	private List<Integer> channels;

	private volatile State state = State.CONNECTING;

	private InputStream inputStream;
	private volatile OutputStream outputStream;

	private DispatchQueue outputThread = new DispatchQueue();
	private Queue<Frames.Frame> outputQueue = new LinkedList<>();

	private boolean shouldCloseWhenOutputIsEmpty = false;

	public static BtLink createClientWithUuids(BtTransport transport, BluetoothDevice device, List<String> uuids)
	{
		BtLink link = new BtLink(transport, device);

		link.uuids = new ArrayList<>(uuids);
		Collections.shuffle(link.uuids);
		return link;
	}

	public static BtLink createClientWithChannels(BtTransport transport, BluetoothDevice device, List<Integer> channels)
	{
		BtLink link = new BtLink(transport, device);
		link.channels = new ArrayList<>(channels);
		Collections.shuffle(link.channels);
		return link;
	}

	public static BtLink createServer(BtTransport transport, BluetoothSocket socket, String uuid)
	{
		BtLink link = new BtLink(transport, socket, uuid);
		return link;
	}

	private BtLink(BtTransport transport, BluetoothDevice device)
	{
		this.client = true;
		this.transport = transport;
		this.device = device;
	}

	private BtLink(BtTransport transport, BluetoothSocket socket, String uuid)
	{
		this.client = false;
		this.transport = transport;
		this.socket = socket;
		this.device = socket.getRemoteDevice();
		this.uuidChannel = uuid;
	}

	public boolean isClient()
	{
		return client;
	}

	public byte[] getAddress()
	{
		return BtUtils.getBytesFromAddress(device.getAddress());
	}

	public BluetoothDevice getDevice()
	{
		return device;
	}

	@Override
	public String toString()
	{
		return "btlink(" + (client ? "c" : "s") + ")"
				+ " uuid " + uuidChannel
				+ " channel " + rfcommChannel
				+ " device '" + (device.getName() == null ? "" : device.getName()) + "'"
				+ " " + device.getAddress();
	}

	//region Link
	@Override
	public long getNodeId()
	{
		return nodeId;
	}

	@Override
	public int getPriority()
	{
		return 20;
	}

	@Override
	public void disconnect()
	{
		outputThread.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				shouldCloseWhenOutputIsEmpty = true;
				writeNextFrame();
			}
		});
	}

	@Override
	public void sendFrame(final byte[] frameData)
	{
		// Listener thread.
		if(state != State.CONNECTED)
			return;

		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.PAYLOAD);

		Frames.PayloadFrame.Builder payload = Frames.PayloadFrame.newBuilder();
		payload.setPayload(ByteString.copyFrom(frameData));
		builder.setPayload(payload);

		final Frames.Frame frame = builder.build();

		sendLinkFrame(frame);
	}

	void sendLinkFrame(final Frames.Frame frame)
	{
		// Listener thread.
		if(state != State.CONNECTED)
			return;

		enqueueFrame(frame);
	}
	//endregion

	private void enqueueFrame(final Frames.Frame frame)
	{
		outputThread.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				outputQueue.add(frame);
				writeNextFrame();
			}
		});
	}

	private void writeNextFrame()
	{
		// Output thread.
		if (state == State.DISCONNECTED)
		{
			outputQueue.clear();
			return;
		}

		Frames.Frame frame = outputQueue.poll();
		if(frame == null)
		{
			if(shouldCloseWhenOutputIsEmpty)
			{
				try
				{
					outputStream.close();
				}
				catch (IOException e)
				{
				}
			}

			//Logger.debug("bt link outputQueue empty");
			return;
		}

		if(!writeFrame(frame))
		{
			outputQueue.clear();
			return;
		}

		outputThread.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				writeNextFrame();
			}
		});
	}

	private boolean writeFrame(Frames.Frame frame)
	{
		// Output thread.
		byte[] buffer = frame.toByteArray();

		ByteBuffer header = ByteBuffer.allocate(4);
		header.order(ByteOrder.BIG_ENDIAN);
		header.putInt(buffer.length);

		try
		{
			outputStream.write(header.array());
			outputStream.write(buffer);
			outputStream.flush();
		}
		catch (IOException ex)
		{
			Logger.warn("bt output write failed.", ex);
			try
			{
				outputStream.close();
				socket.close();
			}
			catch (IOException e)
			{
			}

			return false;
		}

		return true;
	} // writeFrame

	public void connect()
	{
		// Tansport queue.
		transport.pool.execute(new Runnable()
		{
			@Override
			public void run()
			{
				if (client)
					connectClient();
				else
					connectServer();
			}
		});
	}

	private void notifyDisconnect()
	{
		try
		{
			if(socket != null)
				socket.close();
		}
		catch (IOException e)
		{
		}

		final boolean wasConnected = (this.state == State.CONNECTED);
		this.state = State.DISCONNECTED;

		outputThread.close();

		transport.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				transport.linkDisconnected(BtLink.this, wasConnected);
			}
		});
	}

	private void connectClient()
	{
		// Input thread.

		if(channels != null)
		{
			connectClientChannels();
			return;
		}

		if(uuids != null)
		{
			connectClientUuids();
			return;
		}
	}

	private void connectClientChannels()
	{
		for(int channel : channels)
		{
			try
			{
				Logger.debug("bt client connecting to channel {} device '{}' {}",
						channel, device.getName(), device.getAddress());

				BluetoothSocket clientSocket = BtHacks.createInsecureRfcommSocket(device, channel);
				//BluetoothSocket clientSocket = InsecureBluetooth.createRfcommSocket(device, channel, false);

				clientSocket.connect();
				this.socket = clientSocket;
				this.rfcommChannel = channel;
			}
			catch (Throwable ex)
			{
				Logger.warn("bt client connect failed to channel {} device '{}' {}",
						channel, device.getName(), device.getAddress(), ex);
				continue;
			}

			if(!connectStreams())
			{
				try
				{
					this.socket.close();
				}
				catch (IOException ex)
				{
				}

				this.socket = null;
				this.rfcommChannel = -1;

				continue;
			}

			break;
		} // for
	} // connectClientChannels()

	private void connectClientUuids()
	{
		// Input thread.
		for(final String uuid : uuids)
		{
			try
			{
				Logger.debug("bt client connecting to uuid {} device '{}' {}",
						uuid, device.getName(), device.getAddress());
				BluetoothSocket socket =
						device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(uuid));
				socket.connect();
				this.socket = socket;
				this.uuidChannel = uuid;
			}
			catch (Exception ex)
			{
				Logger.warn("bt client connect failed to uuid {} device '{}' {}",
						uuid, device.getName(), device.getAddress(), ex);
				continue;
			}

			Logger.debug("bt client connect() success");

			if(!connectStreams())
			{
				try
				{
					this.socket.close();
				}
				catch (IOException ex)
				{
				}

				this.socket = null;
				this.uuidChannel = null;

				continue;
			}

			break;
		} // for

		if(this.socket == null)
		{
			Logger.warn("bt client unsuitable device '{}' {}", device.getName(), device.getAddress());
			notifyDisconnect();
			return;
		}

		Logger.debug("bt client socket connected to uuid {} device '{}' {}", uuidChannel, device.getName(), device.getAddress());

		inputLoop();
	} // connectClient()

	private void connectServer()
	{
		// Input thread.

		Logger.debug("bt server connecting device '{}' {}",
				device.getName(), device.getAddress());

		if(!connectStreams())
		{
			notifyDisconnect();
			return;
		}

		inputLoop();
	}

	private boolean connectStreams()
	{
		try
		{
			inputStream = socket.getInputStream();
			//Logger.debug("bt retrieved input stream device '{}' {}", device.getName(), device.getAddress());
			outputStream = socket.getOutputStream();
			//Logger.debug("bt retrieved output stream device '{}' {}", device.getName(), device.getAddress());
		}
		catch (IOException ex)
		{
			Logger.warn("bt client streams get failed to uuid {} device '{}' {}",
					uuidChannel, device.getName(), device.getAddress(), ex);
			return false;
		}

		//Logger.debug("bt retrieved streams device '{}' {}", device.getName(), device.getAddress());

		return true;
	}

	private void sendHelloFrame()
	{
		// Input I/O thread.

		//Logger.debug("bt link header send started");

		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.HELLO);

		Frames.HelloFrame.Builder payload = Frames.HelloFrame.newBuilder();
		payload.setNodeId(transport.getNodeId());
		payload.setPeer(transport.getPeerMe());

		builder.setHello(payload);

		final Frames.Frame frame = builder.build();
		enqueueFrame(frame);
	} // sendHelloFrame()

	private void inputLoop()
	{
		// Input I/O thread.

		sendHelloFrame();

		int bufferSize = 4096;
		ByteBuf inputData = Unpooled.buffer(bufferSize);
		inputData.order(ByteOrder.BIG_ENDIAN);

		try
		{
			int len;
			while (true)
			{
				inputData.ensureWritable(bufferSize, true);
				len = inputStream.read(
						inputData.array(),
						inputData.writerIndex(),
						bufferSize);
				if(len <= 0)
					break;

				inputData.writerIndex(inputData.writerIndex() + len);

				if(!formFrames(inputData))
					break;

				inputData.discardReadBytes();
				inputData.capacity(inputData.writerIndex() + bufferSize);
			} // while
		}
		catch (InterruptedIOException ex)
		{
			Logger.warn("bt input timeout: {}", ex);
			try
			{
				inputStream.close();
			}
			catch (IOException ioex)
			{
			}

			notifyDisconnect();
			return;
		}
		catch (Exception ex)
		{
			Logger.warn("bt input read failed.", ex);
			try
			{
				inputStream.close();
			}
			catch (IOException ioex)
			{
			}

			notifyDisconnect();
			return;
		}

		Logger.debug("bt input read end.");
		notifyDisconnect();

	} // inputLoop()

	private boolean formFrames(ByteBuf inputData)
	{
		final int headerSize = 4;

		while(true)
		{
			if(inputData.readableBytes() < headerSize)
				break;

			inputData.markReaderIndex();
			int	frameSize = inputData.readInt();

			if(frameSize > Config.frameSizeMax)
			{
				Logger.warn("bt frame size limit reached.");
				return false;
			}

			if( inputData.readableBytes() < frameSize )
			{
				inputData.resetReaderIndex();
				break;
			}

			final Frames.Frame frame;

			{
				final byte[] frameBody = new byte[frameSize];
				inputData.readBytes(frameBody, 0, frameSize);

				try
				{
					frame = Frames.Frame.parseFrom(frameBody);
				}
				catch (Exception ex)
				{
					continue;
				}
			}

			if(this.state == State.CONNECTING)
			{
				if(frame.getKind() != Frames.Frame.Kind.HELLO)
					continue;

				this.nodeId = frame.getHello().getNodeId();
				this.state = State.CONNECTED;

				Logger.debug("bt connected {}", BtLink.this.toString());

				transport.queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						transport.linkConnected(BtLink.this, frame.getHello().getPeer());
					}
				});

				continue;
			}

			if(frame.getKind() == Frames.Frame.Kind.PAYLOAD)
			{
				if(!frame.hasPayload() || !frame.getPayload().hasPayload())
					continue;

				final byte[] frameData = frame.getPayload().getPayload().toByteArray();
				if(frameData.length == 0)
					continue;

				transport.queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						transport.linkDidReceiveFrame(BtLink.this, frameData);
					}
				});

				continue;
			}

			transport.queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					transport.linkDidReceiveLinkFrame(BtLink.this, frame);
				}
			});
		} // while

		return true;
	}
} // BtLink

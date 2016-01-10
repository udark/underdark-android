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

package impl.underdark.transport.nsd;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import impl.underdark.logging.Logger;
import impl.underdark.protobuf.Frames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.underdark.Config;
import io.underdark.transport.Link;
import io.underdark.util.dispatch.DispatchQueue;
import io.underdark.util.dispatch.SerialExecutorService;

public class NsdLink implements Link
{
	public enum State
	{
		CONNECTING,
		CONNECTED,
		DISCONNECTED
	}

	private boolean client;
	private NsdServer server;
	private Socket socket;
	private long nodeId;

	private InetAddress host;
	private int port;

	// Changed only from input thread.
	private State state = State.CONNECTING;

	private InputStream inputStream;
	private volatile OutputStream outputStream;

	private ScheduledThreadPoolExecutor pool;
	private ExecutorService outputExecutor;
	private Queue<Frames.Frame> outputQueue = new LinkedList<>();

	private boolean shouldCloseWhenOutputIsEmpty = false;

	NsdLink(NsdServer server, Socket socket)
	{
		// Any thread
		super();
		this.client = false;
		this.server = server;
		this.socket = socket;
		this.host = socket.getInetAddress();
		this.port = socket.getPort();

		configureOutput();
	}

	NsdLink(NsdServer server, long nodeId, InetAddress host, int port)
	{
		// Any thread
		super();
		this.client = true;
		this.server = server;
		this.nodeId = nodeId;
		this.host = host;
		this.port = port;

		configureOutput();
	}

	private void configureOutput()
	{
		pool = new ScheduledThreadPoolExecutor(1, new ThreadFactory()
		{
			@Override
			public Thread newThread(Runnable r)
			{
				Thread thread = new Thread(r);
				thread.setName("NsdLink " + this.hashCode() + " Output");
				thread.setDaemon(true);
				return thread;
			}
		});

		outputExecutor = new SerialExecutorService(pool);
	}

	@Override
	public String toString() {
		return (client ? "c" : "s")
				+ "link"
				+ " nodeId " + nodeId
				+ " " + host.toString()
				+ ":" + port;
	}

	void connect()
	{
		// Queue
		Thread inputThread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				connectImpl();
			}
		});
		inputThread.setName("NsdLink " + this.hashCode() + " Input");
		inputThread.setDaemon(true);
		inputThread.start();
	} // connect

	//region Link
	@Override
	public long getNodeId()
	{
		return nodeId;
	}

	@Override
	public int getPriority()
	{
		return 10;
	}

	@Override
	public void disconnect()
	{
		outputExecutor.execute(new Runnable()
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

	private void notifyDisconnect()
	{
		// Input thread
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

		//outputExecutor.close();
		outputExecutor.shutdown();

		pool.shutdown();

		server.queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				server.linkDisconnected(NsdLink.this, wasConnected);
			}
		});
	}

	private void enqueueFrame(final Frames.Frame frame)
	{
		// Any thread.
		outputExecutor.execute(new Runnable()
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

		byte[] frameBytes;

		{
			Frames.Frame frame = outputQueue.poll();
			if (frame == null)
			{
				if (shouldCloseWhenOutputIsEmpty)
				{
					try
					{
						outputStream.close();
						socket.close();
					}
					catch (IOException e)
					{
					}
				}

				//Logger.debug("nsd link outputQueue empty");
				return;
			}

			frameBytes = frame.toByteArray();
		}

		if(!writeFrameBytes(frameBytes))
		{
			outputQueue.clear();
			return;
		}

		outputExecutor.execute(new Runnable()
		{
			@Override
			public void run()
			{
				writeNextFrame();
			}
		});
	}

	private boolean writeFrameBytes(byte[] frameBytes)
	{
		// Output thread.
		ByteBuffer header = ByteBuffer.allocate(4);
		header.order(ByteOrder.BIG_ENDIAN);
		header.putInt(frameBytes.length);

		try
		{
			outputStream.write(header.array());
			outputStream.write(frameBytes);
			//Logger.info("write " + (header.array().length + frameBytes.length));
			outputStream.flush();
		}
		catch (IOException ex)
		{
			Logger.warn("nsd output write failed.", ex);
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

	private void connectImpl()
	{
		// Input thread.
		if (client)
		{
			try
			{
				this.socket = new Socket(host, port);
			}
			catch (IOException ex)
			{
				Logger.warn("nsd link connect failed to {}:{} {}", host, port, ex);
				notifyDisconnect();
				return;
			}
		}

		try
		{
			socket.setTcpNoDelay(true);
			socket.setKeepAlive(true);
			socket.setSoTimeout(Config.bnjTimeoutInterval);
		}
		catch (SocketException ex)
		{
		}

		if (!connectStreams())
		{
			notifyDisconnect();
			return;
		}

		sendHelloFrame();

		pool.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				if(state != State.CONNECTED)
					return;

				sendHeartbeat();
			}
		}, 0, Config.bnjHeartbeatInterval, TimeUnit.MILLISECONDS);

		inputLoop();
	} // connectImpl

	private boolean connectStreams()
	{
		// Input thread.
		try
		{
			inputStream = socket.getInputStream();
			outputStream = socket.getOutputStream();
		}
		catch (IOException ex)
		{
			Logger.error("nsd link streams get failed {}", ex);
			return false;
		}

		//Logger.debug("bt retrieved streams device '{}' {}", device.getName(), device.getAddress());

		return true;
	}

	private void sendHelloFrame()
	{
		// Input thread.
		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.HELLO);

		Frames.HelloFrame.Builder payload = Frames.HelloFrame.newBuilder();
		payload.setNodeId(server.getNodeId());
		payload.setPeer(
				Frames.Peer.newBuilder()
						.setAddress(ByteString.copyFrom(new byte[0]))
						.setLegacy(false)
						.addAllPorts(new ArrayList<Integer>())
		);

		builder.setHello(payload);

		final Frames.Frame frame = builder.build();
		enqueueFrame(frame);
	} // sendHelloFrame

	private void sendHeartbeat()
	{
		// Any thread
		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.HEARTBEAT);

		Frames.HeartbeatFrame.Builder payload = Frames.HeartbeatFrame.newBuilder();

		builder.setHeartbeat(payload);

		final Frames.Frame frame = builder.build();
		enqueueFrame(frame);
	} // sendHeartbeat

	private void inputLoop()
	{
		// Input thread.
		final int bufferSize = 4096;
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
			Logger.warn("nsd input timeout: {}", ex);
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
			Logger.warn("nsd input read failed: {}", ex);
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

		Logger.debug("nsd input read end");
		notifyDisconnect();
	} // inputLoop

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
				Logger.warn("nsd frame size limit reached.");
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

				Logger.debug("nsd connected {}", NsdLink.this.toString());

				server.queue.execute(new Runnable()
				{
					@Override
					public void run()
					{
						server.linkConnected(NsdLink.this);
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

				server.queue.execute(new Runnable()
				{
					@Override
					public void run()
					{
						server.linkDidReceiveFrame(NsdLink.this, frameData);
					}
				});

				continue;
			}

			if(frame.getKind() == Frames.Frame.Kind.HEARTBEAT)
			{
				continue;
			}

			/*server.queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					server.linkDidReceiveLinkFrame(NsdLink.this, frame);
				}
			});*/
		} // while

		return true;
	} // formFrames
} // NsdLink

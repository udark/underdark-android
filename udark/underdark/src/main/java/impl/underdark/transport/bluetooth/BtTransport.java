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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import impl.underdark.protobuf.Frames;
import impl.underdark.transport.bluetooth.discovery.DiscoveryManager;
import impl.underdark.logging.Logger;
import impl.underdark.transport.bluetooth.pairing.BtPairer;
import impl.underdark.transport.bluetooth.server.BtServer;
import impl.underdark.transport.bluetooth.switcher.BtSwitcher;
import impl.underdark.transport.bluetooth.switcher.BtSwitcherDumb;
import impl.underdark.transport.bluetooth.switcher.BtSwitcherSmart;
import io.underdark.transport.Transport;
import io.underdark.transport.TransportListener;
import io.underdark.util.dispatch.DispatchQueue;

public class BtTransport implements Transport, BtServer.Listener, BtPairer.Listener, BtSwitcherSmart.Listener
{
	// Uppercase.
	private static final List<String> uuidTemplates = Arrays.asList(new String[] {
			"1B9839E4-040B-48B2-AE5F-61B6xxxxxxxx",
			"6FB34FD8-579F-4915-88FF-71B2xxxxxxxx",
			"8CC0C5A1-1E22-4C95-89D7-3639xxxxxxxx",
			"71932FDF-9694-46BE-8943-E16Cxxxxxxxx",
			"9F29B4E8-DAF0-48DD-833B-37E8xxxxxxxx",
			"B82B32ED-AF2C-4A99-B8C3-FBE4xxxxxxxx",
			"0707A437-7DFE-4FA0-92F5-46D5xxxxxxxx",
	});

	public final List<String> uuids = new ArrayList<>();

	private static final int REQUEST_ENABLE_BT = 35434;
	private static final int REQUEST_DISCOVERABLE = 35435;

	private final long discoverableDurationMaxSeconds = 300;
	private static long discoverableRequestTime = 0; // Milliseconds.

	private boolean running;
	private final int appId;
	private long nodeId;
	private TransportListener listener;
	public DispatchQueue queue;
	public DispatchQueue listenerQueue;
	Executor pool;

	private Context context;
	private BluetoothAdapter adapter;
	private BroadcastReceiver receiver;

	private BtServer server;
	private List<BtLink> links = new ArrayList<>();

	private DiscoveryManager manager;
	private BtPairer pairer;
	private BtSwitcher switcher;

	public BtTransport(
			int appId,
			long nodeId,
			TransportListener listener,
			DispatchQueue listenerQueue,
			Context context)
	{
		this.queue = DispatchQueue.main;

		this.appId = appId;
		this.nodeId = nodeId;
		this.listener = listener;
		this.listenerQueue = listenerQueue;
		this.context = context;

		this.pairer = new BtPairer(this, context);
		this.switcher = new BtSwitcherDumb(this);

		generateUuids();

		pool = Executors.newCachedThreadPool(
				new ThreadFactory()
				{
					@Override
					public Thread newThread(Runnable r)
					{
						Thread thread = new Thread(r);
						thread.setDaemon(true);
						return thread;
					}
				});

		this.adapter = BluetoothAdapter.getDefaultAdapter();
		if(this.adapter == null)
			return;

		Logger.info("bt adapter address " + BtUtils.getLocalAddress(context));

		int portsCountMax = uuids.size() / 2;
		this.server = new BtServer(appId, uuids, portsCountMax, adapter, pool, this, queue);

		manager = new DiscoveryManager(this, adapter, queue, context, uuids);
	} // BtTransport()

	public int getAppId()
	{
		return appId;
	}

	public long getNodeId()
	{
		return nodeId;
	}

	private void generateUuids()
	{
		for (final String uuidTemplate : uuidTemplates)
		{
			String appIdHex = Integer.toHexString(appId).toUpperCase();
			while(appIdHex.length() != "xxxxxxxx".length())
				appIdHex = "0" + appIdHex;

			String uuid = uuidTemplate.replace("xxxxxxxx", appIdHex);
			uuids.add(uuid);
		}
	}

	//region Transport
	@Override
	public void onMainActivityResumed()
	{
		// Any thread.
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				if(!running)
					return;

				manager.onMainActivityResumed();
			}
		});
	}

	@Override
	public void onMainActivityPaused()
	{
		// Any thread.
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				if(!running)
					return;

				manager.onMainActivityPaused();
			}
		});
	}

	@Override
	public void start()
	{
		// Listener queue.
		this.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				startInternal();
			}
		});
	}

	@Override
	public void stop()
	{
		// Listener queue.
		this.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				stopInternal();
			}
		});
	}

	public void startInternal()
	{
		// Transport queue.
		if(this.running)
			return;

		//this.pairer.start();

		if(adapter == null)
		{
			// Bluetooth not supported.
			Logger.error("Bluetooth is not supported on this device.");
			return;
		}

		if(Build.VERSION.SDK_INT < 18)
		{
			Logger.error("Bluetooth LE is not supported on this device.");
			return;
		}

		this.running = true;

		this.receiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				BtTransport.this.onReceive(context, intent);
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		context.registerReceiver(this.receiver, filter);

		checkScanModeAtStart();
	} // start()

	public void stopInternal()
	{
		// Transport queue.
		if(!this.running)
			return;

		this.running = false;

		manager.stop();

		context.unregisterReceiver(this.receiver);
		this.receiver = null;

		this.server.stop();

		this.pairer.stop();
	} // stop()
	//endregion

	private boolean isDeviceConnected(BluetoothDevice device)
	{
		for (BtLink link : links)
		{
			if(link.getDevice() == null)
				continue;

			if(device.getAddress().equalsIgnoreCase(link.getDevice().getAddress()))
			{
				return true;
			}
		}

		return false;
	}

	//region BtPairer.Listener
	@Override
	public boolean shouldPairDevice(BluetoothDevice device)
	{
		for(BtLink link : links)
		{
			if(link.getDevice().getAddress().equalsIgnoreCase(device.getAddress()))
				return true;
		}

		return true;
	}

	@Override
	public void onDevicePaired(BluetoothDevice device)
	{
	}
	//endregion

	//region BroadcastReceiver
	private void onReceive(Context context, Intent intent)
	{
		// Transport queue.

		if(intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
		{
			onReceive_ACTION_STATE_CHANGED(intent);
			return;
		} // ACTION_STATE_CHANGED

		if(intent.getAction().equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
		{
			onReceive_ACTION_SCAN_MODE_CHANGED(intent);
			return;
		} // ACTION_SCAN_MODE_CHANGED

	} // onReceive

	private void onReceive_ACTION_STATE_CHANGED(Intent intent)
	{
		int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
		if(state == -1)
			return;

		if(state == BluetoothAdapter.STATE_ON)
		{
			Logger.debug("bt bluetooth STATE_ON");

			switcher.setLegacy(!manager.isPeripheralSupported());
			if(!manager.isPeripheralSupported())
				Logger.debug("BLE Peripheral mode is not supported on this device.");

			manager.start();
			startListening();
		}

		if(state == BluetoothAdapter.STATE_OFF)
		{
			Logger.debug("bt bluetooth STATE_OFF");
			manager.stop();
			stopListening();
			discoverableRequestTime = 0;
		}
	} // ACTION_STATE_CHANGED

	private void onReceive_ACTION_SCAN_MODE_CHANGED(Intent intent)
	{
		int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
		if(scanMode == -1)
			return;

		if(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
		{
			Logger.debug("bt SCAN_MODE_CONNECTABLE_DISCOVERABLE");
			switcher.setMyAddress(BtUtils.getBytesFromAddress(BtUtils.getLocalAddress(context)));
		}

		if(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE)
		{
			Logger.debug("bt SCAN_MODE_CONNECTABLE");
			switcher.setMyAddress(BtUtils.getBytesFromAddress(BtUtils.getLocalAddress(context)));
		}

		if(scanMode == BluetoothAdapter.SCAN_MODE_NONE)
		{
			Logger.debug("bt SCAN_MODE_NONE");
			switcher.setMyAddress(new byte[BtUtils.btAddressLen]);
		}
	} // ACTION_SCAN_MODE_CHANGED
	//endregion

	private void checkScanModeAtStart()
	{
		if(!running)
			return;

		// Transport queue.
		if(!adapter.isEnabled())
		{
			requestBluetoothEnabled();
			return;
		}

		if(adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
				|| adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE)
		{
			switcher.setMyAddress(BtUtils.getBytesFromAddress(BtUtils.getLocalAddress(context)));
			switcher.setLegacy(!manager.isPeripheralSupported());
			if(!manager.isPeripheralSupported())
				Logger.debug("BLE Peripheral mode is not supported on this device.");

			startListening();
			manager.start();

			return;
		}

		if(adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_NONE)
		{
			requestBluetoothEnabled();
			return;
		}
	} // checkScanModeAtStart()

	public byte[] getAddress()
	{
		return BtUtils.getBytesFromAddress(BtUtils.getLocalAddress(context));
	}

	public List<Integer> getChannelsListening()
	{
		return server.getChannelsListening();
	}

	public Frames.Peer getPeerMe()
	{
		return switcher.getPeerMe();
	}

	private void requestBluetoothEnabled()
	{
		if(!running)
			return;

		long now = new Date().getTime();
		if (now - discoverableRequestTime < discoverableDurationMaxSeconds * 1000)
			return;

		discoverableRequestTime = now;

		final TransportListener.ActivityCallback callback =
				new TransportListener.ActivityCallback(){
					public void accept(final Activity activity)
					{
						// Any thread.
						queue.dispatch(new Runnable()
						{
							@Override
							public void run()
							{
								// Main thread.
								Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

								activity.startActivityForResult(intent, REQUEST_ENABLE_BT);
							}
						});
					}
				};

		listenerQueue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportNeedsActivity(BtTransport.this, callback);
			}
		});
	} // requestDiscoverable()

	private void requestDiscoverable()
	{
		if(!running)
			return;

		long now = new Date().getTime();
		if (now - discoverableRequestTime < discoverableDurationMaxSeconds * 1000)
			return;

		discoverableRequestTime = now;

		final TransportListener.ActivityCallback callback =
				new TransportListener.ActivityCallback(){
					public void accept(final Activity activity)
					{
						// Any thread.
						queue.dispatch(new Runnable()
						{
							@Override
							public void run()
							{
								// Main thread.
								Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
								intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDurationMaxSeconds);

								activity.startActivityForResult(intent, REQUEST_DISCOVERABLE);
							}
						});
					}
				};

		listenerQueue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportNeedsActivity(BtTransport.this, callback);
			}
		});
	} // requestDiscoverable()

	//region BtServer.Listener
	private void startListening()
	{
		// Transport queue.
		if(!running)
			return;

		this.server.start();
	}

	private void stopListening()
	{
		// Transport queue.
		this.server.stop();
	}

	@Override
	public void onChannelsListeningChanged()
	{
		manager.onChannelsListeningChanged();
		switcher.onPortsChanged(this.getChannelsListening());
	}

	@Override
	public void onSocketAccepted(BluetoothSocket socket, String uuid)
	{
		// Transport queue.
		manager.onChannelsListeningChanged();
		switcher.onPortsChanged(this.getChannelsListening());

		BtLink link = BtLink.createServer(this, socket, uuid);
		linkConnecting(link);
	}
	//endregion

	//region Discovery
	public void onDeviceUuidsDiscovered(BluetoothDevice device, List<String> deviceUuids)
	{
		if(isDeviceConnected(device))
			return;

		if(deviceUuids.isEmpty())
			return;

		connectToDevice(device, deviceUuids);
	}

	public void onDeviceChannelsDiscovered(BluetoothDevice device, List<Integer> channels)
	{
		if(isDeviceConnected(device))
			return;

		switcher.onAddressDiscovered(BtUtils.getBytesFromAddress(device.getAddress()), channels);

		//BtLink link = BtLink.createClientWithChannels(this, device, channels);
		//linkConnecting(link);
	}

	private void connectToDevice(BluetoothDevice device, List<String> deviceUuids)
	{
		BtLink link = BtLink.createClientWithUuids(this, device, deviceUuids);
		linkConnecting(link);
	}
	//endregion

	//region BtSwitcher.Listener
	@Override
	public void onMustConnectAddress(byte[] address, List<Integer> channels)
	{
		if(!running)
			return;

		BluetoothDevice device = this.adapter.getRemoteDevice(address);

		if(isDeviceConnected(device))
			return;

		List<String> deviceUuids = new ArrayList<>();
		for(int channel : channels)
		{
			if(channel < 0 || channel >= uuids.size())
				continue;

			deviceUuids.add(uuids.get(channel));
		}

		connectToDevice(device, deviceUuids);
	}

	@Override
	public void onMustDisconnectAddress(byte[] address)
	{
		if(!running)
			return;

		for(BtLink link : links)
		{
			if(Arrays.equals(link.getAddress(), address))
			{
				link.disconnect();
			}
		}
	}

	@Override
	public void onMustSendFrame(byte[] address, Frames.Frame frame)
	{
		if(!running)
			return;

		Logger.debug("bt onMustSendFrame {} to {}",
				frame.getKind().toString(), BtUtils.getAddressStringFromBytes(address));

		for(BtLink link : links)
		{
			if(Arrays.equals(link.getAddress(), address))
			{
				link.sendLinkFrame(frame);
				break;
			}
		}
	}
	//endregion

	//region Links
	public void linkConnecting(BtLink link)
	{
		// Transport queue.
		links.add(link);
		link.connect();
	}

	void linkConnected(final BtLink link, Frames.Peer peer)
	{
		// Transport queue.

		switcher.onLinkConnected(peer);

		listenerQueue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportLinkConnected(BtTransport.this, link);
			}
		});
	}

	void linkDisconnected(final BtLink link, boolean wasConnected)
	{
		// Transport queue.
		links.remove(link);

		if(wasConnected)
		{
			switcher.onLinkDisconnected(link.getAddress());

			listenerQueue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.transportLinkDisconnected(BtTransport.this, link);
				}
			});
		}

		server.onSocketDisconnected(link.socket);

		if(!link.isClient())
		{
			manager.onChannelsListeningChanged();
			switcher.onPortsChanged(this.getChannelsListening());
		}
	}

	void linkDidReceiveFrame(final BtLink link, final byte[] frameData)
	{
		// Transport queue.
		if(!running)
			return;

		listenerQueue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportLinkDidReceiveFrame(BtTransport.this, link, frameData);
			}
		});
	}

	void linkDidReceiveLinkFrame(BtLink link, Frames.Frame frame)
	{
		if(frame.getKind() == Frames.Frame.Kind.PORTS)
		{
			Logger.debug("bt frame PORTS");
			switcher.onPortsFrame(link.getAddress(), frame.getPorts());
			return;
		}

		if(frame.getKind() == Frames.Frame.Kind.CONNECTED)
		{
			Logger.debug("bt frame CONNECTED");
			switcher.onConnectedFrame(link.getAddress(), frame.getConnected());
			return;
		}

		if(frame.getKind() == Frames.Frame.Kind.DISCONNECTED)
		{
			Logger.debug("bt frame DISCONNECTED");
			switcher.onDisconnectedFrame(link.getAddress(), frame.getDisconnected());
			return;
		}
	}
	//endregion
} // BtTransport

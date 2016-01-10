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

package impl.underdark.transport.bluetooth.discovery.classic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

import impl.underdark.transport.bluetooth.discovery.Scanner;
import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

public class BtScanner implements Scanner
{
	private BluetoothAdapter adapter;
	private Context context;
	private Scanner.Listener listener;
	private DispatchQueue queue;
	private  List<String> uuids = new ArrayList<>();

	private BroadcastReceiver receiver;

	private boolean running;
	private long duration;
	private boolean discovered;

	private List<BluetoothDevice> devicesDiscovered = new ArrayList<>();

	private Runnable stopCommand;

	public BtScanner(
			BluetoothAdapter adapter,
			Context context,
			Scanner.Listener listener,
			DispatchQueue queue,
	        List<String> uuids
	)
	{
		this.adapter = adapter;
		this.context = context;
		this.listener = listener;
		this.queue = queue;
		this.uuids = new ArrayList<>(uuids);
	}

	//region Scanner
	@Override
	public void startScan(long durationMs)
	{
		if(running)
			return;

		this.running = true;
		this.duration = durationMs;

		this.receiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				BtScanner.this.onReceive(context, intent);
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_UUID);
		context.registerReceiver(this.receiver, filter);

		if(!checkScanMode())
		{
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					stopScan();
				}
			});
			return;
		}

		Logger.debug("bt scan started");

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStarted(BtScanner.this);
				startDiscovery();
			}
		});
	} // startScan()

	@Override
	public void stopScan()
	{
		if(!running)
			return;

		queue.cancel(stopCommand);
		stopCommand = null;

		running = false;
		discovered = false;

		context.unregisterReceiver(receiver);
		this.receiver = null;

		if(adapter.isDiscovering())
			adapter.cancelDiscovery();

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				Logger.debug("bt scan stopped");
				listener.onScanStopped(BtScanner.this, false);
			}
		});
	} // stopScan()
	//endregion

	private void startDiscovery()
	{
		if(!running)
			return;

		if(discovered)
			return;

		if(adapter.isDiscovering())
			return;

		discovered = true;

		devicesDiscovered.clear();

		if(!adapter.startDiscovery())
		{
			stopScan();
			return;
		}

		Logger.debug("bt scan discovering");

		stopCommand =
		queue.dispatchAfter(duration / 2, new Runnable()
		{
			@Override
			public void run()
			{
				finishDiscovery();

				stopCommand =
				queue.dispatchAfter(duration / 2, new Runnable()
				{
					@Override
					public void run()
					{
						stopScan();
					}
				});
			}
		});
	} // startDiscovery()

	private void finishDiscovery()
	{
		if(!adapter.isDiscovering())
			return;

		adapter.cancelDiscovery();
		Logger.debug("bt scan fetchUuidsWithSdp()");

		for (BluetoothDevice device : devicesDiscovered)
		{
			if(!device.fetchUuidsWithSdp())
			{
				Logger.warn("bt scan failed fetchUuidsWithSdp() for device '{}' {}",
						device.getName(), device.getAddress());
				continue;
			}
		}
	} // finishDiscovery()

	private boolean checkScanMode()
	{
		if(!running)
			return false;

		// Transport queue.
		if(!adapter.isEnabled())
			return false;

		if(adapter.getScanMode() == BluetoothAdapter.SCAN_MODE_NONE)
			return false;

		return true;
	} // checkScanMode()

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

		if(intent.getAction().equals(BluetoothDevice.ACTION_FOUND))
		{
			onReceive_ACTION_FOUND(intent);
			return;
		}

		if(intent.getAction().equals(BluetoothDevice.ACTION_UUID))
		{
			onReceive_ACTION_UUID(intent);
			return;
		}

	} // onReceive

	private void onReceive_ACTION_STATE_CHANGED(Intent intent)
	{
		int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
		if(state == -1)
			return;

		if(state == BluetoothAdapter.STATE_ON)
		{
			//Logger.debug("Bluetooth turned on");
		}

		if(state == BluetoothAdapter.STATE_OFF)
		{
			//Logger.debug("Bluetooth turned off");
		}
	} // ACTION_STATE_CHANGED

	private void onReceive_ACTION_SCAN_MODE_CHANGED(Intent intent)
	{
		int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
		if(scanMode == -1)
			return;

		if(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
		{
			startDiscovery();
		}

		if(scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE)
		{
			startDiscovery();
		}

		if(scanMode == BluetoothAdapter.SCAN_MODE_NONE)
		{
			finishDiscovery();
		}
	} // ACTION_SCAN_MODE_CHANGED

	private void onReceive_ACTION_FOUND(Intent intent)
	{
		BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		if(device == null)
			return;

		//BluetoothClass btclass = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS);

		//Logger.debug("bt discovered device '{}' {}", device.getName(), device.getAddress());

		// IMPORTANT!
		// Uuids cannot be fetched during discovery.

		devicesDiscovered.add(device);
	} // ACTION_FOUND

	private void onReceive_ACTION_UUID(Intent intent)
	{
		final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		if(device == null)
			return;

		Parcelable[] extraUuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
		if(extraUuids == null)
		{
			//Logger.error("bt uuids null for device '{}' {}", device.getName(), device.getAddress());
			return;
		}

		// IMPORTANT
		// This intent can be called MULTIPLE times with SAME uuids.

		final List<String> deviceUuids = new ArrayList<>();

		for(Parcelable parcelUuid : extraUuids)
		{
			String uuid = parcelUuid.toString().toUpperCase();

			if(!this.uuids.contains(uuid))
				continue;

			Logger.debug("bt found uuid '{}' for device '{}' {}",
					uuid,
					device.getName(),
					device.getAddress()
			);

			deviceUuids.add(uuid);
		}

		if(deviceUuids.isEmpty())
			return;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onDeviceUuidsDiscovered(BtScanner.this, device, deviceUuids);
			}
		});
	} // ACTION_UUID

	//endregion
} // BtScanner

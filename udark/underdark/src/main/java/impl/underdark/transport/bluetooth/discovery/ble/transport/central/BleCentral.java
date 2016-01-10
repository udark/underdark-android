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

package impl.underdark.transport.bluetooth.discovery.ble.transport.central;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import impl.underdark.transport.bluetooth.BtTransport;
import impl.underdark.transport.bluetooth.discovery.Scanner;
import impl.underdark.transport.bluetooth.discovery.ble.detector.BleDetector;
import impl.underdark.transport.bluetooth.discovery.ble.detector.BleDetectorFactory;
import impl.underdark.logging.Logger;
import io.underdark.Config;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(18)
public class BleCentral implements BleDetector.Listener, Scanner
{
	private boolean running;

	private BluetoothAdapter adapter;
	Context context;
	private Scanner.Listener listener;
	DispatchQueue queue;

	// Lower-case device address to link.
	private Map<String, BleCentralLink> links = new HashMap<>();

	// Lower-case addresses of unsuitable devices to date of their detection.
	private Map<String, Date> unsuitables = new HashMap<>();

	private BleDetector detector;

	private Runnable stopCommand;

	public BleCentral(
			Context context,
			Scanner.Listener listener,
			DispatchQueue queue
			)
	{
		this.context = context;
		this.listener = listener;
		this.queue = queue;
	}

	//region Scanner
	@Override
	public void startScan(long durationMs)
	{
		if(Build.VERSION.SDK_INT < 18)
		{
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onScanStopped(BleCentral.this, true);
				}
			});
			return;
		}

		if(running)
			return;

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Logger.error("Bluetooth LE is not supported on this device.");
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onScanStopped(BleCentral.this, true);
				}
			});
			return;
		}

		final BluetoothManager bluetoothManager =
				(BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		this.adapter = bluetoothManager.getAdapter();

		if(this.adapter == null)
		{
			Logger.error("Bluetooth is not supported on this device.");
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onScanStopped(BleCentral.this, true);
				}
			});
			return;
		}

		running = true;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStarted(BleCentral.this);
			}
		});

		this.detector = BleDetectorFactory.create(adapter, this, queue);
		this.detector.startScan();

		stopCommand =
		queue.dispatchAfter(durationMs, new Runnable()
		{
			@Override
			public void run()
			{
				stopScan();
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

		detector.stopScan();
	} // stopScan()
	//endregion

	//region BleDetector.Listener
	@Override
	public void onScanStarted()
	{
		Logger.debug("ble scan started");
	}

	@Override
	public void onScanStopped(final boolean error)
	{
		Logger.debug("ble scan stopped");
		running = false;
		detector = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStopped(BleCentral.this, error);
			}
		});
	}

	@Override
	public void onDeviceDetected(BluetoothDevice device, byte[] scanRecord)
	{
		if(!running)
			return;

		Date unsuitable = unsuitables.get(device.getAddress().toUpperCase());

		if(unsuitable != null
				&& new Date().getTime() - unsuitable.getTime() <= Config.bleUnsuitableCooldown )
		{
			//Logger.debug("ble central found unsuitable device {} '{}'", device.getAddress(), device.getName());
			return;
		}

		unsuitables.remove(device.getAddress().toLowerCase());

		if( null != links.get(device.getAddress().toUpperCase()) )
		{
			//Logger.debug("ble central already connected to {} '{}'", device.getAddress(), device.getName());
			return;
		}

		Logger.debug("ble central found device {} '{}'", device.getAddress(), device.getName());

		BleCentralLink link = new BleCentralLink(this, device);
		link.connect();
	}
	//endregion

	void onAddressDiscovered(byte[] address)
	{
		if(!running)
			return;

		//Logger.debug("bt address discovered {}", BtUtils.getAddressStringFromBytes(address));

		BluetoothDevice device = adapter.getRemoteDevice(address);
		if(device == null)
		{
			Logger.error("bt device is null for address {}", address);
			return;
		}

		listener.onDeviceUuidsDiscovered(this, device, new ArrayList<String>());
	}

	void linkUnsuitable(BleCentralLink link)
	{
		unsuitables.put(link.device.getAddress().toUpperCase(), new Date());
		links.remove(link.getDeviceAddress());
		//Logger.debug("ble central unsuitable {}", link.toString());
	}

	void linkConnecting(BleCentralLink link)
	{
		links.put(link.getDeviceAddress(), link);
	}

	void linkConnected(BleCentralLink link)
	{
	}

	void linkDisconnected(BleCentralLink link, boolean wasConnected)
	{
		links.remove(link.getDeviceAddress());
	}

	void linkDidReceiveFrame(BleCentralLink link, byte[] frameData)
	{
	}
} // BleCentral

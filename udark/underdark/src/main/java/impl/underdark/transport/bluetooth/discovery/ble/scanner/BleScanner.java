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

package impl.underdark.transport.bluetooth.discovery.ble.scanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Arrays;
import java.util.List;

import impl.underdark.transport.bluetooth.BtTransport;
import impl.underdark.transport.bluetooth.BtUtils;
import impl.underdark.transport.bluetooth.discovery.Scanner;
import impl.underdark.transport.bluetooth.discovery.ble.BleConfig;
import impl.underdark.transport.bluetooth.discovery.ble.ManufacturerData;
import impl.underdark.transport.bluetooth.discovery.ble.detector.BleDetector;
import impl.underdark.transport.bluetooth.discovery.ble.detector.BleDetectorFactory;
import impl.underdark.transport.bluetooth.discovery.ble.detector.BleScanRecord;
import impl.underdark.logging.Logger;
import io.underdark.Config;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(18)
public class BleScanner implements BleDetector.Listener, Scanner
{
	private boolean running;

	private int appId;
	private BluetoothAdapter adapter;
	Context context;
	private Scanner.Listener listener;
	DispatchQueue queue;

	private BleDetector detector;

	private Runnable stopCommand;

	public BleScanner(
			int appId,
			Context context,
			Scanner.Listener listener,
			DispatchQueue queue
	)
	{
		this.appId = appId;
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
					listener.onScanStopped(BleScanner.this, true);
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
					listener.onScanStopped(BleScanner.this, true);
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
					listener.onScanStopped(BleScanner.this, true);
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
				//Logger.debug("ble scan started");
				listener.onScanStarted(BleScanner.this);
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
	}

	@Override
	public void onScanStopped(final boolean error)
	{
		//Logger.debug("ble scan stopped");
		running = false;
		detector = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStopped(BleScanner.this, error);
			}
		});
	}

	@Override
	public void onDeviceDetected(BluetoothDevice device, byte[] scanRecordData)
	{
		if(!running)
			return;

		BleScanRecord scanRecord = BleScanRecord.parseFromBytes(scanRecordData);
		if(scanRecord == null)
			return;

		byte[] data = scanRecord.getManufacturerSpecificData(BleConfig.manufacturerId);
		if(data == null)
			return;

		final ManufacturerData manufacturerData =
				ManufacturerData.parse(data);
		if(manufacturerData == null || manufacturerData.getAppId() != appId)
			return;

		final byte[] localAddress = BtUtils.getBytesFromAddress(adapter.getAddress());

		if(Arrays.equals(localAddress, manufacturerData.getAddress()))
			return;

		final BluetoothDevice remoteDevice = adapter.getRemoteDevice(manufacturerData.getAddress());
		//final BluetoothDevice remoteDevice = device;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onDeviceChannelsDiscovered(BleScanner.this, remoteDevice, manufacturerData.getChannels());
			}
		});
	} // onDeviceDetected
	//endregion
} // BleScanner

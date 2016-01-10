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

package impl.underdark.transport.bluetooth.discovery.ble.detector;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(18)
public class BleKitKatDetector implements BleDetector
{
	private BluetoothAdapter adapter;
	private BleDetector.Listener listener;
	private DispatchQueue queue;

	private BluetoothAdapter.LeScanCallback scanCallback;

	public BleKitKatDetector(
			BluetoothAdapter adapter,
			BleDetector.Listener listener,
			DispatchQueue queue)
	{
		this.adapter = adapter;
		this.listener = listener;
		this.queue = queue;
	}

	//region BleDetector
	@Override
	public void startScan()
	{
		if(this.scanCallback != null)
			return;

		this.scanCallback = new BluetoothAdapter.LeScanCallback()
		{
			@Override
			public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord)
			{
				queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						listener.onDeviceDetected(device, scanRecord);
					}
				});
			}
		};

		if(!adapter.startLeScan(this.scanCallback))
		{
			this.scanCallback = null;
			Logger.error("ble central startLeScan() failed");

			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onScanStopped(true);
				}
			});

			return;
		}

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStarted();
			}
		});
	} // startScan()

	@Override
	public void stopScan()
	{
		if(this.scanCallback == null)
			return;

		adapter.stopLeScan(this.scanCallback);
		this.scanCallback = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onScanStopped(false);
			}
		});
	}
	//endregion
} // BleKitKatDetector

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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;

import java.util.List;

import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(21)
public class BleLollipopDetector implements BleDetector
{
	private BluetoothAdapter adapter;
	private BleDetector.Listener listener;
	private DispatchQueue queue;

	private ScanCallback scanCallback;

	public BleLollipopDetector(
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

		BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
		if(scanner == null)
		{
			Logger.error("ble central failed to get BluetoothLeScanner instance");
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

		this.scanCallback = new ScanCallback()
		{
			@Override
			public void onScanResult(int callbackType, final ScanResult result)
			{
				// Any thread.
				super.onScanResult(callbackType, result);

				queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						scanResult(result);
					}
				});
			}

			@Override
			public void onBatchScanResults(List<ScanResult> results)
			{
				// Any thread.
				super.onBatchScanResults(results);

				for(final ScanResult result : results)
				{
					queue.dispatch(new Runnable()
					{
						@Override
						public void run()
						{
							scanResult(result);
						}
					});
				}
			}

			@Override
			public void onScanFailed(int errorCode)
			{
				// Any thread.
				super.onScanFailed(errorCode);

				String errorString = "";

				switch (errorCode)
				{
					case SCAN_FAILED_ALREADY_STARTED:
						errorString = "SCAN_FAILED_ALREADY_STARTED";
						break;
					case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
						errorString = "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
						break;
					case SCAN_FAILED_INTERNAL_ERROR:
						errorString = "SCAN_FAILED_INTERNAL_ERROR";
						break;
					case SCAN_FAILED_FEATURE_UNSUPPORTED:
						errorString = "SCAN_FAILED_FEATURE_UNSUPPORTED";
						break;
				}

				Logger.error("ble central scan failed: {}", errorString);

				queue.dispatch(new Runnable()
				{
					@Override
					public void run()
					{
						BleLollipopDetector.this.scanCallback = null;
						listener.onScanStopped(true);
					}
				});
			}
		}; // scanCallback

		/*List<ScanFilter> filters = new ArrayList<>();
		ScanFilter filter = new ScanFilter.Builder()
				.setServiceUuid(ParcelUuid.fromString(transport.serviceUuid.toString()))
				.build();
		filters.add(filter);

		ScanSettings settings = new ScanSettings.Builder()
				.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
				.build();*/

		try
		{
			scanner.startScan(scanCallback);
			//scanner.startScan(filters, settings, scanCallback);
		}
		catch (Exception ex)
		{
			Logger.error("ble scan failed: illegal adapter state");
			this.scanCallback = null;

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

		BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
		if(scanner == null)
			return;

		scanner.stopScan(scanCallback);
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

	private void scanResult(ScanResult result)
	{
		// Queue.
		listener.onDeviceDetected(result.getDevice(), result.getScanRecord().getBytes());
	}
} // BleLollipopDetector

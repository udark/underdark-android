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

package impl.underdark.transport.bluetooth.discovery.ble.advertiser;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

import impl.underdark.transport.bluetooth.discovery.Advertiser;
import impl.underdark.transport.bluetooth.discovery.ble.BleConfig;
import impl.underdark.logging.Logger;
import impl.underdark.transport.bluetooth.discovery.ble.ManufacturerData;
import io.underdark.Config;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(21)
public class BleAdvertiser implements Advertiser, BleAdvCallback.Listener
{
	private int appId;
	private BluetoothManager manager;
	private BluetoothAdapter adapter;
	private Context context;
	private Advertiser.Listener listener;
	private DispatchQueue queue;

	private BleAdvCallback callback;
	private BluetoothLeAdvertiser advertiser;

	private Runnable stopCommand;

	public BleAdvertiser(
			int appId,
			Context context,
			Advertiser.Listener listener,
			DispatchQueue queue)
	{
		this.appId = appId;
		this.context = context;
		this.listener = listener;
		this.queue = queue;
	}

	//region Advertiser
	@Override
	public boolean isSupported()
	{
		if(Build.VERSION.SDK_INT < 21)
			return false;

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			return false;
		}

		final BluetoothManager bluetoothManager =
				(BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

		if(bluetoothAdapter == null)
		{
			return false;
		}

		if(!bluetoothAdapter.isMultipleAdvertisementSupported())
		{
			return false;
		}

		return true;
	}

	@Override
	public void startAdvertise(long durationMs)
	{
		if(Build.VERSION.SDK_INT < 21)
		{
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BleAdvertiser.this, true);
				}
			});
			return;
		}

		if(this.callback != null)
			return;

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Logger.error("Bluetooth LE is not supported on this device.");
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BleAdvertiser.this, true);
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
					listener.onAdvertiseStopped(BleAdvertiser.this, true);
				}
			});
			return;
		}

		this.manager = bluetoothManager;

		if(!adapter.isMultipleAdvertisementSupported())
		{
			Logger.warn("ble peripheral mode is not supported on this device.");

			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BleAdvertiser.this, true);
				}
			});
			return;
		}

		this.advertiser = adapter.getBluetoothLeAdvertiser();
		if(advertiser == null)
		{
			Logger.error("ble peripheral failed to get BluetoothLeAdvertiser instance");
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BleAdvertiser.this, true);
				}
			});
			return;
		}

		this.callback = new BleAdvCallback(this, queue);

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				//Logger.debug("ble advertise started");
				listener.onAdvertiseStarted(BleAdvertiser.this);
			}
		});

		stopCommand =
				queue.dispatchAfter(durationMs, new Runnable()
				{
					@Override
					public void run()
					{
						stopAdvertise();
					}
				});

		ManufacturerData data = listener.onAdvertisementDataRequested();
		if(data == null)
			stopAdvertise();

		advertise(data);
	} // startAdvertise()

	@Override
	public void stopAdvertise()
	{
		if(this.callback == null)
			return;

		queue.cancel(stopCommand);
		stopCommand = null;

		try
		{
			this.advertiser.stopAdvertising(callback);
		}
		catch (Exception ex)
		{
		}

		this.advertiser = null;
		this.callback = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				//Logger.debug("ble advertise stopped");
				listener.onAdvertiseStopped(BleAdvertiser.this, false);
			}
		});
	} // stopAdvertise()

	@Override
	public void touch()
	{
		if(this.callback == null)
			return;

		try
		{
			this.advertiser.stopAdvertising(callback);
		}
		catch (Exception ex)
		{
		}

		this.callback = new BleAdvCallback(this, queue);

		ManufacturerData data = listener.onAdvertisementDataRequested();
		if(data == null)
			stopAdvertise();

		advertise(data);
	}
	//endregion

	private void advertise(ManufacturerData manufacturerData)
	{
		if(manufacturerData == null)
		{
			stopAdvertise();
			return;
		}

		//Logger.debug("ble advertise {} channels", manufacturerData.getChannels().size());

		AdvertiseSettings settings = new AdvertiseSettings.Builder()
				.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
				.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
				.setConnectable(false)
				.setTimeout(0)
				.build();

		byte[] data = manufacturerData.build();
		if(data == null)
		{
			stopAdvertise();
			return;
		}

		AdvertiseData advertiseData = new AdvertiseData.Builder()
				.setIncludeTxPowerLevel(false)
				.setIncludeDeviceName(false)
				.addManufacturerData(BleConfig.manufacturerId, data)
				.build();

		try
		{
			advertiser.startAdvertising(settings, advertiseData, callback);
		}
		catch (Exception ex)
		{
			stopAdvertise();
		}
	} // advertise()

	//region BleAdcCallback.Listener
	@Override
	public void onStartSuccess(AdvertiseSettings settingsInEffect)
	{
		if(this.callback == null)
			return;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				//Logger.debug("ble advertise onStartSuccess()");
			}
		});
	}

	@Override
	public void onStartFailure(int errorCode)
	{
		Logger.error("ble advertise failed: {}", BleAdvCallback.errorCodeToString(errorCode));
		stopAdvertise();
	}
	//endregion

} // BleAdvertiser

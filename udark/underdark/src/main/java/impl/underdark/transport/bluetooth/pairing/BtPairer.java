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

package impl.underdark.transport.bluetooth.pairing;

// Auto pairing: http://stackoverflow.com/a/30362554/1449965

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import impl.underdark.logging.Logger;

// http://stackoverflow.com/a/30362554/1449965
// http://stackoverflow.com/a/14870424/1449965

@TargetApi(19)
public class BtPairer
{
	public interface Listener
	{
		boolean shouldPairDevice(BluetoothDevice device);
		void onDevicePaired(BluetoothDevice device);
	}

	private boolean running;
	private Context context;
	private Listener listener;
	private BroadcastReceiver receiver;

	public BtPairer(Listener listener, Context context)
	{
		this.listener = listener;
		this.context = context.getApplicationContext();
	}

	public void start()
	{
		if(Build.VERSION.SDK_INT < 19)
			return;

		if(running)
			return;

		running = true;

		receiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				BtPairer.this.onReceive(context, intent);
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

		context.registerReceiver(receiver, filter);
	} // start()

	public void stop()
	{
		if(!running)
			return;

		running = false;

		context.unregisterReceiver(receiver);
		receiver = null;
	} // stop()

	private void onReceive(Context context, Intent intent)
	{
		if(!running)
			return;

		if (intent.getAction().equals(BluetoothDevice.ACTION_PAIRING_REQUEST))
		{
			try
			{
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

				if(!listener.shouldPairDevice(device))
				{
					Logger.debug("bt pairing skipped for device '{}' {}", device.getName(), device.getAddress());
					return;
				}

				int pin = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, 0);

				Logger.debug("bt pairing device '{}' pin {} ", device.getName(), pin);

				byte[] pinBytes = Integer.toString(pin).getBytes("UTF-8");
				device.setPin(pinBytes);
				device.setPairingConfirmation(true);

				listener.onDevicePaired(device);
			}
			catch (Exception ex)
			{
				Logger.error("bt pairing exception {}", ex);
			}

			return;
		} // if
	} // onReceive
} // BtPairer

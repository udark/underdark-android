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

package impl.underdark.transport.bluetooth.discovery.ble.transport.peripheral;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(21)
public class BleSrvCallback extends BluetoothGattServerCallback
{
	private BlePeripheral callback;
	private DispatchQueue queue;

	public BleSrvCallback(BlePeripheral callback, DispatchQueue queue)
	{
		this.callback = callback;
		this.queue = queue;
	}

	public static BluetoothGattServer openGattServer(
			BluetoothManager manager,
			Context context,
			BluetoothGattServerCallback callback)
	{
		try {
			Method m = BluetoothDevice.class.getMethod(
					"openGattServer",
					Context.class,
					BluetoothGattServerCallback.class,
					int.class);

			if (m != null) {
				try{
					return (BluetoothGattServer)m.invoke(
							manager,
							context,
							callback,
							2);
				} catch (IllegalAccessException ex) {
					// Handle exception
					return null;
				} catch(InvocationTargetException ex) {
					// Handle exception
					return null;
				}
			} else {
				return null;
			}
		} catch (NoSuchMethodException ex) {
			// This way of doing it is not compatible for the current device
			return null;
		}
	} // openGattServer()

	//region BluetoothGattServerCallback
	@Override
	public void onConnectionStateChange(final BluetoothDevice device, final int status,
	                                    final int newState)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onConnectionStateChange(device, status, newState);
			}
		});
	}

	@Override
	public void onServiceAdded(final int status, final BluetoothGattService service)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onServiceAdded(status, service);
			}
		});
	}

	@Override
	public void onCharacteristicReadRequest(final BluetoothDevice device, final int requestId,
	                                        final int offset, final BluetoothGattCharacteristic characteristic)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			}
		});
	}

	@Override
	public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId,
	                                         final BluetoothGattCharacteristic characteristic,
	                                         final boolean preparedWrite, final boolean responseNeeded,
	                                         final int offset, final byte[] value)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onCharacteristicWriteRequest(
						device,
						requestId,
						characteristic,
						preparedWrite,
						responseNeeded,
						offset,
						value);
			}
		});
	}

	@Override
	public void onDescriptorReadRequest(final BluetoothDevice device, final int requestId,
	                                    final int offset, final BluetoothGattDescriptor descriptor)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onDescriptorReadRequest(device, requestId, offset, descriptor);
			}
		});
	}

	@Override
	public void onDescriptorWriteRequest(final BluetoothDevice device, final int requestId,
	                                     final BluetoothGattDescriptor descriptor,
	                                     final boolean preparedWrite, final boolean responseNeeded,
	                                     final int offset,  final byte[] value)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onDescriptorWriteRequest(
						device,
						requestId,
						descriptor,
						preparedWrite,
						responseNeeded,
						offset,
						value);
			}
		});
	}

	@Override
	public void onExecuteWrite(final BluetoothDevice device, final int requestId, final boolean execute)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onExecuteWrite(device, requestId, execute);
			}
		});
	}

	@Override
	public void onNotificationSent(final BluetoothDevice device, final int status)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onNotificationSent(device, status);
			}
		});
	}

	@Override
	public void onMtuChanged(final BluetoothDevice device, final int mtu)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onMtuChanged(device, mtu);
			}
		});
	}
	//endregion
} // BleSrvCallback

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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;

import impl.underdark.transport.bluetooth.discovery.ble.BleConfig;
import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(18)
class BleGattAdapter extends BluetoothGattCallback
{

	private BluetoothGattCallback callback;
	private BluetoothGatt gatt;

	private DispatchQueue queue;
	private Queue<Runnable> commands = new LinkedList<>();
	private Runnable currentCommand = null;

	private boolean disconnected;

	public static BluetoothGatt connectGatt(BluetoothDevice device,
	                                         Context context,
	                                         boolean autoConnect,
	                                         BluetoothGattCallback callback
	)
	{
		try {
			Method m = BluetoothDevice.class.getMethod("connectGatt",
					Context.class, boolean.class, BluetoothGattCallback.class, int.class);
			if (m != null) {
				try{
					return (BluetoothGatt)m.invoke(device,
							context, autoConnect, callback, 2);
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
	}

	public BleGattAdapter(BluetoothGattCallback callback, DispatchQueue queue)
	{
		this.callback = callback;
		this.queue = queue;
	}

	public void disconnect()
	{
		commands.clear();
		gatt.disconnect();
		disconnected = true;
	}

	public void close()
	{
		commands.clear();
		gatt.close();
		disconnected = true;
	}

	public void setGatt(BluetoothGatt gatt)
	{
		this.gatt = gatt;
	}

	private void addCommand(Runnable command)
	{
		if(disconnected)
			return;

		commands.add(command);
		if(currentCommand == null)
			nextCommand();
	}

	private void nextCommand()
	{
		currentCommand = null;
		if(disconnected)
			return;

		currentCommand = commands.poll();
		if(currentCommand == null)
			return;

		queue.dispatch(currentCommand);
	}

	//region BluetoothGatt
	public void readCharacteristic(final BluetoothGattCharacteristic characteristic)
	{
		addCommand(new Runnable()
		{
			@Override
			public void run()
			{
				if (!gatt.readCharacteristic(characteristic))
					callback.onCharacteristicRead(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
			}
		});
	}

	public void writeCharacteristic(final BluetoothGattCharacteristic characteristic)
	{
		addCommand(new Runnable()
		{
			@Override
			public void run()
			{
				/*if(!gatt.beginReliableWrite())
				{
					callback.onCharacteristicWrite(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
					return;
				}*/

				if(!gatt.writeCharacteristic(characteristic))
				{
					callback.onCharacteristicWrite(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
					return;
				}

				/*if(!gatt.executeReliableWrite())
				{
					callback.onCharacteristicWrite(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
					return;
				}*/
			}
		});
	}

	public void readDescriptor(final BluetoothGattDescriptor descriptor)
	{
		addCommand(new Runnable()
		{
			@Override
			public void run()
			{
				if (!gatt.readDescriptor(descriptor))
					callback.onDescriptorRead(gatt, descriptor, BluetoothGatt.GATT_FAILURE);
			}
		});
	}

	public void writeDescriptor(final BluetoothGattDescriptor descriptor)
	{
		addCommand(new Runnable()
		{
			@Override
			public void run()
			{
				if (!gatt.writeDescriptor(descriptor))
					callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_FAILURE);
			}
		});
	}

	public void setCharacteristicNotification(final BluetoothGattCharacteristic characteristic,
	                                             final boolean enable)
	{
		addCommand(new Runnable()
		{
			@Override
			public void run()
			{
				if(BluetoothGattCharacteristic.PROPERTY_NOTIFY
				!= (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY))
				{
					Logger.error("ble setCharacteristicNotification() failed: cannot notify");
					callback.onCharacteristicRead(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
					return;
				}

				if(!gatt.setCharacteristicNotification(characteristic, enable))
				{
					Logger.error("ble setCharacteristicNotification() failed");
					callback.onCharacteristicRead(gatt, characteristic, BluetoothGatt.GATT_FAILURE);
					return;
				}

				BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleConfig.stdClientCharacteristicConfigUuid);
				descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
				BleGattAdapter.this.writeDescriptor(descriptor);
			}
		});
	}
	//endregion

	//region BluetoothGattCallback
	@Override
	public void onConnectionStateChange(final BluetoothGatt gatt, final int status,
	                                    final int newState) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onConnectionStateChange(gatt, status, newState);
			}
		});
	}

	@Override
	public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onServicesDiscovered(gatt, status);
			}
		});
	}

	@Override
	public void onCharacteristicChanged(final BluetoothGatt gatt,
	                                    final BluetoothGattCharacteristic characteristic) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onCharacteristicChanged(gatt, characteristic);
			}
		});
	}

	@Override
	public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic,
	                                 final int status) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onCharacteristicRead(gatt, characteristic, status);
				nextCommand();
			}
		});
	}

	@Override
	public void onCharacteristicWrite(final BluetoothGatt gatt,
	                                  final BluetoothGattCharacteristic characteristic, final int status) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onCharacteristicWrite(gatt, characteristic, status);
				nextCommand();
			}
		});
	}

	@Override
	public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor,
	                             final int status) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onDescriptorRead(gatt, descriptor, status);
				nextCommand();
			}
		});
	}

	@Override
	public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor,
	                              final int status) {
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onDescriptorWrite(gatt, descriptor, status);
				nextCommand();
			}
		});
	}
	//endregion
} // BleGattAdapter

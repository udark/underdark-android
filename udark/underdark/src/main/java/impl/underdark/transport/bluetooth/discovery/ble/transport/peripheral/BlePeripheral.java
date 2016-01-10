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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

import impl.underdark.transport.bluetooth.BtUtils;
import impl.underdark.transport.bluetooth.discovery.Advertiser;
import impl.underdark.transport.bluetooth.discovery.ble.BleConfig;
import impl.underdark.transport.bluetooth.discovery.ble.advertiser.BleAdvCallback;
import impl.underdark.logging.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(21)
public class BlePeripheral implements Advertiser, BleAdvCallback.Listener
{
	private long nodeId;
	private BluetoothManager manager;
	private BluetoothAdapter adapter;
	private Context context;
	private Advertiser.Listener listener;
	private DispatchQueue queue;

	private BleAdvCallback advCallback;
	private BleSrvCallback srvCallback;
	BluetoothLeAdvertiser advertiser;
	BluetoothGattServer gattServer;

	private Runnable stopCommand;

	public BlePeripheral(
			long nodeId,
			Context context,
			Advertiser.Listener listener,
			DispatchQueue queue)
	{
		this.nodeId = nodeId;
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
					listener.onAdvertiseStopped(BlePeripheral.this, true);
				}
			});
			return;
		}

		if(this.gattServer != null)
			return;

		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
		{
			Logger.error("Bluetooth LE is not supported on this device.");
			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BlePeripheral.this, true);
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
					listener.onAdvertiseStopped(BlePeripheral.this, true);
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
					listener.onAdvertiseStopped(BlePeripheral.this, true);
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
					listener.onAdvertiseStopped(BlePeripheral.this, true);
				}
			});
			return;
		}

		this.srvCallback = new BleSrvCallback(this, queue);

		this.gattServer = BleSrvCallback.openGattServer(manager, context, srvCallback);
		if(this.gattServer == null)
		{
			Logger.warn("ble peripheral custom openGattServer() failed.");
			this.gattServer = manager.openGattServer(context, srvCallback);
		}

		if(this.gattServer == null)
		{
			Logger.error("ble peripheral openGattServer() failed.");
			this.advertiser = null;

			queue.dispatch(new Runnable()
			{
				@Override
				public void run()
				{
					listener.onAdvertiseStopped(BlePeripheral.this, true);
				}
			});
			return;
		}

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				Logger.debug("ble advertise started");
				listener.onAdvertiseStarted(BlePeripheral.this);
			}
		});

		addService();

		stopCommand =
		queue.dispatchAfter(durationMs, new Runnable()
		{
			@Override
			public void run()
			{
				stopAdvertise();
			}
		});
	} // startAdvertise()

	@Override
	public void stopAdvertise()
	{
		if(this.gattServer == null)
			return;

		queue.cancel(stopCommand);
		stopCommand = null;

		this.advertiser.stopAdvertising(advCallback);
		this.advertiser = null;
		this.advCallback = null;

		this.gattServer.clearServices();
		this.gattServer.close();
		this.gattServer = null;
		this.srvCallback = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				Logger.debug("ble advertise stopped");
				listener.onAdvertiseStopped(BlePeripheral.this, false);
			}
		});
	} // stopAdvertise()

	@Override
	public void touch()
	{
	}
	//endregion

	private void addService()
	{
		BluetoothGattService service =
				new BluetoothGattService(BleConfig.serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		BluetoothGattCharacteristic charactAddress = new BluetoothGattCharacteristic(
				BleConfig.charactAddressUuid,
				BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_READ);

		BluetoothGattDescriptor descBtAddress = new BluetoothGattDescriptor(
				BleConfig.stdCharacteristicUserDescriptionUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		BluetoothGattDescriptor formatBtAddress = new BluetoothGattDescriptor(
				BleConfig.stdCharacteristicPresentationFormatUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		charactAddress.addDescriptor(descBtAddress);
		charactAddress.addDescriptor(formatBtAddress);


		BluetoothGattCharacteristic charactNodeId = new BluetoothGattCharacteristic(
				BleConfig.charactNodeIdUuid,
				BluetoothGattCharacteristic.PROPERTY_READ,
				BluetoothGattCharacteristic.PERMISSION_READ);

		BluetoothGattDescriptor descNodeId = new BluetoothGattDescriptor(
				BleConfig.stdCharacteristicUserDescriptionUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		BluetoothGattDescriptor formatNodeId = new BluetoothGattDescriptor(
				BleConfig.stdCharacteristicPresentationFormatUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		charactNodeId.addDescriptor(descNodeId);
		charactNodeId.addDescriptor(formatNodeId);

		/*BluetoothGattCharacteristic charactJack = new BluetoothGattCharacteristic(
				transport.charactJackUuid,
				BluetoothGattCharacteristic.PROPERTY_WRITE,
				BluetoothGattCharacteristic.PERMISSION_WRITE);

		BluetoothGattDescriptor descJack = new BluetoothGattDescriptor(
				BleTransport.stdCharacteristicUserDescriptionUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		charactJack.addDescriptor(descJack);


		BluetoothGattCharacteristic charactStream = new BluetoothGattCharacteristic(
				transport.charactStreamUuid,
				BluetoothGattCharacteristic.PROPERTY_READ
					| BluetoothGattCharacteristic.PROPERTY_WRITE
					| BluetoothGattCharacteristic.PROPERTY_INDICATE,
				BluetoothGattCharacteristic.PERMISSION_READ
					| BluetoothGattCharacteristic.PERMISSION_WRITE);

		BluetoothGattDescriptor descStream = new BluetoothGattDescriptor(
				BleTransport.stdCharacteristicUserDescriptionUuid,
				BluetoothGattDescriptor.PERMISSION_READ
		);
		charactStream.addDescriptor(descStream);*/

		service.addCharacteristic(charactAddress);
		service.addCharacteristic(charactNodeId);
		//service.addCharacteristic(charactJack);
		//service.addCharacteristic(charactStream);

		if(!gattServer.addService(service))
		{
			Logger.error("ble peripheral gattServer.addService() failed.");
			stopAdvertise();
			return;
		}
	} // addService()

	//region AdvertiseCallback
	@Override
	public void onStartSuccess(final AdvertiseSettings settingsInEffect)
	{
		if(this.gattServer == null)
			return;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				Logger.debug("ble advertise onStartSuccess()");
			}
		});
	}

	@Override
	public void onStartFailure(final int errorCode)
	{
		Logger.error("ble advertise failed: {}", BleAdvCallback.errorCodeToString(errorCode));
		stopAdvertise();
	}
	//endregion

	//region BluetoothGattServerCallback
	void onServiceAdded(int status, BluetoothGattService service)
	{
		if(status != BluetoothGatt.GATT_SUCCESS)
		{
			Logger.error("ble peripheral onServiceAdded() failed: {}",
					BtUtils.gattStatusToString(status));
			stopAdvertise();
			return;
		}

		Logger.debug("ble peripheral onServiceAdded()");

		AdvertiseSettings settings = new AdvertiseSettings.Builder()
				.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
				.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
				.setConnectable(true)
				.setTimeout(0)
				.build();

		this.advCallback = new BleAdvCallback(this, queue);

		AdvertiseData advertiseData = new AdvertiseData.Builder()
				.setIncludeTxPowerLevel(false)
				.setIncludeDeviceName(false)
				.addServiceUuid(ParcelUuid.fromString(BleConfig.serviceUuid.toString()))
				.build();

		advertiser.startAdvertising(settings, advertiseData, advCallback);
	} // onServiceAdded()

	void onConnectionStateChange(
			BluetoothDevice device,
			int status,
			int newState)
	{
		if(newState == BluetoothProfile.STATE_CONNECTED)
		{
			Logger.debug("ble peripheral onConnectionStateChange STATE_CONNECTED");
			return;
		}

		if(newState == BluetoothProfile.STATE_DISCONNECTED)
		{
			Logger.debug("ble peripheral onConnectionStateChange STATE_DISCONNECTED");
			return;
		}
	}

	void onCharacteristicReadRequest(
			final BluetoothDevice device,
			final int requestId,
			final int offset,
			final BluetoothGattCharacteristic characteristic)
	{
		//Logger.debug("ble peripheral onCharacteristicReadRequest");

		if(BleConfig.charactNodeIdUuid.equals(characteristic.getUuid()))
		{
			ByteBuf buffer = Unpooled.buffer();
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.writeLong(this.nodeId);

			if(offset >= buffer.readableBytes())
			{
				Logger.warn("ble peripheral read nodeId failed - invalid offset {}", offset);
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
				return;
			}

			byte[] value = Arrays.copyOfRange(
					buffer.array(),
					offset,
					Math.min(buffer.readableBytes() - offset, BleConfig.charactValueSizeMax)
					);
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
			return;
		} // nodeId

		if(BleConfig.charactAddressUuid.equals(characteristic.getUuid()))
		{
			byte[] address = BtUtils.getBytesFromAddress(this.adapter.getAddress());
			if(address == null)
			{
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
				return;
			}

			onAddressReadRequest(address, device, requestId, offset);

			return;
		} // address

		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
	} // onCharacteristicReadRequest

	private void onAddressReadRequest(
			byte[] address,
			BluetoothDevice device,
			int requestId,
			int offset)
	{
		if(address == null)
		{
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
			return;
		}

		ByteBuf buffer = Unpooled.buffer();
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.writeBytes(address);

		if(offset >= buffer.readableBytes())
		{
			Logger.warn("ble peripheral read address failed - invalid offset {}", offset);
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
			return;
		}

		byte[] value = Arrays.copyOfRange(
				buffer.array(),
				offset,
				Math.min(buffer.readableBytes() - offset, BleConfig.charactValueSizeMax)
		);
		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
	} // onAddressReadRequest()

	void onCharacteristicWriteRequest(
			BluetoothDevice device,
			int requestId,
			BluetoothGattCharacteristic characteristic,
			boolean preparedWrite,
			boolean responseNeeded,
			int offset,
			byte[] value)
	{
		//Logger.debug("ble peripheral onCharacteristicWriteRequest");

		if(BleConfig.charactJackUuid.equals(characteristic.getUuid()))
		{
			/*BlePeripheralLink link = links.get(device);
			if(link != null)
			{
				Logger.warn("ble peripheral link {} already exists.", device.getAddress());
				return;
			}

			ByteBuf buffer = Unpooled.wrappedBuffer(value).order(ByteOrder.BIG_ENDIAN);
			long nodeId;
			try { nodeId = buffer.readLong(); }
			catch (IndexOutOfBoundsException ex)
			{
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, offset, null);
				return;
			}

			link = new BlePeripheralLink(this, device, nodeId);
			linkConnecting(link);
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);

			return;*/
		}

		if(BleConfig.charactStreamUuid.equals(characteristic.getUuid()))
		{
			return;
		}

		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
	}

	void onDescriptorReadRequest(
			BluetoothDevice device,
			int requestId,
			int offset,
			BluetoothGattDescriptor descriptor)
	{
		//Logger.debug("ble peripheral onDescriptorReadRequest");

		if(BleConfig.stdCharacteristicUserDescriptionUuid.equals(descriptor.getUuid()))
		{
			describeCharacteristic(device, requestId, offset, descriptor);
			return;
		}

		if(BleConfig.stdCharacteristicPresentationFormatUuid.equals(descriptor.getUuid()))
		{
			describeCharacteristicFormat(device, requestId, offset, descriptor);
			return;
		}

		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
	} // onDescriptorReadRequest()

	private void describeCharacteristic(
			BluetoothDevice device,
			int requestId,
			int offset,
			BluetoothGattDescriptor descriptor)
	{
		String description = "";

		if(BleConfig.charactAddressUuid.equals(descriptor.getCharacteristic().getUuid()))
			description = "address";
		if(BleConfig.charactNodeIdUuid.equals(descriptor.getCharacteristic().getUuid()))
			description = "nodeId";
		if(BleConfig.charactJackUuid.equals(descriptor.getCharacteristic().getUuid()))
			description = "jack";
		if(BleConfig.charactStreamUuid.equals(descriptor.getCharacteristic().getUuid()))
			description = "stream";

		ByteBuf buffer = Unpooled.buffer();
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.writeBytes(description.getBytes(Charset.forName("utf-8")));
		if(offset >= buffer.readableBytes())
		{
			Logger.warn("ble peripheral describe charact '{}' failed - invalid offset {}", description, offset);
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
			return;
		}

		byte[] value = Arrays.copyOfRange(
				buffer.array(),
				offset,
				Math.min(buffer.readableBytes() - offset, BleConfig.charactValueSizeMax)
		);
		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
	} // describeCharacteristic

	private void describeCharacteristicFormat(
			BluetoothDevice device,
			int requestId,
			int offset,
			BluetoothGattDescriptor descriptor)
	{
		// https://developer.bluetooth.org/gatt/descriptors/Pages/DescriptorViewer.aspx?u=org.bluetooth.descriptor.gatt.characteristic_presentation_format.xml
		// https://developer.mbed.org/teams/Bluetooth-Low-Energy/code/BLE_API/file/ecbc3405c66e/public/GattCharacteristic.h
		byte format = 10;
		if(BleConfig.charactAddressUuid.equals(descriptor.getCharacteristic().getUuid()))
			format = 25;
		if(BleConfig.charactNodeIdUuid.equals(descriptor.getCharacteristic().getUuid()))
			format = 18;

		ByteBuf buffer = Unpooled.buffer();
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.writeByte(format);   // format
		buffer.writeByte(0);        // exponent
		buffer.writeShort(0x2700);  // unit
		buffer.writeByte(1);        // namespace
		buffer.writeShort(0);       // description

		if(offset >= buffer.readableBytes())
		{
			Logger.warn("ble peripheral format charact failed - invalid offset {}", offset);
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
			return;
		}

		byte[] value = Arrays.copyOfRange(
				buffer.array(),
				offset,
				Math.min(buffer.readableBytes() - offset, BleConfig.charactValueSizeMax)
		);
		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
	} // describeCharacteristicFormat()

	void onDescriptorWriteRequest(
			BluetoothDevice device,
			int requestId,
			BluetoothGattDescriptor descriptor,
			boolean preparedWrite,
			boolean responseNeeded,
			int offset,
			byte[] value)
	{
		Logger.debug("ble peripheral onDescriptorWriteRequest");

		if(BleConfig.stdClientCharacteristicConfigUuid.equals(descriptor.getUuid()))
		{
			// Characteristic subscribe/unsubscribe.

			return;
		}

		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
	}

	void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute)
	{
		Logger.debug("ble peripheral onExecuteWrite");

		gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
	}

	void onNotificationSent(BluetoothDevice device, int status)
	{
		Logger.debug("ble peripheral onNotificationSent");

		/* Callback invoked when a notification or indication has been sent to
		a remote device.
		When multiple notifications are to be sent, an application must
		wait for this callback to be received before sending additional
		notifications.*/
	}

	void onMtuChanged(BluetoothDevice device, int mtu)
	{
		Logger.debug("ble peripheral onMtuChanged");
	}
	//endregion
} // BlePeripheral

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
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import impl.underdark.transport.bluetooth.BtUtils;
import impl.underdark.transport.bluetooth.discovery.ble.BleConfig;
import impl.underdark.logging.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.underdark.transport.Link;

@TargetApi(18)
class BleCentralLink extends BluetoothGattCallback implements Link
{
	private static final int frameHeaderSize = 4;

	private BleCentral central;
	BluetoothDevice device;
	private BluetoothGatt gatt;
	private BleGattAdapter gattAdapter;

	private boolean connected;
	private long nodeId;

	private BluetoothGattCharacteristic charactNodeId;
	private BluetoothGattCharacteristic charactJack;
	private BluetoothGattCharacteristic charactStream;
	private BluetoothGattCharacteristic charactAddress;

	private ByteBuf inputBuffer = Unpooled.buffer().order(ByteOrder.BIG_ENDIAN);
	private Queue<ByteBuf> outputQueue = new LinkedList<>();
	private ByteBuf outputBuffer = null;

	public BleCentralLink(BleCentral central, BluetoothDevice device)
	{
		this.central = central;
		this.device = device;
	}

	public void connect()
	{
		central.linkConnecting(this);

		gattAdapter = new BleGattAdapter(this, central.queue);

		gatt = BleGattAdapter.connectGatt(device, central.context, false, gattAdapter);
		if(gatt == null)
		{
			Logger.warn("ble linkc custom connectGatt() failed.");
			gatt = device.connectGatt(central.context, false, gattAdapter);
		}

		if(gatt == null)
		{
			Logger.error("ble linkc connectGatt failed.");
			central.linkDisconnected(this, false);
			return;
		}

		gattAdapter.setGatt(gatt);
	} // connect()

	public String getDeviceAddress()
	{
		return device.getAddress().toUpperCase();
	}

	@Override
	public String toString()
	{
		return "linkc nodeId " + nodeId +" device " + device.getAddress() + " '" + device.getName() + "'";
	}

	//region Link
	@Override
	public long getNodeId()
	{
		// Listener queue.
		return nodeId;
	}

	@Override
	public int getPriority()
	{
		// Listener queue.
		return 30;
	}

	@Override
	public void disconnect()
	{
		// Listener queue.
		central.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				gattAdapter.disconnect();
			}
		});
	}
	//endregion

	@Override
	public void sendFrame(final byte[] frameData)
	{
		// Listener queue.
		central.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				sendFrameInternal(frameData);
			}
		});
	}

	public void sendFrameInternal(byte[] frameData)
	{
		if(!connected)
			return;

		ByteBuf frameBuf = Unpooled.buffer();
		frameBuf.order(ByteOrder.BIG_ENDIAN);
		frameBuf.writeInt(frameData.length);
		frameBuf.writeBytes(frameData);

		if(outputBuffer == null)
		{
			outputBuffer = frameBuf;
			return;
		}

		outputQueue.offer(frameBuf);
	}
	//endregion

	private void writeNextBytes()
	{
		if(!connected)
			return;

		if(outputBuffer.readableBytes() == 0)
		{
			// Frame is fully written - taking next.
			outputBuffer = outputQueue.poll();

			if(outputBuffer == null)
				return;
		}

		int dataLen = Math.min(BleConfig.charactValueSizeMax, outputBuffer.readableBytes());
		byte[] data = new byte[dataLen];
		outputBuffer.readBytes(data, 0, data.length);

		charactStream.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		charactStream.setValue(data);
		gattAdapter.writeCharacteristic(charactStream);
	}

	private void formFrames()
	{
		while(true)
		{
			// If current buffer length is not enough to create frame header - so continue reading.
			if(inputBuffer.readableBytes() < frameHeaderSize)
				break;

			int frameBodySize = inputBuffer.getInt(0);
			int frameSize = frameHeaderSize + frameBodySize;

			// We don't have full frame in input buffer - so continue reading.
			if(frameSize > inputBuffer.readableBytes())
				break;

			// We have our frame at the start of inputData.
			inputBuffer.readInt();
			byte[] frameData = new byte[frameBodySize];
			inputBuffer.readBytes(frameData, 0, frameData.length);
			inputBuffer.discardReadBytes();

			central.linkDidReceiveFrame(this, frameData);
		}
	}

	//region BluetoothGattCallback
	public void onConnectionStateChange(BluetoothGatt gatt,
	                                    int status,
	                                    int newState)
	{
		if(!connected && newState == BluetoothProfile.STATE_DISCONNECTED)
		{
			Logger.error("ble central gatt connection failed " + this.toString());
			this.connected = false;
			outputQueue.clear();
			gattAdapter.close();
			central.linkDisconnected(BleCentralLink.this, false);

			return;
		}

		if(connected && newState == BluetoothProfile.STATE_DISCONNECTED)
		{
			this.connected = false;
			outputQueue.clear();
			gattAdapter.close();
			central.linkDisconnected(BleCentralLink.this, true);

			return;
		}

		if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED)
		{
			if(!gatt.discoverServices())
			{
				Logger.error("ble linkc discoverServices() failed");
				gattAdapter.disconnect();
				return;
			}

			return;
		}
	} //onConnectionStateChange()

	public void onServicesDiscovered(final BluetoothGatt gatt, final int status)
	{
		if(status != BluetoothGatt.GATT_SUCCESS)
		{
			Logger.error("ble linkc onServicesDiscovered() failed");
			gattAdapter.disconnect();

			return;
		}

		BluetoothGattService service =
				gatt.getService(BleConfig.serviceUuid);
		if(service == null)
		{
			Logger.warn("ble service not found " + this.toString());
			gattAdapter.disconnect();
			central.linkUnsuitable(this);

			return;
		}

		for(BluetoothGattCharacteristic charact : service.getCharacteristics())
		{
			if(charact.getUuid().equals(BleConfig.charactNodeIdUuid))
				charactNodeId = charact;

			if(charact.getUuid().equals(BleConfig.charactJackUuid))
				charactJack = charact;

			if(charact.getUuid().equals(BleConfig.charactStreamUuid))
				charactStream = charact;

			if(charact.getUuid().equals(BleConfig.charactAddressUuid))
				charactAddress = charact;
		}

		//if(charactNodeId == null || charactJack == null || charactStream == null)
		if(charactNodeId == null || charactAddress == null)
		{
			Logger.error("ble central missing characteristics in {}", this.toString());
			gattAdapter.disconnect();
			central.linkUnsuitable(this);
			return;
		}

		gattAdapter.readCharacteristic(charactNodeId);
	} // onServicesDiscovered()

	public void onCharacteristicRead(BluetoothGatt gatt,
	                                 BluetoothGattCharacteristic characteristic,
	                                 int status)
	{
		byte[] value = characteristic.getValue();

		if(charactNodeId.getUuid().equals(characteristic.getUuid()))
		{
			if(status != BluetoothGatt.GATT_SUCCESS)
			{
				Logger.error("ble linkc nodeId read failed {} {}",
						BtUtils.gattStatusToString(status), this.toString());
				gattAdapter.disconnect();
				return;
			}

			ByteBuffer buffer = ByteBuffer.wrap(value);
			buffer.order(ByteOrder.BIG_ENDIAN);

			try
			{
				this.nodeId = buffer.getLong();
			}
			catch (BufferUnderflowException ex)
			{
				Logger.error("ble linkc failed to read nodeId");
				gattAdapter.disconnect();
				return;
			}

			//Logger.debug("ble linkc determined nodeId {}", nodeId);

			gattAdapter.readCharacteristic(charactAddress);

			/*buffer = ByteBuffer.allocate(8);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.putLong(central.transport.getNodeId());
			charactJack.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
			charactJack.setValue(buffer.array());
			gattAdapter.writeCharacteristic(charactJack);*/

			return;
		}

		if(charactAddress.getUuid().equals(characteristic.getUuid()))
		{
			if(status != BluetoothGatt.GATT_SUCCESS)
			{
				Logger.error("ble linkc address read failed {} {}",
						BtUtils.gattStatusToString(status), this.toString());
				gattAdapter.disconnect();
				return;
			}

			byte[] address = new byte[6];
			if(value.length < address.length)
			{
				Logger.error("ble linkc failed to read address");
				gattAdapter.disconnect();
				return;
			}

			address = Arrays.copyOf(value, address.length);
			central.onAddressDiscovered(address);

			gattAdapter.disconnect();
			central.linkUnsuitable(this);

			return;
		}
	} // onCharacteristicRead()

	public void onCharacteristicWrite(BluetoothGatt gatt,
	                                  BluetoothGattCharacteristic characteristic,
	                                  int status)
	{
		if(characteristic.getUuid().equals(charactJack.getUuid()))
		{
			if(status != BluetoothGatt.GATT_SUCCESS)
			{
				Logger.error("ble jack write failed {} {}",
						BtUtils.gattStatusToString(status), this.toString());
				gattAdapter.disconnect();
				return;
			}

			gattAdapter.setCharacteristicNotification(charactStream, true);

			return;
		}

		if(characteristic.getUuid().equals(charactStream.getUuid()))
		{
			writeNextBytes();
			return;
		}
	} // onCharacteristicWrite()

	public void onCharacteristicChanged(BluetoothGatt gatt,
	                                    BluetoothGattCharacteristic characteristic)
	{
		if(characteristic.getUuid().equals(charactStream))
		{
			Logger.debug("ble onCharacteristicChanged()");

			if(characteristic.getValue() == null)
				return;

			inputBuffer.writeBytes(characteristic.getValue());
			formFrames();
			return;
		}
	}

	public void onDescriptorRead(BluetoothGatt gatt,
	                             BluetoothGattDescriptor descriptor,
	                             int status)
	{
	}

	public void onDescriptorWrite(BluetoothGatt gatt,
	                              BluetoothGattDescriptor descriptor,
	                              int status)
	{
		if(descriptor.getCharacteristic().getUuid().equals(charactStream))
		{
			if(status != BluetoothGatt.GATT_SUCCESS)
			{
				Logger.error("ble notify enable failed {} '{}'",
						BtUtils.gattStatusToString(status), this.toString());
				gattAdapter.disconnect();
				return;
			}

			this.connected = true;
			central.linkConnected(this);

			return;
		}
	} // onDescriptorWrite()

	public void onReliableWriteCompleted(BluetoothGatt gatt, int status)
	{
	}

	public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
	{
	}

	public void onMtuChanged(BluetoothGatt gatt, int mtu, int status)
	{
	}
	//endregion
} // BleCentralLink

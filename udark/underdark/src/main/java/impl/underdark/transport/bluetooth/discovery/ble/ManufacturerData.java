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

package impl.underdark.transport.bluetooth.discovery.ble;

import java.util.ArrayList;
import java.util.List;

import impl.underdark.transport.bluetooth.BtUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ManufacturerData
{
	private static final int deviceAddressLen = 6;

	private int appId;
	private byte[] address;
	private List<Integer> channels;

	private ManufacturerData(
			int appId,
			byte[] address,
	        List<Integer> channels
	)
	{
		this.appId = appId;
		this.address = address.clone();
		this.channels = new ArrayList<>(channels);
	}

	private static int ipow(int base, int exp)
	{
		int result = 1;
		while (exp != 0)
		{
			if ((exp & 1) != 0)
				result *= base;
			exp >>= 1;
			base *= base;
		}

		return result;
	}

	public static ManufacturerData create(
			int appId,
			byte[] address,
			List<Integer> channels
	)
	{
		if(address == null || address.length != deviceAddressLen)
			return null;

		if(channels == null)
			return null;

		return new ManufacturerData(appId, address, channels);
	}

	public static ManufacturerData parse(byte[] data)
	{
		if(data == null || data.length < (1 + 6 + 4) )
			return null;

		ByteBuf buf = Unpooled.wrappedBuffer(data);
		byte version = buf.readByte();
		if(version != 1)
			return null;

		int appId = buf.readInt();
		byte[] address = new byte[deviceAddressLen];
		buf.readBytes(address);
		int channelsMask = buf.readInt();

		List<Integer> channels = new ArrayList<>();

		for(int channel = 0; channelsMask != 0; ++channel)
		{
			if(channelsMask % 2 != 0)
				channels.add(channel);

			channelsMask >>= 1;
		}

		return new ManufacturerData(appId, address, channels);
	} // parseManufacturerData()

	public byte[] build()
	{
		ByteBuf data = Unpooled.wrappedBuffer(new byte[27]);
		data.clear();
		data.writeByte(1);          // Version
		data.writeInt(appId);
		data.writeBytes(address);

		int channelsMask = 0;

		for(int channel : channels)
		{
			if(channel < 0 || channel > BtUtils.channelNumberMax)
				continue;

			// http://www.vipan.com/htdocs/bitwisehelp.html

			int channelBit = ipow(2, channel);

			channelsMask |= channelBit;
		}

		data.writeInt(channelsMask);

		return data.array();
	} // buildManufacturerData()

	public int getAppId()
	{
		return appId;
	}

	public byte[] getAddress()
	{
		return address;
	}

	public List<Integer> getChannels()
	{
		return channels;
	}
} // ManufacturerData

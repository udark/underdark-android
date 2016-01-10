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

package impl.underdark.transport.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.os.Build;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class BtUtils
{
	public static final int btAddressLen = 6; // bytes
	public static final int channelNumberMax = 30;

	public static String getAddressStringFromBytes(byte[] address)
	{
		if (address == null || address.length != 6) {
			return null;
		}
		return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
				address[0], address[1], address[2], address[3], address[4],
				address[5]);
	}

	public static byte[] getBytesFromAddress(String address)
	{
		if(address == null)
			return null;

		int i, j = 0;
		byte[] output = new byte[btAddressLen];
		for (i = 0; i < address.length(); i++) {
			if (address.charAt(i) != ':') {
				output[j] = (byte) Integer.parseInt(address.substring(i, i+2), 16);
				j++;
				i++;
			}
		}
		return output;
	}

	public static String gattStatusToString(int status)
	{
		String str = "";
		switch (status)
		{
			case BluetoothGatt.GATT_SUCCESS:
				str = "GATT_SUCCESS";
				break;
			case BluetoothGatt.GATT_READ_NOT_PERMITTED:
				str = "GATT_READ_NOT_PERMITTED";
				break;
			case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
				str = "GATT_WRITE_NOT_PERMITTED";
				break;
			case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
				str = "GATT_INSUFFICIENT_AUTHENTICATION";
				break;
			case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
				str = "GATT_REQUEST_NOT_SUPPORTED";
				break;
			case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
				str = "GATT_INSUFFICIENT_ENCRYPTION";
				break;
			case BluetoothGatt.GATT_INVALID_OFFSET:
				str = "GATT_INVALID_OFFSET";
				break;
			case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
				str = "GATT_INVALID_ATTRIBUTE_LENGTH";
				break;
			case BluetoothGatt.GATT_CONNECTION_CONGESTED:
				str = "GATT_CONNECTION_CONGESTED";
				break;
			case BluetoothGatt.GATT_FAILURE:
				str = "GATT_FAILURE";
				break;
		}

		return str;
	} // gattStatusToString()

	public static String getLocalAddress(Context context) {
		// http://stackoverflow.com/a/33749218
		// http://stackoverflow.com/a/34494459

		String address = "";

		if(Build.VERSION.SDK_INT <= 22)
		{
			address = BluetoothAdapter.getDefaultAdapter().getAddress();
		}
		else
		{
			address = android.provider.Settings.Secure.getString(
					context.getContentResolver(), "bluetooth_address"
			);
		}

		return address;
	}
} // BtUtils

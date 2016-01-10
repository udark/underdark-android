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

package impl.underdark.transport.bluetooth.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import impl.underdark.logging.Logger;

public class BtHacks
{
	public static int getChannelOfBluetoothServerSocket(BluetoothServerSocket socket)
	{
		Field[] f = socket.getClass().getDeclaredFields();

		int channel = -1;

		for (Field field : f)
		{
			if(!field.getName().equals("mChannel"))
				continue;

			field.setAccessible(true);
			try
			{
				channel = field.getInt(socket);
			}
			catch (IllegalArgumentException ex)
			{
				Logger.error("bt failed getChannelOfBluetoothServerSocket(): {}", ex);
				return -1;
			}
			catch (IllegalAccessException ex)
			{
				Logger.error("bt failed getChannelOfBluetoothServerSocket(): {}", ex);
				return -1;
			}
			finally
			{
				field.setAccessible(false);
			}

			break;
		}

		return channel;
	} // getChannelOfBluetoothServerSocket()

	public static BluetoothServerSocket listenUsingInsecureRfcommOn(BluetoothAdapter adapter, int channel)
			throws Throwable
	{
		try
		{
			Method method = adapter.getClass().getMethod("listenUsingInsecureRfcommOn", new Class[]{ int.class });
			boolean accessible = method.isAccessible();
			if(!accessible)
				method.setAccessible(true);

			BluetoothServerSocket socket = (BluetoothServerSocket) method.invoke(adapter, channel);

			if(!accessible)
				method.setAccessible(false);

			if(socket == null)
				throw new IOException("socket == null");

			return socket;
		}
		catch (NoSuchMethodException ex)
		{
			Logger.error("bt failed custom listenUsingInsecureRfcommOn(ch={}): NoSuchMethodException", channel);
			throw ex;
		}
		catch (IllegalAccessException ex)
		{
			Logger.error("bt failed custom listenUsingInsecureRfcommOn(ch={}): IllegalAccessException", channel);
			throw ex;
		}
		catch (InvocationTargetException ex)
		{
			//Logger.error("bt failed custom listenUsingInsecureRfcommOn(ch={}): {}", channel, ex.getCause());
			throw ex.getTargetException();
		}
	} // listenUsingInsecureRfcommOn()

	public static BluetoothSocket createInsecureRfcommSocket(BluetoothDevice device, int channel)
			throws Throwable
	{
		try
		{
			Method method = device.getClass().getMethod("createInsecureRfcommSocket", new Class[]{ int.class });
			boolean accessible = method.isAccessible();
			if(!accessible)
				method.setAccessible(true);

			BluetoothSocket socket = (BluetoothSocket) method.invoke(device, Integer.valueOf(channel));

			if(!accessible)
				method.setAccessible(false);

			if(socket == null)
				throw new IOException("socket == null");

			return socket;
		}
		catch (NoSuchMethodException ex)
		{
			Logger.error("bt failed custom createInsecureRfcommSocket(ch={}): NoSuchMethodException", channel);
			throw ex;
		}
		catch (IllegalAccessException ex)
		{
			Logger.error("bt failed custom createInsecureRfcommSocket(ch={}): IllegalAccessException", channel);
			throw ex;
		}
		catch (InvocationTargetException ex)
		{
			//Logger.error("bt failed custom createInsecureRfcommSocket(ch={}): {}", channel, ex.getCause());
			throw ex.getTargetException();
		}
	} // createInsecureRfcommSocket()
} // BtHacks

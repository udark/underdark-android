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
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;

import io.underdark.util.dispatch.DispatchQueue;

@TargetApi(21)
public class BleAdvCallback extends AdvertiseCallback
{
	public interface Listener
	{
		void onStartSuccess(AdvertiseSettings settingsInEffect);

		 void onStartFailure(int errorCode);
	}

	private Listener listener;
	private DispatchQueue queue;

	public BleAdvCallback(Listener listener, DispatchQueue queue)
	{
		this.listener = listener;
		this.queue = queue;
	}

	public static String errorCodeToString(int errorCode)
	{
		String str = "";
		switch (errorCode)
		{
			case AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE:
				str = "ADVERTISE_FAILED_DATA_TOO_LARGE";
				break;

			case AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
				str = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
				break;

			case AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED:
				str = "ADVERTISE_FAILED_ALREADY_STARTED";
				break;

			case AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR:
				str = "ADVERTISE_FAILED_INTERNAL_ERROR";
				break;

			case AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
				str = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
				break;
		}

		return str;
	}

	@Override
	public void onStartSuccess(final AdvertiseSettings settingsInEffect)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onStartSuccess(settingsInEffect);
			}
		});
	}

	@Override
	public void onStartFailure(final int errorCode)
	{
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onStartFailure(errorCode);
			}
		});
	}
} // BleAdvCallback

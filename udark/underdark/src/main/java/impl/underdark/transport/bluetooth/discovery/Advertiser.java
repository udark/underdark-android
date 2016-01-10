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

package impl.underdark.transport.bluetooth.discovery;

import java.util.List;

import impl.underdark.transport.bluetooth.discovery.ble.ManufacturerData;
import io.underdark.transport.TransportListener;

public interface Advertiser
{
	interface Listener
	{
		void onAdvertiseStarted(Advertiser advertiser);
		void onAdvertiseStopped(Advertiser advertiser, boolean error);

		ManufacturerData onAdvertisementDataRequested();
	}

	boolean isSupported();

	void startAdvertise(long durationMs);
	void stopAdvertise();

	void touch();
}

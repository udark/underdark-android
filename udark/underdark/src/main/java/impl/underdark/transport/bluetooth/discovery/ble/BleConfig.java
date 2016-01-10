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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BleConfig
{
	// https://www.bluetooth.org/en-us/specification/assigned-numbers/company-identifiers
	public static final int manufacturerId = 0x07AA;

	public static final int charactValueSizeMax = 20;

	public static final UUID stdClientCharacteristicConfigUuid =
			UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
	public static final UUID stdCharacteristicUserDescriptionUuid =
			UUID.fromString("00002901-0000-1000-8000-00805F9B34FB");
	public static final UUID stdCharacteristicPresentationFormatUuid =
			UUID.fromString("00002904-0000-1000-8000-00805F9B34FB");

	public static final UUID serviceUuid = UUID.fromString("771DDBB5-F7F5-4D9C-8A0A-7507A9E504FB");
	public static final UUID charactNodeIdUuid = UUID.fromString("8971EEE6-5D58-470D-93D1-5D549922AFC9");
	public static final UUID charactJackUuid = UUID.fromString("18D8B369-09D6-48C5-9843-413F9569663F");
	public static final UUID charactStreamUuid = UUID.fromString("935D922B-7753-4B0D-8395-325C209E951F");
	public static final UUID charactAddressUuid = UUID.fromString("D2CDD347-32BB-4C4B-BCEB-E77D25EEFA96");
} // BleConfig

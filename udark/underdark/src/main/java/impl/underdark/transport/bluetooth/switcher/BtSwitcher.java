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

package impl.underdark.transport.bluetooth.switcher;

import java.util.List;

import impl.underdark.protobuf.Frames;

public interface BtSwitcher
{
	interface Listener
	{
		void onMustConnectAddress(byte[] address, List<Integer> channels);
		void onMustDisconnectAddress(byte[] address);
		void onMustSendFrame(byte[] address, Frames.Frame frame);
	}

	void setMyAddress(byte[] address);

	void setLegacy(boolean legacy);

	Frames.Peer getPeerMe();

	void onAddressDiscovered(byte[] address, List<Integer> channels);

	void onPortsChanged(List<Integer> ports);

	void onLinkConnected(Frames.Peer connectedPeer);

	void onLinkDisconnected(byte[] peerAddress);

	void onPortsFrame(byte[] linkAddress, Frames.PortsFrame portsFrame);

	void onConnectedFrame(byte[] linkAddress, Frames.ConnectedFrame connectedFrame);

	void onDisconnectedFrame(byte[] linkAddress, Frames.DisconnectedFrame disconnectedFrame);
}

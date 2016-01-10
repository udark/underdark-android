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

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;

import impl.underdark.protobuf.Frames;
import impl.underdark.transport.bluetooth.BtUtils;

public class BtSwitcherDumb implements BtSwitcher
{
	private Listener listener;
	private Frames.Peer me;

	public BtSwitcherDumb(Listener listener)
	{
		this.listener = listener;
		this.me = Frames.Peer.newBuilder()
				.setAddress(ByteString.copyFrom(new byte[BtUtils.btAddressLen]))
				.addAllPorts(new ArrayList<Integer>())
				.setLegacy(false)
				.build();
	}


	@Override
	public void setMyAddress(byte[] address)
	{
		this.me = Frames.Peer.newBuilder(me)
				.setAddress(ByteString.copyFrom(address))
				.build();
	}

	@Override
	public void setLegacy(boolean legacy)
	{
		this.me = Frames.Peer.newBuilder(me)
				.setLegacy(legacy)
				.build();
	}

	@Override
	public Frames.Peer getPeerMe()
	{
		return this.me;
	}

	@Override
	public void onAddressDiscovered(byte[] address, List<Integer> channels)
	{
		listener.onMustConnectAddress(address, channels);
	}

	@Override
	public void onPortsChanged(List<Integer> ports)
	{
		this.me = Frames.Peer.newBuilder(me)
				.clearPorts()
				.addAllPorts(ports)
				.build();
	}

	@Override
	public void onLinkConnected(Frames.Peer connectedPeer)
	{

	}

	@Override
	public void onLinkDisconnected(byte[] peerAddress)
	{

	}

	@Override
	public void onPortsFrame(byte[] linkAddress, Frames.PortsFrame portsFrame)
	{

	}

	@Override
	public void onConnectedFrame(byte[] linkAddress, Frames.ConnectedFrame connectedFrame)
	{

	}

	@Override
	public void onDisconnectedFrame(byte[] linkAddress, Frames.DisconnectedFrame disconnectedFrame)
	{

	}
} // BtSwitcherDumb

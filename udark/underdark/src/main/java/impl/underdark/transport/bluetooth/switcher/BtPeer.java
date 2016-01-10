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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import impl.underdark.protobuf.Frames;
import impl.underdark.transport.bluetooth.BtUtils;

public class BtPeer
{
	private Frames.Peer me;
	private HashMap<String, Frames.Peer> peers = new HashMap<>();

	public BtPeer(Frames.Peer peer)
	{
		this.me = peer;
	}

	public byte[] getAddress()
	{
		return me.getAddress().toByteArray();
	}

	public boolean isConnectedToAddress(byte[] address)
	{
		for(Frames.Peer peer: this.peers.values())
		{
			if(peer.getAddress() != null
					&& Arrays.equals(peer.getAddress().toByteArray(), address)
					)
			{
				return true;
			}
		} // for

		return false;
	}

	public boolean isLegacy()
	{
		return me.getLegacy();
	}

	public List<Integer> getPorts()
	{
		return me.getPortsList();
	}

	public void setPorts(List<Integer> ports)
	{
		this.me =
				Frames.Peer.newBuilder(me)
						.clearPorts()
						.addAllPorts(ports)
						.build();
	}

	public void setPeerPorts(byte[] address, List<Integer> ports)
	{
		Frames.Peer peer = peers.get(BtUtils.getAddressStringFromBytes(address));
		if(peer == null)
			return;

		peer = Frames.Peer.newBuilder(peer)
				.clearPorts()
				.addAllPorts(ports)
				.build();
		peers.put(BtUtils.getAddressStringFromBytes(address), peer);
	}

	public Frames.Peer getPeerMe()
	{
		return this.me;
	}

	public List<Frames.Peer> getPeers()
	{
		return new ArrayList<>(this.peers.values());
	}

	public Frames.Peer getPeer(byte[] address)
	{
		return peers.get(BtUtils.getAddressStringFromBytes(address));
	}

	public void addPeer(Frames.Peer peer)
	{
		this.peers.put(
				BtUtils.getAddressStringFromBytes(peer.getAddress().toByteArray()),
				peer
				);
	}

	public Frames.Peer removePeer(byte[] address)
	{
		return this.peers.remove(BtUtils.getAddressStringFromBytes(address));
	}
}
// BtPeer

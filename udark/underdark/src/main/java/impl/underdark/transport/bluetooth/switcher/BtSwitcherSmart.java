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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import impl.underdark.logging.Logger;
import impl.underdark.protobuf.Frames;
import impl.underdark.transport.bluetooth.BtUtils;

public class BtSwitcherSmart implements BtSwitcher
{
	private static int connectionsCountMax = 2;

	private Listener listener;
	private Frames.Peer me;
	private HashMap<String, BtPeer> peers = new HashMap<>();

	public BtSwitcherSmart(Listener listener)
	{
		this.listener = listener;

		this.me =
		Frames.Peer.newBuilder()
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
	} // getLinksFrame

	private boolean shouldConnectToAddress(byte[] address, boolean legacy)
	{
		// Already connected directly.
		if(null != peers.get(BtUtils.getAddressStringFromBytes(address)))
			return false;

		int connectionsCount = 0;
		for(BtPeer peer : peers.values())
		{
			// If address is legacy — don't count non-legacy peers
			// towards connections limit.
			if(legacy && !peer.isLegacy())
				continue;

			if(peer.isConnectedToAddress(address))
				++connectionsCount;
		}

		if(connectionsCount >= connectionsCountMax)
			return false;

		return true;
	} // shouldConnectToAddress

	@Override
	public void onAddressDiscovered(byte[] address, List<Integer> channels)
	{
		if(!shouldConnectToAddress(address, false))
			return;

		listener.onMustConnectAddress(address, channels);
	}

	@Override
	public void onPortsChanged(List<Integer> ports)
	{
		this.me =
		Frames.Peer.newBuilder(me)
				.clearPorts()
				.addAllPorts(ports)
				.build();

		// Отправляем соседям инфу об измененных портах.
		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.PORTS);
		builder.setPorts(
				Frames.PortsFrame.newBuilder()
				.setAddress(this.me.getAddress())
				.addAllPorts(ports)
		);

		Frames.Frame frame = builder.build();

		for(BtPeer peer : this.peers.values())
		{
			if(Arrays.equals(peer.getAddress(),
					frame.getPorts().getAddress().toByteArray()))
				continue;

			listener.onMustSendFrame(peer.getAddress(), frame);
		} // for
	}

	@Override
	public void onLinkConnected(Frames.Peer connectedPeer)
	{
		// Мы приконнектились к новому соседу.
		BtPeer linkPeer = this.peers.get(BtUtils.getAddressStringFromBytes(connectedPeer.getAddress().toByteArray()));
		if(linkPeer != null)
		{
			Logger.error("bt sw connected to peer that already exists");
			return;
		}

		// Оповещаем нового соседа о текущих соседях.
		for(BtPeer peer : this.peers.values())
		{
			Frames.Frame.Builder builder = Frames.Frame.newBuilder();
			builder.setKind(Frames.Frame.Kind.CONNECTED);

			Frames.ConnectedFrame.Builder payload = Frames.ConnectedFrame.newBuilder();
			payload.setPeer(peer.getPeerMe());
			builder.setConnected(payload);

			listener.onMustSendFrame(connectedPeer.getAddress().toByteArray(), builder.build());
		}

		linkPeer = new BtPeer(connectedPeer);
		peers.put(BtUtils.getAddressStringFromBytes(connectedPeer.getAddress().toByteArray()), linkPeer);

		// Оповещаем соседей о коннекте нового.
		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.CONNECTED);

		Frames.ConnectedFrame.Builder payload = Frames.ConnectedFrame.newBuilder();
		payload.setPeer(connectedPeer);
		builder.setConnected(payload);

		Frames.Frame frame = builder.build();

		for(BtPeer peer : this.peers.values())
		{
			if(Arrays.equals(peer.getAddress(),
					connectedPeer.getAddress().toByteArray()))
				continue;

			listener.onMustSendFrame(peer.getAddress(), frame);
		} // for
	} // onLinkConnected

	@Override
	public void onLinkDisconnected(byte[] peerAddress)
	{
		// Мы отконнектились от соседа.
		BtPeer linkPeer = peers.remove(BtUtils.getAddressStringFromBytes(peerAddress));
		if(linkPeer == null)
		{
			Logger.error("bt sw disconnected from peer that doesn't exists");
			return;
		}

		// Оповещаем о дисконнекте соседей.
		Frames.Frame.Builder builder = Frames.Frame.newBuilder();
		builder.setKind(Frames.Frame.Kind.DISCONNECTED);

		Frames.DisconnectedFrame.Builder payload = Frames.DisconnectedFrame.newBuilder();
		payload.setAddress(ByteString.copyFrom(linkPeer.getAddress()));
		builder.setDisconnected(payload);

		Frames.Frame frame = builder.build();

		for(BtPeer peer : this.peers.values())
		{
			if(Arrays.equals(peer.getAddress(),
					linkPeer.getAddress()))
				continue;

			listener.onMustSendFrame(peer.getAddress(), frame);
		} // for

		// Пробуем приконнектиться к соседям дисконнектнувшегося соседа.

		for (Frames.Peer peersPeer : linkPeer.getPeers())
		{
			if (shouldConnectToAddress(peersPeer.getAddress().toByteArray(), peersPeer.getLegacy()))
			{
				Logger.debug("bt sw connecting {} {}ch because was connected to its disconnected peer",
						BtUtils.getAddressStringFromBytes(peersPeer.getAddress().toByteArray()),
						peersPeer.getPortsCount());

				listener.onMustConnectAddress(
						peersPeer.getAddress().toByteArray(),
						peersPeer.getPortsList()
				);
			}
		}
	} // onPeerDisconnected

	@Override
	public void onPortsFrame(byte[] linkAddress, Frames.PortsFrame portsFrame)
	{
		// Изменились свободные порты соседа или соседа соседа.

		if(Arrays.equals(portsFrame.getAddress().toByteArray(), this.me.getAddress().toByteArray()))
		{
			Logger.error("bt sw received my own PortsFrame");
			return;
		}

		BtPeer linkPeer = this.peers.get(BtUtils.getAddressStringFromBytes(linkAddress));
		if(linkPeer == null)
		{
			Logger.error("bt sw received PortsFrame from unknown peer");
			return;
		}

		if(Arrays.equals(linkAddress, portsFrame.getAddress().toByteArray()))
		{
			// Изменились порты соседа — рассылаем это остальным соседям.
			linkPeer.setPorts(portsFrame.getPortsList());

			for(BtPeer peer : this.peers.values())
			{
				if(Arrays.equals(peer.getAddress(),
						portsFrame.getAddress().toByteArray()))
					continue;

				Frames.Frame.Builder frame = Frames.Frame.newBuilder();
				frame.setKind(Frames.Frame.Kind.PORTS);
				frame.setPorts(portsFrame);

				listener.onMustSendFrame(peer.getAddress(), frame.build());
			} // for

			return;
		}
		else
		{
			// Изменились порты соседа соседа — пробуем к нему приконнектиться.
			linkPeer.setPeerPorts(portsFrame.getAddress().toByteArray(), portsFrame.getPortsList());

			Frames.Peer peer = linkPeer.getPeer(portsFrame.getAddress().toByteArray());
			if(peer == null)
				return;

			/*if(shouldConnectToAddress(peer.getAddress().toByteArray(), peer.getLegacy()))
			{
				Logger.debug("bt sw connecting {} {}ch because of free ports",
						BtUtils.getAddressStringFromBytes(peer.getAddress().toByteArray()),
						peer.getPortsCount()
						);

				listener.onMustConnectAddress(
						peer.getAddress().toByteArray(),
						peer.getPortsList()
				);
			}*/

			return;
		}
	} // onPortsFrame

	@Override
	public void onConnectedFrame(byte[] linkAddress, Frames.ConnectedFrame connectedFrame)
	{
		// К соседу приконнектился сосед
		if(Arrays.equals(connectedFrame.getPeer().getAddress().toByteArray(),
				this.me.getAddress().toByteArray()))
		{
			Logger.error("bt sw received my own ConnectedFrame");
			return;
		}

		BtPeer linkPeer = this.peers.get(BtUtils.getAddressStringFromBytes(linkAddress));
		if(linkPeer == null)
		{
			Logger.error("bt sw received ConnectedFrame from unknown peer");
			return;
		}

		linkPeer.addPeer(connectedFrame.getPeer());

		// Пробуем приконнектится к соседу.
		if(shouldConnectToAddress(
				connectedFrame.getPeer().getAddress().toByteArray(),
				connectedFrame.getPeer().getLegacy())
				)
		{
			Logger.debug("bt sw connecting {} {}ch because it discovered via peer",
					BtUtils.getAddressStringFromBytes(connectedFrame.getPeer().getAddress().toByteArray()),
					connectedFrame.getPeer().getPortsCount());

			listener.onMustConnectAddress(
					connectedFrame.getPeer().getAddress().toByteArray(),
					connectedFrame.getPeer().getPortsList()
					);
		}

	} // onConnectedFrame

	@Override
	public void onDisconnectedFrame(byte[] linkAddress, Frames.DisconnectedFrame disconnectedFrame)
	{
		// От соседа дисконнектнулся сосед.
		if(Arrays.equals(disconnectedFrame.getAddress().toByteArray(),
				this.me.getAddress().toByteArray()))
		{
			Logger.error("bt sw received my own DisconnectedFrame");
			return;
		}

		BtPeer linkPeer = this.peers.get(BtUtils.getAddressStringFromBytes(linkAddress));
		if(linkPeer == null)
		{
			Logger.error("bt sw received DisconnectedFrame from unknown peer");
			return;
		}

		Frames.Peer disconnectedPeerPeer = linkPeer.removePeer(disconnectedFrame.getAddress().toByteArray());
		if(disconnectedPeerPeer == null)
			return;

		// Пробуем приконнектиться к соседу соседа.

		if(shouldConnectToAddress(
				disconnectedPeerPeer.getAddress().toByteArray(),
				disconnectedPeerPeer.getLegacy())
				)
		{
			Logger.debug("bt sw connecting {} {}ch because its peer disconnected from it",
					BtUtils.getAddressStringFromBytes(disconnectedPeerPeer.getAddress().toByteArray()),
					disconnectedPeerPeer.getPortsCount());

			listener.onMustConnectAddress(
					disconnectedPeerPeer.getAddress().toByteArray(),
					disconnectedPeerPeer.getPortsList()
			);
		}

	} // onDisconnectedFrame

} // BtSwitcher

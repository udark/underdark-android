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

package impl.underdark.transport.aggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.underdark.transport.Link;

public class AggLink implements Link
{
	private AggTransport transport;
	private long nodeId;
	private List<Link> links = new ArrayList<>();

	public AggLink(AggTransport transport, long nodeId)
	{
		this.transport = transport;
		this.nodeId = nodeId;
	}

	@Override
	public String toString() {
		return "alink"+ "(" + links.size() + ")"
				+ " nodeId " + nodeId;
	}

	//region Link
	@Override
	public long getNodeId() {
		return nodeId;
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public void disconnect()
	{
		// Listener queue.

		transport.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				for(Link link : links)
				{
					link.disconnect();
				}
			}
		});
	}

	@Override
	public void sendFrame(final byte[] frameData)
	{
		// Listener queue.

		transport.queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				if (links.isEmpty())
					return;

				Link link = links.get(0);
				link.sendFrame(frameData);
			}
		});
	}
	//endregion

	public boolean isEmpty()
	{
		return links.isEmpty();
	}

	public boolean containsLink(Link link)
	{
		return links.contains(link);
	}

	public void addLink(Link link)
	{
		links.add(link);
		Collections.sort(links, new Comparator<Link>()
		{
			@Override
			public int compare(Link a, Link b)
			{
				if (a.getPriority() == b.getPriority())
					return 0;

				return (a.getPriority() < b.getPriority()) ? -1 : 1;
			}
		});
	}

	public void removeLink(Link link)
	{
		links.remove(link);
	}
} // AggLink

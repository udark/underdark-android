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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.underdark.transport.Link;
import io.underdark.transport.Transport;
import io.underdark.transport.TransportListener;
import io.underdark.util.dispatch.DispatchQueue;
import impl.underdark.logging.Logger;

public class AggTransport implements Transport, TransportListener
{
	private boolean running;

	private long nodeId;
	private TransportListener listener;
	private Context context;
	private Handler handler;

	public DispatchQueue queue;
	private List<Transport> transports = new ArrayList<>();
	private HashMap<Long, AggLink> linksConnected = new HashMap<>();

	private boolean foreground;

	public AggTransport(long nodeId,
						TransportListener listener,
						Handler handler,
						Context context)
	{
		this.nodeId = nodeId;
		this.listener = listener;
		this.handler = (handler == null) ? new Handler(Looper.getMainLooper()) : handler;
		this.context = context.getApplicationContext();

		this.queue = new DispatchQueue();
	}

	public void addTransport(Transport transport)
	{
		this.transports.add(transport);
	}

	//region Transport
	@Override
	public void start()
	{
		// Listener queue.
		if(running)
			return;

		running = true;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				for (Transport transport : transports)
				{
					transport.start();
				}

				if(foreground)
					onMainActivityResumed();
			}
		});
	}

	@Override
	public void stop()
	{
		// Listener queue.
		if(!running)
			return;

		running = false;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				for (Transport transport : transports)
				{
					transport.stop();
				}
			}
		});
	}

	@Override
	public void onMainActivityResumed()
	{
		// Any thread.
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				foreground = true;
			}
		});

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				if(!running)
					return;

				Logger.debug("agg foreground");

				for (Transport transport : transports)
				{
					transport.onMainActivityResumed();
				}
			}
		});
	}

	@Override
	public void onMainActivityPaused()
	{
		// Any thread.
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				foreground = false;
			}
		});

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				if(!running)
					return;

				Logger.debug("agg background");

				for (Transport transport : transports)
				{
					transport.onMainActivityPaused();
				}
			}
		});
	}

	//endregion

	//region TransportListener
	@Override
	public void transportNeedsActivity(Transport transport, final ActivityCallback callback)
	{
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportNeedsActivity(AggTransport.this, callback);
			}
		});
	}

	@Override
	public void transportLinkConnected(Transport transport, Link link)
	{
		// Transport queue.

		AggLink aggregate = linksConnected.get(link.getNodeId());
		boolean linkExisted = (aggregate != null && !aggregate.isEmpty());

		if(aggregate == null)
		{
			aggregate = new AggLink(this, link.getNodeId());
			linksConnected.put(link.getNodeId(), aggregate);
		}

		aggregate.addLink(link);

		if(!linkExisted)
		{
			final AggLink finalLink = aggregate;
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.transportLinkConnected(AggTransport.this, finalLink);
				}
			});
		}
	} // transportLinkConnected()

	@Override
	public void transportLinkDisconnected(Transport transport, Link link)
	{
		// Transport queue.
		AggLink aggregate = linksConnected.get(link.getNodeId());
		if(aggregate == null)
			return;

		if(aggregate.containsLink(link))
			aggregate.removeLink(link);

		if(aggregate.isEmpty())
		{
			linksConnected.remove(link.getNodeId());

			final AggLink finalLink = aggregate;
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					listener.transportLinkDisconnected(AggTransport.this, finalLink);
				}
			});
		}
	} // transportLinkDisconnected()

	@Override
	public void transportLinkDidReceiveFrame(Transport transport, Link link, final byte[] frameData)
	{
		// Transport queue.
		AggLink aggregate = linksConnected.get(link.getNodeId());
		if(aggregate == null)
		{
			Logger.error("Aggregate doesn't exist for " + link.toString());
			return;
		}

		if(!aggregate.containsLink(link))
		{
			Logger.error("Aggregate doesn't contain " + link.toString());
			return;
		}

		final AggLink finalLink = aggregate;
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				listener.transportLinkDidReceiveFrame(AggTransport.this, finalLink, frameData);
			}
		});
	} // transportLinkDidReceiveFrame
	//endregion

} // AggTransport

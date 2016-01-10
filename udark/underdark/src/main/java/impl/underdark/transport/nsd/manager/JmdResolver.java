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

package impl.underdark.transport.nsd.manager;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

public class JmdResolver implements BonjourResolver, ServiceListener
{
	private final String serviceType;
	private final String serviceName;
	private Listener listener;
	private DispatchQueue queue;
	private Context context;

	private boolean running;
	private WifiManager manager;
	private WifiManager.MulticastLock lock;
	private JmDNS jmdns;

	public JmdResolver(
			String serviceTypeWithoutLocal,
			String serviceName,
			Listener listener,
			DispatchQueue queue,
			Context context)
	{
		this.serviceType = serviceTypeWithoutLocal + "local.";
		this.serviceName = serviceName;
		this.listener = listener;
		this.queue = queue;
		this.context = context;
	}

	//region BonjourResolver
	@Override
	public void start(InetAddress address, int port)
	{
		if(!startJmdns(address))
			return;

		startResolveInternal(address);
		startPublishInternal(address, port);
	}

	@Override
	public void startPublishOnly(InetAddress address, int port)
	{
		if(!startJmdns(address))
			return;

		startPublishInternal(address, port);
	}

	@Override
	public void startResolveOnly(InetAddress address)
	{
		if(!startJmdns(address))
			return;

		startResolveInternal(address);
	}

	@Override
	public void stop()
	{
		if(!running)
			return;

		running = false;

		jmdns.removeServiceListener(serviceType, this);
		jmdns.unregisterAllServices();
		try
		{
			jmdns.close();
		} catch (Exception ex) {
			Logger.error("jmdns failed jmdns.close()", ex);
		}

		jmdns = null;

		lock.release();

		Logger.debug("jmdns stopped");
	} // stop()

	private void startPublishInternal(InetAddress address, int port)
	{
		ServiceInfo serviceInfo = ServiceInfo.create(
				serviceType,
				serviceName,
				port,
				"Underdark Service"
		);

		try
		{
			jmdns.registerService(serviceInfo);
		}
		catch (IOException ex)
		{
			Logger.error("jmd failed registerService()", ex);
		}
	}

	private void startResolveInternal(InetAddress address)
	{
		jmdns.addServiceListener(serviceType, this);
	}

	private boolean startJmdns(InetAddress address)
	{
		manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

		lock = manager.createMulticastLock("BnjTransport");
		lock.setReferenceCounted(true);

		//AndroidLoggingHandler.reset(new AndroidLoggingHandler());
		//java.util.logging.Logger.getLogger("javax.jmdns").setLevel(Level.FINEST);

		try
		{
			lock.acquire();
			jmdns = JmDNS.create(address);
			Logger.debug("jmdns bind address {}", address);
		}
		catch (IOException ex)
		{
			lock.release();
			Logger.error("jmdns failed jmdns.create() {}", ex);
			return false;
		}

		running = true;
		Logger.debug("jmdns started");

		return true;
	}
	//endregion

	//ServiceListener
	@Override
	public void serviceAdded(ServiceEvent event)
	{
		// Any thread.
		if(event.getName().equals(this.serviceName))
			return;

		//Logger.debug("jmd serviceAdded '{}' '{}'", event.getName(), event.getType());
		jmdns.requestServiceInfo(event.getType(), event.getName());
	}

	@Override
	public void serviceRemoved(ServiceEvent event)
	{
		// Any thread.
		//Logger.debug("jmd serviceRemoved '{}' '{}'", event.getName(), event.getType());
	}

	@Override
	public void serviceResolved(final ServiceEvent event)
	{
		// Any thread.
		if(event.getName().equals(this.serviceName))
			return;

		/*Logger.debug("jmd serviceResolved '{}' {}:{}",
				event.getName(),
				event.getInfo().getHostAddress(),
				event.getInfo().getPort()
		);*/

		final String name = event.getName();
		final String address = event.getInfo().getHostAddress();
		final int port = event.getInfo().getPort();

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onBonjourServiceResolved(name, address, port);
			}
		});
	} // serviceResolved()
	//endregion
} // JmdResolver

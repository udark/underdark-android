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

// Android Bonjour problems:
// http://stackoverflow.com/questions/4656379/bonjour-implementation-on-android

// Service resolve can be only one at time:
// http://stackoverflow.com/questions/24665342/nsdmanager-doesnt-resolve-multiple-discovered-services

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdServiceInfo;

import java.net.InetAddress;
import java.util.HashMap;

import io.underdark.util.dispatch.DispatchQueue;
import impl.underdark.logging.Logger;

public class NsdResolver implements BonjourResolver, NsdManager.ResolveListener
{
	private volatile boolean running;

	private final String serviceType;
	private final String serviceName;

	private InetAddress address;
	private int port;

	private BroadcastReceiver broadcastReceiver;

	private BonjourResolver.Listener  listener;
	private DispatchQueue queue;
	private Context context;
	private NsdManager nsdManager;

	private NsdManager.RegistrationListener registrationListener;
	private boolean registrationStopping;
	private NsdServiceInfo serviceRegistered;

	private DiscoveryListener discoveryListener;
	private boolean discoveryStopping;

	private HashMap<String, NsdServiceInfo> servicesToResolve = new HashMap<>();

	public NsdResolver(
			String serviceTypeWithoutLocal,
			String serviceName,
			BonjourResolver.Listener listener,
			DispatchQueue queue,
			Context context)
	{
		this.serviceType = serviceTypeWithoutLocal;
		this.serviceName = serviceName;

		this.listener = listener;
		this.queue = queue;
		this.context = context;
		this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
	}

	//region BonjourResolver
	@Override
	public void start(InetAddress address, int port)
	{
		// Node queue.
		if(this.running)
			return;

		this.running = true;

		this.address = address;
		this.port = port;

		registerBroadcastReceiver();
		registerService();
		startDiscovery();
	}

	@Override
	public void startPublishOnly(InetAddress address, int port)
	{
	}

	@Override
	public void startResolveOnly(InetAddress address)
	{
	}

	@Override
	public void stop()
	{
		// Node queue.
		if(!this.running)
			return;

		this.running = false;

		unregisterBroadcastReceiver();
		stopDiscovery();
		unregisterService();
	}
	//endregion

	//region BroadcastReceiver
	private void registerBroadcastReceiver()
	{
		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				NsdResolver.this.onReceive(context, intent);
			}
		};

		IntentFilter filter = new IntentFilter(NsdManager.ACTION_NSD_STATE_CHANGED);
		context.registerReceiver(broadcastReceiver, filter, null, queue.getHandler());
	}

	private void unregisterBroadcastReceiver()
	{
		context.unregisterReceiver(broadcastReceiver);
		broadcastReceiver = null;
	}

	public void onReceive(Context context, Intent intent)
	{
		// Transport queue.

		if(!intent.getAction().equals(NsdManager.ACTION_NSD_STATE_CHANGED))
		{
			onReceive_ACTION_NSD_STATE_CHANGED(context, intent);
			return;
		}
	}

	private void onReceive_ACTION_NSD_STATE_CHANGED(Context context, Intent intent)
	{
		// Transport queue.

		if(!intent.getAction().equals(NsdManager.ACTION_NSD_STATE_CHANGED))
			return;

		int state = intent.getIntExtra(NsdManager.EXTRA_NSD_STATE, 0);
		if(state == 0)
			return;

		if(state == NsdManager.NSD_STATE_ENABLED)
		{
			Logger.debug("nsd state enabled");
			registerService();
			startDiscovery();
		}
		else if (state == NsdManager.NSD_STATE_DISABLED)
		{
			Logger.debug("nsd state disabled");
			stopDiscovery();
			unregisterService();
		}
	}
	//endregion

	//region Registration
	private void registerService()
	{
		// Node queue.
		if(registrationListener != null || registrationStopping)
			return;

		serviceRegistered = new NsdServiceInfo();
		serviceRegistered.setServiceName(serviceName);
		serviceRegistered.setServiceType(serviceType);
		serviceRegistered.setPort(port);

		registrationListener = new NsdManager.RegistrationListener() {
			@Override
			public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				NsdResolver.this.onRegistrationFailed(serviceInfo, errorCode);
			}

			@Override
			public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				NsdResolver.this.onUnregistrationFailed(serviceInfo, errorCode);
			}

			@Override
			public void onServiceRegistered(NsdServiceInfo serviceInfo) {
				NsdResolver.this.onServiceRegistered(serviceInfo);
			}

			@Override
			public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
				NsdResolver.this.onServiceUnregistered(serviceInfo);
			}
		};

		nsdManager.registerService(
				serviceRegistered,
				NsdManager.PROTOCOL_DNS_SD,
				NsdResolver.this.registrationListener);
	} // registerService

	private void unregisterService()
	{
		// Node queue.
		if(registrationListener == null || registrationStopping)
			return;

		registrationStopping = true;

		nsdManager.unregisterService(registrationListener);
	}
	//endregion

	public void startDiscovery()
	{
		// Queue.
		if(discoveryListener != null || discoveryStopping)
			return;

		discoveryListener = new DiscoveryListener() {
			@Override
			public void onStartDiscoveryFailed(String serviceType, int errorCode) {
				NsdResolver.this.onStartDiscoveryFailed(serviceType, errorCode);
			}

			@Override
			public void onStopDiscoveryFailed(String serviceType, int errorCode) {
				NsdResolver.this.onStopDiscoveryFailed(serviceType, errorCode);
			}

			@Override
			public void onDiscoveryStarted(String serviceType) {
				NsdResolver.this.onDiscoveryStarted(serviceType);
			}

			@Override
			public void onDiscoveryStopped(String serviceType) {
				NsdResolver.this.onDiscoveryStopped(serviceType);
			}

			@Override
			public void onServiceFound(NsdServiceInfo serviceInfo) {
				NsdResolver.this.onServiceFound(serviceInfo);
			}

			@Override
			public void onServiceLost(NsdServiceInfo serviceInfo) {
				NsdResolver.this.onServiceLost(serviceInfo);
			}
		};

		nsdManager.discoverServices(
				serviceType,
				NsdManager.PROTOCOL_DNS_SD,
				discoveryListener
		);
	}

	public void stopDiscovery()
	{
		// Queue.
		if(discoveryListener == null || discoveryStopping)
			return;

		discoveryStopping = true;

		nsdManager.stopServiceDiscovery(discoveryListener);
	}

	//region RegistrationListener
	public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode)
	{
		// NSDManager thread.
		Logger.error("nsd service registration failed: errorCode = {}", errorCode);
		this.serviceRegistered = null;
		this.registrationListener = null;
		this.registrationStopping = false;
	}

	public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode)
	{
		// NSDManager thread.
		Logger.error("nsd service unregistration failed: errorCode = {}", errorCode);
		this.serviceRegistered = null;
		this.registrationListener = null;
		this.registrationStopping = false;
	}

	public void onServiceRegistered(NsdServiceInfo serviceInfo)
	{
		// NSDManager thread.
		Logger.debug("onServiceRegistered");
	}

	public void onServiceUnregistered(NsdServiceInfo serviceInfo)
	{
		// NSDManager thread.
		Logger.debug("onServiceUnregistered");
		this.serviceRegistered = null;
		this.registrationListener = null;
		this.registrationStopping = false;
	}
	//endregion

	//region Resolving
	private void resolve(NsdServiceInfo serviceInfo)
	{
		// Transport queue

		if(servicesToResolve.get(serviceInfo.getServiceName()) != null)
			return;

		if(servicesToResolve.isEmpty())
		{
			servicesToResolve.put(serviceInfo.getServiceName(), serviceInfo);
			nsdManager.resolveService(serviceInfo, this);
			return;
		}

		servicesToResolve.put(serviceInfo.getServiceName(), serviceInfo);
	}

	private void resolveNext(NsdServiceInfo serviceInfo)
	{
		// Transport queue.

		servicesToResolve.remove(serviceInfo.getServiceName());

		if(servicesToResolve.isEmpty())
			return;

		serviceInfo = servicesToResolve.values().iterator().next();
		nsdManager.resolveService(serviceInfo, this);
	}

	@Override
	public void onResolveFailed(final NsdServiceInfo serviceInfo, int errorCode)
	{
		// NSDManager thread.

		queue.dispatch(new Runnable() {
			@Override
			public void run() {
				resolveNext(serviceInfo);
			}
		});
	}

	@Override
	public void onServiceResolved(final NsdServiceInfo serviceInfo)
	{
		// NSDManager thread.

		Logger.debug("onServiceResolved"
						+ " name:" + serviceInfo.getServiceName()
						+ " type:" + serviceInfo.getServiceType()
						+ " host:" + serviceInfo.getHost().getHostAddress()
						+ " port:" + serviceInfo.getPort()
		);

		queue.dispatch(new Runnable() {
			@Override
			public void run() {
				resolveNext(serviceInfo);
			}
		});

		final String name = serviceInfo.getServiceName();
		final InetAddress address = serviceInfo.getHost();
		final int port = serviceInfo.getPort();

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onBonjourServiceResolved(name, address.getHostAddress(), port);
			}
		});
	} // onServiceResolved()
	//endregion

	//region DiscoveryListener
	public void onStartDiscoveryFailed(String serviceType, int errorCode)
	{
		// NSDManager thread.

		Logger.debug("nsd onStartDiscoveryFailed: errorCode = {}", errorCode);
		discoveryListener = null;
		discoveryStopping = false;
	}

	public void onStopDiscoveryFailed(String serviceType, int errorCode)
	{
		// NSDManager thread.

		Logger.debug("nsd onStopDiscoveryFailed: errorCode = {}", errorCode);
		discoveryListener = null;
		discoveryStopping = false;
	}

	public void onDiscoveryStarted(String serviceType)
	{
		// NSDManager thread.

		Logger.debug("onDiscoveryStarted");
	}

	public void onDiscoveryStopped(String serviceType)
	{
		// NSDManager thread.

		Logger.debug("onDiscoveryStopped");
		discoveryListener = null;
		discoveryStopping = false;
	}

	public void onServiceFound(final NsdServiceInfo serviceInfo)
	{
		// NSDManager thread.

		// Do not resolve and connect to ourselves.
		if(this.serviceName.equals(serviceInfo.getServiceName()))
			return;

		Logger.debug("onServiceFound"
						+ " name:" + serviceInfo.getServiceName()
						+ " type:" + serviceInfo.getServiceType()
		);

		queue.dispatch(new Runnable() {
			@Override
			public void run() {
				resolve(serviceInfo);
			}
		});
	} // onServiceFound

	public void onServiceLost(final NsdServiceInfo serviceInfo)
	{
		// NSDManager thread.

		Logger.debug("onServiceLost " + serviceInfo.getServiceName());

		queue.dispatch(new Runnable() {
			@Override
			public void run() {
				servicesToResolve.remove(serviceInfo.getServiceName());
			}
		});
	}
	//endregion

} // NsdTransport

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

package impl.underdark.transport.bluetooth.discovery.idle;

import impl.underdark.transport.bluetooth.discovery.Advertiser;
import impl.underdark.logging.Logger;
import io.underdark.util.dispatch.DispatchQueue;

public class IdleAdvertiser implements Advertiser
{
	private Advertiser.Listener listener;
	private DispatchQueue queue;

	private boolean running;
	private Runnable stopCommand;

	public IdleAdvertiser(Advertiser.Listener listener, DispatchQueue queue)
	{
		this.listener = listener;
		this.queue = queue;
	}

	//region Advertiser
	@Override
	public boolean isSupported()
	{
		return true;
	}

	@Override
	public void startAdvertise(long durationMs)
	{
		if(running)
			return;

		running = true;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				//Logger.debug("bt idle started");
				listener.onAdvertiseStarted(IdleAdvertiser.this);
			}
		});

		stopCommand =
		queue.dispatchAfter(durationMs, new Runnable()
		{
			@Override
			public void run()
			{
				stopCommand = null;
				stopAdvertise();
			}
		});
	}

	@Override
	public void stopAdvertise()
	{
		if(!running)
			return;

		running = false;

		queue.cancel(stopCommand);
		stopCommand = null;

		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				//Logger.debug("bt idle stopped");
				listener.onAdvertiseStopped(IdleAdvertiser.this, false);
			}
		});
	}

	@Override
	public void touch()
	{
	}
	//endregion
} // IdleAdvertiser

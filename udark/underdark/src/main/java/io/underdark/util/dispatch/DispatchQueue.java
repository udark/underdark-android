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

package io.underdark.util.dispatch;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import impl.underdark.logging.Logger;

/**
 * Created by virl on 22/04/15.
 */
public class DispatchQueue implements Executor
{
	public static DispatchQueue main = new DispatchQueue(new Handler(Looper.getMainLooper()));

	public static ExecutorService newSerialExecutor(final String name)
	{
		Executor executor = Executors.newSingleThreadExecutor(new ThreadFactory()
		{
			@Override
			public Thread newThread(Runnable r)
			{
				Thread thread = new Thread(r);
				thread.setName(name);
				thread.setDaemon(true);
				return thread;
			}
		});

		return new SerialExecutorService(executor);
	}

	private Handler handler;

	public DispatchQueue(Handler handler)
	{
		this.handler = handler;
	}

	public DispatchQueue()
	{
		this((String)null);
	}

	public DispatchQueue(String name)
	{
		final CountDownLatch latch = new CountDownLatch(1);

		Thread thread = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				Looper.prepare();
				;

				DispatchQueue.this.handler = new Handler();
				latch.countDown();

				Looper.loop();

				//Logger.debug("DispatchQueue finished");
			}
		});

		if(name != null)
			thread.setName(name);

		thread.setDaemon(true);
		thread.start();

		try
		{
			latch.await(5, TimeUnit.SECONDS);
		}
		catch(InterruptedException ex)
		{
			Logger.error("Dispatch thread start failed.");
			return;
		}
	} // DispatchQueue()

	public void close()
	{
		this.handler.getLooper().quit();
	}

	public Handler getHandler()
	{
		return handler;
	}

	public void execute(Runnable runnable)
	{
		dispatch(runnable);
	}

	public boolean dispatch(Runnable runnable)
	{
		if(handler == null)
			return false;

		return handler.post(runnable);
	} // dispatch

	public Runnable dispatchAfter(long delayMs, Runnable runnable)
	{
		if(handler == null)
			return runnable;

		handler.postDelayed(runnable, delayMs);

		return runnable;
	}

	public void cancel(Runnable runnable)
	{
		if(handler == null)
			return;

		if(runnable == null)
			return;

		handler.removeCallbacks(runnable);
	}
} // DispatchQueue

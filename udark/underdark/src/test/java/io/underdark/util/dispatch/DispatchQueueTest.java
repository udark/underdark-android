package io.underdark.util.dispatch;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.underdark.BuildConfig;

@Config(constants = BuildConfig.class, sdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class DispatchQueueTest
{
	@Test
	public void testDispatch() throws InterruptedException
	{
		DispatchQueue queue = new DispatchQueue();

		final CountDownLatch latch = new CountDownLatch(1);
		queue.dispatch(new Runnable()
		{
			@Override
			public void run()
			{
				latch.countDown();
			}
		});

		ShadowLooper shadowLooper = Shadows.shadowOf(queue.getHandler().getLooper());
		shadowLooper.unPause();

		Assert.assertTrue("queue dispatch timeout", latch.await(1000, TimeUnit.MILLISECONDS));
		queue.close();
	} // testDispatch
}

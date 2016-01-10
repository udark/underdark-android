/*
 * Copyright (c) 2015. Vladimir Shabanov aka Virl (virlof@gmail.com ; http://telegram.me/virlof )
 * You may NOT use, copy, store, compile or license this code without written permission from the author.
 */

package impl.underdark.transport.bluetooth.discovery.ble;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.underdark.BuildConfig;


@Config(constants = BuildConfig.class, sdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class ManufacturerDataTest
{
	@Before
	public void setUp() throws Exception
	{
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void testCreateAndParse() throws Exception
	{
		int appId = 543;
		byte[] address = new byte[6];
		new Random().nextBytes(address);
		List<Integer> channels = new ArrayList<>();

		ManufacturerData mdata = ManufacturerData.create(appId, address, channels);
		Assert.assertNotNull(mdata);

		byte[] data = mdata.build();
		Assert.assertNotNull(data);

		ManufacturerData pdata = ManufacturerData.parse(data);
		Assert.assertNotNull(pdata);

		Assert.assertEquals(appId, pdata.getAppId());
		Assert.assertTrue(Arrays.equals(address, pdata.getAddress()));
		Assert.assertTrue(Arrays.equals(channels.toArray(), pdata.getChannels().toArray()));
	} // testCreateAndParse()
} // ManufacturerDataTest

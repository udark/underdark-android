package impl.underdark.transport.nsd;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.underdark.BuildConfig;
import io.underdark.Underdark;
import io.underdark.util.Identity;
import io.underdark.util.dispatch.DispatchQueue;

@Config(constants = BuildConfig.class, sdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class NsdServerTest implements NsdServer.Listener
{
	CountDownLatch latchAccept;
	CountDownLatch latchConnect;
	CountDownLatch latchFrames;
	CountDownLatch latchDisconnect;

	ExecutorService queue;
	NsdServer server1;
	NsdServer server2;

	InetAddress address;
	int port;

	List<NsdLink> links = new ArrayList<>();

	private List<byte[]> framesSent = new ArrayList<>();
	private List<byte[]> framesReceived = new ArrayList<>();

	@Before
	public void setUp() throws Exception
	{
		queue = DispatchQueue.newSerialExecutor("NsdServerTest Queue");

		Underdark.configureLogging(true);
	}

	@After
	public void tearDown() throws Exception
	{
		if(server1 != null)
			server1.stopAccepting();

		if(server2 != null)
			server2.stopAccepting();

		queue.shutdown();
	}

	@Test
	public void testServer() throws Exception
	{
		accept();
		connect();
		frames();
		disconnect();
	} // testConnect()

	private void accept() throws InterruptedException
	{
		latchAccept = new CountDownLatch(1);
		queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				server1 = new NsdServer(Identity.generateNodeId(), NsdServerTest.this, queue);
				server1.startAccepting(BnjUtil.getLocalIpAddress());
			}
		});

		Assert.assertTrue("accept() timeout", latchAccept.await(1500, TimeUnit.MILLISECONDS));
	} // accept

	private void connect() throws InterruptedException
	{
		latchConnect = new CountDownLatch(2);
		queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				server2 = new NsdServer(Identity.generateNodeId(), NsdServerTest.this, queue);
				server2.connect(server1.getNodeId(), address, port);
			}
		});

		Assert.assertTrue("connect() timeout", latchConnect.await(1500, TimeUnit.MILLISECONDS));
	} // connect

	private void genFrame(int size)
	{
		byte[] frameData;

		//frameData = new byte[new Random().nextInt(1024)];
		frameData = new byte[size];
		new Random().nextBytes(frameData);
		framesSent.add(frameData);
	}

	private void frames() throws InterruptedException
	{
		genFrame(6000);
		genFrame(500);
		genFrame(10);
		genFrame(1 * 1024 * 1024);
		genFrame(4000);

		latchFrames = new CountDownLatch(framesSent.size());

		queue.execute(new Runnable()
		{
			@Override
			public void run()
			{
				for(byte[] data : framesSent)
				{
					links.get(0).sendFrame(data);
				}
			}
		});

		Assert.assertTrue("sendFrame() timeout", latchFrames.await(2000, TimeUnit.MILLISECONDS));

		Assert.assertEquals("frames sent and received count not equal", framesSent.size(), framesReceived.size());

		for(int i = 0; i < framesSent.size(); ++i)
		{
			Assert.assertArrayEquals(framesSent.get(i), framesReceived.get(i));
		}
	} // frames

	private void disconnect() throws InterruptedException
	{
		latchDisconnect = new CountDownLatch(2);
		Assert.assertFalse("disconnect due to heartbeat timeout", latchDisconnect.await(
				(long)(io.underdark.Config.bnjTimeoutInterval * 1.1), TimeUnit.MILLISECONDS));
	} // disconnect

	//region NsdServer.Listener
	@Override
	public void onServerAccepting(InetAddress address, int port)
	{
		Assert.assertNotNull("address is null", address);
		Assert.assertNotSame("port is 0", 0, port);

		this.address = address;
		this.port = port;
		latchAccept.countDown();
	}

	@Override
	public void onServerError(InetAddress address)
	{
		Assert.fail("nsd onServerError() called");
	}

	@Override
	public void linkConnected(NsdLink link)
	{
		links.add(link);
		latchConnect.countDown();
	}

	@Override
	public void linkDisconnected(NsdLink link)
	{
		links.remove(link);

		if(latchDisconnect != null)
			latchDisconnect.countDown();
	}

	@Override
	public void linkDidReceiveFrame(NsdLink link, byte[] frameData)
	{
		if(link == links.get(0))
			return;

		framesReceived.add(frameData);
		latchFrames.countDown();
	}
	//endregion
} // NsdServerTest

package impl.underdark.licensing;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import io.underdark.BuildConfig;

@Config(constants = BuildConfig.class, sdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class LicesnerTest
{
	Licenser licenser;

	@Before
	public void setUp() throws Exception
	{
		licenser = new Licenser(RuntimeEnvironment.application);
	}

	@After
	public void tearDown() throws Exception
	{
	}

	@Test
	public void testNullLicense()
	{
		Assert.assertFalse("Null license accepted", licenser.checkLicense());

		licenser.setLicense(null);
		Assert.assertFalse("Null license accepted", licenser.checkLicense());
	}

	@Test
	public void testInvalidLicense()
	{
		licenser.setLicense("IAMBADBADLICENSE");

		Assert.assertFalse("Invalid license accepted", licenser.checkLicense());
	}

	@Test
	public void testRightLicense()
	{
		licenser.setLicense("F4DF0CDA-7A3B-4882-A8FD-C09DB76DF63B");

		Assert.assertTrue("Valid license rejected", licenser.checkLicense());
	}
} // LicesnerTest

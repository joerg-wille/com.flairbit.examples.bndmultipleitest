package com.flairbit.itest2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.apache.karaf.features.FeaturesService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.ServiceTracker;

public class IntegrationTest {

	private static final BundleContext context = FrameworkUtil.getBundle(IntegrationTest.class).getBundleContext();

	@BeforeClass
	public static void beforeClass() throws Exception {
		assertNotNull(context);

		// Install CXF feature in Karaf:
		FeaturesService fs = waitForReference(context, FeaturesService.class, 1000L);
		fs.addRepository(URI.create("mvn:org.apache.karaf.features/standard/4.0.6/xml/features"));
		fs.addRepository(URI.create("mvn:org.apache.cxf.karaf/apache-cxf/3.1.7/xml/features"));
		fs.installFeature("cxf");

		// Force bundle transition from RESOLVED to STARTED no matter what the bundle start level is set to:
		caffeinateBundles(context);
	}

	@Test
	public void test() throws Exception {
		int retries = 10;
		Exception deferredException = null;
		ConfigurationAdmin confAdmin = waitForReference(context, ConfigurationAdmin.class, 1000L);
		do {
			Thread.sleep(5000L);  

			Configuration httpServiceConfig = confAdmin.getConfiguration("org.ops4j.pax.web");
			Integer httpPort = Integer.parseInt((String) httpServiceConfig.getProperties().get("org.osgi.service.http.port"));

			URL url = new URL("http://localhost:" + httpPort + "/cxf/");
			String response = null;
			try {
				response = httpGet(url);
			} catch (Exception ex) {
				deferredException = ex;
				continue;
			}
			assertTrue(response.matches(".*<title>CXF - Service list</title>.+<body>.+No services have been found..+?</body>.*"));
			break;
		} while (--retries > 0);

		if (retries <= 0) {
			throw new Exception(deferredException);
		}
	}

	private static <T> T waitForReference(BundleContext context, Class<T> clazz, long timeout) throws InterruptedException {
		ServiceTracker<T, ?> st = new ServiceTracker<>(context, clazz.getName(), null);
		st.open();
		return clazz.cast(st.waitForService(timeout));
	}

	private static String httpGet(URL url) throws Exception {
		StringBuilder result = new StringBuilder();
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = rd.readLine()) != null) {
			result.append(line);
		}
		rd.close();
		return result.toString();
	}

	private static void caffeinateBundles(BundleContext context) {
		for (Bundle b : context.getBundles()) {
			adjustStartLevel(b);
		}
		BundleTracker<Object> tracker = new BundleTracker<Object>(context, Bundle.STARTING, null) {
			public Object addingBundle(Bundle bundle, BundleEvent event) {
				adjustStartLevel(bundle);

				if (Constants.ACTIVATION_LAZY.equals(bundle.getHeaders().get(Constants.BUNDLE_ACTIVATIONPOLICY))) {
					try {
						bundle.start();
					} catch (BundleException e) { }
					return bundle;
				}
				return null;
			}
		};
		tracker.open();
	}

	private static void adjustStartLevel(Bundle bundle) {
		BundleStartLevel bsl = bundle.adapt(BundleStartLevel.class); 
		if (bundle.getBundleId() == 0 || // skip system bundle
				bsl.getStartLevel() <= 1) 
			return;
		bsl.setStartLevel(1);
	}
}

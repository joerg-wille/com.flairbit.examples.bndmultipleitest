package com.flairbit.itest1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.Arrays;

import org.apache.karaf.features.FeaturesService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.ServiceTracker;

public class IntegrationTest {

	private static LogService log;

	private static BundleContext bcontext = FrameworkUtil.getBundle(IntegrationTest.class).getBundleContext();

	@BeforeClass
	public static void load() throws Exception {

		log = waitForReference(bcontext, LogService.class, 1000L);

		log.log(LogService.LOG_INFO, "loading test...");

		assertNotNull(bcontext);

		// Install CXF feature in Karaf:
		FeaturesService fs = waitForReference(bcontext, FeaturesService.class, 1000L);

		fs.addRepository(URI.create("mvn:org.apache.karaf.features/standard/4.0.8/xml/features"));
		fs.addRepository(URI.create("mvn:org.apache.karaf.features/framework/4.0.8/xml/features"));
		fs.addRepository(URI.create("mvn:org.apache.karaf.features/standard/4.0.8/xml/features"));
		fs.addRepository(URI.create("mvn:org.apache.karaf.features/spring/4.0.8/xml/features"));
		fs.addRepository(URI.create("mvn:org.apache.karaf.features/enterprise/4.0.8/xml/features"));

		fs.installFeature("scr");
		fs.installFeature("aries-blueprint");
		fs.installFeature("jpa");
		fs.installFeature("openjpa");
		fs.installFeature("jdbc");		
		fs.installFeature("pax-jdbc-config");
		fs.installFeature("pax-jdbc");
		fs.installFeature("pax-jdbc-pool-dbcp2");
		fs.installFeature("pax-jdbc-postgresql");

		// Force bundle transition from RESOLVED to STARTED no matter what the bundle start level is set to:
		caffeinateBundles(bcontext);

		log.log(LogService.LOG_INFO, "loading test done.");
	}

	@Test
	public void test() throws Exception {
		String filter = "(objectClass=org.osgi.service.jdbc.DataSourceFactory)";
		ServiceReference<?>[] srvRefs = bcontext.getAllServiceReferences(null, filter);
		// We expect 3: xa, pooled and plain ds factory
		assertEquals(3, srvRefs.length);
	}

	private static <T> T waitForReference(BundleContext context, Class<T> clazz, long timeout) throws InterruptedException {
		ServiceTracker<T, ?> st = new ServiceTracker<>(context, clazz.getName(), null);
		st.open();
		return clazz.cast(st.waitForService(timeout));
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
		
		Arrays.asList(bcontext.getBundles()).stream()
		.filter(it -> it.getState() == Bundle.RESOLVED)
		.forEach(b -> {
			try {
				b.start();
			} catch (BundleException e) {
				;
			}
		});
	}

	private static void adjustStartLevel(Bundle bundle) {
		BundleStartLevel bsl = bundle.adapt(BundleStartLevel.class); 
		if (bundle.getBundleId() == 0 || // skip system bundle
				bsl.getStartLevel() <= 1) 
			return;
		bsl.setStartLevel(1);
	}

}

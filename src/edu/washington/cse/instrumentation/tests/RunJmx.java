package edu.washington.cse.instrumentation.tests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class RunJmx {
	public static void main(String []args) throws IOException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, InterruptedException {
		@SuppressWarnings("resource")
		BufferedWriter w = new BufferedWriter(new FileWriter(new File(args[0])));
		JMXServiceURL u = new JMXServiceURL(
				  "service:jmx:rmi:///jndi/rmi://" + "localhost:" + 30001 +  "/jmxrmi");
		JMXConnector c = JMXConnectorFactory.connect(u);
		MBeanServerConnection conn = c.getMBeanServerConnection();
		MemoryMXBean proxy = ManagementFactory.newPlatformMXBeanProxy(conn, ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class);
		proxy.gc();
		while(true) {
			proxy.gc();
			long l = proxy.getHeapMemoryUsage().getUsed();
			w.write(l + "");
			w.newLine();
			w.flush();
			Thread.sleep(1000);
		}
	}
}

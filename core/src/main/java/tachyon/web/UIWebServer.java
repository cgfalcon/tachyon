/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.web;

import java.io.File;
import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import tachyon.Constants;
import tachyon.TachyonURI;
import tachyon.conf.TachyonConf;
import tachyon.master.MasterInfo;

/**
 * Class that bootstraps and starts the web server for the web interface.
 */
public class UIWebServer {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);

  private Server mServer;
  private String mServerName;
  private InetSocketAddress mAddress;
  private final TachyonConf mTachyonConf;

  /**
   * Constructor that pairs urls with servlets and sets the webapp folder.
   *
   * @param serverName Name of the server
   * @param address Address of the server
   * @param masterInfo MasterInfo for the tachyon filesystem this UIWebServer supports
   * @param conf Tachyon configuration
   */
  public UIWebServer(String serverName, InetSocketAddress address, MasterInfo masterInfo, 
      TachyonConf conf) {
    Preconditions.checkNotNull(serverName, "Server name cannot be null");
    Preconditions.checkNotNull(address, "Server address cannot be null");
    Preconditions.checkNotNull(masterInfo, "Master information cannot be null");
    Preconditions.checkNotNull(conf, "Configuration cannot be null");
    
    mAddress = address;
    mServerName = serverName;
    mTachyonConf = conf;

    QueuedThreadPool threadPool = new QueuedThreadPool();
    int webThreadCount = mTachyonConf.getInt(Constants.MASTER_WEB_THREAD_COUNT, 1);

    mServer = new Server();
    SelectChannelConnector connector = new SelectChannelConnector();
    connector.setPort(address.getPort());
    connector.setAcceptors(webThreadCount);
    mServer.setConnectors(new Connector[] { connector });

    // Jetty needs at least (1 + selectors + acceptors) threads.
    threadPool.setMinThreads(webThreadCount * 2 + 1);
    threadPool.setMaxThreads(webThreadCount * 2 + 100);
    mServer.setThreadPool(threadPool);

    WebAppContext webappcontext = new WebAppContext();

    webappcontext.setContextPath(TachyonURI.SEPARATOR);
    String tachyonHome = mTachyonConf.get(Constants.TACHYON_HOME, Constants.DEFAULT_HOME);
    File warPath =
        new File(mTachyonConf.get(Constants.WEB_RESOURCES, tachyonHome + "/core/src/main/webapp"));
    webappcontext.setWar(warPath.getAbsolutePath());
    webappcontext
        .addServlet(new ServletHolder(new WebInterfaceGeneralServlet(masterInfo)), "/home");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceWorkersServlet(masterInfo)),
        "/workers");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceConfigurationServlet(masterInfo)),
        "/configuration");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceBrowseServlet(masterInfo)),
        "/browse");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceMemoryServlet(masterInfo)),
        "/memory");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceDependencyServlet(masterInfo)),
        "/dependency");
    webappcontext.addServlet(new ServletHolder(new WebInterfaceDownloadServlet(masterInfo)),
        "/download");

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[] {webappcontext, new DefaultHandler()});
    mServer.setHandler(handlers);
  }

  public void setHandler(AbstractHandler handler) {
    mServer.setHandler(handler);
  }

  public void shutdownWebServer() throws Exception {
    mServer.stop();
  }

  public void startWebServer() {
    try {
      mServer.start();
      if (mAddress.getPort() == 0) {
        mAddress = new InetSocketAddress(mAddress.getHostName(), 
            mServer.getConnectors()[0].getLocalPort());
      }
      LOG.info(mServerName + " started @ " + mAddress);
    } catch (Exception e) {
      throw Throwables.propagate(e);
    }
  }
}

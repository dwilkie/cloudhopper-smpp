package com.cloudhopper.smpp.demo.persist;

/*
 * #%L
 * ch-smpp
 * %%
 * Copyright (C) 2009 - 2014 Cloudhopper by Twitter
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.charset.GSMCharset;
import com.cloudhopper.commons.charset.Charset;

import com.cloudhopper.commons.util.*;
import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.type.*;
import org.junit.Assert;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.io.FileInputStream;
import java.util.Properties;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.ParseException;

public class Main {
  public static void main(String[] args) throws IOException, RecoverablePduException, InterruptedException,
      SmppChannelException, UnrecoverablePduException, SmppTimeoutException {

    // create the command line parser
    CommandLineParser parser = new BasicParser();

    // create Options object
    Options options = new Options();

    Option configOption = OptionBuilder.withLongOpt("config")
                                .withDescription("smpp configuration file")
                                .hasArg()
                                .withArgName("FILE")
                                .isRequired()
                                .create('c');

    options.addOption(configOption);

    String header = "Starts the client with the given configuration file\n\n";
    String footer = "";

    HelpFormatter formatter = new HelpFormatter();

    String smppConfigurationFile = "";

    try {
      CommandLine line = parser.parse(options, args);
      smppConfigurationFile = line.getOptionValue("c");
    }

    catch(ParseException exp) {
      formatter.printHelp("chibi-smpp-client", header, options, footer, true);
      System.exit(1);
    }

    // set up new properties object
    // from the config file
    try {
      FileInputStream propFile = new FileInputStream(smppConfigurationFile);
      Properties p = new Properties(System.getProperties());
      p.load(propFile);

      // set the system properties
      System.setProperties(p);

    } catch (IOException e) {
      System.err.println(e.toString());
      System.exit(1);
    }

    DummySmppClientMessageService smppClientMessageService = new DummySmppClientMessageService();
    int i = 0;
    final LoadBalancedList<OutboundClient> balancedList = LoadBalancedLists.synchronizedList(new RoundRobinLoadBalancedList<OutboundClient>());
    balancedList.set(createClient(smppClientMessageService, ++i), 1);
    balancedList.set(createClient(smppClientMessageService, ++i), 1);
    balancedList.set(createClient(smppClientMessageService, ++i), 1);

    final ExecutorService executorService = Executors.newFixedThreadPool(10);

    Scanner terminalInput = new Scanner(System.in);
    while (true) {
      String s = terminalInput.nextLine();
      final long messagesToSend;
      try {
        messagesToSend = Long.parseLong(s);
      } catch (NumberFormatException e) {
        break;
      }
      final AtomicLong alreadySent = new AtomicLong();
      for (int j = 0; j < 10; j++) {
        executorService.execute(new Runnable() {
          @Override
          public void run() {
            try {
              long sent = alreadySent.incrementAndGet();
              while (sent <= messagesToSend) {
                final OutboundClient next = balancedList.getNext();
                final SmppSession session = next.getSession();
                if (session != null && session.isBound()) {
                  String defaultText = "\u20AC Lorem [ipsum] dolor sit amet, consectetur adipiscing elit. Proin feugiat, leo id commodo tincidunt, nibh diam ornare est, vitae accumsan risus lacus sed sem metus.";

                  String text160 = System.getProperty("SMPP_TEST_MT_MESSAGE_TEXT", defaultText);

                  byte[] textBytes;
                  byte dataCoding;

                  if (GSMCharset.canRepresent(text160)) {
                    textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_GSM);
                    dataCoding = SmppConstants.DATA_CODING_GSM;
                  } else {
                    textBytes = CharsetUtil.encode(text160, CharsetUtil.CHARSET_UCS_2);
                    dataCoding = SmppConstants.DATA_CODING_UCS2;

                    ByteOrder destByteOrder;

                    if(Integer.parseInt(System.getProperty("SMPP_MT_UCS2_LITTLE_ENDIANNESS", "1")) == 1) {
                      destByteOrder = ByteOrder.LITTLE_ENDIAN;
                    } else {
                      destByteOrder = ByteOrder.BIG_ENDIAN;
                    }

                    ByteBuffer sourceByteBuffer = ByteBuffer.wrap(textBytes);
                    ByteBuffer destByteBuffer = ByteBuffer.allocate(textBytes.length);

                    destByteBuffer.order(destByteOrder);
                    while(sourceByteBuffer.hasRemaining()) {
                      destByteBuffer.putShort(sourceByteBuffer.getShort());
                    }

                    textBytes = destByteBuffer.array();
                  }

                  SubmitSm submit = new SubmitSm();
                  int sourceTon = Integer.parseInt(System.getProperty("SMPP_SOURCE_TON", "3"));
                  int sourceNpi = Integer.parseInt(System.getProperty("SMPP_SOURCE_NPI", "0"));
                  String sourceAddress = System.getProperty("SMPP_SOURCE_ADDRESS", "40404");
                  submit.setSourceAddress(new Address((byte) sourceTon, (byte) sourceNpi, sourceAddress));
                  int destTon = Integer.parseInt(System.getProperty("SMPP_DESTINATION_TON", "1"));
                  int destNpi = Integer.parseInt(System.getProperty("SMPP_DESTINATION_NPI", "1"));
                  String destAddress = System.getProperty("SMPP_TEST_MT_NUMBER", "44555519205");
                  submit.setDestAddress(new Address((byte) destTon, (byte) destNpi, destAddress));
                  submit.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
                  submit.setServiceType(System.getProperty("SMPP_SERVICE_TYPE", "vma"));
                  submit.setDataCoding(dataCoding);
                  submit.setShortMessage(textBytes);
                  final SubmitSmResp submit1 = session.submit(submit, 10000);
                  Assert.assertNotNull(submit1);
                }
                sent = alreadySent.incrementAndGet();
              }
            } catch (Exception e) {
              System.err.println(e.toString());
              return;
            }
          }
        });
      }
    }

    executorService.shutdownNow();
    ReconnectionDaemon.getInstance().shutdown();
    for (LoadBalancedList.Node<OutboundClient> node : balancedList.getValues()) {
      node.getValue().shutdown();
    }
  }

  private static OutboundClient createClient(DummySmppClientMessageService smppClientMessageService, int i) {
    OutboundClient client = new OutboundClient();
    client.initialize(getSmppSessionConfiguration(i), smppClientMessageService);
    client.scheduleReconnect();
    return client;
  }

  private static SmppSessionConfiguration getSmppSessionConfiguration(int i) {
    SmppSessionConfiguration config = new SmppSessionConfiguration();
    config.setWindowSize(Integer.parseInt(System.getProperty("SMPP_WINDOW_SIZE", "5")));
    config.setName("Tester.Session." + i);
    config.setType(SmppBindType.TRANSCEIVER);
    config.setHost(System.getProperty("SMPP_HOST", "127.0.0.1"));
    config.setPort(Integer.parseInt(System.getProperty("SMPP_PORT", "2776")));
    config.setConnectTimeout(Integer.parseInt(System.getProperty("SMPP_CONNECTION_TIMEOUT", "10000")));
    config.setSystemId(System.getProperty("SMPP_SYSTEM_ID", "systemId" + i));
    config.setPassword(System.getProperty("SMPP_PASSWORD", "password"));
    config.getLoggingOptions().setLogBytes(false);
    // to enable monitoring (request expiration)

    config.setRequestExpiryTimeout(Integer.parseInt(System.getProperty("SMPP_REQUEST_EXPIRY_TIMEOUT", "30000")));
    config.setWindowMonitorInterval(Integer.parseInt(System.getProperty("SMPP_WINDOW_MONITOR_INTERVAL", "15000")));

    config.setCountersEnabled(false);

    return config;
  }
}

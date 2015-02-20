package com.cloudhopper.smpp.demo.persist;

/*
 * #%L
 * ch-smpp
 * %%
 * Copyright (C) 2009 - 2015 Cloudhopper by Twitter
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * Complicated constructor to test JSON serialization.
 *
 * @author Greg Haines
 */
public class TestJob implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(TestJob.class);
  private final String arg1;
  private final String arg2;
  private final String arg3;
  private final String arg4;
  private BlockingQueue queue;

  public TestJob(final String arg1, final String arg2, final String arg3, final String arg4) {
    this.arg1 = arg1;
    this.arg2 = arg2;
    this.arg3 = arg3;
    this.arg4 = arg4;
  }

  public void setQueue(final BlockingQueue q) { queue = q; }

  public void run() {
    try { queue.put(produce()); }
    catch (InterruptedException ex) { log.error( "failed!", ex ); }
  }

  private MtMessageJob produce() {
    final String[] args = {arg1, arg2, arg3, arg4};
    return new MtMessageJob(args);
  }
}

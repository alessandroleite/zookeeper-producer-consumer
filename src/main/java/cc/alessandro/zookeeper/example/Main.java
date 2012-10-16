/**
 * No Copyright (c) 2012
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package cc.alessandro.zookeeper.example;

import java.io.IOException;
import java.util.Random;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.alessandro.zookeeper.example.SyncPrimitive.Barrier;
import cc.alessandro.zookeeper.example.SyncPrimitive.Queue;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);
	
	// -------------------------------------------------------
	// - 			 Application parameters  				 -
	// -------------------------------------------------------

	@Parameter(names = "-address", required = true, description = "comma separated host:port pairs, each corresponding to a zk server. e.g. "
			+ "127.0.0.1:2181,127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002")
	private String address;

	@Parameter(names = "-max", required = true, description = "maximum numbers of elements to be produced, consumed or nodes in the barrier")
	private Integer max;

	@Parameter(names = "-queue", description = "Execute a queue producer or consumer")
	private Boolean queue;

	@Parameter(names = "-p", description = "start a queue producer")
	private Boolean producer;
	
	// -------------------------------------------------------
	// - 			       Test methods  				     -
	// -------------------------------------------------------
	

	private void queue_test() throws IOException, KeeperException, InterruptedException {
		Queue q = new Queue(address, "/app1");

		if (producer != null) {
			LOG.info("Producer...");
			for (int i = 0; i < max; i++) {
				q.produce(10 + i);
			}
		} else {
			LOG.info("Consumer...");
			for (int i = 0; i < max; i++) {
				try {
					int r = q.consume();
					LOG.info("Item: {}", r);
				} catch (KeeperException exception) {
					i--;
					LOG.error(exception.getMessage());
				}
			}
		}
	}

	private void barrier_test() throws IOException, KeeperException,
			InterruptedException {
		Barrier b = new Barrier(address, "/b1", max);

		boolean flag = b.enter();
		LOG.info("Barrier enter: {}", flag);

		if (!flag)
			LOG.info("Error when entering the barrier");

		Random rand = new Random();
		int r = rand.nextInt(100);

		// Loop for random iterations
		for (int i = 0; i < r; i++) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				LOG.warn(ex.getMessage(), ex);
			}
		}

		b.leave();
		LOG.info("Left barrier!");
	}
	
	// -------------------------------------------------------
	// - 			       Main method  				     -
	// -------------------------------------------------------

	public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
		Main m = new Main();

		JCommander jc = new JCommander();
		jc.addObject(m);
		
		if (args.length < 3)
			jc.usage();
		else {
			jc.parse(args);

			if (m.queue != null) {
				m.queue_test();
			} else {
				m.barrier_test();
			}
		}
	}
}
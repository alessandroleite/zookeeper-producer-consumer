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
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class SyncPrimitive implements Watcher {

	private static final Logger LOG = LoggerFactory.getLogger(SyncPrimitive.class);

	static ZooKeeper zk;
	static Integer mutex;

	final String root;

	public SyncPrimitive(final String address, final String root)
			throws IOException {
		
		if (zk == null) {
			LOG.debug("Starting ZooKeeper ...");
			zk = new ZooKeeper(Preconditions.checkNotNull(address), 3000, this);
			mutex = new Integer(-1);
			LOG.debug("Finished starting ZooKeeper: {}", zk);
		} else {
			mutex = new Integer(-1);
		}
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(root));
		this.root = root;
	}

	@Override
	public synchronized void process(WatchedEvent event) {
		synchronized (mutex) {
			LOG.info("Process: {}", event.getType());
			mutex.notify();
		}
	}

	public String getRoot() {
		return root;
	}
	
	/**
	 * Create the barrier node if it doesn't exist.
	 * 
	 * @throws KeeperException
	 * @throws InterruptedException
	 */
	void createNode() throws KeeperException {
		if (zk != null) {
			try {
				Stat s = zk.exists(this.root, false);
				if (s == null) {
					zk.create(root, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				}
			} catch (InterruptedException exception) {
					LOG.error("Ocurred in InterruptedException when try to create a ZooKeeper's node.", exception);
			}
		}
	}

	// -------------------------------------------------------
	// - 				 	Barrier Class  					 -
	// -------------------------------------------------------

	static public class Barrier extends SyncPrimitive {
		int size;
		final String hostName;
		String nodeName;

		Barrier(String address, String root, int size) throws IOException, KeeperException {
			super(address, root);
			
			this.size = size;
			this.createNode();
			this.hostName = InetAddress.getLocalHost().getCanonicalHostName().toString();
		}

		/**
		 * Join the barrier.
		 * 
		 * @return <code>true</code> if succeed, <code>false</code> otherwise.
		 * @throws KeeperException
		 * @throws InterruptedException
		 */
		boolean enter() throws KeeperException, InterruptedException {
			nodeName = zk.create(this.root + "/" + hostName, new byte[0], Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
			while (true) {
				synchronized (mutex) {
					List<String> children = zk.getChildren(root, true);
					if (children.size() < size)
						mutex.wait();
					else
						return true;
				}
			}
		}
		
		/**
		 * Wait until all reach barrier
		 * 
		 * @return <code>true</code> if all reached barrier.
		 * 
		 * @throws InterruptedException
		 * @throws KeeperException
		 */
		boolean leave() throws InterruptedException, KeeperException {
			zk.delete(nodeName, 0);
			LOG.info("Node deleted: {}", nodeName);
			while (true) {
				synchronized (mutex) {
					final List<String> list = zk.getChildren(root, true);
					if (!list.isEmpty()) {
						mutex.wait();
					} else {
						return true;
					}
				}
			}
		}
	}
	
	// -------------------------------------------------------
	// - 				 	Queue Class  					 -
	// -------------------------------------------------------
	static class Queue extends SyncPrimitive {

		public Queue(String address, String root) throws IOException, KeeperException {
			super(address, root);
			this.createNode();
		}
		
		/**
		 * Add an element to the queue
		 * 
		 * @param element Element to enqueue.
		 * @return <code>true</code> if succeed, <code>false</code> otherwise.
		 * 
		 * @throws KeeperException
		 * @throws InterruptedException
		 */
		boolean produce (int value) throws KeeperException, InterruptedException {
			ByteBuffer b = ByteBuffer.allocate(4);
			// add a child with value 'value'
			b.putInt(value);
			zk.create(root + "/element", b.array(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
			return true;
		}
		
		/**
		 * Remove the first element from the queue.
		 * 
		 * @return the value of the first element dequeued.
		 * @throws KeeperException
		 * @throws InterruptedException
		 */
		/**
		 * @return
		 * @throws KeeperException
		 * @throws InterruptedException
		 */
		int consume() throws KeeperException, InterruptedException{
			while (true) {
				Stat stat = new Stat();
				
				synchronized (mutex) {
					List<String> children = zk.getChildren(root, true);
					if (children.isEmpty()){
						LOG.info("Going to wait");
						mutex.wait();
					} else {
						Collections.sort(children);
						final String first = children.get(0);
						
						final String path = root + "/" + first;
						byte[] data = zk.getData(path, false, stat);
						zk.delete(path, stat.getVersion());
						
						return ByteBuffer.wrap(data).getInt();
					}
				}
			}
		}
	}
}
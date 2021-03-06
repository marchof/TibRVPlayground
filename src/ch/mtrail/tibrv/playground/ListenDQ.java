package ch.mtrail.tibrv.playground;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.tibco.tibrv.TibrvCmListener;
import com.tibco.tibrv.TibrvCmMsg;
import com.tibco.tibrv.TibrvCmQueueTransport;
import com.tibco.tibrv.TibrvException;
import com.tibco.tibrv.TibrvListener;
import com.tibco.tibrv.TibrvMsg;
import com.tibco.tibrv.TibrvMsgCallback;
import com.tibco.tibrv.TibrvQueue;

public class ListenDQ extends Abstract implements TibrvMsgCallback {

	private final String dqGroupName = "DQgroupName";
	private TibrvQueue queue;
	private final List<RvDispatcher> dispatchers = new ArrayList<>();

	private final static int threads = 5;
	private TibrvCmQueueTransport dq;

	public ListenDQ(final String service, final String network, final String daemon, final String subject) {
		super(service, network, daemon);
		
		try {
			queue = new TibrvQueue();
			
			/**
			 * TibrvCmQueueTransport(
			 *  TibrvRvdTransport transport,
			 *  java.lang.String cmName,
			 *  int workerWeight,
			 *  int workerTasks,
			 *  int schedulerWeight,
			 *  double schedulerHeartbeat,   
			 *          The scheduler sends heartbeat messages at this interval (in seconds).
			 *  double schedulerActivation   
			 *          When the heartbeat signal from the scheduler has been silent for this
			 *          interval (in seconds), the cooperating member with the greatest
			 *          scheduler weight takes its place as the new scheduler.
			 * )
			 */
			dq = new TibrvCmQueueTransport(transport, dqGroupName);
			dq.setWorkerTasks(threads);

			new TibrvCmListener(queue, this, dq, subject, null);
			System.out.println("Listening on: " + subject);

		} catch (final TibrvException e) {
			handleFatalError(e);
		}

		for (int i = 0; i <= threads; i++) {
			final RvDispatcher dispatcher = new RvDispatcher(queue);
			dispatchers.add(dispatcher);
			final Thread thread = new Thread(dispatcher, "Dispatcher-" + i);
			thread.start();
		}
	}

	public void printDebugInfos() {
		while (true) {
			try {
				System.out.println(
						"QueueLength: " + queue.getCount() + " DQueueLength: " + dq.getUnassignedMessageCount());
				System.out.flush();

				LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onMsg(final TibrvListener listener, final TibrvMsg msg) {
		long seqno = -1;
		try {
			seqno = TibrvCmMsg.getSequence(msg);
		} catch (final TibrvException e) {
			e.printStackTrace();
		}

		System.out.println((new Date()).toString() + " " + Thread.currentThread().getName() + " START " //
				+ "subject=" + msg.getSendSubject() + ", message=" + msg.toString() + ", seqno=" + seqno);
		System.out.flush();

		msg.dispose();

		LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));

		System.out.println((new Date()).toString() + " " + Thread.currentThread().getName() + " FINISHED");
		System.out.flush();
	}

	@Override
	public void setPerformDispatch(final boolean performDispatch) {
		dispatchers.forEach(dispatcher -> {
			dispatcher.setRun(performDispatch);
		});
	}

	public static void main(final String args[]) {
		// Debug.diplayEnvInfo();

		final ArgParser argParser = new ArgParser("TibRvListenFT");
		argParser.setOptionalParameter("service", "network", "daemon");
		argParser.setRequiredArg("subject");
		argParser.parse(args);

		final ListenDQ listen = new ListenDQ(//
				argParser.getParameter("service"), //
				argParser.getParameter("network"), //
				argParser.getParameter("daemon"), //
				argParser.getArgument("subject"));

		listen.startKeyListener();

		listen.printDebugInfos();
	}

	class RvDispatcher implements Runnable {
		
		private boolean performDispatch = true;
		private final TibrvQueue queue;
		
		public RvDispatcher(final TibrvQueue queue) {
			this.queue = queue;
		}
		
		@Override
		public void run() {
			while (true) {
				if (performDispatch) {
					// dispatch Tibrv events
					try {
						// Wait max 0.5 sec, to listen on keyboard.
						queue.timedDispatch(0.5d);
					} catch (final TibrvException e) {
						handleFatalError(e);
					} catch (final InterruptedException ie) {
						System.exit(1);
					}
					
				} else {
					// Dispatch is disabled, just idle
					LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
				}
			}
		}
		
		public void setRun(final boolean performDispatch) {
			this.performDispatch = performDispatch;
		}
	}
}


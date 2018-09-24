/**
 * jvmtop - java monitoring for the command-line
 * <p>
 * Copyright (C) 2013 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * <p>
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.midi.Soundbank;

import org.jnativehook.GlobalScreen;

import com.jvmtop.profiler.HeapSampler;
import com.jvmtop.view.ConsoleView;
import com.jvmtop.view.VMDetailView;
import com.jvmtop.view.VMMemProfileView;
import com.jvmtop.view.VMOverviewView;
import com.jvmtop.view.VMProfileView;
import com.sun.tools.attach.AttachNotSupportedException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * JvmTop entry point class.
 * <p>
 * - parses program arguments - selects console view - prints header - main
 * "iteration loop"
 * <p>
 * @author zakharc
 * @author paru
 * @author tckb
 */
public class JvmTop {

	public static final String VERSION = "0.9";
	private final static double DELAY_OVERVIEW = 5.5;
	final static double DELAY_DETAIL = 3.0;
	final static double MIN_ELEMENTS_SHOWN = 3;
	final static double MAX_ELEMENTS_SHOWN = 10;
	final static int MAXIMUM_SYSTEM_PID_LENGTH = 5;
	private final static String ERASE_TERMINAL_SCREEN = new String(
			new byte[] { (byte) 0x1b, (byte) 0x5b, (byte) 0x31, (byte) 0x4a, (byte) 0x1b, (byte) 0x5b, (byte) 0x48 });
	final static String ERASE_TERMINAL_LINE = new String(
			new byte[] { (byte) 0x1b, (byte) 0x5b, (byte) 0x31, (byte) 0x4b});

	Double delay_ = 1.0;
	private int maxIterations_ = -1;
	private Boolean supportsSystemAverage_;
	private java.lang.management.OperatingSystemMXBean localOSBean_;
	private static JvmTopKeyListener keyListener = null;
	static JvmTop jvmTop = new JvmTop();
	
	static VMDetailView vmDetailView;
	private static VMOverviewView vmOverviewView;
	private static Logger logger;
	private static Logger loggerJNative = Logger.getLogger(GlobalScreen.class.getPackage().getName());

	private static OptionParser createOptionParser() {
		OptionParser parser = new OptionParser();
		parser.acceptsAll(Arrays.asList("help", "?", "h"), "shows this help").forHelp();
		parser.accepts("once", "jvmtop will exit after first output iteration [deprecated, use -n 1 instead]");
		parser.acceptsAll(Arrays.asList("n", "iteration"), "jvmtop will exit after n output iterations")
				.withRequiredArg().ofType(Integer.class);
		parser.acceptsAll(Arrays.asList("d", "delay"), "delay between each output iteration").withRequiredArg()
				.ofType(Double.class);
		parser.accepts("profile", "start CPU profiling at the specified jvm");
		parser.accepts("enable-deltas",
				"shows deltas between the updates (currently only applicable with --profile-mem)");

		parser.accepts("profile-mem", "start memory profiling at the specified jvm").requiredIf("enable-deltas");

		parser.accepts("sysinfo", "outputs diagnostic information");
		parser.accepts("verbose", "verbose mode");
		parser.accepts("threadlimit", "sets the number of displayed threads in detail mode").withRequiredArg()
				.ofType(Integer.class);
		parser.accepts("stacklimit", "sets the number of displayed stack trace elements in detail mode")
				.withRequiredArg().ofType(Integer.class);
		parser.accepts("disable-threadlimit", "displays all threads in detail mode");

		parser.acceptsAll(Arrays.asList("p", "pid"), "PID to connect to").withRequiredArg().ofType(Integer.class);

		parser.acceptsAll(Arrays.asList("w", "width"), "Width in columns for the console display").withRequiredArg()
				.ofType(Integer.class);

		parser.accepts("threadnamewidth", "sets displayed thread name length in detail mode (defaults to 30)")
				.withRequiredArg().ofType(Integer.class);

		parser.accepts("thread-dump", "dumps the status of current running threads");
		parser.accepts("heap-dump", "generates and dumps the heap to the specified file").withRequiredArg()
				.ofType(String.class);

		return parser;
	}

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);

		logger = Logger.getLogger("jvmtop");
		loggerJNative.setLevel(Level.OFF);
		loggerJNative.setUseParentHandlers(false);
		
		keyListener = new JvmTopKeyListener(jvmTop);
		GlobalScreen.addNativeKeyListener(keyListener);
		keyListener.init();
		

		OptionParser parser = createOptionParser();
		OptionSet a = parser.parse(args);

		if (a.has("help")) {
			System.out.println("jvmtop - java monitoring for the command-line");
			System.out.println("Usage: jvmtop.sh [options...] [PID]");
			System.out.println("");
			parser.printHelpOn(System.out);
			System.exit(0);
		}

		boolean sysInfoOption = a.has("sysinfo");
		Integer pid = null;
		Integer width = null;
		boolean profileMode = a.has("profile");
		boolean profileMemMode = a.has("profile-mem");
		boolean deltasEnabled = a.has("enable-deltas");
		Integer iterations = a.has("once") ? 1 : -1;
		Integer threadlimit = null;
		Integer stackLimit = null;
		boolean threadLimitEnabled = true;
		Integer threadNameWidth = null;
		double delay;

		if (a.hasArgument("n")) {
			iterations = (Integer) a.valueOf("n");
		}

		// to support PID as non option argument
		if (a.nonOptionArguments().size() > 0) {
			pid = Integer.valueOf((String) a.nonOptionArguments().get(0));
		}

		if (a.hasArgument("pid")) {
			pid = (Integer) a.valueOf("pid");
		}

		if (a.hasArgument("width")) {
			width = (Integer) a.valueOf("width");
		}

		if (a.hasArgument("threadlimit")) {
			threadlimit = (Integer) a.valueOf("threadlimit");
		}

		if (a.hasArgument("stacklimit")) {
			stackLimit = (Integer) a.valueOf("stacklimit");
		}

		if (a.has("disable-threadlimit")) {
			threadLimitEnabled = false;
		}

		if (a.has("verbose")) {
			fineLogging();
			logger.setLevel(Level.ALL);
			logger.fine("Verbosity mode.");
		}
		delay = (pid == null) ? DELAY_OVERVIEW : DELAY_DETAIL;
		if (a.hasArgument("delay")) {
			delay = (Double) (a.valueOf("delay"));
			if (delay < 0.1d) {
				throw new IllegalArgumentException("Delay cannot be set below 0.1");
			}
		}

		if (a.hasArgument("threadnamewidth")) {
			threadNameWidth = (Integer) a.valueOf("threadnamewidth");
		}

		// non-view arguments
		if (a.has("thread-dump") || a.has("heap-dump")) {
			handleNonViewArgs(a, pid);
		}

		if (sysInfoOption) {
			outputSystemProps();
		} else {
			jvmTop.setDelay(delay);
			jvmTop.setMaxIterations(iterations);

			if (pid == null) {
				jvmTop.setDelay(DELAY_OVERVIEW);
				vmOverviewView = new VMOverviewView(width);
				jvmTop.run(vmOverviewView);
			} else {
				if (profileMode) {
					jvmTop.run(new VMProfileView(pid, width));
				}
				if (profileMemMode) {
					jvmTop.run(new VMMemProfileView(pid, width, deltasEnabled));
				} else {
					vmDetailView = new VMDetailView(pid, width);
					vmDetailView.setDisplayedThreadLimit(threadLimitEnabled);
					if (threadlimit != null) {
						vmDetailView.setNumberOfDisplayedThreads(threadlimit);
					}
					if (stackLimit != null) {
						vmDetailView.setStackTraceElementsShown(stackLimit);
					}
					if (threadNameWidth != null) {
						vmDetailView.setThreadNameDisplayWidth(threadNameWidth);
					}
					jvmTop.run(vmDetailView);

				}

			}
		}
	}

	private static void handleNonViewArgs(final OptionSet options, Integer pid) {
		if (pid == null) {
			System.err.println("");
			System.err.println("ERROR: pid required");
			System.err.println("");
			System.exit(100);
		}

		try {
			HeapSampler sampler = new HeapSampler(pid);

			if (options.has("thread-dump")) {
				System.out.println(sampler.threadDump());
				System.exit(0);
			}

			if (options.has("heap-dump")) {
				String file = (String) options.valueOf("heap-dump");
				if (sampler.dumpHeap(new File(file))) {
					System.out.println("Heap dump created successfully at " + file);
					System.exit(0);
				} else {
					System.err.println("Heap dump creation failed! ");
					System.exit(100);
				}
			}

		} catch (IOException e) {
			System.err.println("");
			System.err.println("ERROR:" + e.getMessage());
			System.err.println("");
		} catch (AttachNotSupportedException e) {
			System.err.println("");
			System.err.println("ERROR: Unable to attach pid, is the vm still running? " + e.getMessage());
			System.err.println("");
		}
	}

	private static void fineLogging() {
		// get the top Logger:
		Logger topLogger = java.util.logging.Logger.getLogger("");

		// Handler for console (reuse it if it already exists)
		Handler consoleHandler = null;
		// see if there is already a console handler
		for (Handler handler : topLogger.getHandlers()) {
			if (handler instanceof ConsoleHandler) {
				// found the console handler
				consoleHandler = handler;
				break;
			}
		}

		if (consoleHandler == null) {
			// there was no console handler found, create a new one
			consoleHandler = new ConsoleHandler();
			topLogger.addHandler(consoleHandler);
		}
		// set the console handler to fine:
		consoleHandler.setLevel(java.util.logging.Level.FINEST);
	}

	private static void outputSystemProps() {
		for (Object key : System.getProperties().keySet()) {
			System.out.println(key + "=" + System.getProperty(key + ""));
		}
	}

	@SuppressWarnings("resource")
	protected void run(ConsoleView view) throws Exception {
		try {
			System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)), false));

			if (view instanceof VMDetailView) {
				keyListener.addDetailedView((VMDetailView) view);
			}
			if (view instanceof VMOverviewView) {
				keyListener.addOverviewView((VMOverviewView) view);
			}
			int iterations = 0;
			while (!view.shouldExit()) {
				if (maxIterations_ > 1 || maxIterations_ == -1) {
					clearTerminal();
				}
				printTopBar();
				view.printView();
				view.printFooter();
				System.out.flush();
				if (iterations >= maxIterations_ && maxIterations_ > 0) {
					break;
				}
				if (iterations != 0) {
					view.sleep((int) (delay_ * 1000));
				}
				iterations++;
			}
		} catch (NoClassDefFoundError e) {
			e.printStackTrace(System.err);
			System.err.println("");
			System.err.println("ERROR: Some JDK classes cannot be found.");
			System.err.println("       Please check if the JAVA_HOME environment variable has been set to a JDK path.");
			System.err.println("");
		}
	}

	/**
	 *
	 */
	private static void clearTerminal() {
		if (System.getProperty("os.name").contains("Windows")) {
			// hack
			System.out.printf("%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n%n");
		} else if (System.getProperty("jvmtop.altClear") != null) {
			System.out.print('\f');
		} else {
			System.out.println(ERASE_TERMINAL_SCREEN);
		}
	}

	public JvmTop() {
		localOSBean_ = ManagementFactory.getOperatingSystemMXBean();
	}

	private void printTopBar() {
		System.out.printf(" JvmTop %s - %8tT, %6s, %2d cpus, %15.15s", VERSION, new Date(), localOSBean_.getArch(),
				localOSBean_.getAvailableProcessors(), localOSBean_.getName() + " " + localOSBean_.getVersion());

		if (supportSystemLoadAverage() && localOSBean_.getSystemLoadAverage() != -1) {
			System.out.printf(", load avg %3.2f%n", localOSBean_.getSystemLoadAverage());
		} else {
			System.out.println();
		}
	}

	private boolean supportSystemLoadAverage() {
		if (supportsSystemAverage_ == null) {
			try {
				supportsSystemAverage_ = (localOSBean_.getClass().getMethod("getSystemLoadAverage") != null);
			} catch (Throwable e) {
				supportsSystemAverage_ = false;
			}
		}
		return supportsSystemAverage_;
	}

	public Double getDelay() {
		return delay_;
	}

	public void setDelay(Double delay) {
		delay_ = delay;
	}

	public int getMaxIterations() {
		return maxIterations_;
	}

	public void setMaxIterations(int iterations) {
		maxIterations_ = iterations;
	}

}

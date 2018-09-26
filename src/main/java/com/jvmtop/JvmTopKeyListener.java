/**
 * jvmtop - java monitoring for the command-line
 * 
 * Copyright (C) 2018 by Patric Rufflar. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.jvmtop;
import java.util.List;

import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import com.jvmtop.view.VMDetailView;
import com.jvmtop.view.VMOverviewView;

/**
 * 
 * The listener for receiving <code>NativeKeyEvents</code>. Fires corresponding event on action.
 * <p>
 * When the <code>NativeKeyEvent</code> occurs, fires object's appropriate method.
 * 
 * @author zakharc
 * @version	1.0
 * @since	0.9
 *
 * */
class JvmTopKeyListener implements NativeKeyListener {
	
	private final static int MIN_DELAY = 1;
	private final static int MAX_DELAY = 10;
	private final static double MIN_ELEMENTS_SHOWN = 3;
	private final static double MAX_ELEMENTS_SHOWN = 10;
	private JvmTop instance;
	private VMDetailView detailView;
	private VMOverviewView overviewView;
	private StringBuilder sb = new StringBuilder();

	public JvmTopKeyListener(JvmTop instance) {
		super();
		this.instance = instance;
	}

	public void nativeKeyPressed(NativeKeyEvent event) {
		waitForExitButtonPressed(event);
		triggerActionForDetailView(event);
		triggerActionForOverviewView(event);
	}

	/**
	 * Proceeds with actions if {@code VMOverviewView} specified
	 * */
	private void triggerActionForOverviewView(NativeKeyEvent event) {
		if (overviewView != null) {
			try {
				String keyLabel = NativeKeyEvent.getKeyText(event.getKeyCode());
				if (keyLabel.contains("Enter") && sb.length() != 0) {
					setDetailedView();
				} else {
					Integer inputDigit = Integer.parseInt(NativeKeyEvent.getKeyText(event.getKeyCode()));
					if (sb.length() < JvmTop.MAXIMUM_SYSTEM_PID_LENGTH) {
						sb.append(String.valueOf(inputDigit));
					} else {
						setDetailedView();
					}
				}
			} catch (Exception e) {
				// do nothing
			}
		}
	}

	/**
	 * Proceeds with actions if {@code VMDetailedView} specified
	 * */
	private void triggerActionForDetailView(NativeKeyEvent event) {
		if (detailView != null) {
			fireOnKeyPressed(event, "Page Down", () -> increaseNumberOfStackAndThreadElementsShown());
			fireOnKeyPressed(event, "Page Up", () -> decreaseNumberOfStackAndThreadElementsShown());
			fireOnKeyPressed(event, "Close Bracket", () -> decreaseDelay());
			fireOnKeyPressed(event, "Slash", () -> increaseDelay());
			fireOnKeyPressed(event, "0xe4e", () -> decreaseDelay()); // 0xe4e - "+" on numerical pad
			fireOnKeyPressed(event, "0xe4a", () -> increaseDelay()); // 0xe4a - "-" on numerical pad
		}
	}

	/**
	 * Applies action if specified key triggered
	 * 
	 * @param key keyboard key text to fire action on
	 * @param action action to apply if key pressed
	 * */
	private static void fireOnKeyPressed(NativeKeyEvent event, String key, Runnable action) {
		if (NativeKeyEvent.getKeyText(event.getKeyCode()).contains(key)) {
			action.run();
		}
	}

	private void decreaseNumberOfStackAndThreadElementsShown() {
		final int numberOfDisplayedThreads = detailView.getNumberOfDisplayedThreads();
		final int stackTraceElementsShown = detailView.getStackTraceElementsShown();
		if (numberOfDisplayedThreads > MIN_ELEMENTS_SHOWN
				&& stackTraceElementsShown > MIN_ELEMENTS_SHOWN) {
			detailView.decrementNumberOfDisplayedThreads();
			detailView.decrementStackTraceElementsShown();
		}
	}

	private void increaseNumberOfStackAndThreadElementsShown() {
		final int numberOfDisplayedThreads = detailView.getNumberOfDisplayedThreads();
		final int stackTraceElementsShown = detailView.getStackTraceElementsShown();
		if (numberOfDisplayedThreads < MAX_ELEMENTS_SHOWN
				&& stackTraceElementsShown < MAX_ELEMENTS_SHOWN) {
			detailView.incrementNumberOfDisplayedThreads();
			detailView.incrementStackTraceElementsShown();
		}
	}

	private void increaseDelay() {
		if (instance.getDelay() < MAX_DELAY) {
			instance.delay_++;
		}
	}

	private void decreaseDelay() {
		if (instance.getDelay() > MIN_DELAY) {
			instance.delay_--;
		}
	}
	
	private void setDetailedView() throws Exception {
		int targetVMID = Integer.parseInt(sb.toString());
		List<Integer> vmIDs = overviewView.getVMIDs();
		if (vmIDs.contains(targetVMID)) {
			overviewView.exit();
			JvmTop.vmDetailView = new VMDetailView(targetVMID, null);
			instance.setDelay(JvmTop.DELAY_DETAIL);
		} else {
			overviewView.setAdditionalFooterMessage(" [!] VMID " + targetVMID + " is not found. Please enter correct VMID.");
			// remove entered number
			sb.setLength(0);
		}
	}
	
	private static void waitForExitButtonPressed(NativeKeyEvent e) {
		if (NativeKeyEvent.getKeyText(e.getKeyCode()).contains("Q")) {
			System.out.println(JvmTop.ERASE_TERMINAL_LINE);
			System.exit(0);
		}
	}
	
	public void addDetailedView(VMDetailView view) {
		this.detailView = view;
	}
	
	public void addOverviewView(VMOverviewView view) {
		this.overviewView = view;
	}

	public void nativeKeyReleased(NativeKeyEvent e) {
		// do nothing
	}

	public void nativeKeyTyped(NativeKeyEvent e) {
		// do nothing
	}
}
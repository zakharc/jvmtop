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

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import com.jvmtop.view.VMDetailView;
import com.jvmtop.view.VMOverviewView;
class JvmTopKeyListener implements NativeKeyListener {

	public JvmTopKeyListener(JvmTop instance) {
		super();
		this.instance = instance;
	}

	JvmTop instance;
	VMDetailView detailView;
	VMOverviewView overviewView;
	StringBuilder sb = new StringBuilder();

	public void nativeKeyPressed(NativeKeyEvent event) {
		if (detailView != null) {
			waitForExitButtonPressed(event);
			try {
				Double newDelay = Double.parseDouble(NativeKeyEvent.getKeyText(event.getKeyCode()));
				if (newDelay > 0 && newDelay < 10) {
					instance.delay_ = newDelay.doubleValue();
				}
			} catch (Exception e2) {
				// do nothing
			}
			int numberOfDisplayedThreads;
			int stackTraceElementsShown;
			if (detailView != null) {
				numberOfDisplayedThreads = detailView.getNumberOfDisplayedThreads();
				stackTraceElementsShown = detailView.getStackTraceElementsShown();
				if (NativeKeyEvent.getKeyText(event.getKeyCode()).contains("Close Bracket")) {
					if (numberOfDisplayedThreads < JvmTop.MAX_ELEMENTS_SHOWN && stackTraceElementsShown < JvmTop.MAX_ELEMENTS_SHOWN) {
						detailView.incrementNumberOfDisplayedThreads();
						detailView.incrementStackTraceElementsShown();
					}
				}
				if (NativeKeyEvent.getKeyText(event.getKeyCode()).contains("Slash")) {
					if (numberOfDisplayedThreads > JvmTop.MIN_ELEMENTS_SHOWN && stackTraceElementsShown > JvmTop.MIN_ELEMENTS_SHOWN) {
						detailView.decrementNumberOfDisplayedThreads();
						detailView.decrementStackTraceElementsShown();
					}
				}
			}
		} else {
			waitForExitButtonPressed(event);
			if (overviewView != null) {
				try {
					String keyText = NativeKeyEvent.getKeyText(event.getKeyCode());
					if (keyText.contains("Enter") && sb.length() != 0) {
						runDetailViewPerId();
					} else {
						Integer inputDigit = Integer.parseInt(NativeKeyEvent.getKeyText(event.getKeyCode()));
						if (sb.length() < JvmTop.MAXIMUM_SYSTEM_PID_LENGTH) {
							sb.append(String.valueOf(inputDigit));
						} else {
							runDetailViewPerId();
						}
					}
				} catch (Exception e) {
					// do nothing
					e.printStackTrace();
				}
			}
		}
	}

	private void runDetailViewPerId() throws Exception {
		int vmIdToFind = Integer.parseInt(sb.toString());
		List<Integer> vmIDs = overviewView.getVMIDs();
		if (vmIDs.contains(vmIdToFind)) {
			overviewView.exit();
			JvmTop.vmDetailView = new VMDetailView(vmIdToFind, null);
			JvmTop.jvmTop.setDelay(JvmTop.DELAY_DETAIL);
			JvmTop.jvmTop.run(JvmTop.vmDetailView);
		} else {
			overviewView.setAdditionalFooterMessage(" [!] VMID " + vmIdToFind + " is not found. Please enter correct VMID.");
			sb.setLength(0);
		}
	}

	public void nativeKeyReleased(NativeKeyEvent e) {
		// do nothing
	}

	public void nativeKeyTyped(NativeKeyEvent e) {
		// do nothing
	}

	private void waitForExitButtonPressed(NativeKeyEvent e) {
		if (NativeKeyEvent.getKeyText(e.getKeyCode()).contains("Q")) {
			// TODO: erase terminal line before exit
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

	public void init() {
		try {
			GlobalScreen.registerNativeHook();
		} catch (NativeHookException ex) {
			// do nothing
		}
	}
	
	public void killNativeHook() {
		try {
			GlobalScreen.unregisterNativeHook();
		} catch (NativeHookException e) {
			// do nothing
		}
	}
}
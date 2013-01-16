package jp.kshoji.driver.midi.device;

import java.util.Arrays;

import jp.kshoji.driver.midi.handler.MidiMessageCallback;
import jp.kshoji.driver.midi.listener.OnMidiInputEventListener;
import jp.kshoji.driver.midi.util.Constants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * MIDI Input Device
 * stop() method must be called when the application will be destroyed.
 * 
 * @author K.Shoji
 */
public final class MidiInputDevice {

	private final UsbDevice usbDevice;
	final UsbDeviceConnection usbDeviceConnection;
	private final UsbInterface usbInterface;
	final UsbEndpoint inputEndpoint;

	private final WaiterThread waiterThread;

	/**
	 * constructor
	 * 
	 * @param usbDevice
	 * @param usbDeviceConnection
	 * @param usbInterface
	 * @param midiEventListener
	 * @throws IllegalArgumentException endpoint not found.
	 */
	public MidiInputDevice(final UsbDevice usbDevice, final UsbDeviceConnection usbDeviceConnection, final UsbInterface usbInterface, final UsbEndpoint usbEndpoint, final OnMidiInputEventListener midiEventListener) throws IllegalArgumentException {
		this.usbDevice = usbDevice;
		this.usbDeviceConnection = usbDeviceConnection;
		this.usbInterface = usbInterface;

		waiterThread = new WaiterThread(new Handler(new MidiMessageCallback(this, midiEventListener)));

		inputEndpoint = usbEndpoint;
		if (inputEndpoint == null) {
			throw new IllegalArgumentException("Input endpoint was not found.");
		}

		usbDeviceConnection.claimInterface(usbInterface, true);
		
		waiterThread.start();
	}

	/**
	 * stops the watching thread
	 */
	public void stop() {
		synchronized (waiterThread) {
			waiterThread.stopFlag = true;
		}
	}

	/**
	 * @return the usbDevice
	 */
	public UsbDevice getUsbDevice() {
		return usbDevice;
	}

	/**
	 * @return the usbInterface
	 */
	public UsbInterface getUsbInterface() {
		return usbInterface;
	}

	/**
	 * @return the usbEndpoint
	 */
	public UsbEndpoint getUsbEndpoint() {
		return inputEndpoint;
	}
	
	/**
	 * Polling thread for input data.
	 * Loops infinitely while stopFlag == false.
	 * 
	 * @author K.Shoji
	 */
	private class WaiterThread extends Thread {
		private byte[] readBuffer = new byte[64];

		public boolean stopFlag;
		
		private Handler receiveHandler;

		/**
		 * constructor
		 * 
		 * @param handler
		 */
		public WaiterThread(final Handler handler) {
			stopFlag = false;
			this.receiveHandler = handler;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			while (true) {
				synchronized (this) {
					if (stopFlag) {
						return;
					}
				}
				if (inputEndpoint == null) {
					continue;
				}
				
				int length = usbDeviceConnection.bulkTransfer(inputEndpoint, readBuffer, readBuffer.length, 0);
				if (length > 0) {
					byte[] read = new byte[length];
					System.arraycopy(readBuffer, 0, read, 0, length);
					Log.d(Constants.TAG, "Input:" + Arrays.toString(read));
					
					Message message = new Message();
					message.obj = read;
					receiveHandler.sendMessage(message);
				}
				
				// XXX uncomment below if high CPU usage with many devices. (But, this sleep makes latency).
				// try {
				// sleep(10);
				// } catch (InterruptedException e) {
				// // do nothing
				// }
			}
		}
	}
}
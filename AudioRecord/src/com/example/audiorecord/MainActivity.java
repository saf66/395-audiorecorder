package com.example.audiorecord;

import java.io.FileOutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;

public class MainActivity extends Activity implements OnClickListener {
	
	private AudioInputThread recordingThread = null;
	private ConsumerThread processingThread = null;
	private LinkedBlockingQueue<Short> sharedQueue = new LinkedBlockingQueue<Short>();
	
	private Button btnToggle = null;
	private TextView txtDisplay = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        btnToggle = (Button) findViewById(R.id.btnToggle);
        txtDisplay = (TextView) findViewById(R.id.txtDisplay);
        
        btnToggle.setOnClickListener(this);
    }

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btnToggle: {
				if (recordingThread != null && recordingThread.isAlive() && processingThread != null && processingThread.isAlive()) {
	            	recordingThread.close();
	            	recordingThread = null;
	            	processingThread.close();
	            	processingThread = null;
	            	((Button) v).setText(getString(R.string.start));
	            } else {
	            	((Button) v).setText(getString(R.string.stop));
	            	processingThread = new ConsumerThread();
	            	processingThread.start();
	            	recordingThread = new AudioInputThread();
	            	recordingThread.start();
	            }
			}
		}
	}
	
	//[TODO] This is just an example of how to read from the shared queue. Actual processing code would go here.
	private class ConsumerThread extends Thread {

		private static final String TAG = "ConsumerThread";
		
		private boolean running = false;
		
		private ConsumerThread() {
			setDaemon(true);
		}

		@Override
		public void run() {
			GZIPOutputStream zos = null;
			try {
				zos = new GZIPOutputStream(new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + "/audio.pcm.gz"));
				running = true;
				while (running) {
					byte[] data = new byte[256];
					for (int i = 0; i < data.length; i++) {
						// LinkedBlockingQueue.take() is a blocking operation
						data[i] = requantize(sharedQueue.take().shortValue());
					}
					zos.write(data, 0, data.length);
				}
				zos.flush();
				zos.finish();
				zos.close();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				zos = null;
			}
			Log.d(TAG, "thread finished");
		}
		
		private byte requantize(short data) {
			byte out = (byte) (data >> 8);
			if (out < 0xff && ((data & 0xff) > 0x80)) {
				out++;
			}
			return out;
		}
	    
		/*
	    private byte[] short2byte(short[] input, int iterations) {
			int short_index, byte_index;
			byte[] buffer = new byte[iterations * 2];
			short_index = byte_index = 0;
			while (short_index != iterations) {
			    buffer[byte_index]     = (byte) (input[short_index] & 0x00FF); 
			    buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);
			    ++short_index;
			    byte_index += 2;
			}
			return buffer;
	    }
	    */
		
		private void close() {
			running = false;
		}
		
	}
	
	private class AudioInputThread extends Thread {

		private static final String TAG = "AudioInputThread";
		private static final int RECORDER_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
		private static final int RECORDER_SAMPLERATE = 8000;
		private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
		//[NOTE] AudioFormat.ENCODING_PCM_8BIT fails because it's unimplemented
		private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
		private final int RECORDER_MIN_BUFFER = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
		
		private boolean running = false;
		
		private AudioInputThread() {
			setDaemon(true);
		}
		
		@Override
		public void run() {
			Log.d(TAG, "Recording thread started");
			Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
			AudioRecord recorder = null;
			try {
				short[] buffer = new short[RECORDER_MIN_BUFFER];
				recorder = new AudioRecord(RECORDER_SOURCE, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, buffer.length);
				recorder.startRecording();
				running = true;
				while (running) {
					int samples = recorder.read(buffer, 0, buffer.length);
					for (int i = 0; i < samples; i++) {
						sharedQueue.offer((Short) buffer[i]);
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				recorder.stop();
				recorder.release();
				recorder = null;
			}
			Log.d(TAG, "thread finished");
		}
		
		private void close() {
			running = false;
		}
	}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
}

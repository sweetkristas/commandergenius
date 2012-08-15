/*
Simple DirectMedia Layer
Java source code (C) 2009-2012 Sergii Pylypenko
  
This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:
  
1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required. 
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/

package net.sourceforge.clonekeenplus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.os.Environment;

import android.widget.TextView;
import org.apache.http.client.methods.*;
import org.apache.http.*;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.conn.*;
import org.apache.http.conn.params.*;
import org.apache.http.conn.scheme.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.impl.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.SingleClientConnManager;
import java.security.cert.*;
import java.security.SecureRandom;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.util.zip.*;
import java.io.*;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.Resources;
import java.lang.String;
import android.text.SpannedString;


class CountingInputStream extends BufferedInputStream {

	private long bytesReadMark = 0;
	private long bytesRead = 0;

	public CountingInputStream(InputStream in, int size) {

		super(in, size);
	}

	public CountingInputStream(InputStream in) {

		super(in);
	}

	public long getBytesRead() {

		return bytesRead;
	}

	public synchronized int read() throws IOException {

		int read = super.read();
		if (read >= 0) {
			bytesRead++;
		}
		return read;
	}

	public synchronized int read(byte[] b, int off, int len) throws IOException {

		int read = super.read(b, off, len);
		if (read >= 0) {
			bytesRead += read;
		}
		return read;
	}

	public synchronized long skip(long n) throws IOException {

		long skipped = super.skip(n);
		if (skipped >= 0) {
			bytesRead += skipped;
		}
		return skipped;
	}

	public synchronized void mark(int readlimit) {

		super.mark(readlimit);
		bytesReadMark = bytesRead;
	}

	public synchronized void reset() throws IOException {

		super.reset();
		bytesRead = bytesReadMark;
	}
}


class DataDownloader extends Thread
{

	public static final String DOWNLOAD_FLAG_FILENAME = "libsdl-DownloadFinished-";

	class StatusWriter
	{
		private TextView Status;
		private MainActivity Parent;
		private SpannedString oldText = new SpannedString("");

		public StatusWriter( TextView _Status, MainActivity _Parent )
		{
			Status = _Status;
			Parent = _Parent;
		}
		public void setParent( TextView _Status, MainActivity _Parent )
		{
			synchronized(DataDownloader.this) {
				Status = _Status;
				Parent = _Parent;
				setText( oldText.toString() );
			}
		}
		
		public void setText(final String str)
		{
			class Callback implements Runnable
			{
				public TextView Status;
				public SpannedString text;
				public void run()
				{
					Status.setText(text);
				}
			}
			synchronized(DataDownloader.this) {
				Callback cb = new Callback();
				oldText = new SpannedString(str);
				cb.text = new SpannedString(str);
				cb.Status = Status;
				if( Parent != null && Status != null )
					Parent.runOnUiThread(cb);
			}
		}
		
	}
	public DataDownloader( MainActivity _Parent, TextView _Status )
	{
		Parent = _Parent;
		Status = new StatusWriter( _Status, _Parent );
		//Status.setText( "Connecting to " + Globals.DataDownloadUrl );
		outFilesDir = Globals.DataDir;
		DownloadComplete = false;
		this.start();
	}
	
	public void setStatusField(TextView _Status)
	{
		synchronized(this) {
			Status.setParent( _Status, Parent );
		}
	}

	@Override
	public void run()
	{
		String [] downloadFiles = Globals.DataDownloadUrl.split("\\^");
		int total = 0;
		int count = 0;
		for( int i = 0; i < downloadFiles.length; i++ )
		{
			if( downloadFiles[i].length() > 0 &&
				( Globals.OptionalDataDownload.length > i && Globals.OptionalDataDownload[i] ) ||
				( Globals.OptionalDataDownload.length <= i && downloadFiles[i].indexOf("!") == 0 ) )
				total += 1;
		}
		for( int i = 0; i < downloadFiles.length; i++ )
		{
			if( downloadFiles[i].length() > 0 &&
				( Globals.OptionalDataDownload.length > i && Globals.OptionalDataDownload[i] ) ||
				( Globals.OptionalDataDownload.length <= i && downloadFiles[i].indexOf("!") == 0 ) )
			{
				if( ! DownloadDataFile(downloadFiles[i], DOWNLOAD_FLAG_FILENAME + String.valueOf(i) + ".flag", count+1, total) )
				{
					DownloadFailed = true;
					return;
				}
				count += 1;
			}
		}
		DownloadComplete = true;
		initParent();
	}

	public boolean DownloadDataFile(final String DataDownloadUrl, final String DownloadFlagFileName, int downloadCount, int downloadTotal)
	{
		String [] downloadUrls = DataDownloadUrl.split("[|]");
		if( downloadUrls.length < 2 )
			return false;
		
		Resources res = Parent.getResources();

		String path = getOutFilePath(DownloadFlagFileName);
		InputStream checkFile = null;
		try {
			checkFile = new FileInputStream( path );
		} catch( FileNotFoundException e ) {
		} catch( SecurityException e ) { };
		if( checkFile != null )
		{
			try {
				byte b[] = new byte[ Globals.DataDownloadUrl.getBytes("UTF-8").length + 1 ];
				int readed = checkFile.read(b);
				String compare = "";
				if( readed > 0 )
					compare = new String( b, 0, readed, "UTF-8" );
				boolean matched = false;
				//System.out.println("Read URL: '" + compare + "'");
				for( int i = 1; i < downloadUrls.length; i++ )
				{
					//System.out.println("Comparing: '" + downloadUrls[i] + "'");
					if( compare.compareTo(downloadUrls[i]) == 0 )
						matched = true;
				}
				//System.out.println("Matched: " + String.valueOf(matched));
				if( ! matched )
					throw new IOException();
				Status.setText( res.getString(R.string.download_unneeded) );
				return true;
			} catch ( IOException e ) {};
		}
		checkFile = null;
		
		// Create output directory (not necessary for phone storage)
		System.out.println("Downloading data to: '" + outFilesDir + "'");
		try {
			File outDir = new File( outFilesDir );
			if( !(outDir.exists() && outDir.isDirectory()) )
				outDir.mkdirs();
			OutputStream out = new FileOutputStream( getOutFilePath(".nomedia") );
			out.flush();
			out.close();
		}
		catch( SecurityException e ) {}
		catch( FileNotFoundException e ) {}
		catch( IOException e ) {};

		HttpResponse response = null;
		HttpGet request;
		long totalLen = 0;
		CountingInputStream stream;
		byte[] buf = new byte[16384];
		boolean DoNotUnzip = false;
		boolean FileInAssets = false;
		String url = "";

		int downloadUrlIndex = 1;
		while( downloadUrlIndex < downloadUrls.length )
		{
			System.out.println("Processing download " + downloadUrls[downloadUrlIndex]);
			url = new String(downloadUrls[downloadUrlIndex]);
			DoNotUnzip = false;
			if(url.indexOf(":") == 0)
			{
				url = url.substring( url.indexOf(":", 1) + 1 );
				DoNotUnzip = true;
			}
			Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.connecting_to, url) );
			if( url.indexOf("http://") == -1 && url.indexOf("https://") == -1 ) // File inside assets
			{
				System.out.println("Fetching file from assets: " + url);
				FileInAssets = true;
				break;
			}
			else
			{
				System.out.println("Connecting to: " + url);
				request = new HttpGet(url);
				request.addHeader("Accept", "*/*");
				try {
					DefaultHttpClient client = HttpWithDisabledSslCertCheck();
					client.getParams().setBooleanParameter("http.protocol.handle-redirects", true);
					response = client.execute(request);
				} catch (IOException e) {
					System.out.println("Failed to connect to " + url);
					downloadUrlIndex++;
				};
				if( response != null )
				{
					if( response.getStatusLine().getStatusCode() != 200 )
					{
						response = null;
						System.out.println("Failed to connect to " + url);
						downloadUrlIndex++;
					}
					else
						break;
				}
			}
		}
		if( FileInAssets )
		{
			int multipartCounter = 0;
			InputStream multipart = null;
			while( true )
			{
				try {
					// Make string ".zip00", ".zip01" etc for multipart archives
					String url1 = url + String.format("%02d", multipartCounter);
					CountingInputStream stream1 = new CountingInputStream(Parent.getAssets().open(url1), 8192);
					while( stream1.skip(65536) > 0 ) { };
					totalLen += stream1.getBytesRead();
					stream1.close();
					InputStream s = Parent.getAssets().open(url1);
					if( multipart == null )
						multipart = s;
					else
						multipart = new SequenceInputStream(multipart, s);
					System.out.println("Multipart archive found: " + url1);
				} catch( IOException e ) {
					break;
				}
				multipartCounter += 1;
			}
			if( multipart != null )
				stream = new CountingInputStream(multipart, 8192);
			else
			{
				try {
					stream = new CountingInputStream(Parent.getAssets().open(url), 8192);
					while( stream.skip(65536) > 0 ) { };
					totalLen += stream.getBytesRead();
					stream.close();
					stream = new CountingInputStream(Parent.getAssets().open(url), 8192);
				} catch( IOException e ) {
					System.out.println("Unpacking from assets '" + url + "' - error: " + e.toString());
					Status.setText( res.getString(R.string.error_dl_from, url) );
					return false;
				}
			}
		}
		else
		{
			if( response == null )
			{
				System.out.println("Error connecting to " + url);
				Status.setText( res.getString(R.string.failed_connecting_to, url) );
				return false;
			}

			Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_from, url) );
			totalLen = response.getEntity().getContentLength();
			try {
				stream = new CountingInputStream(response.getEntity().getContent(), 8192);
			} catch( java.io.IOException e ) {
				Status.setText( res.getString(R.string.error_dl_from, url) );
				return false;
			}
		}
		
		if(DoNotUnzip)
		{
			path = getOutFilePath(downloadUrls[downloadUrlIndex].substring( 1,
					downloadUrls[downloadUrlIndex].indexOf(":", 1) ));
			System.out.println("Saving file '" + path + "'");
			OutputStream out = null;
			try {
				try {
					File outDir = new File( path.substring(0, path.lastIndexOf("/") ));
					if( !(outDir.exists() && outDir.isDirectory()) )
						outDir.mkdirs();
				} catch( SecurityException e ) { };

				out = new FileOutputStream( path );
			} catch( FileNotFoundException e ) {
				System.out.println("Saving file '" + path + "' - error creating output file: " + e.toString());
			} catch( SecurityException e ) {
				System.out.println("Saving file '" + path + "' - error creating output file: " + e.toString());
			};
			if( out == null )
			{
				Status.setText( res.getString(R.string.error_write, path) );
				System.out.println("Saving file '" + path + "' - error creating output file");
				return false;
			}

			try {
				int len = stream.read(buf);
				while (len >= 0)
				{
					if(len > 0)
						out.write(buf, 0, len);
					len = stream.read(buf);

					float percent = 0.0f;
					if( totalLen > 0 )
						percent = stream.getBytesRead() * 100.0f / totalLen;
					Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
				}
				out.flush();
				out.close();
				out = null;
			} catch( java.io.IOException e ) {
				Status.setText( res.getString(R.string.error_write, path) );
				System.out.println("Saving file '" + path + "' - error writing: " + e.toString());
				return false;
			}
			System.out.println("Saving file '" + path + "' done");
		}
		else
		{
			System.out.println("Reading from zip file '" + url + "'");
			ZipInputStream zip = new ZipInputStream(stream);
			
			while(true)
			{
				ZipEntry entry = null;
				try {
					entry = zip.getNextEntry();
					if( entry != null )
						System.out.println("Reading from zip file '" + url + "' entry '" + entry.getName() + "'");
				} catch( java.io.IOException e ) {
					Status.setText( res.getString(R.string.error_dl_from, url) );
					System.out.println("Error reading from zip file '" + url + "': " + e.toString());
					return false;
				}
				if( entry == null )
				{
					System.out.println("Reading from zip file '" + url + "' finished");
					break;
				}
				if( entry.isDirectory() )
				{
					System.out.println("Creating dir '" + getOutFilePath(entry.getName()) + "'");
					try {
						File outDir = new File( getOutFilePath(entry.getName()) );
						if( !(outDir.exists() && outDir.isDirectory()) )
							outDir.mkdirs();
					} catch( SecurityException e ) { };
					continue;
				}

				OutputStream out = null;
				path = getOutFilePath(entry.getName());
				float percent = 0.0f;

				System.out.println("Saving file '" + path + "'");

				try {
					File outDir = new File( path.substring(0, path.lastIndexOf("/") ));
					if( !(outDir.exists() && outDir.isDirectory()) )
						outDir.mkdirs();
				} catch( SecurityException e ) { };
				
				try {
					CheckedInputStream check = new CheckedInputStream( new FileInputStream(path), new CRC32() );
					while( check.read(buf, 0, buf.length) >= 0 ) {};
					check.close();
					if( check.getChecksum().getValue() != entry.getCrc() )
					{
						File ff = new File(path);
						ff.delete();
						throw new Exception();
					}
					System.out.println("File '" + path + "' exists and passed CRC check - not overwriting it");
					if( totalLen > 0 )
						percent = stream.getBytesRead() * 100.0f / totalLen;
					Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
					continue;
				} catch( Exception e ) { }

				try {
					out = new FileOutputStream( path );
				} catch( FileNotFoundException e ) {
					System.out.println("Saving file '" + path + "' - cannot create file: " + e.toString());
				} catch( SecurityException e ) {
					System.out.println("Saving file '" + path + "' - cannot create file: " + e.toString());
				};
				if( out == null )
				{
					Status.setText( res.getString(R.string.error_write, path) );
					System.out.println("Saving file '" + path + "' - cannot create file");
					return false;
				}

				if( totalLen > 0 )
					percent = stream.getBytesRead() * 100.0f / totalLen;
				Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
				
				try {
					int len = zip.read(buf);
					while (len >= 0)
					{
						if(len > 0)
							out.write(buf, 0, len);
						len = zip.read(buf);

						percent = 0.0f;
						if( totalLen > 0 )
							percent = stream.getBytesRead() * 100.0f / totalLen;
						Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_progress, percent, path) );
					}
					out.flush();
					out.close();
					out = null;
				} catch( java.io.IOException e ) {
					Status.setText( res.getString(R.string.error_write, path) );
					System.out.println("Saving file '" + path + "' - error writing or downloading: " + e.toString());
					return false;
				}
				
				try {
					long count = 0, ret = 0;
					CheckedInputStream check = new CheckedInputStream( new FileInputStream(path), new CRC32() );
					while( ret >= 0 )
					{
						count += ret;
						ret = check.read(buf, 0, buf.length);
					}
					check.close();
					if( check.getChecksum().getValue() != entry.getCrc() || count != entry.getSize() )
					{
						File ff = new File(path);
						ff.delete();
						System.out.println("Saving file '" + path + "' - CRC check failed, ZIP: " +
											String.format("%x", entry.getCrc()) + " actual file: " + String.format("%x", check.getChecksum().getValue()) +
											" file size in ZIP: " + entry.getSize() + " actual size " + count );
						throw new Exception();
					}
				} catch( Exception e )
				{
					Status.setText( res.getString(R.string.error_write, path) );
					return false;
				}
				System.out.println("Saving file '" + path + "' done");
			}
		};

		OutputStream out = null;
		path = getOutFilePath(DownloadFlagFileName);
		try {
			out = new FileOutputStream( path );
			out.write(downloadUrls[downloadUrlIndex].getBytes("UTF-8"));
			out.flush();
			out.close();
		} catch( FileNotFoundException e ) {
		} catch( SecurityException e ) {
		} catch( java.io.IOException e ) {
			Status.setText( res.getString(R.string.error_write, path) );
			return false;
		};
		Status.setText( downloadCount + "/" + downloadTotal + ": " + res.getString(R.string.dl_finished) );

		try {
			stream.close();
		} catch( java.io.IOException e ) {
		};

		return true;
	};
	
	private void initParent()
	{
		class Callback implements Runnable
		{
			public MainActivity Parent;
			public void run()
			{
				Parent.initSDL();
			}
		}
		Callback cb = new Callback();
		synchronized(this) {
			cb.Parent = Parent;
			if(Parent != null)
				Parent.runOnUiThread(cb);
		}
	}
	
	private String getOutFilePath(final String filename)
	{
		return outFilesDir + "/" + filename;
	};
	
	private static DefaultHttpClient HttpWithDisabledSslCertCheck()
	{
		return new DefaultHttpClient();
		// This code does not work
		/*
        HostnameVerifier hostnameVerifier = org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        DefaultHttpClient client = new DefaultHttpClient();

        SchemeRegistry registry = new SchemeRegistry();
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        registry.register(new Scheme("https", socketFactory, 443));
        SingleClientConnManager mgr = new SingleClientConnManager(client.getParams(), registry);
        DefaultHttpClient http = new DefaultHttpClient(mgr, client.getParams());

        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

        return http;
		*/
	}
	
	public StatusWriter Status;
	public boolean DownloadComplete = false;
	public boolean DownloadFailed = false;
	private MainActivity Parent;
	private String outFilesDir = null;
}


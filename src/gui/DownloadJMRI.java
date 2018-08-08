package gui;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class DownloadJMRI {
	
	final JFrame_MainWindow jFrame_MainWindow;
	long lastTime;
	final PrintWriter info;
	final PrintWriter outputFile;
	final Map<String, String> cookies = new HashMap<>();
    
    int numMessages = 0;
	
	
	public DownloadJMRI(JFrame_MainWindow jFrame_MainWindow)
            throws FileNotFoundException,
                    UnsupportedEncodingException,
                    KeyManagementException,
                    NoSuchAlgorithmException {
        
		this.jFrame_MainWindow = jFrame_MainWindow;
		lastTime = System.currentTimeMillis();
		info = new PrintWriter("info.txt","UTF-8");
		outputFile = new PrintWriter("jmri_archive.txt","UTF-8");
        
        // configure the SSLContext with a TrustManager
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
        SSLContext.setDefault(ctx);
	}
	
	
	public String downloadPagePrim(String urlStr) throws IOException {
		
		try {
			System.out.println("Url: "+urlStr);
			URL url = new URL(urlStr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection(); // Cast shouldn't fail
            
			HttpURLConnection.setFollowRedirects(false);
			
			// allow both GZip and Deflate (ZLib) encodings
			conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
			conn.setRequestProperty("User-agent", "Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; rv:11.0) like Gecko");
			
			String encoding = conn.getContentEncoding();
			InputStream inStream;
			
			try {
				// create the appropriate stream wrapper based on
				// the encoding type
				if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
					inStream = new GZIPInputStream(conn.getInputStream());
				} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
					inStream = new InflaterInputStream(conn.getInputStream(),
						new Inflater(true));
				} else {
					inStream = conn.getInputStream();
				}
			}
			catch (FileNotFoundException e) {
				return null;
			}
			
			StringBuilder message = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inStream))) {
				String line;
				while ((line = reader.readLine()) != null) {
					message.append(line);
				}
			}
			
			return message.toString();
			
		} catch (MalformedURLException ex) {
			Logger.getLogger(DownloadJMRI.class.getName()).log(Level.SEVERE, null, ex);
		} catch (IOException ex) {
			Logger.getLogger(DownloadJMRI.class.getName()).log(Level.SEVERE, null, ex);
		}
		
		return null;
	}
	
	
	public String downloadPage(String urlStr) throws IOException {
        
        int retries = 0;
        
        while (retries < 3) {
            try {
                return downloadPagePrim(urlStr);
            } catch (IOException ex) {
                if (ex.getMessage().startsWith("Server returned HTTP response code: 504")) {
                    // Do nothing
                } else {
                    throw ex;
                }
            }
            retries++;
        }
        
        throw new IOException("Could not download page");
    }
    
    
	public void downloadAndParsePage(String url, int pageNo) throws IOException {
		
		String page = downloadPage(url + Integer.toString(pageNo));
		Document document = Jsoup.parse(page);
		info.write(document.toString());
		info.flush();
		
		Elements elements = document.select("[id^=msg]");
		for (Element element : elements) {
            
            numMessages++;
            
            String id = element.attr("id");
            System.out.format("%d, %d: id: %s\n", pageNo, numMessages, id);
            
            Element headerElement = element.child(0).child(0).child(0).child(0).child(0).child(0).child(0);
//            System.out.format("tag: %s\n", headerElement.tagName());
//            System.out.format("text: %s\n", headerElement.text());
            
            Element fromElement = element.child(0).child(0).child(0).child(0).child(1);
//            System.out.format("tag: %s\n", fromElement.tagName());
//            System.out.format("from: %s\n", fromElement.text());
            
            Element bodyElement = element.child(0).child(1).child(0).child(0);
//            System.out.format("tag: %s\n", bodyElement.tagName());
//            System.out.format("body: %s\n", bodyElement.text());
            
            outputFile.format("%s%n", fromElement.text());
            outputFile.format("%s%n", bodyElement.text());
            outputFile.println();
            outputFile.flush();
		}
	}
	
	
	public void download() throws IOException {
		
		int numPages = 1;
		numPages = 109;
		
		for (int i=46; i < numPages; i++) {
			
			downloadAndParsePage("https://sourceforge.net/p/jmri/mailman/jmri-developers/?limit=250&page=", i);
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				Logger.getLogger(DownloadJMRI.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
        
        info.close();
        outputFile.close();
        
        System.out.format("%n%n===================%n");
        System.out.format("Num messages: %d%n", numMessages);
	}
	
	
    
    private static class DefaultTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
    
}

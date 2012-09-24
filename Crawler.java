/** Description of My Web Crawler
*
* @version 1.0 Aug 2012 
*
* @author Xiaomou Wang
*/


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler implements Runnable {


	private HashMap<String, ArrayList<String>> disallowLinks = new HashMap<String, ArrayList<String>>();
	private String startUrl = null;
	private String searchString = null;
	private int maxUrlNum = 0;
	ArrayList<String> result = new ArrayList<String>();
	boolean caseSenstive = false;

	/**
	 * @param startUrl  the first URL to crawl
	 * @param searchString  the key words
	 * @param maxUrlNum the number of URLs we want to get
	 */
	public Crawler(String startUrl, String searchString, int maxUrlNum) {
		this.startUrl = startUrl;
		this.searchString = searchString;
		this.maxUrlNum = maxUrlNum;
	}

	/**
	 * 
	 * @return the result 
	 */
	public ArrayList<String> getResult() {
		return result;
	}
	
	/**
	 * 
	 * @param urlToVerify url to be check 
	 * @return if the URL is allowed to crawl
	 */
	private boolean ifURLAllowedToCrawl(URL urlToVerify) {
		String host = urlToVerify.getHost().toLowerCase();
		ArrayList<String> disallowList = disallowLinks.get(host);

		// if not visit the robots.txt file for this URL visit.
		if (disallowList == null) {
			disallowList = new ArrayList<String>();
			try {
				URL robotUrl = new URL("http://" + host + "/robots.txt");
				BufferedReader reader = new BufferedReader(
														 new InputStreamReader(robotUrl.openStream()));
				// read the file.
				String line;
				while ((line = reader.readLine()) != null) {
					// if there is any path that is forbidden to visit.
					int index = line.indexOf("Disallow:");
					if (index == 0) {
						// cut of the comment
						String file = line.substring("Dissallow".length());
						index = line.indexOf("#");
						if (index != -1) {
							file = file.substring(0, index);
						}
						file = file.trim();
						// add the path to the disallowed list on this page
						disallowList.add(file);
					}
				}

			} catch (IOException e) {
				System.out.println("NO Robot found " + e);
				return true;
			}
			disallowLinks.put(host, disallowList);
			//System.out.println(disallowList);
		} else {
			// Check if this url allow to visit
			String path = urlToVerify.getQuery();
			if (disallowList.contains(path))
				return false;
		}
		return true;
	}

	/**
	 * 
	 * @param url the string to be verify
	 * @return if the string format is legal URL change it to a URL
	 */
	private URL getUrl(String url) {
		// check if the url contain protocol area
		if (!url.toLowerCase().startsWith("http://"))
			return null;
		URL verifiedUrl = null;
		try {
			verifiedUrl = new URL(url);
		} catch (MalformedURLException e) {
			//System.out.println("Error in getUrl " + e);
			//System.out.println("URL is " + url);
			return null;
		}
		return verifiedUrl;
	}

	/**
	 * 
	 * @param pageUrl the URL of the web page to be download
	 * @return the web page content
	 * @throws IOException
	 */
	private String downLoad(URL pageUrl) throws IOException {
		try {
			// down load the web page of pageUrl
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					pageUrl.openStream()));
			StringBuffer page = new StringBuffer();
			String s = null;

			while ((s = reader.readLine()) != null) {
				page.append(s);
			}
			return page.toString();
		} catch (IOException e) {
			System.out.println("Eorror in downloading" + pageUrl + ":" + e);
			return null;
		}
	}

	/**
	 * 
	 * @param url the url of page which will be retrieve 
	 * @param pageContent page content of the certain page
	 * @param crawledUrl the set of urls which has been crawled 
	 * @return all links on this page
	 */
	private ArrayList<String> retrieveLink(URL url, String pageContent,
			HashSet<String> crawledUrl) {
		// regular express to find all links
		Pattern p = Pattern.compile("<a\\s+href=\"(.*?)[\"|>]",
				Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(pageContent);

		// to maintain the links on this page
		ArrayList<String> listOfLink = new ArrayList<String>();

		while (m.find()) {
			String link = m.group(1).trim();

			// if not a proper link
			if (link.length() < 1) {
				continue;
			}

			// ignore the link direct to a loaction in the same page
			if (link.charAt(0) == '#') {
				continue;
			}
			
			// ignore the link for email and javascript
			if ((link.indexOf("mailto") != -1)
					|| (link.toLowerCase().indexOf("javascript") != -1)) {
				continue;
			}

			URL verifiedLink = getUrl(link);

			// if not a correct url
			if (verifiedLink == null) {
				continue;
			}

			// if the link has been visited
			if (crawledUrl.contains(link)) {
				continue;
			}

			listOfLink.add(link);
		}

		return listOfLink;
	}

	/**
	 * 
	 * @param searchString the key words
	 * @param pageContent the page content
	 * @param isCaseSensitive whether is a case sensitive search
	 * @return if the page contain that key words
	 */
	private boolean searchStringMatchs(String searchString, String pageContent,
			boolean isCaseSensitive) {
		if (searchString.length() < 0 || pageContent.length() < 0l) {
			return false;
		}
		String pageToSearch = pageContent;
		if (!isCaseSensitive) {
			pageContent.toLowerCase();
		}
		
		// split the key words by space
		Pattern p = Pattern.compile("[\\s]+");
		String[] terms = p.split(searchString);
		
		// To find if the page has at least one key word
		for (int i = 0; i < terms.length; ++i) {
			if (isCaseSensitive) {
				if (pageToSearch.indexOf(terms[i]) == -1) {
					return false;
				}
			} else {
				if (pageToSearch.indexOf(terms[i].toLowerCase()) == -1) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 
	 * @param verifirdUrl the correct url to search
	 * @param crawledList urls that have been checked
	 * @param toCrawlList urls that not been checked
	 * @param tmpUrl the string in to crawl list
	 */
	private void checkIfUrlContainKey(URL verifirdUrl, HashSet<String> crawledList, ArrayList<String> toCrawlList, String tmpUrl) {
		String pageContent;
		try {
			// download the page
			pageContent = this.downLoad(verifirdUrl);

			if (pageContent == null) {
				System.out.println("Download failed");
				return;
			}
			
			// add Url to crawled list
			crawledList.add(tmpUrl);

			if (pageContent != null && pageContent.length() > 0) {
				// extract all links in this page
				ArrayList<String> links = retrieveLink(verifirdUrl,
						pageContent, crawledList);
				
				// add them to the to crawl list
				toCrawlList.addAll(links);

				// check if the page contain key words
				if (searchStringMatchs(pageContent, searchString,caseSenstive)) {
					result.add(tmpUrl);
					System.out.println(tmpUrl);
				}
				System.out.println(tmpUrl);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 
	 * @param startUrl the start url
	 * @param maxUrlNum	 the max number of urls we want
	 * @param searchString the key words
 	 * @param isCaseSensitive if the search is a case sensative search
	 * @return result set
	 */
	public ArrayList<String> crawl(String startUrl, int maxUrlNum,
			String searchString, boolean isCaseSensitive) {
		System.out.println("seach String " + searchString);
		
		// the links visited
		HashSet<String> crawledList = new HashSet<String>();
		
		// the links to be visit
		ArrayList<String> toCrawlList = new ArrayList<String>();
		
		if (startUrl.length() < 1) {
			System.out.println("wrong strat URL");
			return null;
		}

		if (maxUrlNum < 1) {
			System.out.println("max is less than one");
			return null;
		}


		toCrawlList.add(this.startUrl);

		while (toCrawlList.size() > 0) {
			
			if (crawledList.size() == maxUrlNum)
				break;
			
			// get a url
			String tmpUrl = toCrawlList.iterator().next();
			toCrawlList.remove(tmpUrl);
			
			// check if it a correct url
			URL verifirdUrl = getUrl(tmpUrl);
			if (verifirdUrl == null)
				continue;
			
			// if url is allow to visit
			if (!ifURLAllowedToCrawl(verifirdUrl))
				continue;
			
			// down load and check the page
			 checkIfUrlContainKey(verifirdUrl, crawledList, toCrawlList,tmpUrl); 
		}
		return this.result;
	}

	/**
	 * 
	 */
	public void run() {
		System.out.println("thread created");
		crawl(startUrl, maxUrlNum, searchString, caseSenstive);
		System.out.println("thread exit");
	}

	/**
	 * 
	 * @param args intput argument should be start Url max number of urls and key words
	 */
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("Input should be start Url,  max number of urls and key words");
			return;
		}
		int max = Integer.parseInt(args[1]);
		Crawler crawler = new Crawler(args[0], args[2], max);
		Thread worker = new Thread(crawler);
		System.out.println("Start searching...");
		System.out.println("result:");
		worker.start();
	}

}

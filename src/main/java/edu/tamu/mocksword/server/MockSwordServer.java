package edu.tamu.mocksword.server;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.purl.sword.atom.Author;
import org.purl.sword.atom.Content;
import org.purl.sword.atom.Contributor;
import org.purl.sword.atom.Generator;
import org.purl.sword.atom.InvalidMediaTypeException;
import org.purl.sword.atom.Summary;
import org.purl.sword.atom.Title;
import org.purl.sword.base.AtomDocumentRequest;
import org.purl.sword.base.AtomDocumentResponse;
import org.purl.sword.base.Collection;
import org.purl.sword.base.Deposit;
import org.purl.sword.base.DepositResponse;
import org.purl.sword.base.ErrorCodes;
import org.purl.sword.base.SWORDAuthenticationException;
import org.purl.sword.base.SWORDEntry;
import org.purl.sword.base.SWORDErrorException;
import org.purl.sword.base.SWORDException;
import org.purl.sword.base.Service;
import org.purl.sword.base.ServiceDocument;
import org.purl.sword.base.ServiceDocumentRequest;
import org.purl.sword.base.Workspace;
import org.purl.sword.server.SWORDServer;

/**
 * This is a simple (aka very dumb) mock sword server. It will respond to
 * service document requests and deposit requests correctly but will not
 * actually depost the requests into anything. Instead they will just be dropped
 * on the floor.
 * 
 * The workspaces and collections are hard coded in the application, providing
 * three deposit collections (a,b,c) across two workspaces. For authentication
 * any username and password pair are accepted as long as they are the same
 * value. Also if no username or password is supplied then the credentials are
 * not checked.
 * 
 * @author Alexey Maslov
 * @author Scott Phillips, http://www.scottphillips.com
 */
public class MockSwordServer implements SWORDServer {

	private static Logger log = Logger.getLogger(MockSwordServer.class);


	/**
	 * A counter to count submissions, so the response to a deposit can
	 * increment
	 */
	private static int counter = 0;

	/**
	 * Provides a dumb but plausible service document - it contains two
	 * workspaces with three collections split between them, and one
	 * personalized for the onBehalfOf user.
	 * 
	 * @param onBehalfOf
	 *            The user that the client is acting on behalf of
	 */
	public ServiceDocument doServiceDocument(ServiceDocumentRequest sdr)
			throws SWORDAuthenticationException, SWORDErrorException,
			SWORDException {
		// Authenticate the user
		String username = sdr.getUsername();
		String password = sdr.getPassword();
		if ((username != null)
				&& (password != null)
				&& (((username.equals("")) && (password.equals(""))) || (!username
						.equalsIgnoreCase(password)))) {
			// User not authenticated
			log.info("User failed credentials check with username='"+username+"', password='"+password+"'");
			throw new SWORDAuthenticationException("Bad credentials");
		}

		// Allow users to force the throwing of a SWORD error exception by
		// setting the OBO user to 'error'
		if ((sdr.getOnBehalfOf() != null)
				&& (sdr.getOnBehalfOf().equals("error"))) {
			// Throw the error exception
			log.info("OnBehalfOf user set to 'error', throwing a meaningless exception.");
			throw new SWORDErrorException(ErrorCodes.MEDIATION_NOT_ALLOWED, "Mediated deposits not allowed");
		}

		// Create and return a dummy ServiceDocument
		ServiceDocument document = new ServiceDocument();
		Service service = new Service("1.3", true, true);
		document.setService(service);

		String location = sdr.getLocation().substring(0,
				sdr.getLocation().length() - 16);

		// Workspace 1
		Workspace workspace = new Workspace();
		workspace.setTitle("Workspace 1");
		service.addWorkspace(workspace);

		// Collection A
		Collection collection = new Collection();
		collection.setLocation(location + "/deposit/a");
		collection.setTitle("Collection A");
		collection.addAccepts("application/zip");
		collection.addAcceptPackaging(
				"http://purl.org/net/sword-types/METSDSpaceSIP", (float) 1.0);
		collection
		.setCollectionPolicy("This collection does not actually exist and will not receive deposits. ");
		collection.setAbstract("This is test collection.");
		collection.setMediation(true);
		workspace.addCollection(collection);

		// Collection B
		collection = new Collection();
		collection.setLocation(location + "/deposit/b");
		collection.setTitle("Collection B");
		collection.addAccepts("application/zip");
		collection.addAcceptPackaging("http://purl.org/net/sword-types/METSDSpaceSIP", (float) 1.0);
		collection.setCollectionPolicy("This collection does not actually exist and will not receive deposits. ");
		collection.setAbstract("This is test collection.");
		collection.setMediation(true);
		workspace.addCollection(collection);

		// Workspace 1
		workspace = new Workspace();
		workspace.setTitle("Workspace 2");
		service.addWorkspace(workspace);

		// Collection C
		collection = new Collection();
		collection.setLocation(location + "/deposit/c");
		collection.setTitle("Collection C");
		collection.addAccepts("application/zip");
		collection.addAcceptPackaging("http://purl.org/net/sword-types/METSDSpaceSIP", (float) 1.0);
		collection.setCollectionPolicy("This collection does not actually exist and will not receive deposits. ");
		collection.setAbstract("This is test collection.");
		collection.setMediation(true);
		workspace.addCollection(collection);

		log.info("Generated mock service document with three collections across two workspaces.");
		return document;
	}

	/**
	 * Tests deposits for the presence of a zip file containing a mets manifest
	 * with key metadata values. 
	 * 
	 * @param onBehalfOf
	 *            The user that the client is acting on behalf of
	 * @throws SWORDAuthenticationException
	 *             If the credentials are bad
	 * @throws SWORDErrorException
	 *             If something goes wrong, such as
	 */
	public DepositResponse doDeposit(Deposit deposit)
			throws SWORDAuthenticationException, SWORDErrorException,
			SWORDException {

		// Authenticate the user
		String username = deposit.getUsername();
		String password = deposit.getPassword();
		if ((username != null)
				&& (password != null)
				&& (((username.equals("")) && (password.equals(""))) || (!username
						.equalsIgnoreCase(password)))) {
			// User not authenticated
			log.info("User failed credentials check with username='"+username+"', password='"+password+"'");
			throw new SWORDAuthenticationException("Bad credentials");
		}

		// Check this is a collection that takes obo deposits, else thrown an
		// error
		if (((deposit.getOnBehalfOf() != null) && (!deposit.getOnBehalfOf()
				.equals("")))
				&& (!deposit.getLocation().contains("deposit?user="))) {
			log.info("OnBehalfOf user set to 'error', throwing a meaningless exception.");
			throw new SWORDErrorException(ErrorCodes.MEDIATION_NOT_ALLOWED,
					"Mediated deposit not allowed to this collection");
		}

		// Get the filenames
		boolean metsFound = false;
		StringBuffer filenames = new StringBuffer("Deposit file contained: ");
		if (deposit.getFilename() != null) {
			filenames.append("(filename = " + deposit.getFilename() + ") ");
		}
		if (deposit.getSlug() != null) {
			filenames.append("(slug = " + deposit.getSlug() + ") ");
		}
		try {
			ZipInputStream zip = new ZipInputStream(deposit.getFile());
			ZipEntry ze;
			while ((ze = zip.getNextEntry()) != null) {

				filenames.append(" " + ze.toString());

				/* Build the mets.xml file as a string */
				String rawMets = "";
				StringBuilder sb = new StringBuilder();
				if ("mets.xml".equals(ze.getName())) {
					metsFound = true;
					byte[] buf = new byte[1024];
					int len;

					while ((len = zip.read(buf)) > 0) {
						sb.append(new String(buf, 0, len));
					}

					rawMets = sb.toString();

					SAXBuilder builder = new SAXBuilder();
					Document doc = builder.build(new StringReader(rawMets));
					Map<String, Element> tests = new HashMap<String, Element>();

					// Three required metadata fields
					tests.put("dc.title", getField(doc, "dc", "title"));
					//tests.put("dc.creator", getField(doc, "dc", "creator"));
					//tests.put("dc.date", getField(doc, "dc", "date"));

					// If any of the fields come back empty, we are missing
					// metadata. Something is wrong.
					if (tests.containsValue(null) || tests.containsValue("")) {
						for (String key : tests.keySet()) {
							if(tests.get(key) == null|| "".equals(tests.get(key))){
								log.info("Required field is missing or empty: "+ key);
							}
						}
						throw new SWORDException(
								"Missing or empty required fields", null,
								ErrorCodes.ERROR_CONTENT);
					}
				}
			}
		} catch (IOException ioe) {
			log.error("Failed to open deposited zip file",ioe);
			throw new SWORDException("Failed to open deposited zip file", null,
					ErrorCodes.ERROR_CONTENT);
		} catch (JDOMException e) {
			log.error("Encountered a JDOM exception while procesing the metadata",e);
			throw new SWORDException("Encountered a JDOM exception while procesing the metadata", null,
					ErrorCodes.ERROR_CONTENT);
		} catch (RuntimeException re) {
			log.error("Encountered a Runtime Exception while procesing the metadata",re);
			throw new SWORDException("Encountered a Runtime Exception while procesing the metadata", null,
					ErrorCodes.ERROR_CONTENT);
		}

		if ( !metsFound ) {
			log.error("The deposit package did not contain a mets manifest.");
			throw new SWORDException("Mets manifest document was not found, unable to process item deposit.");
		}


		// Handle the deposit
		if (!deposit.isNoOp()) {
			counter++;
		}
		DepositResponse dr = new DepositResponse(Deposit.CREATED);
		SWORDEntry se = new SWORDEntry();

		Title t = new Title();
		t.setContent("DummyServer Deposit: #" + counter);
		se.setTitle(t);

		se.addCategory("Category");

		if (deposit.getSlug() != null) {
			se.setId(deposit.getSlug() + " - ID: " + counter);
		} else {
			se.setId("ID: " + counter);
		}

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		TimeZone utc = TimeZone.getTimeZone("UTC");
		sdf.setTimeZone(utc);
		String milliFormat = sdf.format(new Date());
		se.setUpdated(milliFormat);

		Summary s = new Summary();
		s.setContent(filenames.toString());
		se.setSummary(s);
		Author a = new Author();
		if (username != null) {
			a.setName(username);
		} else {
			a.setName("unknown");
		}
		se.addAuthors(a);

		if (deposit.getOnBehalfOf() != null) {
			Contributor c = new Contributor();
			c.setName(deposit.getOnBehalfOf());
			c.setEmail(deposit.getOnBehalfOf() + "@swordapp.org");
			se.addContributor(c);
		}

		Generator generator = new Generator();
		generator.setContent("Mock SWORD Server");
		generator.setUri("http://localhost/");
		generator.setVersion("1.3");
		se.setGenerator(generator);

		Content content = new Content();
		try {
			content.setType("application/zip");
		} catch (InvalidMediaTypeException ex) {
			ex.printStackTrace();
		}
		content.setSource("http://localhost/uploads/upload-" + counter
				+ ".zip");
		se.setContent(content);

		// A required human-readable field
		se.setTreatment("The mock submission was treated with all the respect and dignity befitting its status.");

		if (deposit.isVerbose()) {
			se.setVerboseDescription("Verbose is on");
		}

		se.setNoOp(deposit.isNoOp());

		dr.setEntry(se);

		dr.setLocation("http://localhost/" + counter);

		return dr;
	}

	/**
	 * Parse the included JDOM element and pull out the specified dc value from
	 * its descriptive metadata section
	 * 
	 * @param doc
	 * @param schema
	 * @param element
	 * @param qualifier
	 * @return
	 * @throws JDOMException
	 */
	private Element getField(Document doc, String schema, String element,
			String qualifier) throws JDOMException {
		if (qualifier == null)
			return getField(doc, schema, element);

		XPath x = XPath
				.newInstance("/mets:mets/mets:dmdSec/mets:mdWrap/mets:xmlData/dim:dim/dim:field"
						+ "[@mdschema='"
						+ schema
						+ "'][@element='"
						+ element
						+ "'][@qualifier='" + qualifier + "']");
		x.addNamespace("mets", "http://www.loc.gov/METS/");
		x.addNamespace("dim", "http://www.dspace.org/xmlns/dspace/dim");

		Element test = (Element) x.selectSingleNode(doc);

		return test;
	}

	/**
	 * Parse the included JDOM element and pull out the specified dc value from
	 * its descriptive metadata section
	 * 
	 * @param doc
	 * @param schema
	 * @param element
	 * @return
	 * @throws JDOMException
	 */
	private Element getField(Document doc, String schema, String element)
			throws JDOMException {
		XPath x = XPath
				.newInstance("/mets:mets/mets:dmdSec/mets:mdWrap/mets:xmlData/dim:dim/dim:field"
						+ "[@mdschema='"
						+ schema
						+ "'][@element='"
						+ element
						+ "']");
		x.addNamespace("mets", "http://www.loc.gov/METS/");
		x.addNamespace("dim", "http://www.dspace.org/xmlns/dspace/dim");

		Element test = (Element) x.selectSingleNode(doc);

		return test;
	}

	public AtomDocumentResponse doAtomDocument(AtomDocumentRequest adr)
			throws SWORDAuthenticationException, SWORDErrorException,
			SWORDException {
		// Authenticate the user
		String username = adr.getUsername();
		String password = adr.getPassword();
		if ((username != null)
				&& (password != null)
				&& (((username.equals("")) && (password.equals(""))) || (!username
						.equalsIgnoreCase(password)))) {
			// User not authenticated
			throw new SWORDAuthenticationException("Bad credentials");
		}

		return new AtomDocumentResponse(HttpServletResponse.SC_OK);
	}


	/** private field for the running Jetty service **/
	private static Server server;

	/**
	 * Start the mock sword server running on the local host at the provided port number.
	 */
	public static void start(int port) throws Exception {

		server = new Server(port);
		Context root = new Context(server,"/",Context.SESSIONS);
		Map<String,String> initParams = new HashMap<String,String>();

		// Configure our SWORD Server
		String swordServer = new MockSwordServer().getClass().getCanonicalName();
		initParams.put("sword-server-class", swordServer);
		root.setInitParams(initParams);

		// Install the two SWORD Servlets
		root.addServlet(new ServletHolder(new MockSwordDepositServlet()),"/deposit/*");
		root.addServlet(new ServletHolder(new MockSwordServiceDocumentServlet()),"/servicedocument/*");

		// Start the server
		server.start();
	}  

	/**
	 * Stop the mock sword server running on the local host.
	 */
	public static void stop() throws Exception {
		if (server != null) {
			server.stop();
			server = null;
		}
	}
	//	

}

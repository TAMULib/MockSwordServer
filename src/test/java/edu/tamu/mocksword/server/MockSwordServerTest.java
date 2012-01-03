package edu.tamu.mocksword.server;

import java.io.File;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.purl.sword.base.Collection;
import org.purl.sword.base.DepositResponse;
import org.purl.sword.base.Service;
import org.purl.sword.base.ServiceDocument;
import org.purl.sword.base.Workspace;
import org.purl.sword.client.Client;
import org.purl.sword.client.ClientConstants;
import org.purl.sword.client.PostMessage;
import org.purl.sword.client.SWORDClientException;

/**
 * Unit test for the mock sword server
 */
public class MockSwordServerTest
{

	
	@BeforeClass
	public static void startMockSwordServer() throws Exception {
		MockSwordServer.start(8082);
	}
	
	@AfterClass
	public static void stopMockSwordServer() throws Exception {
		MockSwordServer.stop();
	}
	
	@Test
    public void testServiceDocument() throws Exception
    {
        
        Client client = new Client();
        client.setServer("localhost", 8082);
        client.setCredentials("testUser", "testPassword");
        
        // Get the service document 
        ServiceDocument serviceDocument = client.getServiceDocument("http://localhost:8082/servicedocument");
        assertNotNull(serviceDocument);        
        Service service = serviceDocument.getService();
        assertNotNull(service);
        
        // Sword collections are grouped together into workspaces. 
        List<Workspace> workspaces = service.getWorkspacesList();
        assertNotNull(workspaces);
        assertEquals(2,workspaces.size());
        
        // Verify the first workspace.
        Workspace workspace = workspaces.get(0);
        assertEquals("Workspace 1",workspace.getTitle());
        List<Collection> collections = workspace.getCollections();
        assertNotNull(collections);
        assertEquals(2,collections.size());
        assertEquals("Collection A",collections.get(0).getTitle());
        assertEquals("http://localhost:8082/deposit/a",collections.get(0).getLocation());
        assertEquals("Collection B",collections.get(1).getTitle());
        assertEquals("http://localhost:8082/deposit/b",collections.get(1).getLocation());
        
        // Verify the second workspace
        workspace = workspaces.get(1);
        assertEquals("Workspace 2",workspace.getTitle());
        collections = workspace.getCollections();
        assertNotNull(collections);
        assertEquals(1,collections.size());
        assertEquals("Collection C",collections.get(0).getTitle());
        assertEquals("http://localhost:8082/deposit/c",collections.get(0).getLocation());
    }
	
	
	@Test(expected=SWORDClientException.class)
	public void testServiceDocumentWithBadAuth() throws SWORDClientException {
		
		Client client = new Client();
        client.setServer("localhost", 8082);
        client.setCredentials("invalidUser", "invalidPassword");
        
        // Get the service document 
        ServiceDocument serviceDocument = client.getServiceDocument("http://localhost:8082/servicedocument");
	}
	
	@Test(expected=SWORDClientException.class)
	public void testServiceDocumentWithOnBehalfOfError() throws SWORDClientException {
		
		Client client = new Client();
        client.setServer("localhost", 8082);
        client.setCredentials("testUser", "testPassword");
        
        // Get the service document 
        ServiceDocument serviceDocument = client.getServiceDocument("http://localhost:8082/servicedocument","error");
	}
	
	@Test
	public void testValidDeposit() throws Exception 
	{
		
        Client client = new Client();
        client.setServer("localhost", 8082);
        client.setCredentials("testUser", "testPassword");
        
        File depositPackage = new File("src/main/resources/validDeposit.zip");
        assertTrue(depositPackage.exists());
        
        PostMessage message = new PostMessage();
        message.setFilepath(depositPackage.getAbsolutePath());
        message.setDestination("http://localhost:8082/deposit/c");
        message.setFiletype("application/zip");
        message.setUseMD5(false);
        message.setVerbose(false);
        message.setNoOp(false);
        message.setFormatNamespace("http://purl.org/net/sword-types/METSDSpaceSIP");
        message.setSlug("");
        message.setChecksumError(false);
        message.setUserAgent(ClientConstants.SERVICE_NAME);
        
        DepositResponse response = client.postFile(message);
        assertEquals(201,response.getHttpResponse());
        assertEquals("ID: 1",response.getEntry().getId());
	}
	
	@Test(expected=SWORDClientException.class)
	public void testInvalidDeposit() throws Exception 
	{
		
        Client client = new Client();
        client.setServer("localhost", 8082);
        client.setCredentials("testUser", "testPassword");
        
        File depositPackage = new File("src/main/resources/invalidDeposit.zip");
        assertTrue(depositPackage.exists());
        
        PostMessage message = new PostMessage();
        message.setFilepath(depositPackage.getAbsolutePath());
        message.setDestination("http://localhost:8082/deposit/c");
        message.setFiletype("application/zip");
        message.setUseMD5(false);
        message.setVerbose(false);
        message.setNoOp(false);
        message.setFormatNamespace("http://purl.org/net/sword-types/METSDSpaceSIP");
        message.setSlug("");
        message.setChecksumError(false);
        message.setUserAgent(ClientConstants.SERVICE_NAME);
        
        client.postFile(message);
	}
	
}

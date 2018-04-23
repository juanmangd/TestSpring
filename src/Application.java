

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.llrp.ltk.exceptions.InvalidLLRPMessageException;
import org.llrp.ltk.generated.LLRPMessageFactory;
import org.llrp.ltk.generated.enumerations.AISpecStopTriggerType;
import org.llrp.ltk.generated.enumerations.AccessReportTriggerType;
import org.llrp.ltk.generated.enumerations.AirProtocols;
import org.llrp.ltk.generated.enumerations.GetReaderCapabilitiesRequestedData;
import org.llrp.ltk.generated.enumerations.NotificationEventType;
import org.llrp.ltk.generated.enumerations.ROReportTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecStartTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecState;
import org.llrp.ltk.generated.enumerations.ROSpecStopTriggerType;
import org.llrp.ltk.generated.messages.ADD_ROSPEC;
import org.llrp.ltk.generated.messages.ADD_ROSPEC_RESPONSE;
import org.llrp.ltk.generated.messages.CLOSE_CONNECTION;
import org.llrp.ltk.generated.messages.DELETE_ROSPEC;
import org.llrp.ltk.generated.messages.DISABLE_ROSPEC;
import org.llrp.ltk.generated.messages.ENABLE_EVENTS_AND_REPORTS;
import org.llrp.ltk.generated.messages.ENABLE_ROSPEC;
import org.llrp.ltk.generated.messages.GET_READER_CAPABILITIES;
import org.llrp.ltk.generated.messages.READER_EVENT_NOTIFICATION;
import org.llrp.ltk.generated.messages.SET_READER_CONFIG;
import org.llrp.ltk.generated.messages.START_ROSPEC;
import org.llrp.ltk.generated.messages.STOP_ROSPEC;
import org.llrp.ltk.generated.parameters.AISpec;
import org.llrp.ltk.generated.parameters.AISpecStopTrigger;
import org.llrp.ltk.generated.parameters.AccessReportSpec;
import org.llrp.ltk.generated.parameters.C1G2EPCMemorySelector;
import org.llrp.ltk.generated.parameters.EventNotificationState;
import org.llrp.ltk.generated.parameters.InventoryParameterSpec;
import org.llrp.ltk.generated.parameters.ROBoundarySpec;
import org.llrp.ltk.generated.parameters.ROReportSpec;
import org.llrp.ltk.generated.parameters.ROSpec;
import org.llrp.ltk.generated.parameters.ROSpecStartTrigger;
import org.llrp.ltk.generated.parameters.ROSpecStopTrigger;
import org.llrp.ltk.generated.parameters.ReaderEventNotificationData;
import org.llrp.ltk.generated.parameters.ReaderEventNotificationSpec;
import org.llrp.ltk.generated.parameters.TagReportContentSelector;
import org.llrp.ltk.types.Bit;
import org.llrp.ltk.types.LLRPMessage;
import org.llrp.ltk.types.UnsignedByte;
import org.llrp.ltk.types.UnsignedInteger;
import org.llrp.ltk.types.UnsignedShort;
import org.llrp.ltk.types.UnsignedShortArray;
//import org.omg.CORBA.NameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class Application  {

	private static String ipAddress = "73.205.181.2";//10.0.0.1
	private static int readerPort = 50007;
	private static int attempsNumber = 3;
	private Socket connection;
	private DataOutputStream out;
	private static int ROSPEC_ID = 1;
	private ReadThread rt = null;

	public static void main(String[] args) {

		if (args.length < 2 || args.length > 3) {
			ipAddress = "73.205.181.2";
			readerPort = 5084;
			attempsNumber = 3;
			System.out.println("No IP address and port were supplied.  Using "
					+ ipAddress + ":" + readerPort);
		} else if(args.length == 2){
			ipAddress = args[0];
			readerPort = Integer.parseInt(args[1]);
			attempsNumber = 3;
		}else if(args.length == 3)
		{
			ipAddress = args[0];
			readerPort = Integer.parseInt(args[1]);
			attempsNumber = Integer.parseInt(args[2]);
		}

		try {
			
			for(int i = 0 ; i < attempsNumber ; ++i)
			{
				new Application();
				System.out.println("LLRP has terminated");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public Application() throws IOException {
		
		// Try to establish a connection to the reader
		connection = new Socket(ipAddress, readerPort);
		out = new DataOutputStream(connection.getOutputStream());

		// Start up the ReaderThread to read messages form socket to Console
		rt = new ReadThread(connection);
		rt.start();

		// Wait for the NotificationEvent the Reader sends whenever a
		// connection attempt is made
		pause(250);
		LLRPMessage m = rt.getNextMessage();
		READER_EVENT_NOTIFICATION readerEventNotification = (READER_EVENT_NOTIFICATION) m;
		ReaderEventNotificationData red = readerEventNotification
				.getReaderEventNotificationData();
		if (red.getConnectionAttemptEvent() != null) {
			System.out.println("Connection attempt was successful\n");
		}else{
			System.out.println("Connection attempt was unsucessful");
			System.exit(-1);
		}

		// Create a GET_READER_CAPABILITIES Message and send it to the reader
		GET_READER_CAPABILITIES getReaderCap = new GET_READER_CAPABILITIES();
		getReaderCap.setRequestedData(new GetReaderCapabilitiesRequestedData(
				GetReaderCapabilitiesRequestedData.All));
		write(getReaderCap, "GET_READER_CAPABILITIES");
		pause(250);

		// Create a SET_READER_CONFIG Message and send it to the reader
		SET_READER_CONFIG setReaderConfig = createSetReaderConfig();
		write(setReaderConfig, "SET_READER_CONFIG");
		pause(250);

		//CREATE an ADD_ROSPEC Message and send it to the reader
		ADD_ROSPEC addROSpec = new ADD_ROSPEC();
		addROSpec.setROSpec(createROSpec());
		write(addROSpec, "ADD_ROSPEC");
		pause(250);
		
		ADD_ROSPEC_RESPONSE response = new ADD_ROSPEC_RESPONSE();
		

		//Create an ENABLE_ROSPEC message and send it to the reader
		ENABLE_ROSPEC enableROSpec = new ENABLE_ROSPEC();
		enableROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
		write(enableROSpec, "ENABLE_ROSPEC");
		pause(250);

		//Create a START_ROSPEC message and send it to the reader
		START_ROSPEC startROSpec = new START_ROSPEC();
		startROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
		write(startROSpec, "START_ROSPEC");
		//wait for five seconds for tag reads
		pause(500);

		
		
		ENABLE_EVENTS_AND_REPORTS report = new ENABLE_EVENTS_AND_REPORTS();
		write(report, "ENABLE_EVENTS_AND_REPORTS");
		
		

		//Create a STOP_ROSPEC message and send it to the reader
		STOP_ROSPEC stopROSpec = new STOP_ROSPEC();
		stopROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
		write(stopROSpec, "STOP_ROSPEC");
		pause(250);

		//Create a DISABLE_ROSPEC message and send it to the reader
		DISABLE_ROSPEC disableROSpec = new DISABLE_ROSPEC();
		disableROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
		write(disableROSpec, "DISABLE_ROSPEC");
		pause(250);

		//Create a DELTE_ROSPEC message and send it to the reader
		DELETE_ROSPEC deleteROSpec = new DELETE_ROSPEC();
		deleteROSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));
		write(deleteROSpec, "DELETE_ROSPEC");
		pause(250);

		//wait for one second before closing the connection
		pause(1000);

		// Create a CLOSE_CONNECTION message and send it to the reader
		CLOSE_CONNECTION cc = new CLOSE_CONNECTION();
		write(cc, "CloseConnection");

		
		synchronized (rt) {
			try {
				System.out.println("\n Wait for the Reader to close the Connection");
				rt.wait();
			} catch (InterruptedException e) {
				// Quit the Program
			}
		}

	}
	
	private ROSpec createROSpec() {
		
		//create a new rospec
		ROSpec roSpec = new ROSpec();
		roSpec.setPriority(new UnsignedByte(0));
		roSpec.setCurrentState(new ROSpecState(ROSpecState.Disabled));
		roSpec.setROSpecID(new UnsignedInteger(ROSPEC_ID));

		//set up ROBoundary (start and stop triggers)
		ROBoundarySpec roBoundarySpec = new ROBoundarySpec();

		ROSpecStartTrigger startTrig = new ROSpecStartTrigger();
		startTrig.setROSpecStartTriggerType(new ROSpecStartTriggerType(
				ROSpecStartTriggerType.Null));
		roBoundarySpec.setROSpecStartTrigger(startTrig);

		ROSpecStopTrigger stopTrig = new ROSpecStopTrigger();
		stopTrig.setDurationTriggerValue(new UnsignedInteger(0));
		stopTrig.setROSpecStopTriggerType(new ROSpecStopTriggerType(
				ROSpecStopTriggerType.Null));
		roBoundarySpec.setROSpecStopTrigger(stopTrig);

		roSpec.setROBoundarySpec(roBoundarySpec);

		//Add an AISpec
		AISpec aispec = new AISpec();
		
		//set AI Stop trigger to null
		AISpecStopTrigger aiStopTrigger = new AISpecStopTrigger();
		aiStopTrigger.setAISpecStopTriggerType(new AISpecStopTriggerType(
				AISpecStopTriggerType.Null));
		aiStopTrigger.setDurationTrigger(new UnsignedInteger(0));
		aispec.setAISpecStopTrigger(aiStopTrigger);

		UnsignedShortArray antennaIDs = new UnsignedShortArray();
		antennaIDs.add(new UnsignedShort(0));
		aispec.setAntennaIDs(antennaIDs);

		InventoryParameterSpec inventoryParam = new InventoryParameterSpec();
		inventoryParam.setProtocolID(new AirProtocols(
				AirProtocols.EPCGlobalClass1Gen2));
		inventoryParam.setInventoryParameterSpecID(new UnsignedShort(1));
		aispec.addToInventoryParameterSpecList(inventoryParam);

		roSpec.addToSpecParameterList(aispec);
		
		return roSpec;
	}

	private SET_READER_CONFIG createSetReaderConfig() {
		SET_READER_CONFIG setReaderConfig = new SET_READER_CONFIG();

		// Create a default RoReportSpec so that reports are sent at the end of ROSpecs
		 
		ROReportSpec roReportSpec = new ROReportSpec();
		roReportSpec.setN(new UnsignedShort(0));
		roReportSpec.setROReportTrigger(new ROReportTriggerType(
				ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec));
		TagReportContentSelector tagReportContentSelector = new TagReportContentSelector();
		tagReportContentSelector.setEnableAccessSpecID(new Bit(0));
		tagReportContentSelector.setEnableChannelIndex(new Bit(0));
		tagReportContentSelector.setEnableFirstSeenTimestamp(new Bit(0));
		tagReportContentSelector.setEnableInventoryParameterSpecID(new Bit(0));
		tagReportContentSelector.setEnableLastSeenTimestamp(new Bit(0));
		tagReportContentSelector.setEnablePeakRSSI(new Bit(0));
		tagReportContentSelector.setEnableSpecIndex(new Bit(0));
		tagReportContentSelector.setEnableTagSeenCount(new Bit(0));
		
		tagReportContentSelector.setEnableAntennaID(new Bit(1));
		tagReportContentSelector.setEnableROSpecID(new Bit(1));
		
		C1G2EPCMemorySelector epcMemSel = new C1G2EPCMemorySelector();
		epcMemSel.setEnableCRC(new Bit(0));
		epcMemSel.setEnablePCBits(new Bit(0));
		tagReportContentSelector
				.addToAirProtocolEPCMemorySelectorList(epcMemSel);
		roReportSpec.setTagReportContentSelector(tagReportContentSelector);
		setReaderConfig.setROReportSpec(roReportSpec);

		//  Set default AccessReportSpec
		 
		AccessReportSpec accessReportSpec = new AccessReportSpec();
		accessReportSpec.setAccessReportTrigger(new AccessReportTriggerType(
				AccessReportTriggerType.Whenever_ROReport_Is_Generated));
		setReaderConfig.setAccessReportSpec(accessReportSpec);

		// Set up reporting for AISpec events, ROSpec events, and GPI Events
		 
		ReaderEventNotificationSpec eventNoteSpec = new ReaderEventNotificationSpec();
		EventNotificationState noteState = new EventNotificationState();
		noteState.setEventType(new NotificationEventType(
				NotificationEventType.AISpec_Event));
		noteState.setNotificationState(new Bit(1));
		eventNoteSpec.addToEventNotificationStateList(noteState);
		noteState = new EventNotificationState();
		noteState.setEventType(new NotificationEventType(
				NotificationEventType.ROSpec_Event));
		noteState.setNotificationState(new Bit(1));
		eventNoteSpec.addToEventNotificationStateList(noteState);
		
		setReaderConfig.setReaderEventNotificationSpec(eventNoteSpec);

		setReaderConfig.setResetToFactoryDefault(new Bit(0));

		return setReaderConfig;

	}

	private void pause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private String HexToASCII(String hex)
	{
		StringBuilder output = new StringBuilder();
	    for (int i = 0; i < hex.length(); i+=2) {
	        String str = hex.substring(i, i+2);
	        output.append((char)Integer.parseInt(str, 16));
	    }
	    return output.toString();
	}
	
	
	private void write(LLRPMessage msg, String message) {
		try {
			//System.out.println(" Sending message: \n" + msg.toXMLString());
			out.write(msg.encodeBinary());
		} catch (IOException e) {
			System.out.println("Couldn't send Command "+ e);
		} catch (InvalidLLRPMessageException e) {
			System.out.println("Couldn't send Command "+ e);
		}
	}

	private void sendToDataBaseForUpdate(String tag)
	{
		try {
		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost("http://45.55.53.108/api/v1/taginfos/");

		// Request parameters and other properties.
		List<NameValuePair> params = new ArrayList<NameValuePair>(1);
		params.add(new BasicNameValuePair("findBySerialNumber", tag));
		
			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		

		//Execute and get the response.
		HttpResponse response = httpclient.execute(httppost);
		HttpEntity entity = response.getEntity();

		if (entity != null) {
		    InputStream instream = entity.getContent();
		    try {
		        // do something useful
		    } finally {
		        instream.close();
		    }
		}
		}catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	class ReadThread extends Thread {

		private DataInputStream inStream = null;
		private Socket socket = null;
		private LinkedBlockingQueue<LLRPMessage> queue = null;

		public ReadThread(Socket socket) {
			this.socket = socket;
			this.queue = new LinkedBlockingQueue<LLRPMessage>();
			try {
				this.inStream = new DataInputStream(socket.getInputStream());
			} catch (IOException e) {
				System.out.println("Cannot get input stream "+ e);
			}
		}
		
		@Override
		public void run() {
			super.run();
			if (socket.isConnected()) {
				while (!socket.isClosed()) {
					LLRPMessage message = null;
					try {
						message = read();
						
						if (message != null) {
							queue.put(message);

							try {
								
						
							  if (message.toXMLString().contains("AntennaID")){
							    	int aaaa= 1;
							    }
							    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			                    
			                    InputStream stream = new ByteArrayInputStream(message.toXMLString().getBytes(StandardCharsets.UTF_8));
			                    
			                    Document doc = dBuilder.parse(stream);
			                    
			                    
			                    doc.getDocumentElement().normalize();

			                    //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

			                    NodeList nList = doc.getElementsByTagName("llrp:TagReportData");

			                    

			                    for ( int temp = 0; temp < nList.getLength(); temp++ ) {

			                           Node nNode = nList.item(temp);

			                          // System.out.println("\nCurrent Element :" + temp + nNode.getNodeName());

			                           if ( nNode.getNodeType() == Node.ELEMENT_NODE ) {

			                                  Element eElement = (Element) nNode;

			                                  String converted = "";
			                                  String value = eElement.getElementsByTagName("llrp:EPC").item(0).getTextContent().trim();
				                                boolean found = false;
			                                  if (value.length() == 24)
			                                  {
			                                	  converted = HexToASCII(value);
			                                	  found = true;
			                                  }
			                                  else
			                                	  converted = value;
			                                  
			                                  System.out.println("llrp:EPC -> "
			                                               + converted);
			                                 
			                                  if(found)
			                                	  sendToDataBaseForUpdate(converted);
			                                  
			                                  System.out.println("llrp:AntennaID -> "
			                                               + eElement.getElementsByTagName("llrp:AntennaID").item(0).getTextContent().trim());
			                           }
			                    }
			                      
							} catch (Exception e) {

							} 

						} else {
							System.out.println("closing socket");
							socket.close();
						}

					} catch (IOException e) {
						System.out.println("Error while reading message "+ e);
					} catch (InvalidLLRPMessageException e) {
						System.out.println("Error while reading message "+ e);
					} catch (InterruptedException e) {
						System.out.println("Error while reading message "+ e);
					}
				}
			}

		}

		public LLRPMessage read() throws IOException,
				InvalidLLRPMessageException {
			LLRPMessage m = null;
			// The message header
			byte[] first = new byte[12];//was 6


			byte[] msg;

			if (inStream.read(first, 0, 12) == -1) {//was 6 not 12
				return null;
			}
			int msgLength = 0;

			try {
				msgLength = calculateLLRPMessageLength(first);
			} catch (IllegalArgumentException e) {
				throw new IOException("Incorrect Message Length");
			}
			
			byte[] temp = new byte[msgLength /*- 12*/];//was 6
			ArrayList<Byte> accumulator = new ArrayList<Byte>();

			for (byte b : first) {
				accumulator.add(b);
			}
			
			int numBytesRead = 0;

			while (((msgLength - accumulator.size()) != 0)
					&& numBytesRead != -1) {

				numBytesRead = inStream.read(temp, 0, msgLength
						- accumulator.size());

				for (int i = 0; i < numBytesRead; i++) {
					accumulator.add(temp[i]);
				}
			}

			if ((msgLength - accumulator.size()) != 0) {
				throw new IOException("Error: Discrepency between message size"
						+ " in header and actual number of bytes read");
			}

			msg = new byte[msgLength];

			for (int i = 0; i < accumulator.size(); i++) {
				msg[i] = accumulator.get(i);
			}

			m = LLRPMessageFactory.createLLRPMessage(msg);
			return m;
		}

		private int calculateLLRPMessageLength(byte[] bytes)
				throws IllegalArgumentException {
			long msgLength = 0;
			int num1 = 0;
			int num2 = 0;
			int num3 = 0;
			int num4 = 0;

			num1 = ((unsignedByteToInt(bytes[2])));
			num1 = num1 << 32;
			if (num1 > 127) {
				throw new RuntimeException(
						"Cannot construct a message greater than "
								+ "2147483647 bytes (2^31 - 1), due to the fact that there are "
								+ "no unsigned ints in java");
			}

			num2 = ((unsignedByteToInt(bytes[3])));
			num2 = num2 << 16;

			num3 = ((unsignedByteToInt(bytes[4])));
			num3 = num3 << 8;

			num4 = (unsignedByteToInt(bytes[5]));

			msgLength = num1 + num2 + num3 + num4;

			if (msgLength < 0) {
				throw new IllegalArgumentException(
						"LLRP message length is less than 0");
			} else {
				return (int) msgLength;
			}
		}
		
		private int unsignedByteToInt(byte b) {
			return (int) b & 0xFF;
		}

		public LLRPMessage getNextMessage() {
			LLRPMessage m = null;
			try {
				m = queue.take();
			} catch (InterruptedException e) {
				// nothing
				
			}
			return m;
		}
	}
    
}
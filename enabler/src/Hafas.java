import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;



@SuppressWarnings("unused")
public class Hafas {
	private static boolean DEBUG = false;
	
	private static char[] buf;
    private static boolean isUtf8 = false;
    private static int connectionsHeader;
    private static int extendedHeader;
    private static int extendedHeaderSize;

	public static void main(String[] args) throws Exception {
		buf = getFile();
		decode();
		
		DEBUG = true;
	}
	
	public static char[] getFile() throws IOException {
		InputStreamReader reader = new InputStreamReader(new FileInputStream("D:\\Android\\bahn\\chemnitz4"), "iso-8859-1");
		StringBuilder buf = new StringBuilder();
		char[] temp = new char[1024];
		int read;
		
		while ((read = reader.read(temp)) > 0) {
			buf.append(temp, 0, read);
		}
		reader.close();
		
		char[] result = new char[buf.length()];
		buf.getChars(0, buf.length(), result, 0);
		return result;
	}

    public static void decode() throws Exception {
		
		if (buf.length < 2)
			throw new Exception("Empty Data");
		
		if (!isCorrectDataVersion())
			throw new Exception("Wrong Data Version");
		
		extendedHeader = getDword(0x46);
		if (extendedHeader == 0)
			return;
		extendedHeaderSize = getDword(extendedHeader);
		
		String encoding = getEncoding();
		if (encoding.toUpperCase().equals("UTF-8"))
			isUtf8 = true;
		
		connectionsHeader = getDword(extendedHeader + 0xc);
		if (getConnectionsHeaderVersion() > 1)
			connectionsHeader = 0;
		
		/*
		if (A() == 0x2490)
			throw new Exception("CAP_ERROR / HAFAS_SERVER_ERROR_8");
		if (z() == 0x76)
			throw new Exception("CAP_ERROR / SOT_ERROR_" + A());
		if (z() == 0x28 && A() == 0x37f)
			throw new Exception("CAP_ERROR / HAFAS_SERVER_ERROR_1");
		if (z() == 0x64 && A() == 0x8)
			// ok, return this
		if (z() != 0x1e || A() != 0x37f)
			throw new Exception("CAP_ERROR / HAFAS_SERVER_ERROR");
		// ok, call d() and return this
		*/
		
		System.out.println(date(getTravelDate()));
		System.out.println(date(getTravelDatePlus30()));
		
		int connectionCount = getConnectionCount();
		int specialConnectionIdx = getSpecialConnectionIdx();
		if (specialConnectionIdx != -1) {
			Connection specialConnection = new Connection(specialConnectionIdx);
			connectionCount--;
		}

		Connection[] connections = new Connection[connectionCount];
		for (int arrIdx = 0, connIdx = 0; arrIdx < connectionCount; arrIdx++, connIdx++) {
			if (connIdx == specialConnectionIdx)
				connIdx++;
			connections[arrIdx] = new Connection(connIdx);
			Connection c = connections[arrIdx]; 
			System.out.println("---");
			System.out.println("Connection attributes: " + c.getConnectionAttributes());
			System.out.println("Changes: " + c.getConnectionChanges());
			System.out.println("Duration: " + time(c.getConnectionDuration()));
			System.out.println("Days: " + c.getConnectionDays());
			System.out.println();
			
			for (int i = 0; i < c.getConnectionPartCount(); i++) {
				System.out.printf("Connection %d, Part %d\n", connIdx, i);
				System.out.println("Line: " + c.getPartLine(i));
				
				int depStation = c.getPartDepartureStation(i);
				System.out.printf("Departure station: %s {%d}\n", getStationName(depStation), getStationId(depStation));
				System.out.printf("Departure time: %s (realtime: %s)\n",
						time(c.getPartPlannedDepartureTime(i)), time(c.getPartRealtimeDepartureTime(i)));
				System.out.printf("Departure track: %s (realtime: %s)\n",
						c.getPartPlannedDepartureTrack(i).trim(), c.getPartRealtimeDepartureTrack(i).trim());				
				
				int stopCount = c.getPartStopCount(i);
				for (int j = 0; j < stopCount; j++) {
					int stationIdx = c.getPartStopStationIdx(i, j);
					int plannedArr = c.getPartStopTime(i, j, false, false);
					int plannedDep = c.getPartStopTime(i, j, false, true);
					int realArr = c.getPartStopTime(i, j, true, false);
					int realDep = c.getPartStopTime(i, j, true, true);
					System.out.printf("Stop %s {%d} %s-%s track %s > %s (realtime: %s-%s track %s > %s)\n",
							getStationName(stationIdx), getStationId(stationIdx),
							time(plannedArr), time(plannedDep),
							c.getPartStopTrack(i, j, false, false).trim(),
							c.getPartStopTrack(i, j, false, true).trim(),
							time(realArr), time(realDep),
							c.getPartStopTrack(i, j, true, false).trim(),
							c.getPartStopTrack(i, j, true, true).trim()
						);
				}				
				
				int arrStation = c.getPartArrivalStation(i);
				System.out.printf("Arrival station: %s {%d}\n", getStationName(arrStation), getStationId(arrStation));
				System.out.printf("Arrival time: %s (realtime: %s)\n",
						time(c.getPartPlannedArrivalTime(i)), time(c.getPartRealtimeArrivalTime(i)));
				System.out.printf("Arrival track: %s (realtime: %s)\n",
						c.getPartPlannedArrivalTrack(i).trim(), c.getPartRealtimeArrivalTrack(i).trim());			
				
				System.out.println("Walking duration: " + c.getPartWalkingDuration(i));
				System.out.println("Attributes: " + c.getPartAttributes(i));
				System.out.println("shouldHide: " + c.shouldHidePart(i));
				
				int noteCount = c.getPartNoteCount(i);
				for (int j = 0; j < noteCount; j++) {
					System.out.println("Note: " + c.getPartNote(i, j));
				}
				
				System.out.println();
			}
		}
	}
    
    public static class Connection {
    	private int connectionOffset;
    	private int connIdx;
    	
    	public Connection(int idx) {
    		this.connIdx = idx;
        	if (connectionsHeader <= 0)
        		return;
        	
        	connectionOffset = getWord(connectionsHeader + getConnectionsTableOffset() + idx*2);
        	connectionOffset += connectionsHeader;
        }
    	
    	public int getConnectionPartCount() {
    		return getWord(0x4a + connIdx*0xc + 6);
    	}
    	
    	public int getConnectionChanges() {
    		return getWord(0x4a + connIdx*0xc + 0x8);
    	}
    	
    	public int getConnectionDuration() {
    		return getWord(0x4a + connIdx*0xc + 0xa);
    	}
    	
    	public String getConnectionDays() {
    		int addr = getConnectionDaysTable() + getWord(0x4a + connIdx*0xc);
    		return getString(addr);
    	}
    	
    	public int getConnectionPartWordField(int partIdx, int field) {
    		int partsTable = getDword(0x4a + connIdx*0xc + 2);
    		return getWord(0x4a + partsTable + partIdx*0x14 + field);
    	}
    	
    	public String getConnectionPartStringField(int partIdx, int field) {
    		int partsTable = getDword(0x4a + connIdx*0xc + 2);
    		return getString(0x4a + partsTable + partIdx*0x14 + field);
    	}
    	
    	public int getPartPlannedDepartureTime(int partIdx) {
    		return getConnectionPartWordField(partIdx, 0);
    	}
    	
    	public int getPartDepartureStation(int partIdx) {
    		return getConnectionPartWordField(partIdx, 2);
    	}
    	
    	public int getPartPlannedArrivalTime(int partIdx) {
    		return getConnectionPartWordField(partIdx, 4);
    	}
    	
    	public int getPartArrivalStation(int partIdx) {
    		return getConnectionPartWordField(partIdx, 6);
    	}
    	
    	public int getPartType(int partIdx) {
    		return getConnectionPartWordField(partIdx, 8);
    	}
    	
    	public String getPartLine(int partIdx) {
    		String line = getConnectionPartStringField(partIdx, 0xa);
    		
    		int hash = line.indexOf('#');
    		if (hash != -1)
    			line = line.substring(0, hash);
    		
    		StringBuffer sb = new StringBuffer(line);
    		while (sb.length() < 8)
    			sb.append(" ");
    		
    		return sb.toString();
    	}
    	
    	public String getPartLineGroup(int partIdx) {
    		String line = getConnectionPartStringField(partIdx, 0xa);
    		
    		int hash = line.indexOf('#');
    		String fromHash = line.substring(hash + 1);
    		int pos = 0;
    		while (fromHash.length() > 0) {
    			char firstChar = fromHash.charAt(pos);
    			char lowerFirstChar = Character.toLowerCase(firstChar);
    			
    			if (lowerFirstChar != '-' && lowerFirstChar <= (char)0xc0
    					&& (lowerFirstChar < 'a'  || lowerFirstChar > 'z'))
    				break;
    			
    			pos++;
    		}
        	
    		return fromHash.substring(0, pos);
    	}
    	
    	public String getPartPlannedDepartureTrack(int partIdx) {
    		return trimTrack(getConnectionPartStringField(partIdx, 0xc));
    	}
    	
    	public String getPartPlannedArrivalTrack(int partIdx) {
    		return trimTrack(getConnectionPartStringField(partIdx, 0xe));
    	}
    	
    	public int getPartRealtimeDepartureTime(int partIdx) {
    		int result = getWord(connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx);
    		return (result != 0xffff) ? result : -1;
    	}
    	
    	public int getPartRealtimeArrivalTime(int partIdx) {
    		int result = getWord(connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx + 0x2);
    		return (result != 0xffff) ? result : -1;
    	}
    	
    	public String getPartRealtimeDepartureTrack(int partIdx) {
    		int addr = connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx + 0x4;
    		return getString(addr);
    	}
    	
    	public String getPartRealtimeArrivalTrack(int partIdx) {
    		int addr = connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx + 0x6;
    		return getString(addr);
    	}
    	
    	private String trimTrack(String track) {
    		if (track.startsWith("Gleis "))
    			return track.substring(6);
    		else if (track.startsWith("Voie "))
    			return track.substring(5);
    		else if (track.startsWith("Bin. "))
    			return track.substring(5);
    		else if (track.startsWith("Per. "))
    			return track.substring(5);
    		else if (track.startsWith("Pl. "))
    			return track.substring(4);
    		return "---";
    	}
    	
    	public int getArrivalTime() {
    		return getPartPlannedArrivalTime(getConnectionPartCount() - 1);
    	}
    	
    	public int getPartWalkingDuration(int partIdx) {
    		if (getPartType(partIdx) == 2)
    			return -1;
    		
    		int duration = -1;
    		try {
    			String durationStr;
    			
	    		if (isDataVersionGE6()) {
	    			int idx = getConnectionPartWordField(partIdx, 0x10);
	    			durationStr = getStringValueForKey(idx, "Duration");
	    		} else {
	    			durationStr = getConnectionPartStringField(partIdx, 0x10);
	    		}
	    		
	    		duration = Integer.parseInt(durationStr);
	    		duration = (duration / 100)*60 + (duration % 60);
    		} catch (Exception ignored) {}
    		return duration;
    	}
    	
    	public Hashtable<String, String> getPartAttributes(int partIdx) {
    		int startIdx;
    		if (partIdx >= 0) {
    			startIdx = getConnectionPartWordField(partIdx, 0x10);
    		} else {
    			int connAttribTable = getConnectionAttributesTable();
    			if (connAttribTable == 0)
    				return null;
    			
    			startIdx = getWord(connAttribTable + connIdx*2);
    		}
    		return getStringKeyValuePairHashtable(startIdx);
    	}
    	
    	public int getPartNoteCount(int partIdx) {
    		return getWord(getNotesTable() + getConnectionPartWordField(partIdx, 0x12));
    	}
    	
    	public String getPartNote(int partIdx, int noteIdx) {
    		int addr = getNotesTable() + getConnectionPartWordField(partIdx, 0x12) + 2 + noteIdx*2;
    		return getString(addr);
    	}
    	
    	public Hashtable<String, String> getConnectionAttributes() {
    		return getPartAttributes(-1);
    	}
    	
    	public boolean shouldHidePart(int partIdx) {
    		if (getPartType(partIdx) != 1)
    			return false;
    		
    		Hashtable<String, String> attributes = getPartAttributes(partIdx);
    		if (attributes == null)
    			return false;
    		
    		String hideValue = attributes.get("Hide");
    		if (hideValue == null)
    			return false;
    		
    		return hideValue.equals("1");
    	}
    	
    	public int getPartStopStationIdx(int partIdx, int stopIdx) {
    		return getWord(getPartStop(partIdx, stopIdx) + 0x18);
    	}
    	
    	public int getPartStop(int partIdx, int stopIdx) {
    		int firstStop = getWord(connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx + 0xc);
    		return getStopsTable() + (firstStop + stopIdx) * getStopElementSize();
    	}
    	
    	public int getPartStopCount(int partIdx) {
    		if (getStopElementSize() <= 0)
    			return 0;
    		return getWord(connectionOffset + getConnectionPartInfoOffset() + getConnectionPartInfoSize() * partIdx + 0xe);
    	}
    	
    	public int getPartStopTime(int partIdx, int stopIdx, boolean realtime, boolean departure) {
    		int stop = getPartStop(partIdx, stopIdx);
    		
    		if (realtime)
    			stop += 0xc;
    		if (!departure)
    			stop += 0x2;
    		
    		int result = getWord(stop);
    		return (result != 0xffff) ? result : -1; 
    	}
    	
    	public String getPartStopTrack(int partIdx, int stopIdx, boolean realtime, boolean departure) {
    		int stop = getPartStop(partIdx, stopIdx);
    		
    		if (realtime)
    			stop += 0xc;
    		if (!departure)
    			stop += 0x2;
    		
    		return getString(stop + 0x4); 
    	}
    }
    
	public static String getEncoding() {
		if (extendedHeader != 0 && extendedHeaderSize >= 0x22)
			return getString(extendedHeader + 0x20);
		else
			return "iso-8859-1";
	}
	
	public static boolean isCorrectDataVersion() {
		int version = getWord(0);
		return (version == 5 || version == 6);
	}
	
	public static boolean isDataVersionGE6() {
		return getWord(0) >= 6;
	}
	
    public static int getConnectionsHeaderVersion() {
    	if (connectionsHeader != 0)
    		return getWord(connectionsHeader);
		return 1;
    }
    
    public static int A() {
    	if (extendedHeader != 0)
    		return getWord(extendedHeader + 0x10);
   		return 0;
    }
    
    public static int getConnectionAttributesTable() {
    	if (extendedHeader == 0 || extendedHeaderSize < 0x30)
    		return 0;
    	return getDword(extendedHeader + 0x2c);
    }
    
    public static int getConnectionsHeader() {
    	return connectionsHeader;
    }
	
    public static int getConnectionsTableOffset() {
    	if (connectionsHeader == 0)
    		return 0;
    	
    	if (getConnectionsHeaderVersion() == 0)
    		return 4;
    	else
    		return getWord(connectionsHeader + 4);
    }
    
    public static int getConnectionPartInfoOffset() {
    	if (connectionsHeader == 0)
    		return 0;
    	
    	if (getConnectionsHeaderVersion() == 0)
    		return 8;
    	else
    		return getWord(connectionsHeader + 6);
    }
    
    public static int getConnectionPartInfoSize() {
    	if (connectionsHeader == 0)
    		return 0;
    	
    	if (getConnectionsHeaderVersion() == 0)
    		return 8;
    	else
    		return getWord(connectionsHeader + 8);
    }
    
    public static int getStopElementSize() {
    	if (getConnectionsTableOffset() <= 0xa)
    		return 0;
    	else
    		return getWord(connectionsHeader + 0xa);
    }
    
    public static int getStopsTable() {
    	return connectionsHeader + getWord(connectionsHeader + 0xc);
    }
    
    public static int getStationsTable() {
    	return getDword(0x36);
    }
    
    public static String getStationName(int idx) {
    	return getString(getStationsTable() + idx*0xe);
    }
    
    public static int getStationId(int idx) {
    	return getDword(getStationsTable() + idx*0xe + 2);
    }
    
    public static int getConnectionDaysTable() {
    	return getDword(0x20);
    }
    
    public static int getNotesTable() {
    	return getDword(0x3a);
    }
    
    public static int z() {
    	if (extendedHeader != 0)
    		return getWord(extendedHeader + 0x4);
   		return 0;
    }
    
    public static String getIdent() {
    	if (extendedHeader != 0)
    		return getString(extendedHeader + 0xa);
   		return null;
    }
    
    public static Hashtable<String, String> getRequestAttributes() {
    	if (extendedHeader == 0 || extendedHeaderSize < 0x32)
    		return null;
    	
    	return getStringKeyValuePairHashtable(getWord(extendedHeader + 0x30));
    }
	
	public static Hashtable<String, String> getStringKeyValuePairHashtable(int startIdx) {
    	if (!isDataVersionGE6())
    		return null;
    	
    	Hashtable<String, String> result = new Hashtable<String, String>();

    	for (int i = startIdx; i < 0x10000; i++) {
	    	String[] kvp = getStringKeyValuePair(i);
	    	if (kvp[0].equals("---"))
	    		break;
	    	
	    	result.put(kvp[0], kvp[1]);
    	}
    	return result;
	}
	
	public static String[] getStringKeyValuePair(int pos) {
		if (extendedHeader == 0 || extendedHeaderSize < 0x28)
			return null;
		
		int offset = getDword(extendedHeader + 0x24);
		if (offset == 0)
			return null;
		
		String[] result = new String[2];
		result[0] = getString(offset + pos*4);
		result[1] = getString(offset + pos*4 + 2);
		return result;
	}
	
	public static String getStringValueForKey(int startIdx, String key) {
		for (int i = startIdx; i < 0x10000; i++) {
			String[] kvp = getStringKeyValuePair(i);
			
			if (kvp[0].equals("---"))
				return null;
			
			if (kvp[0].equals(key))
				return kvp[1];
		}
		return null;		
	}
	
	public static int getConnectionCount() {
		return getWord(0x1e);
	}
	
	public static int getTravelDate() {
		return getWord(0x28);
	}
	
	public static int getTravelDatePlus30() {
		return getWord(0x2a);
	}
	
	public static boolean q() {
		return (getDword(0x42) & 1) != 0;
	}
	
	public static boolean r() {
		return (getDword(0x42) & 2) != 0;
	}
	
	public static int getSpecialConnectionIdx() {
		if (extendedHeader == 0 || extendedHeaderSize < 0x20)
			return -1;
		
		int value = getWord(extendedHeader + 0x1e);
		return (value != 0xffff) ? value : -1;
	}
	
	public static int K() {
		if (extendedHeader == 0 || extendedHeaderSize < 0x18)
			return 0;
		
		int p = getDword(extendedHeader + 0x14);
		int val = getWord(p);
		
		if (val == 0 || val == 1)
			return p;
		else
			return 0;
	}
	
	public static Enumeration<String> L() {
		System.out.println("-----------");
		Hashtable<Integer, String> table = new Hashtable<Integer, String>();
		
		int K = K();
		if (K == 0)
			return table.elements();
			
		for (int i = getConnectionCount() - 1; i >= 0; i--) {
			System.out.println("connection " + i);
			int bla = getWord(K + 2 + i*2);
			while (bla != 0) {
				int bla2 = getWord(K + bla + 0x4) & 3;
				if (bla2 > 0) {
					String value = getString(K + bla + 0xa);
					table.put(K + bla, value);
				}
				
				bla = getWord(K + bla + 0x10);
			}
		}
		return table.elements();
	}
	
	
	
	
	
	// helpers
	public static int unsigned(byte b) {
		return b & 0xff;
	}
	
	public static int getDword(int pos) {
		int result = buf[pos]
		           + buf[pos+1] * 0x100
		           + buf[pos+2] * 0x10000
		           + buf[pos+3] * 0x1000000;
		if (DEBUG) System.out.printf("getDword(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	public static int getWord(int pos) {
		int result = buf[pos] + buf[pos+1] * 0x100;
		if (DEBUG) System.out.printf("getWord(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	public static int getByte(int pos) {
		int result = buf[pos];
		if (DEBUG) System.out.printf("getByte(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	public static String getString(int pos) {
		int stringsStart = getDword(0x24);
		pos = stringsStart + getWord(pos);
		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		while (true) {
			char b = buf[pos++];
			if (b == 0)
				break;
			bytes.write(b);
		}
		
		String result;
        try {
	        result = bytes.toString(isUtf8 ? "utf-8" : "iso-8859-1");
        } catch (UnsupportedEncodingException e) {
        	e.printStackTrace();
	        result = bytes.toString();
        }
		if (DEBUG) System.out.printf("getString(0x%x) = %s\n", pos, result);
		return result;
	}
	
    public static String hex(int i) {
    	return "0x" + Integer.toHexString(i);
    }
    
    public static String date(int i) {
    	Calendar cal = Calendar.getInstance();
    	cal.set(1980, 0, 1, 0, 0, 0);
    	cal.add(Calendar.DAY_OF_MONTH, i);
    	return DateFormat.getDateTimeInstance().format(cal.getTime());
    }
    
    public static String time(int i) {
    	if (i < 0)
    		return "##:##";
    	
    	String day = "";
    	if (i >= 2400) {
    		day = "+" + (i / 2400);
    		i = i % 2400;
    	}
    	return String.format("%02d:%02d%s", i / 100, i % 100, day);
    }
}


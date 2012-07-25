package de.schildbach.pte.util;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Vector;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

public class HafasBinaryFile
{
	boolean DEBUG = false;
	
	private final char[] buf;
	private final TimeZone timezone;
	
	private final int version;
    private boolean isUtf8 = false;
    
    private final int baseDay;
    private final int serviceDaysTable;
    private final int stationsTable;
    private final int remarksTable;
    private int extendedHeader;
    
    private int connectionsHeader;
    private int connectionAttributesTable;
	private int connectionsTableOffset;
	private int connectionPartInfoOffset;
	private int connectionPartInfoSize;
	private int stopElementSize;
	
	private final Vector<HafasBinaryFile.Connection> connections = new Vector<HafasBinaryFile.Connection>();
	private static final Hashtable<String, String> EMPTY_STRING_HASHTABLE = new Hashtable<String, String>(0);
	
	public HafasBinaryFile(final char[] buf, final TimeZone timezone)
	{
		this.buf = buf;
		this.timezone = timezone;
		
		if (buf.length < 2)
			throw new IllegalStateException("Empty Data");
		
		version = getWord(0);
		if (version < 5 || version > 6)
			throw new IllegalStateException("Wrong Data Version");
		
		serviceDaysTable = getDword(0x20);
		baseDay = getWord(0x28);
		stationsTable = getDword(0x36);
		remarksTable = getDword(0x3a);
		extendedHeader = getDword(0x46);
		
		if (extendedHeader == 0)
			return;
		
		final int extendedHeaderSize = getDword(extendedHeader);
		if (extendedHeaderSize < 0x32) {
			extendedHeader = 0;
			return;
		}
		
		String encoding = getString(extendedHeader + 0x20);
		if (encoding.toUpperCase().equals("UTF-8"))
			isUtf8 = true;
		
		connectionsHeader = getDword(extendedHeader + 0xc);
		if (connectionsHeader != 0 && getWord(connectionsHeader) > 1)
			connectionsHeader = 0;
		
		if (connectionsHeader == 0)
			return;
		
		connectionAttributesTable = getDword(extendedHeader + 0x2c);
		
		connectionsTableOffset = getWord(connectionsHeader + 4);
		connectionPartInfoOffset = getWord(connectionsHeader + 6);
		connectionPartInfoSize = getWord(connectionsHeader + 8);
		if (connectionsTableOffset <= 0xa)
			stopElementSize = getWord(connectionsHeader + 0xa);
			
		final int numConnections = getWord(0x1e);
		// TODO the original app check (extendedHeader + 0x1e) for an index of a "special" connection that is not added to the array
    	for (int i = 0; i < numConnections; i++) {
    		connections.add(new Connection(i));
    	}
	}

	private int getDword(final int pos)
	{
		int result = buf[pos]
		           + buf[pos+1] * 0x100
		           + buf[pos+2] * 0x10000
		           + buf[pos+3] * 0x1000000;
		if (DEBUG) System.out.printf("getDword(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	private int getWord(final int pos)
	{
		int result = buf[pos] + buf[pos+1] * 0x100;
		if (DEBUG) System.out.printf("getWord(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	private int getByte(final int pos)
	{
		int result = buf[pos];
		if (DEBUG) System.out.printf("getByte(0x%x) = 0x%x\n", pos, result);
		return result;
	}
	
	private Date makeDate(final int day, final int time)
	{
		Calendar cal = new GregorianCalendar(timezone);
		cal.set(1980, 0, day, 0, 0, 0);
		cal.set(Calendar.HOUR_OF_DAY, time / 100);
		cal.set(Calendar.MINUTE, time % 100);
		return cal.getTime();
	}
	
	private String getString(int pos)
	{
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
	
	public static String normalizeString(String str, String noneValue)
	{
		if (str == null)
			return noneValue;
		
		str = str.trim();
		if (str == null || str.isEmpty() || str.equals("---"))
			return noneValue;
		
		return str;
	}
	
	public static String normalizeString(String str)
	{
		return normalizeString(str, null);
	}
	
	private String[] getKeyValuePair(int pos)
	{
		int offset = getDword(extendedHeader + 0x24);
		if (offset == 0)
			return null;
		
		String[] result = new String[2];
		result[0] = getString(offset + pos*4);
		result[1] = getString(offset + pos*4 + 2);
		return result;
	}
	
	private Hashtable<String, String> getKeyValuePairs(int startIdx)
	{
    	Hashtable<String, String> result = new Hashtable<String, String>();

    	for (int i = startIdx; i < 0x10000; i++) {
	    	String[] kvp = getKeyValuePair(i);
	    	if (kvp == null || kvp[0].equals("---"))
	    		break;
	    	
	    	result.put(kvp[0], kvp[1]);
    	}
    	return result;
	}
	
	
	public Location getStation(final int idx)
	{
		final int ptr = stationsTable + idx * 14;
		final String name = getString(ptr);
		final int id = getDword(ptr + 2);
		final int lon = getWord(ptr + 6);
		final int lat = getWord(ptr + 8);
		return new Location(LocationType.STATION, id, lat, lon, null, name);
	}
	
	public Location getFrom()
	{
		return new Location(LocationType.STATION, 0, getDword(0xc), getDword(0x8), null, getString(0x2));
	}
	
	public Location getTo()
	{
		return new Location(LocationType.STATION, 0, getDword(0x1a), getDword(0x16), null, getString(0x10));
	}
	
	public String getRequestId()
	{
    	return getString(extendedHeader + 0xa);
	}
	
    public String getLd()
    {
    	return getString(extendedHeader + 0x22);
    }
    
    public Vector<HafasBinaryFile.Connection> getConnections()
    {
    	return connections;
    }
    
    // FIXME
    public boolean isDataVersionGE6() {return true;}
    
    public class Connection
    {
    	private final int connectionOffset;
    	private final int connIdx;
    	private final int connectionDay;
    	
    	private final Vector<HafasBinaryFile.Connection.Part> parts = new Vector<HafasBinaryFile.Connection.Part>();
    	private Hashtable<String, String> attributes;
    	
    	public Connection(int idx)
    	{
    		this.connIdx = idx;
        	this.connectionOffset = connectionsHeader + getWord(connectionsHeader + connectionsTableOffset + idx*2);
        	
    		this.connectionDay = baseDay + getBaseDay();
        	
        	final int partCount = getWord(0x4a + connIdx*0xc + 6);
        	for (int i = 0; i < partCount; i++) {
        		parts.add(new Part(i));
        	}
        }
    	
    	public int getIndex()
    	{
    		return connIdx;
        }
    	
    	private int getBaseDay()
    	{
    		final int addr = serviceDaysTable + getWord(0x4a + connIdx*0xc);
    		final int serviceBitBase = getWord(addr + 2);
    		final int serviceBitBytes = getWord(addr + 4);
    		int day = serviceBitBase * 8;
    		for (int i = 0; i < serviceBitBytes; i++)
    		{
    			int serviceBits = getByte(addr + 6 + i);
    			if (serviceBits == 0)
    			{
    				day += 8;
    				continue;
    			}
    			while ((serviceBits & 0x80) == 0)
    			{
    				serviceBits = serviceBits << 1;
    				day++;
    			}
    			break;
    		}
    		return day;
    	}
    	
    	public Vector<HafasBinaryFile.Connection.Part> getParts()
    	{
	        return parts;
        }
    	
        public Hashtable<String, String> getAttributes()
        {
        	if (attributes != null)
        		return attributes;
        	
    		if (connectionAttributesTable == 0) {
    			attributes = EMPTY_STRING_HASHTABLE; 
    		} else {
    			int startIdx = getWord(connectionAttributesTable + connIdx*2);
    			attributes = getKeyValuePairs(startIdx);
    		}
    		return attributes;
        }
        
    	public int getNumChanges()
    	{
    		return getWord(0x4a + connIdx*0xc + 0x8);
    	}
    	
    	public int getDuration()
    	{
    		int result = getWord(0x4a + connIdx*0xc + 0xa);
    		return (result / 100) * 60 + (result % 100);
    	}
    	
    	public String getServiceDays()
    	{
    		return getString(serviceDaysTable + getWord(0x4a + connIdx*0xc));
    	}
    	
		public Location getDepartureStation()
		{
			Part first = parts.firstElement();
			return (first == null) ? null : first.getDepartureStation(); 
		}
		
		public Location getArrivalStation()
		{
			Part first = parts.lastElement();
			return (first == null) ? null : first.getArrivalStation(); 
		}
		
    	public class Part
    	{
    		private final int partIdx;
    		private final int partInfoOffset;
    		private final Vector<HafasBinaryFile.Connection.Part.Stop> stops = new Vector<HafasBinaryFile.Connection.Part.Stop>();
    		private Hashtable<String, String> attributes;
    		private Vector<String> remarks;
    		
    		public Part(int partIdx)
    		{
    			this.partIdx = partIdx;
    			this.partInfoOffset = 0x4a + getDword(0x4a + connIdx*0xc + 2) + partIdx*0x14;
            }
    		
        	public int getIndex() {
        		return partIdx;
            }
        	
        	public Vector<HafasBinaryFile.Connection.Part.Stop> getStops() {
	            return stops;
            }
    		
    		public int getType()
    		{
    			return getWord(partInfoOffset + 8);
    		}
    		
    		public Location getDepartureStation()
    		{
    			return getStation(getWord(partInfoOffset + 2));
    		}
    		public Location getArrivalStation()
    		{
    			return getStation(getWord(partInfoOffset + 6));
    		}
    		
    		public Date getPlannedDepartureTime()
    		{
        		return makeDate(connectionDay, getWord(partInfoOffset + 0));
        	}
        	public Date getPlannedArrivalTime()
        	{
        		return makeDate(connectionDay, getWord(partInfoOffset + 4));
        	}
        	
    		public Date getEstimatedDepartureTime()
    		{
    			int result = getWord(connectionOffset + connectionPartInfoOffset + connectionPartInfoSize * partIdx + 0);
        		return (result != 0xffff) ? makeDate(connectionDay, result) : null;
        	}
        	public Date getEstimatedArrivalTime()
        	{
    			int result = getWord(connectionOffset + connectionPartInfoOffset + connectionPartInfoSize * partIdx + 2);
    			return (result != 0xffff) ? makeDate(connectionDay, result) : null;
        	}

        	public String getPlannedDeparturePlatform()
        	{
        		return trimTrack(getString(partInfoOffset + 0xc));
        	}
        	public String getPlannedArrivalPlatform()
        	{
        		return trimTrack(getString(partInfoOffset + 0xe));
        	}
        	private String trimTrack(String track)
        	{
        		if (track.equals("---"))
        			return null;
        			
        		track = track.trim();
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
        		return null;
        	}

        	public String getEstimatedDeparturePlatform()
        	{
        		String result = getString(connectionOffset + connectionPartInfoOffset + connectionPartInfoSize * partIdx + 4);
        		return normalizeString(result);
        	}
        	public String getEstimatedArrivalPlatform()
        	{
        		String result = getString(connectionOffset + connectionPartInfoOffset + connectionPartInfoSize * partIdx + 6);
        		return normalizeString(result);
        	}
        	
        	public String getLine()
        	{
        		String line = getString(partInfoOffset + 0xa);
        		
        		// this is from the original code, no idea whether this occurs in real life
        		int hash = line.indexOf('#');
        		if (hash != -1)
        			line = line.substring(0, hash);
        		
        		return normalizeString(line);
        	}
        	
        	public Location getDirection()
        	{
				final String direction = normalizeString(getAttributes().get("Direction"));
				if (direction != null)
					return new Location(LocationType.STATION, 0, null, direction);
				else
					return null;
        	}
    		
            public Hashtable<String, String> getAttributes()
            {
            	if (attributes != null)
            		return attributes;
            	attributes = getKeyValuePairs(getWord(partInfoOffset + 0x10)); 
        		return attributes;
            }
            
            public Vector<String> getRemarks()
            {
            	if (remarks != null)
            		return remarks;
            	
            	final int remarksOffset = remarksTable + getWord(partInfoOffset + 0x12);
            	final int remarkssCount = getWord(remarksOffset);
            	
            	remarks = new Vector<String>(remarkssCount);
            	for (int i = 1; i <= remarkssCount; i++)
            		remarks.add(getString(remarksOffset + 2*i));
            	
        		return remarks;
            }
    		
    		public int getFootwayDuration()
    		{
        		if (getType() == 2)
        			return -1;
        		
        		int duration = -1;
        		try {
        			String durationStr;
    	    		if (isDataVersionGE6())
    	    			durationStr = getAttributes().get("Duration");
    	    		else
    	    			durationStr = getString(partInfoOffset + 0x10);
    	    		
    	    		duration = Integer.parseInt(durationStr);
    	    		duration = (duration / 100)*60 + (duration % 60);
        		} catch (Exception ignored) {}
        		return duration;
        	}
    		
    		// TODO I have seen this being true for a connection which started with two footways (duration has to be summed up etc.)
    		public boolean shouldHide()
    		{
        		if (getType() != 1)
        			return false;
        		
        		String hideValue = getAttributes().get("Hide");
        		return hideValue != null && hideValue.equals("1");
        	}
    		
    		
    		public class Stop
    		{
    			// FIXME
    		}
    	}
    }
}

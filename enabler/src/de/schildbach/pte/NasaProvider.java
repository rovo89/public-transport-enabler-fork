/*
 * Copyright 2010-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.schildbach.pte;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.schildbach.pte.dto.Connection;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryConnectionsContext;
import de.schildbach.pte.dto.QueryConnectionsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.ResultHeader;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.util.ParserUtils;

/**
 * @author Andreas Schildbach
 */
public class NasaProvider extends AbstractHafasProvider
{
	public static final NetworkId NETWORK_ID = NetworkId.NASA;
	private static final String API_BASE = "http://reiseauskunft.insa.de/bin/";

	public static class Context implements QueryConnectionsContext
	{
		public final String context;

		public Context(final String context)
		{
			this.context = context;
		}

		public boolean canQueryLater()
		{
			return context != null;
		}

		public boolean canQueryEarlier()
		{
			return context != null;
		}
	}

	public NasaProvider()
	{
		super(API_BASE + "query.exe/dn", 8, null);
	}

	public NetworkId id()
	{
		return NETWORK_ID;
	}

	public boolean hasCapabilities(final Capability... capabilities)
	{
		for (final Capability capability : capabilities)
			if (capability == Capability.AUTOCOMPLETE_ONE_LINE || capability == Capability.DEPARTURES)
				return true;

		return false;
	}

	@Override
	protected char intToProduct(final int value)
	{
		if (value == 1)
			return 'I';
		if (value == 2)
			return 'I';
		if (value == 4)
			return 'R';
		if (value == 8)
			return 'R';
		if (value == 16)
			return 'S';
		if (value == 32)
			return 'T';
		if (value == 64)
			return 'B';
		if (value == 128) // Rufbus
			return 'P';

		throw new IllegalArgumentException("cannot handle: " + value);
	}

	@Override
	protected void setProductBits(final StringBuilder productBits, final char product)
	{
		if (product == 'I')
		{
			productBits.setCharAt(0, '1'); // ICE
			productBits.setCharAt(1, '1'); // IC/EC
		}
		else if (product == 'R')
		{
			productBits.setCharAt(3, '1'); // RE/RB
			productBits.setCharAt(7, '1'); // Tourismus-Züge
			productBits.setCharAt(2, '1'); // undokumentiert
		}
		else if (product == 'S' || product == 'U')
		{
			productBits.setCharAt(4, '1'); // S/U
		}
		else if (product == 'T')
		{
			productBits.setCharAt(5, '1'); // Straßenbahn
		}
		else if (product == 'B' || product == 'P')
		{
			productBits.setCharAt(6, '1'); // Bus
		}
		else if (product == 'F' || product == 'C')
		{
		}
		else
		{
			throw new IllegalArgumentException("cannot handle: " + product);
		}
	}

	private static final String[] PLACES = { "Leipzig", "Halle (Saale)", "Halle" };

	@Override
	protected String[] splitPlaceAndName(final String name)
	{
		for (final String place : PLACES)
		{
			if (name.startsWith(place + " ") || name.startsWith(place + "-"))
				return new String[] { place, name.substring(place.length() + 1) };
			else if (name.startsWith(place + ", "))
				return new String[] { place, name.substring(place.length() + 2) };
		}

		return super.splitPlaceAndName(name);
	}

	public NearbyStationsResult queryNearbyStations(final Location location, final int maxDistance, final int maxStations) throws IOException
	{
		final StringBuilder uri = new StringBuilder(API_BASE);

		if (location.type == LocationType.STATION && location.hasId())
		{
			uri.append("stboard.exe/dn?near=Anzeigen");
			uri.append("&distance=").append(maxDistance != 0 ? maxDistance / 1000 : 50);
			uri.append("&input=").append(location.id);

			return htmlNearbyStations(uri.toString());
		}
		else
		{
			throw new IllegalArgumentException("cannot handle: " + location.toDebugString());
		}
	}

	public QueryDeparturesResult queryDepartures(final int stationId, final int maxDepartures, final boolean equivs) throws IOException
	{
		final StringBuilder uri = new StringBuilder();
		uri.append(API_BASE).append("stboard.exe/dn");
		uri.append("?productsFilter=").append(allProductsString());
		uri.append("&boardType=dep");
		uri.append("&disableEquivs=yes"); // don't use nearby stations
		uri.append("&maxJourneys=50"); // ignore maxDepartures because result contains other stations
		uri.append("&start=yes");
		uri.append("&L=vs_java3");
		uri.append("&input=").append(stationId);

		return xmlQueryDepartures(uri.toString(), stationId);
	}

	public List<Location> autocompleteStations(final CharSequence constraint) throws IOException
	{
		return xmlMLcReq(constraint);
	}

	private String connectionsQueryUri(final Location from, final Location via, final Location to, final Date date, final boolean dep,
			final String products)
	{
		final Calendar c = new GregorianCalendar(timeZone());
		c.setTime(date);

		final StringBuilder uri = new StringBuilder();

		// clientSystem=Android7&REQ0JourneyProduct_prod_list_1=11111111111&timeSel=depart&hcount=0&date=11.07.2012&ignoreMinuteRound=yes&androidversion=1.0.2&SID=A%3d1%40O%3dLeipzig%20Hbf%40X%3d12383333%40Y%3d51346546%40U%3d80%40L%3d008010205%40B%3d1%40V%3d12.9,%40p%3d1341849783%40&h2g-direct=11&time=18%3a04&REQ0HafasNumCons0=3&start=1&REQ0HafasNumCons1=0&clientDevice=Milestone&REQ0HafasNumCons2=3&htype=Milestone&ZID=A%3d1%40O%3dDresden%20Hbf%40X%3d13732038%40Y%3d51040562%40U%3d80%40L%3d008010085%40B%3d1%40V%3d12.9,%40p%3d1341849783%40&clientType=ANDROID

		uri.append(API_BASE).append("query.exe/dn");

		uri.append("?start=Suchen");

		uri.append("&REQ0JourneyStopsS0ID=").append(ParserUtils.urlEncode(locationId(from), ISO_8859_1));
		uri.append("&REQ0JourneyStopsZ0ID=").append(ParserUtils.urlEncode(locationId(to), ISO_8859_1));

		if (via != null)
		{
			// workaround, for there does not seem to be a REQ0JourneyStops1.0ID parameter

			uri.append("&REQ0JourneyStops1.0A=").append(locationType(via));

			if (via.type == LocationType.STATION && via.hasId() && isValidStationId(via.id))
			{
				uri.append("&REQ0JourneyStops1.0L=").append(via.id);
			}
			else if (via.hasLocation())
			{
				uri.append("&REQ0JourneyStops1.0X=").append(via.lon);
				uri.append("&REQ0JourneyStops1.0Y=").append(via.lat);
				if (via.name == null)
					uri.append("&REQ0JourneyStops1.0O=").append(
							ParserUtils.urlEncode(String.format(Locale.ENGLISH, "%.6f, %.6f", via.lat / 1E6, via.lon / 1E6), ISO_8859_1));
			}
			else if (via.name != null)
			{
				uri.append("&REQ0JourneyStops1.0G=").append(ParserUtils.urlEncode(via.name, ISO_8859_1));
				if (via.type != LocationType.ANY)
					uri.append('!');
			}
		}

		uri.append("&REQ0HafasSearchForw=").append(dep ? "1" : "0");
		uri.append("&REQ0JourneyDate=").append(
				String.format("%02d.%02d.%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) - 2000));
		uri.append("&REQ0JourneyTime=").append(String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));

		for (final char p : products.toCharArray())
		{
			if (p == 'I')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_5=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_5=1");
			}
			if (p == 'R')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_6=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_6=1");
			}
			if (p == 'S')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_0=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_0=1");
			}
			if (p == 'U')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_1=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_1=1");
			}
			if (p == 'T')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_2=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_2=1");
			}
			if (p == 'B')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_3=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_3=1");
			}
			if (p == 'P')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_7=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_7=1");
			}
			if (p == 'F')
			{
				uri.append("&REQ0JourneyProduct_prod_section_0_4=1");
				if (via != null)
					uri.append("&REQ0JourneyProduct_prod_section_1_4=1");
			}
		}

		uri.append("&h2g-direct=11");

		return uri.toString();
	}

	@Override
	public QueryConnectionsResult queryConnections(Location from, Location via, Location to, Date date, boolean dep, int maxNumConnections,
			String products, WalkSpeed walkSpeed, Accessibility accessibility, Set<Option> options) throws IOException
	{
		final String uri = connectionsQueryUri(from, via, to, date, dep, products);

		System.out.println(uri);

		final DataInputStream is = new DataInputStream(new BufferedInputStream(ParserUtils.scrapeInputStream(uri)));
		is.mark(32768);

		// quick check of status
		final short status = Short.reverseBytes(is.readShort());
		System.out.println("Status: " + status);

		// quick seek for pointers
		is.reset();
		is.skipBytes(0x20);
		final int serviceDaysTablePtr = Integer.reverseBytes(is.readInt());
		final int stringTablePtr = Integer.reverseBytes(is.readInt());

		is.reset();
		is.skipBytes(0x36);
		final int stationTablePtr = Integer.reverseBytes(is.readInt());
		final int commentsTablePtr = Integer.reverseBytes(is.readInt());

		is.reset();
		is.skipBytes(0x46);
		final int extensionHeaderPtr = Integer.reverseBytes(is.readInt());

		// read strings
		is.reset();
		is.skipBytes(stringTablePtr);
		final byte[] stringTable = new byte[serviceDaysTablePtr - stringTablePtr];
		is.readFully(stringTable);

		is.reset();
		is.skipBytes(extensionHeaderPtr);

		// read extension header
		final int extensionHeaderLength = Integer.reverseBytes(is.readInt());
		if (extensionHeaderLength < 0x32)
			throw new IllegalArgumentException("too short: " + extensionHeaderLength);
		is.readInt();
		is.readShort();
		final String requestId = string(is, stringTable).toString();
		System.out.println("Request-ID: " + requestId);
		final int connectionDetailsPtr = Integer.reverseBytes(is.readInt());

		// determine stops offset
		is.reset();
		is.skipBytes(connectionDetailsPtr + 0x0c);
		final short stopsOffset = Short.reverseBytes(is.readShort());
		System.out.println("stopsOffset: " + stopsOffset);

		// read stations
		is.reset();
		is.skipBytes(stationTablePtr);
		final byte[] stationTable = new byte[commentsTablePtr - stationTablePtr];
		is.readFully(stationTable);

		// read comments
		is.reset();
		is.skipBytes(commentsTablePtr);
		final byte[] commentsTable = new byte[connectionDetailsPtr - commentsTablePtr];
		is.readFully(commentsTable);

		// really read header
		is.reset();
		is.skipBytes(0x02);

		final Location resFrom = location(is, stringTable);
		System.out.println("From: " + resFrom.toDebugString());

		final Location resTo = location(is, stringTable);
		System.out.println("To: " + resTo.toDebugString());

		final short numConnections = Short.reverseBytes(is.readShort());
		System.out.println("Num connections: " + numConnections);

		is.readInt();

		is.readInt();

		final long resDate = date(is);

		final ResultHeader header = new ResultHeader(SERVER_PRODUCT, null, 0, null);

		final List<Connection> connections = new ArrayList<Connection>(numConnections);

		// read connections
		for (int iConnection = 0; iConnection < numConnections; iConnection++)
		{
			System.out.println("=== connection " + iConnection);

			is.reset();
			is.skipBytes(0x4a + iConnection * 12);

			is.readShort(); // service days

			final int offset = Integer.reverseBytes(is.readInt());

			final short numParts = Short.reverseBytes(is.readShort());
			System.out.println("  numParts: " + numParts);

			final short numChanges = Short.reverseBytes(is.readShort());
			System.out.println("  numChanges: " + numChanges);

			final long duration = time(is, 0);
			System.out.println("  duration: " + duration);

			is.reset();
			is.skipBytes(connectionDetailsPtr + 0x0e + iConnection * 2);
			final short connectionDetailsOffset = Short.reverseBytes(is.readShort());

			is.reset();
			is.skipBytes(connectionDetailsPtr + connectionDetailsOffset);
			final short hasRealtime = Short.reverseBytes(is.readShort());
			System.out.println("  hasRealtime: " + hasRealtime);
			
			final short delay = Short.reverseBytes(is.readShort());
			System.out.println("  delay: " + delay);
			
			final List<Connection.Part> parts = new ArrayList<Connection.Part>(numParts);

			for (int iPart = 0; iPart < numParts; iPart++)
			{
				System.out.println("  === part " + iPart);

				is.reset();
				is.skipBytes(0x4a + offset + iPart * 20);

				final long plannedDepartureTime = time(is, resDate);
				System.out.println("    plannedDepartureTime: " + new Date(plannedDepartureTime));

				final Location departure = station(is, stationTable, stringTable);
				System.out.println("    departure: " + departure.toDebugString());

				final long plannedArrivalTime = time(is, resDate);
				System.out.println("    plannedArrivalTime: " + new Date(plannedArrivalTime));

				final Location arrival = station(is, stationTable, stringTable);
				System.out.println("    arrival: " + arrival.toDebugString());

				final short type = Short.reverseBytes(is.readShort());
				System.out.println("    type: " + type);

				final String lineStr = string(is, stringTable).toString();
				System.out.println("    line: " + lineStr);

				final String departurePosition = string(is, stringTable).toString();
				System.out.println("    departurePosition: " + departurePosition);

				final String arrivalPosition = string(is, stringTable).toString();
				System.out.println("    arrivalPosition: " + arrivalPosition);

				System.out.println("    ?: " + is.readShort());

				final CharSequence[] comments = comments(is, commentsTable, stringTable);
				System.out.println("    " + Arrays.toString(comments));

				is.reset();
				is.skipBytes(connectionDetailsPtr + connectionDetailsOffset + 0x0c + iPart * 16);

				final long predictedDepartureTime = time(is, resDate);
				System.out.println("    predictedDepartureTime: " + new Date(predictedDepartureTime));

				final long predictedArrivalTime = time(is, resDate);
				System.out.println("    predictedArrivalTime: " + new Date(predictedArrivalTime));

				is.readInt();

				is.readInt();

				final short firstStopIndex = Short.reverseBytes(is.readShort());
				System.out.println("   firstStopIndex: " + firstStopIndex);

				final short numStops = Short.reverseBytes(is.readShort());
				System.out.println("   numStops: " + numStops);

				List<Stop> intermediateStops = null;

				if (numStops > 0)
				{
					is.reset();
					is.skipBytes(connectionDetailsPtr + stopsOffset + firstStopIndex);

					intermediateStops = new ArrayList<Stop>(numStops);

					for (int iStop = 0; iStop < numStops; iStop++)
					{
						final long plannedStopDepartureTime = time(is, resDate);
						System.out.println("      plannedStopDepartureTime: " + new Date(plannedStopDepartureTime));

						final long plannedStopArrivalTime = time(is, resDate);
						System.out.println("      plannedStopArrivalTime: " + new Date(plannedStopArrivalTime));

						is.readInt();

						is.readInt();

						final long predictedStopDepartureTime = time(is, resDate);
						System.out.println("      predictedStopDepartureTime: " + new Date(predictedStopDepartureTime));

						final long predictedStopArrivalTime = time(is, resDate);
						System.out.println("      predictedStopArrivalTime: " + new Date(predictedStopArrivalTime));

						is.readInt();

						is.readInt();

						final Location stopLocation = station(is, stationTable, stringTable);
						System.out.println("      stop: " + stopLocation.toDebugString());

						final Stop stop = new Stop(stopLocation, null, new Date(plannedStopDepartureTime));

						intermediateStops.add(stop);
					}
				}

				final Connection.Part part;
				if (type == 1)
				{
					final int min = (int) ((plannedArrivalTime - plannedDepartureTime) / 1000);

					part = new Connection.Footway(min, departure, arrival, null);
				}
				else
				{
					final Line line = parseLineWithoutType(lineStr);

					part = new Connection.Trip(line, null, new Date(plannedDepartureTime), null, departurePosition, departure, new Date(
							plannedArrivalTime), null, arrivalPosition, arrival, intermediateStops, null);
				}
				parts.add(part);
			}

			final Connection connection = new Connection(null, null, from, to, parts, null, null, (int) numChanges);

			connections.add(connection);
		}

		is.close();

		final QueryConnectionsResult result = new QueryConnectionsResult(header, uri, resFrom, via, resTo, new Context(requestId), connections);

		return result;
	}

	private Location location(final DataInputStream is, final byte[] stringTable) throws IOException
	{
		final String name = string(is, stringTable).toString();
		is.readShort();
		is.readShort();
		final int lon = Integer.reverseBytes(is.readInt());
		final int lat = Integer.reverseBytes(is.readInt());

		return new Location(LocationType.STATION, 0, lat, lon, null, name);
	}

	private Location station(final DataInputStream is, final byte[] stationTable, final byte[] stringTable) throws IOException
	{
		final short index = Short.reverseBytes(is.readShort());
		final int ptr = index * 14;

		final DataInputStream stationInputStream = new DataInputStream(new ByteArrayInputStream(stationTable, ptr, 14));

		try
		{
			final String name = string(stationInputStream, stringTable).toString();
			final int id = Integer.reverseBytes(stationInputStream.readInt());
			final int lon = Integer.reverseBytes(stationInputStream.readInt());
			final int lat = Integer.reverseBytes(stationInputStream.readInt());

			return new Location(LocationType.STATION, id, lat, lon, null, name);
		}
		finally
		{
			stationInputStream.close();
		}
	}

	private long date(final DataInputStream is) throws IOException
	{
		final short days = Short.reverseBytes(is.readShort());

		final Calendar date = new GregorianCalendar(timeZone());
		date.clear();
		date.set(Calendar.YEAR, 1980);
		date.set(Calendar.DAY_OF_YEAR, days);

		return date.getTimeInMillis();
	}

	private long time(final DataInputStream is, final long baseDate) throws IOException
	{
		final short value = Short.reverseBytes(is.readShort());

		final int hours = value / 100;
		final int minutes = value % 100;

		final Calendar time = new GregorianCalendar(timeZone());
		time.setTimeInMillis(baseDate);
		time.add(Calendar.HOUR, hours);
		time.add(Calendar.MINUTE, minutes);

		return time.getTimeInMillis();
	}

	private CharSequence string(final DataInputStream is, final byte[] stringTable) throws IOException
	{
		final short pointer = Short.reverseBytes(is.readShort());

		final InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(stringTable, pointer, stringTable.length - pointer), UTF_8);

		try
		{
			final StringBuilder builder = new StringBuilder();

			int c;
			while ((c = reader.read()) != 0)
				builder.append((char) c);

			return builder;
		}
		finally
		{
			reader.close();
		}
	}

	private CharSequence[] comments(final DataInputStream is, final byte[] commentsTable, final byte[] stringTable) throws IOException
	{
		final short pointer = Short.reverseBytes(is.readShort());

		final DataInputStream commentsInputStream = new DataInputStream(new ByteArrayInputStream(commentsTable, pointer, commentsTable.length
				- pointer));

		try
		{
			final short numComments = Short.reverseBytes(commentsInputStream.readShort());
			final CharSequence[] comments = new CharSequence[numComments];

			for (int i = 0; i < numComments; i++)
				comments[i] = string(commentsInputStream, stringTable);

			return comments;
		}
		finally
		{
			commentsInputStream.close();
		}
	}

	@Override
	protected char normalizeType(String type)
	{
		final String ucType = type.toUpperCase();

		if ("ECW".equals(ucType))
			return 'I';
		if ("IXB".equals(ucType)) // ICE International
			return 'I';
		if ("RRT".equals(ucType))
			return 'I';

		if ("DPF".equals(ucType)) // mit Dampflok bespannter Zug
			return 'R';
		if ("DAM".equals(ucType)) // Harzer Schmalspurbahnen: mit Dampflok bespannter Zug
			return 'R';
		if ("TW".equals(ucType)) // Harzer Schmalspurbahnen: Triebwagen
			return 'R';
		if ("RR".equals(ucType)) // Polen
			return 'R';
		if ("BAHN".equals(ucType))
			return 'R';
		if ("ZUGBAHN".equals(ucType))
			return 'R';
		if ("DAMPFZUG".equals(ucType))
			return 'R';

		if ("E".equals(ucType)) // Stadtbahn Karlsruhe: S4/S31/xxxxx
			return 'S';

		if ("BSV".equals(ucType))
			return 'B';
		if ("RUFBUS".equals(ucType)) // Rufbus
			return 'B';
		if ("RBS".equals(ucType)) // Rufbus
			return 'B';

		final char t = super.normalizeType(type);
		if (t != 0)
			return t;

		return 0;
	}
}

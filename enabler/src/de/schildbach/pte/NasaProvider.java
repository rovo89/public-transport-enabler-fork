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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryConnectionsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.util.ParserUtils;

/**
 * @author Andreas Schildbach
 */
public class NasaProvider extends AbstractHafasProvider
{
	public static final NetworkId NETWORK_ID = NetworkId.NASA;
	private static final String API_BASE = "http://reiseauskunft.insa.de/bin/";

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
	public QueryConnectionsResult queryConnections(Location from, Location via, Location to, Date date, boolean dep, int numConnections,
			String products, WalkSpeed walkSpeed, Accessibility accessibility, Set<Option> options) throws IOException
	{
		final String uri = connectionsQueryUri(from, via, to, date, dep, products);
		
		System.out.println("===== TRYING: " + uri);

		BufferedReader reader = new BufferedReader(new InputStreamReader(ParserUtils.scrapeInputStream(uri), ISO_8859_1));
		
		while(true)
		{
			String line = reader.readLine();
			if(line == null)
				break;
			
			System.out.println(line);
		}
		
		// TODO Auto-generated method stub
		return super.queryConnections(from, via, to, date, dep, numConnections, products, walkSpeed, accessibility, options);
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

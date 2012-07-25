package de.schildbach.pte;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import de.schildbach.pte.util.HafasBinaryFile;

public class TestHafasBinary {
	private static DateFormat dateFormat = new SimpleDateFormat("d.M. HH:mm");;
	
	public static void main(String[] args) throws Exception
	{
		char[] buf = getFile();
		HafasBinaryFile f = new HafasBinaryFile(buf, TimeZone.getTimeZone("CET"));
		
		System.out.println("Request ID: " + f.getRequestId());
		System.out.println("ld: " + f.getLoad());
		System.out.println("Sequence: " + f.getSeqNr());
		System.out.println("From: " + f.getFrom() + ", Type: " + f.getFrom().type);
		System.out.println("To: " + f.getTo() + ", Type: " + f.getTo().type);
		System.out.println();
		
		for (HafasBinaryFile.Connection c : f.getConnections())
		{
			System.out.println("---");
			System.out.println("Connection attributes: " + c.getAttributes());
			System.out.println("Changes: " + c.getNumChanges());
			System.out.println("Duration: " + c.getDuration());
			System.out.println("Service days: " + c.getServiceDays());
			System.out.println();

			for (HafasBinaryFile.Connection.Part p : c.getParts())
			{
				System.out.printf("Connection %d, Part %d\n", c.getIndex(), p.getIndex());
				System.out.println("Line: " + p.getLine());

				System.out.println("Departure station: " + p.getDepartureStation());
				System.out.printf("Departure time: %s (estimated: %s)\n",
						formatDate(p.getPlannedDepartureTime()), formatDate(p.getEstimatedDepartureTime()));
				System.out.printf("Departure platform: %s (estimated: %s)\n",
						p.getPlannedDeparturePlatform(), p.getEstimatedDeparturePlatform());
				
				for (HafasBinaryFile.Connection.Part.Stop s : p.getStops())
				{
					System.out.printf("Stop %s at %s to %s, track %s to %s (realtime: %s to %s, track %s to %s)\n",
							s.getStation(),
							formatDate(s.getPlannedArrivalTime()), formatDate(s.getPlannedDepartureTime()),
							s.getPlannedArrivalPlatform(), s.getPlannedDeparturePlatform(),
							formatDate(s.getEstimatedArrivalTime()), formatDate(s.getEstimatedDepartureTime()),
							s.getEstimatedArrivalPlatform(), s.getEstimatedDeparturePlatform());
				}
				
				System.out.println("Arrival station: " + p.getArrivalStation());
				System.out.printf("Arrival time: %s (estimated: %s)\n",
						formatDate(p.getPlannedArrivalTime()), formatDate(p.getEstimatedArrivalTime()));
				System.out.printf("Arrival platform: %s (estimated: %s)\n",
						p.getPlannedArrivalPlatform(), p.getEstimatedArrivalPlatform());			

				System.out.println("Walking duration: " + p.getFootwayDuration());
				System.out.println("Attributes: " + p.getAttributes());
				System.out.println("shouldHide: " + p.shouldHide());
				System.out.println("Remarks: " + p.getRemarks());

				System.out.println();
			}
		}
	}
	
	private static String formatDate(Date date) {
		return (date == null) ? "null" : dateFormat.format(date);
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
}

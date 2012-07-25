package de.schildbach.pte;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TimeZone;

import de.schildbach.pte.HafasBinaryFile;

public class TestHafasBinary {
	public static void main(String[] args) throws Exception
	{
		char[] buf = getFile();
		HafasBinaryFile f = new HafasBinaryFile(buf, TimeZone.getTimeZone("CET"));

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
						p.getPlannedDepartureTime(), p.getEstimatedDepartureTime());
				System.out.printf("Departure platform: %s (estimated: %s)\n",
						p.getPlannedDeparturePlatform(), p.getEstimatedDeparturePlatform());				
/*
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
				*/

				System.out.println("Arrival station: " + p.getArrivalStation());
				System.out.printf("Arrival time: %s (estimated: %s)\n",
						p.getPlannedArrivalTime(), p.getEstimatedArrivalTime());
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

	public static char[] getFile() throws IOException {
		InputStreamReader reader = new InputStreamReader(new FileInputStream("D:\\Android\\bahn\\heidelberg1"), "iso-8859-1");
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

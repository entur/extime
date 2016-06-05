package no.rutebanken.extime.routes.avinor;

//@Component(value = "avinor2GTFSConverter")
public class Avinor2GTFSConverter {

/*
	public File parse(AvinorDataset dataset) throws IOException {

		List<Airport> airports = dataset.getAirports();
		
		Set<String> airportNames = airports.stream().map(t -> t.getName()).collect(Collectors.toSet());
		
		Set<String> airlines = new HashSet<String>();
		
		for(Airport airport : airports) {
			for(Flight f : airport.getFlights().getFlight()) {
				airlines.add(f.getAirline());
			}
		}
		
			
		GtfsWriter writer = new GtfsWriter();
		
		String filename = UUID.randomUUID().toString();
		File tmpFolder = new File(System.getProperty("java.io.tmpdir"));
		
	    File gtfsFolder = new File(tmpFolder,filename);
		writer.setOutputLocation(gtfsFolder);
		
	    for(String code : airportNames) {
	    	Stop stop = new Stop();
	    	stop.setCode(code);
	    	stop.setId(new AgencyAndId("AVINOR", code));
	    	stop.setName("TODO");
	    	
	    	writer.handleEntity(stop);
	    }
	    
	    for(String airlineCode : airlines) {
	    	Agency agency = new Agency();
	    	agency.setId(airlineCode);
	    	agency.setName("TODO");
	    	agency.setUrl("http://www.fake.no");
	    	agency.setTimezone("Europe/Oslo");
	    
	    	writer.handleEntity(agency);
	    }

	    FeedInfo feedInfo = new FeedInfo();
	    feedInfo.setPublisherName("Avinor");
	    feedInfo.setPublisherUrl("http://www.avinor.no");
	    feedInfo.setLang("no");
	    feedInfo.setStartDate(new ServiceDate(new Date()));
	    // TODO add end date
	    // writer.handleEntity(feedInfo);
	    
	    writer.close();
	    
	    // Zip and return content
	    
	    
	    File destGtfsZipFile = new File("/tmp/avinor.zip");
		ZipUtil.pack(gtfsFolder, destGtfsZipFile);
	    
		return destGtfsZipFile;

	}
*/

}

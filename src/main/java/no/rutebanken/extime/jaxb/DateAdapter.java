package no.rutebanken.extime.jaxb;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateAdapter {

	public static Date parseDate(String s) {
		return DatatypeConverter.parseDate(s).getTime();
	}

	public static String printDate(Date dt) {
		Calendar cal = new GregorianCalendar();
		cal.setTime(dt);
		return DatatypeConverter.printDate(cal);
	}
}

package uk.sawcz.calendarchecker;

import microsoft.exchange.webservices.data.Appointment;
import microsoft.exchange.webservices.data.ServiceLocalException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by sawczc01 on 05/02/2015.
 */
public class Main
{
    public static class CalendarCheckerApp
    {
        public interface Listener
        {
            public void appointmentsCreated(List<Appointment> appointments);
        }

        private CalendarChecker calendarChecker;
        private final Listener listener;
        private CalendarChecker.Listener calendarCheckerListener = new CalendarChecker.Listener()
        {
            @Override
            public void newCalendarEventCreated(List<Appointment> appointment)
            {
                listener.appointmentsCreated(appointment);
            }
        };

        public CalendarCheckerApp(Listener listener)
        {
            try
            {
                calendarChecker = createCalendarCheckerFromStream(getClass().getResourceAsStream("userdetails.txt"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            this.listener = listener;
        }

        public void invoke()
        {
            calendarChecker.startChecking();
        }

        private CalendarChecker createCalendarCheckerFromStream(InputStream detailsStream) throws IOException
        {
            InputStream userDetailsStream = detailsStream;
            BufferedReader detailsReader = new BufferedReader(new InputStreamReader(userDetailsStream));
            String domain = detailsReader.readLine();
            String username = detailsReader.readLine();
            String password = detailsReader.readLine();
            String ewsEndpoint = detailsReader.readLine();

            return new CalendarChecker(calendarCheckerListener, domain, username, password, ewsEndpoint);
        }
    }

    public static void main(String... args)
    {
        System.out.println("CalendarChecker");
        new CalendarCheckerApp(new CalendarCheckerApp.Listener()
        {
            @Override
            public void appointmentsCreated(List<Appointment> appointments)
            {
                try
                {
                    for (Appointment appointment : appointments)
                    {
                        System.out.println("Appointment: " + appointment.getSubject() + " " + appointment.getStart());
                    }
                }
                catch (ServiceLocalException e)
                {
                    e.printStackTrace();
                }
            }
        }).invoke();
    }
}
